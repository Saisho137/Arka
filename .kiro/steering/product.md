# Arka — Product Context

B2B e-commerce backend for wholesale PC accessories distribution (Colombia/LATAM). Clients are stores buying in bulk.

## Core Problems (Priority Order)

1. Overselling from concurrent stock access
2. Manual inventory management at unsustainable scale
3. No automated order flow
4. Customers lack order status visibility

## Microservices (9 total, 4 delivery phases)

| Service          | Domain                          | DB                      | Phase |
| ---------------- | ------------------------------- | ----------------------- | ----- |
| ms-catalog       | Products, nested reviews        | MongoDB + Redis         | 1     |
| ms-inventory     | Stock, reservations, locks      | PostgreSQL 17 (R2DBC)   | 1     |
| ms-order         | Orders, Saga orchestrator       | PostgreSQL 17 (R2DBC)   | 1     |
| ms-notifications | Emails via AWS SES              | MongoDB                 | 1     |
| ms-cart          | Cart, abandonment detection     | MongoDB                 | 2     |
| ms-payment       | Payments ACL (Stripe/Wompi/MP)  | PostgreSQL 17 (R2DBC)   | 2     |
| ms-reporter      | Analytics, CQRS, Event Sourcing | PostgreSQL 17 (JDBC)+S3 | 3     |
| ms-shipping      | Logistics ACL (DHL/FedEx)       | PostgreSQL 17 (R2DBC)   | 3     |
| ms-provider      | Supplier purchase orders ACL    | PostgreSQL 17 (R2DBC)   | 4     |

## Business Rules

- B2B only: public email domains blocked via Tenant Restrictions
- Phase 1: deferred invoicing (30-60 day terms), no payment gateway
- Stock reservation: pessimistic lock (`SELECT ... FOR UPDATE`), 15-min expiry
- Order states: `PENDIENTE_RESERVA → CONFIRMADO → EN_DESPACHO → ENTREGADO | CANCELADO`
- Phase 2 adds `PENDIENTE_PAGO` between reservation and confirmation
- `StockDepleted` → ms-provider auto-generates purchase orders (Phase 4)
- Admin updates stock manually via `PUT /inventory/{sku}/stock` on goods arrival
- RBAC: CUSTOMER (B2B client), ADMIN (internal staff)
- Reviews stored as subdocuments in ms-catalog MongoDB (no separate service)
- Naming: kebab-case with `ms-` prefix for all repos/containers/projects

## Communication

- gRPC sync: ms-order → ms-inventory (stock reservation), ms-cart → ms-catalog (checkout price)
- Kafka async: Saga orchestration, domain events, Event Sourcing for ms-reporter
- Outbox Pattern: ms-order, ms-inventory, ms-catalog (atomic DB write + event publish, relay polls every 5s)

## Kafka Topics (7, one per bounded context: `<domain>-events`)

| Topic            | Producer     | Key             | Events (eventType)                                                            |
| ---------------- | ------------ | --------------- | ----------------------------------------------------------------------------- |
| product-events   | ms-catalog   | productId       | ProductCreated, ProductUpdated, PriceChanged                                  |
| inventory-events | ms-inventory | sku             | StockReserved, StockReserveFailed, StockReleased, StockDepleted, StockUpdated |
| order-events     | ms-order     | orderId         | OrderCreated, OrderConfirmed, OrderStatusChanged, OrderCancelled              |
| cart-events      | ms-cart      | cartId          | CartAbandoned                                                                 |
| payment-events   | ms-payment   | orderId         | PaymentProcessed, PaymentFailed                                               |
| shipping-events  | ms-shipping  | orderId         | ShippingDispatched                                                            |
| provider-events  | ms-provider  | purchaseOrderId | PurchaseOrderCreated                                                          |

Event envelope: `{ eventId, eventType, timestamp, source, correlationId, payload }`. Consumers filter by eventType, ignore unknown types with WARN log.

## Security (Zero Trust)

- AWS API Gateway: single public entry, JWT validation (Entra ID / Cognito), Rate Limiting (100 req/s/IP)
- Identity propagated via `X-User-Email` header into private VPC
- Tenant Restrictions block public email domains for B2B enforcement
- BFF pattern permanently discarded
- Secrets in AWS Secrets Manager
- Bean Validation (`@NotNull`, `@Size`, `@Positive`) on all inputs

## Architectural Patterns

| Pattern               | Where                                                                                     |
| --------------------- | ----------------------------------------------------------------------------------------- |
| Sequential Saga       | ms-order: OrderCreated → StockReserved → PaymentProcessed → OrderConfirmed                |
| CQRS / Event Sourcing | ms-reporter consumes all 7 Kafka topics                                                   |
| Outbox Pattern        | ms-order, ms-inventory (PostgreSQL), ms-catalog (MongoDB outbox_events)                   |
| Cache-Aside           | ms-catalog with Redis (TTL 1h, invalidated on write + Kafka event)                        |
| Anti-Corruption Layer | ms-payment, ms-shipping, ms-provider (external APIs)                                      |
| Circuit Breaker       | Resilience4j in ms-payment, ms-shipping (50% threshold, 30s open, 3 retries with backoff) |
| Database per Service  | Each microservice owns its storage exclusively                                            |
| Idempotent Consumers  | processed_events table/collection with unique eventId                                     |
