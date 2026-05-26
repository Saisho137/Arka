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

### Task 1 — Configure dependencies and application.yaml


- [ ] 1.1 Add dependencies in `build.gradle` (app-service) and `main.gradle`:
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

- [ ] 1.2 Create `application.yaml` (base), `application-local.yaml` and `application-docker.yaml`:
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
        interval: 5000  # 5 seconds
    
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
      endpoint: http://localhost:4566  # LocalStack
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

### Task 2 — Create database schema

- [ ] 2.1 Create `schema.sql` in `applications/app-service/src/main/resources/`:
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

- [ ] 2.2 Configure R2DBC to run schema on startup:
  - Add `@Bean ConnectionFactoryInitializer` in `MainApplication` or a `@Configuration` class
  - Use `ResourceDatabasePopulator` with `schema.sql`

---

### Task 3 — Implement domain model (`domain/model`)

- [ ] 3.1 Generate model module with Scaffold:
  ```bash
  cd ms-shipping && ./gradlew generateModel --name=Shipment
  ```

- [ ] 3.2 Create domain entities in `com.arka.model.shipment`:
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

- [ ] 3.3 Create outbox model in `com.arka.model.outbox`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/outboxevent/`
  - **`OutboxEvent`** — record with `id`, `eventType`, `topic`, `partitionKey`, `payload`, `status`, `createdAt`
  - **`OutboxStatus`** — enum with `PENDING`, `PUBLISHED`
  - **`EventType`** — enum with `SHIPPING_DISPATCHED`
  - **`DomainEventEnvelope`** — record with standard envelope fields, `MS_SOURCE = "ms-shipping"`

- [ ] 3.4 Create processed events model in `com.arka.model.processedevent`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/processedevent/`
  - **`ProcessedEvent`** — simple record with `UUID eventId`, `Instant processedAt`

- [ ] 3.5 Create gateway ports in `com.arka.model.shipment.gateways`:
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

- [ ] 3.6 Create gateway ports in `com.arka.model.outbox.gateways`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/outboxevent/gateways/`
  - **`OutboxEventRepository`** — interface with methods for outbox pattern

- [ ] 3.7 Create gateway ports in `com.arka.model.processedevent.gateways`:
  - **Copy** from `ms-inventory/domain/model/src/main/java/com/arka/model/processedevent/gateways/`
  - **`ProcessedEventRepository`** — interface with methods for idempotency

- [ ] 3.8 Create domain exceptions in `com.arka.model.exceptions`:
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

### Task 4 — Implement use cases (`domain/usecase`)

