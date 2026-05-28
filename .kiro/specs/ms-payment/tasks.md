# Plan de Implementación: ms-payment (Producción — Outbox + ACL)

## Visión General

Implementación **completa y production-ready** del microservicio `ms-payment`. Incluye persistencia, idempotencia, Outbox Pattern, Anti-Corruption Layer (ACL) con Strategy pattern para pasarelas de pago, y endpoints REST de consulta.

### Arquitectura

1. **Consumir** eventos `OrderCreated` del tópico `order-events` (producidos por ms-order).
2. **Procesar** el pago vía ACL (`PaymentGateway` port) — actualmente un mock (80/20), reemplazable por Stripe/Wompi/MercadoPago sin tocar dominio.
3. **Persistir** el resultado en PostgreSQL (R2DBC) con tabla `payments`.
4. **Publicar** el evento resultante (`PaymentProcessed` o `PaymentFailed`) via **Outbox Pattern** (tabla `outbox_events` → scheduler → Kafka `payment-events`).
5. **Garantizar idempotencia** con tabla `processed_events`.
6. **Exponer** endpoint REST de consulta: `GET /api/v1/payments/orders/{orderId}`.

**CON:** PostgreSQL 17 (R2DBC), Outbox Pattern, idempotencia, ACL Strategy, Spring Security, Rate Limiting, REST endpoint.

---

## Estructura Implementada

```
ms-payment/
├── domain/
│   ├── model/           → :model   — Payment, EventType, OutboxEvent, PaymentGatewayResult, ports
│   └── usecase/         → :usecase — ProcessPaymentUseCase, OutboxRelayUseCase
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── kafka-producer/      — KafkaPaymentProducer (Outbox Relay + publisher)
│   │   │                          MockPaymentGateway (ACL impl)
│   │   └── r2dbc-postgresql/    — R2DBC adapters (Payment, Outbox, ProcessedEvent, Transaction)
│   │                              JacksonJsonSerializer
│   └── entry-points/
│       ├── kafka-consumer/      — KafkaEventConsumer, KafkaConfig, KafkaConsumerLifecycle
│       └── reactive-web/        — PaymentController (REST), RateLimitFilter
└── applications/
    └── app-service/             — SecurityConfig, UseCasesConfig, MainApplication
```

---

## Contrato de Eventos (sin cambios respecto al mock original)

### Evento consumido — `order-events` (eventType: `OrderCreated`)

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "timestamp": "2026-04-21T10:00:00Z",
  "source": "ms-order",
  "correlationId": "uuid",
  "payload": {
    "orderId": "uuid",
    "customerId": "uuid",
    "customerEmail": "customer@arka.com",
    "items": [...],
    "totalAmount": 690000.00
  }
}
```

### Evento publicado — `payment-events` (vía Outbox Pattern)

```json
{
  "eventId": "uuid-generado",
  "eventType": "PaymentProcessed",
  "timestamp": "2026-04-21T10:00:01Z",
  "source": "ms-payment",
  "correlationId": "orderId",
  "payload": {
    "orderId": "uuid",
    "transactionId": "mock-txn-uuid",
    "status": "COMPLETED"
  }
}
```

---

## Tareas

### Tarea 1 — Configurar proyecto y módulos Gradle

- [x] 1.1 Configurar `settings.gradle` con módulos: `:app-service`, `:model`, `:usecase`, `:kafka-producer`, `:r2dbc-postgresql`, `:kafka-consumer`, `:reactive-web`
- [x] 1.2 Crear `application.yaml` con R2DBC datasource (`r2dbc:postgresql://localhost:5434/db_payment`), Kafka config, pool settings
- [x] 1.3 Crear perfiles `application-local.yaml` y `application-docker.yaml`
- [x] 1.4 Configurar `app-service/build.gradle` con dependencias: spring-boot-starter-security, webflux, actuator, data-r2dbc, r2dbc-postgresql

---

### Tarea 2 — Implementar modelo de dominio

- [x] 2.1 Crear `Payment` record: id, orderId, gateway, transactionId, amount, currency, status, failureReason, createdAt, updatedAt
- [x] 2.2 Crear `PaymentStatus` enum: PENDING, COMPLETED, FAILED
- [x] 2.3 Crear `PaymentGatewayResult` record: success, transactionId, failureReason
- [x] 2.4 Crear `EventType` enum con `value()` explícito: PAYMENT_PROCESSED("PaymentProcessed"), PAYMENT_FAILED("PaymentFailed")
- [x] 2.5 Crear `OutboxEvent` record: id, eventType, payload (JSON), partitionKey, published, createdAt
- [x] 2.6 Crear `PaymentProcessedPayload` y `PaymentFailedPayload` records
- [x] 2.7 Crear `DomainEventEnvelope` record (source="ms-payment")

---

### Tarea 3 — Definir puertos (Gateway interfaces)

- [x] 3.1 `PaymentRepository`: save, findById, findByOrderId, updateStatus
- [x] 3.2 `PaymentGateway` (ACL port): charge(orderId, amount, currency) → Mono<PaymentGatewayResult>, gatewayName()
- [x] 3.3 `OutboxEventRepository`: save, findPendingEvents, markAsPublished
- [x] 3.4 `ProcessedEventRepository`: exists(eventId), save(eventId)
- [x] 3.5 `TransactionalGateway`: executeInTransaction(Mono<T>)
- [x] 3.6 `JsonSerializer`: serialize(Object) → String
- [x] 3.7 `PaymentEventPublisher`: publishPaymentEvent(orderId, envelope)

