# Implementation Plan: ms-shipping

## Overview

Full implementation of the `ms-shipping` microservice. This service acts as an Anti-Corruption Layer (ACL) for integrating multiple external logistics providers (DHL, FedEx, Legacy) and manages the complete lifecycle of order shipments. It is a critical component of the Sequential Saga orchestrated by `ms-order` in Phase 3 of the project.

**Key responsibilities:**

1. **Consume** `OrderConfirmed` events from the `order-events` topic (produced by ms-order)
2. **Validate** delivery addresses (Colombia only, 5-digit postal codes)
3. **Generate** shipping labels with the selected logistics provider (DHL, FedEx, or Legacy)
4. **Upload** shipping labels (PDFs) to AWS S3
5. **Publish** `ShippingDispatched` events to the `shipping-events` topic with `orderId` as partition key
6. **Expose** REST endpoints for shipment management (query, list, update status, retry failed)
7. **Receive** tracking updates via webhooks from logistics providers

**Technology stack:**

- Spring Boot 4.0.3 + Spring WebFlux (reactive)
- PostgreSQL 17 with R2DBC
- Apache Kafka with Transactional Outbox Pattern
- AWS S3 for label storage (LocalStack in development)
- AWS Secrets Manager for credentials (LocalStack in development)
- Resilience4j (Circuit Breaker, Bulkhead, Retry)

---

## Implementation Rule

All modules (Model, UseCase, Driven Adapter, Entry Point) **MUST be generated using the Bancolombia Scaffold Plugin Gradle tasks**. Manual creation of module structure is **FORBIDDEN**.

```bash
# Always from ms-shipping/ root
./gradlew generateModel --name=<Name>
./gradlew generateUseCase --name=<Name>
./gradlew generateDrivenAdapter --type=<type> [--name=<name>]
./gradlew generateEntryPoint --type=<type>
./gradlew validateStructure
```

See `.agents/skills/scaffold-tasks/SKILL.md` for complete reference.

**MANDATORY REUSE:** Kafka Consumer pattern (`KafkaReceiver` + `KafkaConsumerConfig` + `KafkaConsumerLifecycle`), Kafka Producer, Outbox Relay, ProcessedEvents, and GlobalExceptionHandler **must be copied and adapted** from `ms-inventory`. Reason: `ReactiveKafkaConsumerTemplate` was removed in spring-kafka 4.0 (Spring Boot 4.0.3); the only correct approach is `KafkaReceiver` from reactor-kafka directly (§B.12 of design).

---

## Expected Final Structure

```
ms-shipping/
├── domain/
│   ├── model/
│   │   ├── shipment/           → Shipment, ShippingStatus, Carrier, DeliveryAddress
│   │   ├── outbox/             → OutboxEvent, EventType, OutboxStatus
│   │   ├── processedevent/     → ProcessedEvent
│   │   └── gateways/           → Ports (repositories, ShippingCarrier, Factory, S3Storage, SecretsManager)
│   └── usecase/
│       ├── processshipment/    → ProcessShipmentUseCase
│       ├── getshipment/        → GetShipmentUseCase
│       ├── listshipments/      → ListShipmentsUseCase
│       ├── updatestatus/       → UpdateShipmentStatusUseCase
│       ├── retryshipment/      → RetryShipmentUseCase
│       ├── processwebhook/     → ProcessWebhookUseCase
│       └── outboxrelay/        → OutboxRelayUseCase
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/   → Shipment, Outbox, ProcessedEvent repositories
│   │   ├── dhl-carrier/        → DHLShippingCarrier, DHLResponseParser, DHLWebhookParser
│   │   ├── fedex-carrier/      → FedExShippingCarrier, FedExResponseParser, FedExWebhookParser
│   │   ├── legacy-carrier/     → LegacyShippingCarrier, LegacyResponseParser, LegacyWebhookParser
│   │   ├── carrier-factory/    → ShippingCarrierFactoryImpl
│   │   ├── aws-s3/             → AwsS3StorageAdapter
│   │   ├── aws-secrets/        → AwsSecretsManagerAdapter
│   │   └── kafka-producer/     → KafkaOutboxRelay
│   └── entry-points/
│       ├── reactive-web/       → ShipmentController, ShipmentHandler, WebhookController, GlobalExceptionHandler
│       └── kafka-consumer/     → KafkaEventConsumer, Config, Lifecycle
└── applications/
    └── app-service/            → Spring Boot main, application.yaml, Resilience4j config
```

