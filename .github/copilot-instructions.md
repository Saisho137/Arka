# Arka — Copilot Instructions

## Project Identity

Monorepo e-commerce B2B (Colombia/LATAM). 9 microservicios Java con Clean Architecture.

## Stack

Java 21 · Spring Boot 4.0.3 (WebFlux + MVC/Virtual Threads) · Project Reactor · Kafka 8 (KRaft) · PostgreSQL 17 · MongoDB (Reactive Drivers) · Redis (Cache-Aside) · gRPC (comunicación síncrona interna) · LocalStack (CloudFormation) · Gradle + Bancolombia Scaffold Plugin 4.2.0 · Lombok 1.18.42

## Paradigma Híbrido

- **Reactivo (WebFlux):** ms-catalog, ms-inventory, ms-order, ms-cart, ms-notifications — I/O-Bound, drivers no bloqueantes (R2DBC, Reactive Mongo)
- **Imperativo (MVC + Virtual Threads):** ms-reporter — CPU-Bound, `reactive=false` en gradle.properties

## Microservicios

| Servicio         | Dominio                                     | BD                         | Paradigma  |
| ---------------- | ------------------------------------------- | -------------------------- | ---------- |
| ms-order         | Gestión de pedidos, Saga orchestrator       | PostgreSQL 17 db_orders    | Reactivo   |
| ms-catalog       | Catálogo de productos y reseñas anidadas    | MongoDB + Redis            | Reactivo   |
| ms-inventory     | Stock, reservas y lock pesimista            | PostgreSQL 17 db_inventory | Reactivo   |
| ms-payment       | Procesamiento de pagos (ACL pasarelas)      | PostgreSQL 17 db_payment   | Reactivo\* |
| ms-shipping      | Logística y envíos (ACL logística)          | PostgreSQL 17              | Reactivo\* |
| ms-provider      | Proveedores B2B (ACL externa)               | PostgreSQL 17              | Reactivo\* |
| ms-notifications | Alertas y notificaciones (AWS SES)          | MongoDB                    | Reactivo   |
| ms-reporter      | Reportes y analítica (CQRS, Event Sourcing) | PostgreSQL 17 + S3         | Imperativo |
| ms-cart          | Carrito de compras y abandono               | MongoDB                    | Reactivo   |

> \* Diseñados para migrar a Spring MVC + Virtual Threads (ver diseño arquitectónico)

## Clean Architecture — Capas y Módulos Gradle

```
applications/app-service/    → :app-service  (Spring Boot main, DI)
domain/model/                → :model        (Entities, Value Objects, Ports/Interfaces)
domain/usecase/              → :usecase      (Business logic, orchestration)
infrastructure/
  driven-adapters/           → Repos, clients externos (R2DBC, REST, Kafka producer)
  entry-points/              → Controllers (WebFlux), Kafka consumers
  helpers/                   → Utilidades compartidas
```

## Paquete Base

`com.arka` (definido en `gradle.properties` como `package=com.arka` para todos los microservicios)

## Patrones Clave

- **Saga Secuencial** (ms-order orquestador): OrderCreated → StockReserved → PaymentProcessed → OrderConfirmed
- **CQRS / Event Sourcing** para analítica (ms-reporter consume todos los eventos)
- **Outbox Pattern** en ms-order y ms-inventory (transacción BD + evento atómico)
- **Cache-Aside** para lecturas de catálogo (Redis)
- **Circuit Breaker** para llamadas a servicios externos
- **Anti-Corruption Layer (ACL)** en ms-payment, ms-shipping y ms-provider
- **gRPC** síncrono: ms-order → ms-inventory (reserva stock), ms-cart → ms-catalog (precio actual)

## Kafka Topics (1 tópico por servicio, discriminado por `eventType`)

| Tópico             | Productor    | Eventos (`eventType`)                                                             |
| ------------------ | ------------ | --------------------------------------------------------------------------------- |
| `product-events`   | ms-catalog   | ProductCreated · ProductUpdated · PriceChanged                                    |
| `inventory-events` | ms-inventory | StockReserved · StockReserveFailed · StockReleased · StockDepleted · StockUpdated |
| `order-events`     | ms-order     | OrderCreated · OrderConfirmed · OrderStatusChanged · OrderCancelled               |
| `cart-events`      | ms-cart      | CartAbandoned                                                                     |
| `payment-events`   | ms-payment   | PaymentProcessed · PaymentFailed                                                  |
| `shipping-events`  | ms-shipping  | ShippingDispatched                                                                |
| `provider-events`  | ms-provider  | PurchaseOrderCreated                                                              |

Partition key = aggregate ID (`productId`, `sku`, `orderId`, etc.). Consumidores filtran por `eventType` e ignoran eventos desconocidos.

## Scaffold — Tareas Gradle Principales

Ejecutar desde la raíz del microservicio (`cd ms-<name>`):

- `./gradlew generateModel --name=X` (gm) → Model + Gateway interface
- `./gradlew generateUseCase --name=X` (guc) → UseCase class
- `./gradlew generateDrivenAdapter --type=X` (gda) → r2dbc, restconsumer, kafka, secrets, etc.
- `./gradlew generateEntryPoint --type=X` (gep) → webflux, kafka, graphql, etc.
- `./gradlew generateHelper --name=X` (gh) → Helper module
- `./gradlew validateStructure` → Validar capas

## Convenciones de Código

- Reactivo por defecto: retornar `Mono<T>` o `Flux<T>` en servicios WebFlux; imperativo en ms-reporter (MVC + Virtual Threads)
- Lombok: `@Builder`, `@Value` para modelos; `@RequiredArgsConstructor` para inyección
- Tests: JUnit 5 + `reactor-test` (StepVerifier) + mocks con Mockito
- Dockerfile: corretto 21 Alpine, usuario no-root `appuser`
- Infraestructura local: `docker compose up` ( PostgreSQL 17 + LocalStack)

## NO Incluido Aquí

Guías detalladas de testing → usar skill/agent `TestScaffolder`
Scaffolding paso a paso → usar skill `scaffold-tasks`
