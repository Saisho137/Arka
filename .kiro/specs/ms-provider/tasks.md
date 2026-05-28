# Implementation Plan: ms-provider

## Overview

Full implementation of the `ms-provider` microservice. This service acts as an Anti-Corruption Layer (ACL) for integrating multiple external suppliers and manages the complete lifecycle of purchase orders for stock replenishment. It is the main component of Phase 4 of the project.

**Key responsibilities:**

1. **Consume** `StockDepleted` events from the `inventory-events` topic (produced by ms-inventory)
2. **Generate** purchase orders automatically with preferred supplier and optimal quantity
3. **Publish** `PurchaseOrderCreated` and `PurchaseOrderSent` events to `provider-events` topic
4. **Expose** REST endpoints for supplier management (CRUD + product assignment)
5. **Expose** REST endpoints for purchase order management (list, detail, state transitions, manual creation)
6. **Delegate** email notifications to ms-notifications via Kafka events

**Technology stack:**

- Spring Boot 4.0.3 + Spring WebFlux (reactive)
- PostgreSQL 17 with R2DBC
- Apache Kafka with Transactional Outbox Pattern
- KafkaReceiver (reactor-kafka) for consumption
- Bancolombia Scaffold Plugin 4.2.0

---

## Implementation Rule

All modules (Model, UseCase, Driven Adapter, Entry Point) **MUST be generated using the Bancolombia Scaffold Plugin Gradle tasks**. Manual creation of module structure is **FORBIDDEN**.

```bash
# Always from ms-provider/ root
./gradlew generateModel --name=<Name>
./gradlew generateUseCase --name=<Name>
./gradlew generateDrivenAdapter --type=<type> [--name=<name>]
./gradlew generateEntryPoint --type=<type>
./gradlew validateStructure
```

See `.agents/skills/scaffold-tasks/SKILL.md` for complete reference.

**MANDATORY REUSE:** Kafka Consumer pattern (`KafkaReceiver` + `KafkaConsumerConfig` + `KafkaConsumerLifecycle`), Kafka Producer, Outbox Relay, ProcessedEvents, GlobalExceptionHandler, and PageResponse **must be copied and adapted** from `ms-inventory`. Reason: `ReactiveKafkaConsumerTemplate` was removed in spring-kafka 4.0 (Spring Boot 4.0.3); the only correct approach is `KafkaReceiver` from reactor-kafka directly.

---

## Expected Final Structure

```
ms-provider/
├── domain/
│   ├── model/
│   │   ├── supplier/           → Supplier, SupplierProduct
│   │   ├── purchaseorder/      → PurchaseOrder, PurchaseOrderItem, PurchaseOrderStatus
│   │   ├── outbox/             → OutboxEvent, EventType, OutboxStatus
│   │   ├── processedevent/     → ProcessedEvent
│   │   └── gateways/           → Ports (SupplierRepository, SupplierProductRepository, PurchaseOrderRepository, OutboxEventRepository, ProcessedEventRepository)
│   └── usecase/
│       ├── generatepurchaseorder/   → GeneratePurchaseOrderUseCase
│       ├── createsupplier/          → CreateSupplierUseCase
│       ├── getsupplier/             → GetSupplierUseCase
│       ├── listsuppliers/           → ListSuppliersUseCase
│       ├── updatesupplier/          → UpdateSupplierUseCase
│       ├── deactivatesupplier/      → DeactivateSupplierUseCase
│       ├── assignsupplierproduct/   → AssignSupplierProductUseCase
│       ├── listpurchaseorders/      → ListPurchaseOrdersUseCase
│       ├── getpurchaseorder/        → GetPurchaseOrderUseCase
│       ├── transitionpurchaseorder/ → TransitionPurchaseOrderUseCase
│       ├── createmanualpo/          → CreateManualPurchaseOrderUseCase
│       └── outboxrelay/             → OutboxRelayUseCase
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/   → All R2DBC adapters (Supplier, SupplierProduct, PurchaseOrder, Outbox, ProcessedEvent)
│   │   └── kafka-producer/     → KafkaOutboxRelay
│   └── entry-points/
│       ├── reactive-web/       → SupplierController, SupplierHandler, PurchaseOrderController, PurchaseOrderHandler, GlobalExceptionHandler
│       └── kafka-consumer/     → KafkaEventConsumer, KafkaConsumerConfig, KafkaConsumerLifecycle
└── applications/
    └── app-service/            → Spring Boot main, application.yaml, schema.sql
```