---

## Tasks

- [x] 1. Configure dependencies and application.yaml
  - [x] 1.1 Add dependencies in `build.gradle` (app-service) and `main.gradle`:
  - `io.projectreactor.kafka:reactor-kafka:1.3.25`
  - `org.springframework.boot:spring-boot-starter-data-r2dbc` (from BOM)
  - `org.postgresql:r2dbc-postgresql` (from BOM)
  - `io.github.resilience4j:resilience4j-spring-boot3` (from BOM)
  - `io.github.resilience4j:resilience4j-reactor` (from BOM)
  - `software.amazon.awssdk:s3` (version 2.20.0)
  - `software.amazon.awssdk:secretsmanager` (version 2.20.0)
  - `org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2`
  - `net.jqwik:jqwik:1.9.2` (testImplementation)
  - `io.projectreactor.tools:blockhound-junit-platform:1.0.16.RELEASE` (testImplementation)
  - _Versions from "Unified Versioning" table in `reusability.md`. Reference: `ms-inventory/build.gradle` + `ms-inventory/main.gradle`._

- [x] 1.2 Create `application.yaml` (base), `application-local.yaml` and `application-docker.yaml`:
  - **Base (`application.yaml`):**

    ```yaml
    spring:
      profiles:
        active: ${SPRING_PROFILES_ACTIVE:local}
      application:
        name: ms-shipping
      r2dbc:
        url: r2dbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5435}/${DB_NAME:shipping_db}
        username: ${DB_USER:postgres}
        password: ${DB_PASSWORD:postgres}

    server:
      port: ${SERVER_PORT:8085}

    kafka:
      consumer:
        group-id: shipping-service-group
        topics:
          order-events: order-events
      producer:
        topics:
          shipping-events: shipping-events

    shipping:
      carriers:
        dhl:
          api-key: ${aws.secretsmanager:/shipping/dhl/api-key}
          webhook-secret: ${aws.secretsmanager:/shipping/dhl/webhook-secret}
        fedex:
          api-key: ${aws.secretsmanager:/shipping/fedex/api-key}
          webhook-secret: ${aws.secretsmanager:/shipping/fedex/webhook-secret}
        legacy:
          api-key: ${aws.secretsmanager:/shipping/legacy/api-key}
          webhook-secret: ${aws.secretsmanager:/shipping/legacy/webhook-secret}
      s3:
        bucket-name: arka-shipping-labels
        region: us-east-1
      timeout:
        carrier-call: 30s

    scheduler:
      outbox-relay:
        interval: 5000 # 5 seconds

    resilience4j:
      circuitbreaker:
        instances:
          dhl-carrier:
            failure-rate-threshold: 50
            minimum-number-of-calls: 10
            wait-duration-in-open-state: 30s
            permitted-number-of-calls-in-half-open-state: 3
          fedex-carrier:
            failure-rate-threshold: 50
            minimum-number-of-calls: 10
            wait-duration-in-open-state: 30s
            permitted-number-of-calls-in-half-open-state: 3
          legacy-carrier:
            failure-rate-threshold: 50
            minimum-number-of-calls: 10
            wait-duration-in-open-state: 30s
            permitted-number-of-calls-in-half-open-state: 3
      retry:
        instances:
          dhl-carrier:
            max-attempts: 3
            wait-duration: 2s
            exponential-backoff-multiplier: 2
          fedex-carrier:
            max-attempts: 3
            wait-duration: 2s
            exponential-backoff-multiplier: 2
          legacy-carrier:
            max-attempts: 3
            wait-duration: 2s
            exponential-backoff-multiplier: 2
      bulkhead:
        instances:
          dhl-carrier:
            max-concurrent-calls: 10
          fedex-carrier:
            max-concurrent-calls: 10
          legacy-carrier:
            max-concurrent-calls: 10

    springdoc:
      api-docs:
        path: /api-docs
      swagger-ui:
        path: /swagger-ui.html
        enabled: true
    ```

  - **Local (`application-local.yaml`):**

    ```yaml
    spring:
      kafka:
        bootstrap-servers: localhost:9092

    aws:
      endpoint: http://localhost:4566 # LocalStack
      region: us-east-1
      access-key-id: test
      secret-access-key: test

    logging:
      level:
        com.arka: DEBUG
        io.r2dbc: DEBUG
    ```

  - **Docker (`application-docker.yaml`):**

    ```yaml
    spring:
      r2dbc:
        url: r2dbc:postgresql://postgres-shipping:5432/shipping_db
      kafka:
        bootstrap-servers: arka-kafka:9092

    aws:
      endpoint: http://localstack:4566
      region: us-east-1
      access-key-id: test
      secret-access-key: test

    logging:
      level:
        com.arka: INFO
    ```