---

### Tarea 4 — Implementar casos de uso

- [x] 4.1 `ProcessPaymentUseCase.process(eventId, orderId, amount, currency)`:
  - Idempotencia: check processedEventRepository.exists(eventId)
  - Crear Payment PENDING → save → paymentGateway.charge() → handle result
  - Success: update COMPLETED + OutboxEvent(PAYMENT_PROCESSED)
  - Failure: update FAILED + OutboxEvent(PAYMENT_FAILED)
  - Todo envuelto en transactionalGateway.executeInTransaction()
- [x] 4.2 `OutboxRelayUseCase`: fetchPendingEvents(), markAsPublished(event)
- [x] 4.3 `ProcessPaymentUseCase.getPaymentByOrderId(orderId)` — consulta

---

### Tarea 5 — Implementar driven adapters R2DBC

- [x] 5.1 `R2dbcPaymentAdapter` (implements PaymentRepository): CRUD con SpringDataPaymentRepository
- [x] 5.2 `R2dbcOutboxAdapter` (implements OutboxEventRepository): queries con limit 50 pending
- [x] 5.3 `R2dbcProcessedEventAdapter` (implements ProcessedEventRepository): exists + save
- [x] 5.4 `R2dbcTransactionalAdapter` (implements TransactionalGateway): TransactionalOperator
- [x] 5.5 `JacksonJsonSerializer` (implements JsonSerializer): Jackson ObjectMapper
- [x] 5.6 DTOs: PaymentDTO, OutboxEventDTO, ProcessedEventDTO con @Table mappings

---

### Tarea 6 — Implementar Kafka Producer (Outbox Relay)

- [x] 6.1 `KafkaProducerConfig`: bean KafkaSender<String, String> (acks=all, retries=3)
- [x] 6.2 `KafkaPaymentProducer`:
  - `@Scheduled(fixedDelay=5000)` relay() → poll outbox → publish → mark published
  - implements PaymentEventPublisher (direct publish, backward compat)
- [x] 6.3 `MockPaymentGateway` (implements PaymentGateway): Strategy pattern, 80% success, gatewayName="MOCK"

---

### Tarea 7 — Implementar Kafka Consumer (Entry Point)

- [x] 7.1 `KafkaConfig`: bean KafkaReceiver (group: payment-service-group, topic: order-events, manual commit)
- [x] 7.2 `KafkaEventConsumer`: consume OrderCreated → extract orderId, eventId, totalAmount → delegate to UseCase
- [x] 7.3 `KafkaConsumerLifecycle`: start on ApplicationReadyEvent

---

### Tarea 8 — Implementar REST Entry Point

- [x] 8.1 `PaymentController`: GET /api/v1/payments/orders/{orderId} → 200 Payment | 404
- [x] 8.2 `RateLimitFilter`: WebFilter token-bucket (100 req/s per IP)

---

### Tarea 9 — Seguridad y configuración app-service

- [x] 9.1 `SecurityConfig`: X-User-Role header filter, role-based authorization, CSRF disabled
- [x] 9.2 `UseCasesConfig`: ComponentScan for UseCase beans
- [x] 9.3 `MainApplication`: @EnableScheduling para Outbox Relay scheduler

---

### Tarea 10 — Base de datos

- [x] 10.1 Schema PostgreSQL (`postgresql-scripts/init_payment.sql`):
  - Tabla `payments`: id, order_id, gateway, transaction_id, amount, currency, status, failure_reason, timestamps
  - Tabla `outbox_events`: id, event_type, payload (JSON), partition_key, published, created_at
  - Tabla `processed_events`: id (UUID PK), processed_at
  - Índices: idx_payments_order_id, idx_payments_status, idx_outbox_pending (partial)

---

### Tarea 11 — Validación y compilación

- [x] 11.1 `./gradlew validateStructure` → proyecto válido
- [x] 11.2 `./gradlew compileJava` → BUILD SUCCESSFUL
- [x] 11.3 Verificar retrocompatibilidad: ms-order consume PaymentProcessed/PaymentFailed sin cambios

---

## Diferencias vs Mock Original

| Aspecto | Mock (tasks.md original) | Producción (implementado) |
|---------|--------------------------|---------------------------|
| Persistencia | Sin BD — todo en memoria | PostgreSQL 17 + R2DBC |
| Outbox Pattern | Publicación directa a Kafka | Tabla outbox_events + scheduler relay |
| Idempotencia | Sin — mock sin riesgo | Tabla processed_events + check |
| ACL | Random directo en UseCase | PaymentGateway port + MockPaymentGateway Strategy |
| REST | Sin endpoints | GET /api/v1/payments/orders/{orderId} |
| Seguridad | Sin | Spring Security + RateLimitFilter |
| Transaccionalidad | Sin | TransactionalOperator (BD + outbox atómico) |
| Escalabilidad | Sin | Extensible via Strategy: Stripe/Wompi/MercadoPago |