---

## Tasks

- [x] 1. Configure dependencies and application.yaml
  - [x] 1.1 Add dependencies in `build.gradle` (app-service) and `main.gradle`:
    - `io.projectreactor.kafka:reactor-kafka:1.3.25`
    - `org.springframework.boot:spring-boot-starter-data-r2dbc` (from BOM)
    - `org.postgresql:r2dbc-postgresql` (from BOM)
    - `org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2`
    - `net.jqwik:jqwik:1.9.2` (testImplementation)
    - `io.projectreactor.tools:blockhound-junit-platform:1.0.16.RELEASE` (testImplementation)
    - _Reference: `ms-inventory/build.gradle` + `ms-inventory/main.gradle` for exact patterns._

  - [x] 1.2 Create `application.yaml` (base), `application-local.yaml` and `application-docker.yaml`:
    - See Design Document § Configuration for full content
    - Port: 8089
    - DB: provider_db on port 5436 (local) / postgres-provider:5432 (docker)
    - Consumer group: provider-service-group
    - Topics consumed: inventory-events
    - Topics produced: provider-events
    - Outbox relay interval: 5000ms

  - [x] 1.3 Create `schema.sql` in `applications/app-service/src/main/resources/`:
    - See Design Document § Database Schema for full DDL
    - Tables: suppliers, supplier_products, purchase_orders, purchase_order_items, outbox_events, processed_events

  - [x] 1.4 Configure R2DBC schema initialization:
    - Add `@Bean ConnectionFactoryInitializer` with `ResourceDatabasePopulator` loading `schema.sql`

  - [x] 1.5 Add PostgreSQL container to `compose.yaml` (root):
    - Service: `postgres-provider`, image: `postgres:17`, port: 5436:5432, db: provider_db
    - Add init script in `postgresql-scripts/` for provider_db

---

- [x] 2. Implement domain model (`domain/model`)
  - [x] 2.1 Generate model module with Scaffold:
    ```bash
    cd ms-provider && ./gradlew generateModel --name=Supplier
    ```

  - [x] 2.2 Create domain records in `domain/model/src/main/java/com/arka/model/`:
    - `supplier/Supplier.java` — record with @Builder: id, name, email, phone, address, country, active, createdAt, updatedAt
    - `supplier/SupplierProduct.java` — record with @Builder: id, supplierId, sku, supplierSku, unitPrice, leadTimeDays, reorderMultiplier, preferred, createdAt
    - `purchaseorder/PurchaseOrder.java` — record with @Builder: id, supplierId, status, totalAmount, notes, createdAt, updatedAt, sentAt, confirmedAt, receivedAt, items (List)
    - `purchaseorder/PurchaseOrderItem.java` — record with @Builder: id, purchaseOrderId, sku, productName, quantity, unitPrice, subtotal
    - `purchaseorder/PurchaseOrderStatus.java` — enum: PENDING, SENT, CONFIRMED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED. Method `canTransitionTo(PurchaseOrderStatus target): boolean`
    - `outbox/OutboxEvent.java` — record with @Builder: id, eventType, topic, partitionKey, payload, status, createdAt
    - `outbox/OutboxStatus.java` — enum: PENDING, PUBLISHED
    - `outbox/EventType.java` — enum with value() field: PURCHASE_ORDER_CREATED("PurchaseOrderCreated"), PURCHASE_ORDER_SENT("PurchaseOrderSent")
    - `processedevent/ProcessedEvent.java` — record: eventId, processedAt
    - `envelope/DomainEventEnvelope.java` — record with @Builder: eventId, eventType, timestamp, source, correlationId, payload

  - [x] 2.3 Create gateway interfaces (ports) in `domain/model/src/main/java/com/arka/model/gateways/`:
    - `SupplierRepository.java` — save, findById, findAll (paginated), findByEmail, deactivate
    - `SupplierProductRepository.java` — save, findBySupplierIdAndSku, findBySku, findPreferredBySku, deleteBySupplierIdAndSku
    - `PurchaseOrderRepository.java` — save, findById, findAll (filtered + paginated), existsBySkuAndStatusIn
    - `OutboxEventRepository.java` — save, findByStatus(PENDING), updateStatus
    - `ProcessedEventRepository.java` — save, existsByEventId

---