---

- [x] 2. Create database schema
  - [x] 2.1 Create `schema.sql` in `applications/app-service/src/main/resources/`:

  ```sql
  -- shipments table
  CREATE TABLE IF NOT EXISTS shipments (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      order_id UUID NOT NULL,
      tracking_number VARCHAR(100) UNIQUE,
      carrier VARCHAR(20) NOT NULL,
      shipping_label_url TEXT,
      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      delivery_address JSONB NOT NULL,
      estimated_delivery_date TIMESTAMP,
      actual_delivery_date TIMESTAMP,
      failure_reason TEXT,
      created_at TIMESTAMP NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

      CONSTRAINT chk_status CHECK (status IN ('PENDING', 'LABEL_GENERATED', 'IN_TRANSIT', 'DELIVERED', 'FAILED')),
      CONSTRAINT chk_carrier CHECK (carrier IN ('DHL', 'FEDEX', 'LEGACY'))
  );

  CREATE INDEX IF NOT EXISTS idx_shipments_order_id ON shipments(order_id);
  CREATE INDEX IF NOT EXISTS idx_shipments_tracking_number ON shipments(tracking_number);
  CREATE INDEX IF NOT EXISTS idx_shipments_status ON shipments(status);
  CREATE INDEX IF NOT EXISTS idx_shipments_carrier ON shipments(carrier);
  CREATE INDEX IF NOT EXISTS idx_shipments_created_at ON shipments(created_at DESC);

  -- outbox_events table
  CREATE TABLE IF NOT EXISTS outbox_events (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      event_type VARCHAR(50) NOT NULL,
      topic VARCHAR(100) NOT NULL DEFAULT 'shipping-events',
      partition_key VARCHAR(100) NOT NULL,
      payload JSONB NOT NULL,
      status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
      created_at TIMESTAMP NOT NULL DEFAULT NOW(),

      CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
  );

  CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_events(status, created_at) WHERE status = 'PENDING';

  -- processed_events table
  CREATE TABLE IF NOT EXISTS processed_events (
      event_id UUID PRIMARY KEY,
      processed_at TIMESTAMP NOT NULL DEFAULT NOW()
  );

  CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(processed_at);
  ```

- [x] 2.2 Configure R2DBC to run schema on startup:
  - Add `@Bean ConnectionFactoryInitializer` in `MainApplication` or a `@Configuration` class
  - Use `ResourceDatabasePopulator` with `schema.sql`

---

- [x] 3. Implement domain model (`domain/model`)
  - [x] 3.1 Generate model module with Scaffold:

  ```bash
  cd ms-shipping && ./gradlew generateModel --name=Shipment
  ```

- [x] 3.2 Create domain entities in `com.arka.model.shipment`:
  - **`Shipment`** — record with all fields from design
    - `@Builder(toBuilder = true)`, Lombok
    - Compact constructor with validations
    - Helper methods: `isPending()`, `isLabelGenerated()`, `isFailed()`, `isDelivered()`
  - **`ShippingStatus`** — sealed interface with records: `Pending`, `LabelGenerated`, `InTransit`, `Delivered`, `Failed`
    - Static method `fromValue(String value)`
    - Each record implements `value()` method
  - **`Carrier`** — enum with values `DHL`, `FEDEX`, `LEGACY`
    - Static method `fromValue(String value)`
  - **`DeliveryAddress`** — record with `street`, `city`, `state`, `postalCode`, `country`
    - Compact constructor validates: all fields required, country = "CO", postalCode matches `\d{5}`
  - **`ShippingResult`** — record with `success`, `trackingNumber`, `labelPdf` (byte[]), `estimatedDeliveryDate`, `reason`
    - Compact constructor validates: if success, trackingNumber and labelPdf required; if not success, reason required

- [x] 3.3 Create outbox model in `com.arka.model.outbox`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/outboxevent/`
  - **`OutboxEvent`** — record with `id`, `eventType`, `topic`, `partitionKey`, `payload`, `status`, `createdAt`
  - **`OutboxStatus`** — enum with `PENDING`, `PUBLISHED`
  - **`EventType`** — enum with `SHIPPING_DISPATCHED`
  - **`DomainEventEnvelope`** — record with standard envelope fields, `MS_SOURCE = "ms-shipping"`

- [x] 3.4 Create processed events model in `com.arka.model.processedevent`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/processedevent/`
  - **`ProcessedEvent`** — simple record with `UUID eventId`, `Instant processedAt`

- [x] 3.5 Create gateway ports in `com.arka.model.shipment.gateways`:
  - **`ShipmentRepository`** — interface with methods:
    - `Mono<Shipment> save(Shipment shipment)`
    - `Mono<Shipment> findByOrderId(UUID orderId)`
    - `Mono<Shipment> findByTrackingNumber(String trackingNumber)`
    - `Mono<Shipment> updateStatus(UUID orderId, String newStatus, Instant actualDeliveryDate)`
    - `Flux<Shipment> findByFilters(String status, String carrier, int page, int size)`
  - **`ShippingCarrier`** — interface with methods:
    - `Mono<ShippingResult> generateLabel(UUID orderId, DeliveryAddress address)`
    - `Carrier supportedCarrier()`
  - **`ShippingCarrierFactory`** — interface with method:
    - `ShippingCarrier getCarrier(Carrier carrier)`
  - **`S3Storage`** — interface with method:
    - `Mono<String> uploadFile(byte[] content, String key, String contentType)`
  - **`SecretsManager`** — interface with method:
    - `Mono<String> getSecret(String secretName)`

- [x] 3.6 Create gateway ports in `com.arka.model.outbox.gateways`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/outboxevent/gateways/`
  - **`OutboxEventRepository`** — interface with methods for outbox pattern

- [x] 3.7 Create gateway ports in `com.arka.model.processedevent.gateways`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/processedevent/gateways/`
  - **`ProcessedEventRepository`** — interface with methods for idempotency

- [x] 3.8 Create domain exceptions in `com.arka.model.exceptions`:
  - **`DomainException`** — abstract base class
  - **`ShipmentNotFoundException`** — 404
  - **`InvalidShipmentStateException`** — 409
  - **`ShippingCarrierUnavailableException`** — 503
  - **`InvalidCarrierException`** — 400
  - **`InvalidShippingStatusException`** — 400
  - **`DuplicateTrackingNumberException`** — 409
  - **`CircuitBreakerOpenException`** — 503
  - **`BulkheadFullException`** — 503
  - **`InvalidDeliveryAddressException`** — 400
  - **`S3UploadException`** — 500
  - **`InvalidWebhookSignatureException`** — 401

---