- [x] 3. Implement use cases (`domain/usecase`)
  - [x] 3.1 Generate use case modules with Scaffold:
    ```bash
    ./gradlew generateUseCase --name=GeneratePurchaseOrder
    ./gradlew generateUseCase --name=CreateSupplier
    ./gradlew generateUseCase --name=GetSupplier
    ./gradlew generateUseCase --name=ListSuppliers
    ./gradlew generateUseCase --name=UpdateSupplier
    ./gradlew generateUseCase --name=DeactivateSupplier
    ./gradlew generateUseCase --name=AssignSupplierProduct
    ./gradlew generateUseCase --name=ListPurchaseOrders
    ./gradlew generateUseCase --name=GetPurchaseOrder
    ./gradlew generateUseCase --name=TransitionPurchaseOrder
    ./gradlew generateUseCase --name=CreateManualPurchaseOrder
    ./gradlew generateUseCase --name=OutboxRelay
    ```

  - [x] 3.2 Implement `GeneratePurchaseOrderUseCase`:
    - Input: DomainEventEnvelope (from Kafka)
    - Steps:
      1. Check idempotency (ProcessedEventRepository.existsByEventId)
      2. Check no active order exists (PurchaseOrderRepository.existsBySkuAndStatusIn)
      3. Find preferred supplier (SupplierProductRepository.findPreferredBySku)
      4. Calculate reorderQuantity: (threshold × reorderMultiplier) - currentStock
      5. Create PurchaseOrder + PurchaseOrderItem
      6. Save PurchaseOrder, OutboxEvent(PurchaseOrderCreated), ProcessedEvent — same transaction
    - Returns: Mono<Void>

  - [x] 3.3 Implement `CreateSupplierUseCase`:
    - Check email uniqueness → 409 if exists
    - Save Supplier with active=true
    - Returns: Mono<Supplier>

  - [x] 3.4 Implement `GetSupplierUseCase`:
    - Find by ID, include supplier_products
    - Returns: Mono<Supplier> (with products populated)

  - [x] 3.5 Implement `ListSuppliersUseCase`:
    - Paginated list of active suppliers
    - Returns: Mono<PageResponse<Supplier>>

  - [x] 3.6 Implement `UpdateSupplierUseCase`:
    - Find by ID → 404 if not found
    - Update fields, set updatedAt
    - Returns: Mono<Supplier>

  - [x] 3.7 Implement `DeactivateSupplierUseCase`:
    - Set active=false, updatedAt=now
    - Returns: Mono<Void>

  - [x] 3.8 Implement `AssignSupplierProductUseCase`:
    - If assigning as preferred → remove preferred from other suppliers for same SKU
    - Save SupplierProduct
    - Returns: Mono<SupplierProduct>

  - [x] 3.9 Implement `ListPurchaseOrdersUseCase`:
    - Filters: status, supplierId, sku, dateFrom, dateTo
    - Paginated
    - Returns: Mono<PageResponse<PurchaseOrder>>

  - [x] 3.10 Implement `GetPurchaseOrderUseCase`:
    - Find by ID with items
    - Returns: Mono<PurchaseOrder>

  - [x] 3.11 Implement `TransitionPurchaseOrderUseCase`:
    - Validate transition via PurchaseOrderStatus.canTransitionTo()
    - If invalid → throw InvalidStateTransitionException (422)
    - If transition to SENT → also insert OutboxEvent(PurchaseOrderSent)
    - Update status, set timestamp field (sentAt, confirmedAt, receivedAt)
    - Returns: Mono<PurchaseOrder>

  - [x] 3.12 Implement `CreateManualPurchaseOrderUseCase`:
    - Admin creates PO without StockDepleted event
    - Input: supplierId, items: [{sku, quantity}]
    - Lookup unit prices from supplier_products
    - Create PO + items + OutboxEvent(PurchaseOrderCreated)
    - Returns: Mono<PurchaseOrder>

  - [x] 3.13 Implement `OutboxRelayUseCase`:
    - Query outbox_events WHERE status = PENDING ORDER BY created_at
    - For each: build DomainEventEnvelope, delegate to KafkaProducer, mark PUBLISHED
    - Returns: Flux<Void> (scheduled)

---