- [x] 4. Implement use cases (`domain/usecase`)
  - [x] 4.1 Generate use case modules with Scaffold
    - Execute `./gradlew generateUseCase --name=ProcessShipment` from `ms-shipping/`
    - Execute `./gradlew generateUseCase --name=GetShipment` from `ms-shipping/`
    - Execute `./gradlew generateUseCase --name=ListShipments` from `ms-shipping/`
    - Execute `./gradlew generateUseCase --name=UpdateShipmentStatus` from `ms-shipping/`
    - Execute `./gradlew generateUseCase --name=RetryShipment` from `ms-shipping/`
    - Execute `./gradlew generateUseCase --name=ProcessWebhook` from `ms-shipping/`
    - Execute `./gradlew generateUseCase --name=OutboxRelay` from `ms-shipping/`
    - Verify modules are created in `domain/usecase/`

  - [x] 4.2 Implement `ProcessShipmentUseCase`
    - Inject dependencies: `ShipmentRepository`, `OutboxEventRepository`, `ProcessedEventRepository`, `ShippingCarrierFactory`, `S3Storage`
    - Implement `execute(OrderConfirmedEvent)`: verify idempotency (processed_events), extract orderId/customerId/deliveryAddress/preferredCarrier, validate address, select carrier via Factory, invoke `generateLabel()`, parse response, upload label to S3, persist Shipment with status LABEL_GENERATED or FAILED, insert ShippingDispatched event in outbox (only if successful), register eventId in processed_events
    - All operations in a single R2DBC transaction using `@Transactional`
    - Handle errors with `onErrorResume()` to prevent blocking subsequent events

  - [x] 4.3 Implement `GetShipmentUseCase`
    - Inject `ShipmentRepository`
    - Implement `execute(UUID orderId, String customerId, boolean isAdmin)`: if admin, find by orderId; if not, find by orderId and customerId; throw `ShipmentNotFoundException` if not found

  - [x] 4.4 Implement `ListShipmentsUseCase`
    - Inject `ShipmentRepository`
    - Implement `execute(String status, String carrier, int page, int size, boolean isAdmin)`: filter by status and carrier (optional), paginate, return Flux<Shipment>
    - Only ADMIN role allowed

  - [x] 4.5 Implement `UpdateShipmentStatusUseCase`
    - Inject `ShipmentRepository`
    - Implement `execute(UUID orderId, String newStatus, Instant actualDeliveryDate)`: update status, if status=DELIVERED set actualDeliveryDate, if status=FAILED require failureReason
    - Only ADMIN role allowed

  - [x] 4.6 Implement `RetryShipmentUseCase`
    - Inject dependencies: `ShipmentRepository`, `OutboxEventRepository`, `ShippingCarrierFactory`, `S3Storage`
    - Implement `execute(UUID orderId)`: find Shipment with status FAILED, retry label generation with same carrier and address, upload label to S3, update status to LABEL_GENERATED (if successful) or maintain FAILED (if fails), insert ShippingDispatched event only if successful
    - Only ADMIN role allowed

  - [x] 4.7 Implement `ProcessWebhookUseCase`
    - Inject `ShipmentRepository`
    - Implement `execute(TrackingUpdate)`: find Shipment by trackingNumber, update status according to carrier-reported state (IN_TRANSIT, DELIVERED), set actualDeliveryDate if delivered

  - [x] 4.8 Implement `OutboxRelayUseCase`
    - **Copy** from `ms-inventory/domain/usecase/outboxrelay/OutboxRelayUseCase.java`
    - Adapt: `BATCH_SIZE=100`, `fetchPendingEvents`, `markAsPublished`
    - Inject `OutboxEventRepository`

  - [x] 4.9 Write unit tests for use cases
    - Test for `ProcessShipmentUseCase`: verify idempotency, address validation, carrier selection, label generation, S3 upload, event insertion
    - Test for `GetShipmentUseCase`: verify authorization (ADMIN vs CUSTOMER)
    - Test for `RetryShipmentUseCase`: verify retry logic, status transitions
    - Use Mockito for mocks, StepVerifier for reactive verification

- [x] 5. Checkpoint — Verify domain and use cases
  - Ensure all tests pass, ask user if questions arise

## Notes

- **MANDATORY REUSE:** All transversal patterns (Outbox Relay, Kafka Consumer with `KafkaReceiver`, Kafka Producer, ProcessedEvents, GlobalExceptionHandler) **MUST be copied and adapted** from `ms-inventory`. Reason: `ReactiveKafkaConsumerTemplate` was removed in spring-kafka 4.0 (Spring Boot 4.0.3); the only correct approach is `KafkaReceiver` from reactor-kafka directly (§B.12 of design).
- **Scaffold Plugin is MANDATORY** for generating modules (Model, UseCase, Driven Adapter, Entry Point) — never create module structure manually.
- **Unified Versioning** according to `reusability.md` — same versions across the entire monorepo.
- **Spring Profiles**: `local` (default for development) and `docker` (injected by Compose).
- **R2DBC Transactions**: Use `@Transactional` for atomic operations (Shipment + OutboxEvent + ProcessedEvent).
- **Resilience4j**: Circuit Breaker, Bulkhead, and Retry configured per carrier (DHL, FedEx, Legacy).
- **AWS LocalStack**: Used in `local` profile for S3 and Secrets Manager simulation.
- **Idempotency**: Three levels — Kafka (processed_events table), Database (UNIQUE constraint on tracking_number), Application (validation before INSERT).
- **Blocking SDKs**: All carrier SDKs are blocking — wrap with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`.
- **Timeout**: 30 seconds per carrier call using Reactor's `timeout()` operator.
- **Webhook Validation**: HMAC signature validation using shared secret per carrier.
- **Address Validation**: Colombia only (country = "CO"), 5-digit postal codes.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["2.1", "2.2"] },
    { "id": 2, "tasks": ["3.1"] },
    { "id": 3, "tasks": ["3.2", "3.3", "3.4", "3.5", "3.6", "3.7", "3.8"] },
    { "id": 4, "tasks": ["4.1"] },
    { "id": 5, "tasks": ["4.2", "4.3", "4.4", "4.5", "4.6", "4.7", "4.8"] },
    { "id": 6, "tasks": ["4.9"] },
    { "id": 7, "tasks": ["6.1", "6.2", "6.3", "6.4", "6.5"] },
    { "id": 8, "tasks": ["7.1", "7.2"] },
    { "id": 9, "tasks": ["8.1", "8.2", "8.3"] },
    { "id": 10, "tasks": ["9.1", "9.2", "9.3"] },
    { "id": 11, "tasks": ["10.1", "10.2", "10.3", "10.4"] },
    { "id": 12, "tasks": ["11.1", "11.2", "11.3"] }
  ]
}
```

---

## Wave 6 — REST API Production-Ready (DTOs, Validation, Authorization, OpenAPI)

- [x] 6. Refine REST API layer for production readiness
  - [x] 6.1 Create request/response DTOs in `com.arka.api.dto`:
    - **`ShipmentResponse`** — record with `@Builder`: `id`, `orderId`, `trackingNumber`, `carrier`, `shippingLabelUrl`, `status`, `deliveryAddress` (nested), `estimatedDeliveryDate`, `actualDeliveryDate`, `failureReason`, `createdAt`, `updatedAt`
    - **`DeliveryAddressDto`** — record: `street`, `city`, `state`, `postalCode`, `country`
    - **`UpdateStatusRequest`** — record with `@NotBlank String status`, `@Nullable String failureReason`, `@Nullable Instant actualDeliveryDate`
    - **`WebhookPayload`** — record with `@NotBlank String trackingNumber`, `@NotBlank String status`, `@Nullable Instant deliveryDate`
    - **`PageResponse<T>`** — generic record with `List<T> content`, `int page`, `int size`, `long totalElements`
    - Replace raw `Map<String, String>` in controller with strongly-typed DTOs
    - _Requisitos: Clean Architecture (domain no se expone directamente), Bean Validation_

  - [x] 6.2 Create `ShipmentDtoMapper` in `com.arka.api.mapper`:
    - Static methods: `toResponse(Shipment)`, `toDeliveryAddressDto(DeliveryAddress)`
    - Use `@NoArgsConstructor(access = AccessLevel.PRIVATE)` + `final` class
    - _Requisitos: Separation of concerns_

  - [x] 6.3 Add role-based authorization via request headers:
    - Extract `X-User-Email` and `X-User-Role` headers in controller
    - `GET /shipments/{orderId}`: if not ADMIN, pass customerId to use case (restrict to own shipments)
    - `GET /shipments`: ADMIN only — return 403 if not ADMIN
    - `PUT /shipments/{orderId}/status`: ADMIN only — return 403 if not ADMIN
    - `POST /shipments/retry/{orderId}`: ADMIN only — return 403 if not ADMIN
    - Webhook endpoints: no auth header required (validated by HMAC signature)
    - _Requisitos: req 3 (GET), req 5 (PUT), req 6 (POST retry)_

  - [x] 6.4 Refactor controller to `ShipmentController` + `WebhookController`:
    - **`ShipmentController`** (`/api/v1/shipments`):
      - `GET /{orderId}` → 200 `ShipmentResponse` or 404
      - `GET /` → 200 `PageResponse<ShipmentResponse>` (query params: status, carrier, page, size)
      - `PUT /{orderId}/status` → 200 `ShipmentResponse` or 404/409
      - `POST /retry/{orderId}` → 200 `ShipmentResponse` or 404/409
    - **`WebhookController`** (`/api/v1/webhooks`):
      - `POST /{carrier}/tracking` → 200 (accepts tracking updates per carrier)
      - Validate `X-Webhook-Signature` header using HMAC-SHA256 with carrier-specific secret
      - Return 401 if signature invalid
    - Add `@Tag(name = "Shipments")`, `@Operation(summary = ...)`, `@ApiResponse` for Springdoc
    - _Requisitos: req 1-6 (REST endpoints), req 8 (webhook per carrier), req 16 (HMAC)_

  - [x] 6.5 Add Bean Validation on requests:
    - `@Valid` on all `@RequestBody` parameters
    - `UpdateStatusRequest`: `@NotBlank status`, validate status is a valid `ShippingStatus` value
    - `WebhookPayload`: `@NotBlank trackingNumber`, `@NotBlank status`
    - Return 400 with `ErrorResponse` listing validation failures (already handled by `GlobalExceptionHandler`)
    - _Requisitos: req 12 (input validation)_