- [x] 4. Implement driven adapters (`infrastructure/driven-adapters`)
  - [x] 4.1 Generate R2DBC adapter with Scaffold:
    ```bash
    ./gradlew generateDrivenAdapter --type=r2dbc
    ```

  - [x] 4.2 Implement R2DBC entities (Spring Data mapping):
    - `SupplierEntity.java` — @Table("suppliers"), maps to Supplier domain record
    - `SupplierProductEntity.java` — @Table("supplier_products"), maps to SupplierProduct
    - `PurchaseOrderEntity.java` — @Table("purchase_orders"), maps to PurchaseOrder
    - `PurchaseOrderItemEntity.java` — @Table("purchase_order_items"), maps to PurchaseOrderItem
    - `OutboxEventEntity.java` — @Table("outbox_events"), maps to OutboxEvent
    - `ProcessedEventEntity.java` — @Table("processed_events"), maps to ProcessedEvent

  - [x] 4.3 Implement Spring Data R2DBC repositories (interfaces):
    - `SupplierR2dbcRepository extends ReactiveCrudRepository<SupplierEntity, UUID>`
    - `SupplierProductR2dbcRepository extends ReactiveCrudRepository<SupplierProductEntity, UUID>` — custom queries: findBySku, findBySkuAndPreferredTrue
    - `PurchaseOrderR2dbcRepository extends ReactiveCrudRepository<PurchaseOrderEntity, UUID>` — custom queries with @Query for filtering
    - `PurchaseOrderItemR2dbcRepository extends ReactiveCrudRepository<PurchaseOrderItemEntity, UUID>` — findByPurchaseOrderId
    - `OutboxEventR2dbcRepository extends ReactiveCrudRepository<OutboxEventEntity, UUID>` — findByStatusOrderByCreatedAt
    - `ProcessedEventR2dbcRepository extends ReactiveCrudRepository<ProcessedEventEntity, UUID>`

  - [x] 4.4 Implement adapter classes that implement domain ports:
    - `R2dbcSupplierAdapter implements SupplierRepository` — delegates to SupplierR2dbcRepository with entity↔domain mapping
    - `R2dbcSupplierProductAdapter implements SupplierProductRepository`
    - `R2dbcPurchaseOrderAdapter implements PurchaseOrderRepository`
    - `R2dbcOutboxAdapter implements OutboxEventRepository`
    - `R2dbcProcessedEventAdapter implements ProcessedEventRepository`

  - [x] 4.5 Generate Kafka producer adapter with Scaffold:
    ```bash
    ./gradlew generateDrivenAdapter --type=kafka
    ```

  - [x] 4.6 Implement `KafkaOutboxRelay`:
    - Copy pattern from ms-inventory's `KafkaOutboxRelay`
    - `@Scheduled(fixedDelayString = "${scheduler.outbox-relay.interval}")`
    - Query PENDING events → publish to Kafka with partition key → mark PUBLISHED
    - Use `EventType.value()` for eventType in envelope (NOT toCamelCase)
    - Source: `"ms-provider"`

---

- [x] 5. Implement entry points (`infrastructure/entry-points`)
  - [x] 5.1 Generate WebFlux entry point with Scaffold:
    ```bash
    ./gradlew generateEntryPoint --type=webflux
    ```

  - [x] 5.2 Implement `SupplierController` + `SupplierHandler`:
    - Router bean with paths under `/api/v1/suppliers`
    - All endpoints require `X-User-Role: ADMIN` header check
    - Delegate to corresponding UseCases
    - Response DTOs: SupplierResponse, SupplierDetailResponse (with products), PageResponse<SupplierResponse>

  - [x] 5.3 Implement `PurchaseOrderController` + `PurchaseOrderHandler`:
    - Router bean with paths under `/api/v1/purchase-orders`
    - All endpoints require `X-User-Role: ADMIN` header check
    - Delegate to corresponding UseCases
    - Response DTOs: PurchaseOrderResponse, PurchaseOrderDetailResponse (with items), PageResponse<PurchaseOrderResponse>

  - [x] 5.4 Implement `GlobalExceptionHandler`:
    - Copy from ms-inventory and adapt
    - Handle: SupplierNotFoundException (404), PurchaseOrderNotFoundException (404), DuplicateEmailException (409), InvalidStateTransitionException (422), SupplierNotFoundForSkuException (422)

  - [x] 5.5 Generate Kafka consumer entry point with Scaffold:
    ```bash
    ./gradlew generateEntryPoint --type=kafka
    ```

  - [x] 5.6 Implement Kafka consumer (copy pattern from ms-inventory):
    - `KafkaConsumerConfig.java` — bean `KafkaReceiver<String, String>` for topic `inventory-events`
    - `KafkaConsumerLifecycle.java` — `@EventListener(ApplicationReadyEvent.class)` starts consumption
    - `KafkaEventConsumer.java` — deserializes envelope, filters by eventType == "StockDepleted", delegates to GeneratePurchaseOrderUseCase
    - Ignores unknown eventTypes with WARN log