---

## Wave 7 — Secrets Manager Adapter + Webhook HMAC Validation

- [x] 7. Implement AWS Secrets Manager adapter and webhook signature validation
  - [ ] 7.1 Create `AwsSecretsManagerAdapter` in driven adapter module:
    - Implement `SecretsManager` port from domain model
    - Use `software.amazon.awssdk:secretsmanager` (already in dependencies)
    - Create `SecretsManagerConfig` class with `@Bean SecretsManagerAsyncClient` (endpoint from `aws.endpoint` property for LocalStack)
    - Implementation: `getSecret(String secretName)` → wraps `client.getSecretValue(req).thenApply(...)` in `Mono.fromFuture()`
    - Cache secrets in-memory with a TTL of 5 minutes to reduce calls
    - _Requisitos: req 15 (credentials via Secrets Manager)_

  - [x] 7.2 Implement webhook HMAC-SHA256 signature validation:
    - Create `WebhookSignatureValidator` component in reactive-web module
    - Method: `boolean isValid(String carrier, String payload, String signatureHeader)`
    - Use `SecretsManager` to retrieve `shipping/{carrier}/webhook-secret`
    - Compute `HMAC-SHA256(secret, rawBody)` and compare with `X-Webhook-Signature` header
    - Called from `WebhookController` before processing webhook payload
    - Return 401 `InvalidWebhookSignatureException` if mismatch
    - _Requisitos: req 16 (webhook HMAC validation)_

---

## Wave 8 — Resilience4j Annotations on Carrier Adapters

- [x] 8. Add Resilience4j patterns to carrier adapter calls
  - [x] 8.1 Annotate carrier adapters with Resilience4j:
    - Add `@CircuitBreaker(name = "dhl-carrier", fallbackMethod = "carrierFallback")` on `DHLShippingCarrier.generateLabel()`
    - Add `@Retry(name = "dhl-carrier")` on `DHLShippingCarrier.generateLabel()`
    - Add `@Bulkhead(name = "dhl-carrier", type = Bulkhead.Type.SEMAPHORE)` on `DHLShippingCarrier.generateLabel()`
    - Repeat for `FedExShippingCarrier` and `LegacyShippingCarrier` with their respective instance names
    - **Fallback method**: return `Mono<ShippingResult>` with `success=false`, `reason="Circuit breaker open for {carrier}"`
    - _Requisitos: req 13 (Circuit Breaker 50%/30s), req 14 (Retry 3x exponential), req 14 (Bulkhead 10 concurrent)_

  - [x] 8.2 Add resilience4j-reactor dependency in carrier-factory module:
    - `implementation 'io.github.resilience4j:resilience4j-reactor'`
    - `implementation 'io.github.resilience4j:resilience4j-spring-boot3'`
    - Verify that `resilience4j.circuitbreaker.instances.*` in application.yaml is picked up
    - Write test verifying Circuit Breaker opens after configured failures
    - _Requisitos: req 13, req 14_

  - [x] 8.3 Add `@EnableScheduling` to MainApplication (if missing):
    - Required for `@Scheduled` on `KafkaOutboxRelay.relay()`
    - Verify outbox relay runs every 5s in local profile
    - _Requisitos: Outbox Pattern relay scheduling_

---

## Wave 9 — Actuator, Health, and Observability

- [x] 9. Configure Spring Actuator and observability
  - [x] 9.1 Configure Actuator endpoints in `application.yaml`:
    ```yaml
    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus,metrics
      endpoint:
        health:
          show-details: when-authorized
          probes:
            enabled: true
      health:
        r2dbc:
          enabled: true
    ```
    - Expose `/actuator/health/liveness` and `/actuator/health/readiness` for Kubernetes probes
    - _Requisitos: Production readiness, compose.yaml healthcheck already uses `/actuator/health`_

  - [x] 9.2 Add custom health indicators:
    - **`KafkaHealthIndicator`** — checks Kafka broker connectivity
    - **`S3HealthIndicator`** — checks S3 bucket exists (head-bucket call)
    - Both return `Health.up()` or `Health.down()` with details
    - _Requisitos: Observability for dependent services_

  - [ ] 9.3 Add Micrometer metrics for business operations:
    - Counter: `shipping.labels.generated` (tag: carrier)
    - Counter: `shipping.labels.failed` (tag: carrier, reason)
    - Timer: `shipping.carrier.duration` (tag: carrier)
    - Counter: `shipping.webhooks.received` (tag: carrier)
    - Counter: `shipping.webhooks.invalid_signature` (tag: carrier)
    - Gauge: `shipping.outbox.pending_events`
    - Instrument in `ProcessShipmentUseCase`, carrier adapters, and webhook controller
    - _Requisitos: Observability, Prometheus scraping_

---

## Wave 10 — Complete Test Coverage

- [x] 10. Write comprehensive tests
  - [x] 10.1 Complete unit tests for all use cases:
    - `ListShipmentsUseCaseTest` — verify filtering, pagination, ADMIN-only
    - `UpdateShipmentStatusUseCaseTest` — verify state transitions, invalid transitions throw 409
    - `ProcessWebhookUseCaseTest` — verify tracking number lookup, status update
    - `OutboxRelayUseCaseTest` — verify batch fetch + mark as published
    - Use Mockito + StepVerifier
    - _Requisitos: All use case logic_

  - [x] 10.2 Write REST controller integration tests:
    - `ShipmentControllerTest` with `@WebFluxTest`:
      - Test GET /{orderId} — 200, 404, 403 (non-admin)
      - Test GET / — 200 with pagination, 403 (non-admin)
      - Test PUT /{orderId}/status — 200, 400 (invalid status), 403
      - Test POST /retry/{orderId} — 200, 404, 409, 403
    - `WebhookControllerTest`:
      - Test POST /{carrier}/tracking — 200, 401 (invalid sig)
    - Mock use cases with `@MockBean`
    - Use `WebTestClient`
    - _Requisitos: REST layer validation_

  - [ ] 10.3 Write Kafka consumer integration test:
    - `KafkaEventConsumerTest`:
      - Verify `OrderConfirmed` event is consumed and forwarded to `ProcessShipmentUseCase`
      - Verify unknown `eventType` is ignored (offset committed without processing)
      - Verify error handling: malformed JSON doesn't block consumer
      - Mock `ProcessShipmentUseCase`, verify invocations
    - _Requisitos: Kafka consumer resilience_

  - [ ] 10.4 Write carrier adapter test with Resilience4j:
    - `DHLShippingCarrierTest`:
      - Verify label generation returns ShippingResult on success
      - Verify timeout after 30s triggers failure result
      - Verify Circuit Breaker opens after 50% failure rate
    - Use `@SpringBootTest` with real Resilience4j config
    - _Requisitos: req 13, req 14_

---

## Wave 11 — Documentation, README, and Final Validation

- [x] 11. Documentation and final refinements
  - [x] 11.1 Create proper `README.md` for ms-shipping:
    - Service description and responsibilities
    - Stack and architecture overview
    - How to run locally: `./gradlew bootRun` (requires PostgreSQL + Kafka + LocalStack)
    - How to run tests: `./gradlew test`
    - How to build Docker image: `docker build -t ms-shipping:latest -f deployment/Dockerfile .`
    - API endpoints table with methods, paths, descriptions, auth requirements
    - Kafka topics consumed/produced
    - Configuration variables table
    - _Requisitos: Developer onboarding_

  - [x] 11.2 Verify `@EnableScheduling` + outbox relay scheduling:
    - Ensure `MainApplication` has `@EnableScheduling`
    - Verify `KafkaOutboxRelay.relay()` fires every 5s
    - Verify `KafkaConsumerLifecycle` starts consuming on app startup
    - _Requisitos: Background processing_

  - [x] 11.3 Final build + architecture validation:
    - Run `./gradlew clean build` — must pass with no errors
    - Run `./gradlew validateStructure` — must pass Clean Architecture validation
    - Verify no domain layer imports from infrastructure
    - All tests green
    - _Requisitos: Final checkpoint_