---

- [x] 6. Application configuration and wiring
  - [x] 6.1 Update `MainApplication.java`:
    - `@SpringBootApplication`
    - `@EnableScheduling` (for Outbox Relay)
    - `ConnectionFactoryInitializer` bean for schema.sql

  - [x] 6.2 Add PostgreSQL container to root `compose.yaml`:
    ```yaml
    postgres-provider:
      image: postgres:17
      environment:
        POSTGRES_DB: provider_db
        POSTGRES_USER: postgres
        POSTGRES_PASSWORD: postgres
      ports:
        - "5436:5432"
      volumes:
        - provider_data:/var/lib/postgresql/data
    ```

  - [x] 6.3 Add ms-provider service to root `compose.yaml`:
    ```yaml
    ms-provider:
      build: ./ms-provider
      ports:
        - "8089:8089"
      environment:
        SPRING_PROFILES_ACTIVE: docker
      depends_on:
        - postgres-provider
        - arka-kafka
    ```

  - [x] 6.4 Create init script `postgresql-scripts/init-provider-db.sql`:
    - Same DDL as schema.sql for Docker initialization

---

- [ ] 7. Unit tests
  - [ ] 7.1 Test `GeneratePurchaseOrderUseCase`:
    - Happy path: StockDepleted → finds supplier → creates PO + outbox + processed
    - Idempotency: duplicate eventId → no-op
    - Active order exists: same SKU with PENDING → no-op
    - No supplier found: logs error → no PO created
    - Quantity calculation: verify (threshold × multiplier) - currentStock

  - [ ] 7.2 Test `TransitionPurchaseOrderUseCase`:
    - Valid transitions: PENDING→SENT, SENT→CONFIRMED, CONFIRMED→RECEIVED, etc.
    - Invalid transitions: CANCELLED→SENT → throws 422
    - SENT transition inserts PurchaseOrderSent outbox event

  - [ ] 7.3 Test `CreateSupplierUseCase`:
    - Happy path: saves supplier
    - Duplicate email: throws 409

  - [ ] 7.4 Test `AssignSupplierProductUseCase`:
    - Preferred reassignment: removes previous preferred

  - [ ] 7.5 Test `CreateManualPurchaseOrderUseCase`:
    - Creates PO with correct totals from supplier_products prices

  - [ ] 7.6 Test `KafkaEventConsumer`:
    - StockDepleted event → delegates to GeneratePurchaseOrderUseCase
    - Unknown eventType → ignored with WARN log
    - Duplicate eventId → no-op (idempotent)

  - [ ] 7.7 Test `KafkaOutboxRelay`:
    - Publishes pending events → marks PUBLISHED
    - No pending events → no-op

---

- [x] 8. Integration and build verification
  - [x] 8.1 Run `./gradlew validateStructure` — must pass
  - [x] 8.2 Run `./gradlew build` — must compile and pass all tests
  - [ ] 8.3 Verify schema.sql creates all tables on startup with embedded PostgreSQL (or Testcontainers)
  - [ ] 8.4 Verify Kafka consumer connects and filters correctly in integration test

---

- [x] 9. Documentation
  - [x] 9.1 Create/update `ms-provider/README.md` following monorepo convention (see ms-reporter/README.md as template)
  - [ ] 9.2 Update `docs/04-api-endpoints.md` with ms-provider endpoints
  - [ ] 9.3 Update `docs/10-urls-puertos-globales.md` with ms-provider port 8089 and Swagger UI
  - [x] 9.4 Verify `docs/03-kafka-eventos.md` already has `provider-events` topic documented

---

## Optional Tasks (Post-MVP)

- [ ] O.1 Property-based tests (jqwik) for PurchaseOrderStatus state machine
- [ ] O.2 Property-based tests for reorder quantity calculation (always positive, never negative)
- [ ] O.3 Integration tests with Testcontainers (PostgreSQL + Kafka)
- [ ] O.4 Metrics with Micrometer: purchase_orders_created_total, outbox_events_published_total
- [ ] O.5 Bulk purchase order creation (multiple SKUs in one event batch)
