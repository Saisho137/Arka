# Arka — Copilot Instructions

## Project Identity

Monorepo e-commerce B2B (Colombia/LATAM). 9 microservicios Java con Clean Architecture.

## Stack

Java 21 · Spring Boot 4.0.3 (WebFlux + MVC/Virtual Threads) · Project Reactor · Kafka 8 (KRaft) · PostgreSQL 16 · MongoDB (Reactive Drivers) · Redis (Cache-Aside) · gRPC (comunicación síncrona interna) · LocalStack (CloudFormation) · Gradle + Bancolombia Scaffold Plugin 4.2.0 · Lombok 1.18.42

## Paradigma Híbrido

- **Reactivo (WebFlux):** ms-catalog, ms-inventory, ms-order, ms-cart, ms-notifications — I/O-Bound, drivers no bloqueantes (R2DBC, Reactive Mongo)
- **Imperativo (MVC + Virtual Threads):** ms-reporter — CPU-Bound, `reactive=false` en gradle.properties

## Microservicios

| Servicio         | Dominio                                     | BD                      | Paradigma  |
| ---------------- | ------------------------------------------- | ----------------------- | ---------- |
| ms-order         | Gestión de pedidos, Saga orchestrator       | PostgreSQL db_orders    | Reactivo   |
| ms-catalog       | Catálogo de productos y reseñas anidadas    | MongoDB + Redis         | Reactivo   |
| ms-inventory     | Stock, reservas y lock pesimista            | PostgreSQL db_inventory | Reactivo   |
| ms-payment       | Procesamiento de pagos (ACL pasarelas)      | PostgreSQL db_payment   | Reactivo\* |
| ms-shipping      | Logística y envíos (Strangler Fig)          | PostgreSQL              | Reactivo\* |
| ms-provider      | Proveedores B2B (ACL externa)               | PostgreSQL              | Reactivo\* |
| ms-notifications | Alertas y notificaciones (AWS SES)          | MongoDB                 | Reactivo   |
| ms-reporter      | Reportes y analítica (CQRS, Event Sourcing) | PostgreSQL + S3         | Imperativo |
| ms-cart          | Carrito de compras y abandono               | MongoDB                 | Reactivo   |

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

- **Saga Secuencial** (ms-order orquestador): order-created → stock-reserved → payment-processed → order-confirmed
- **CQRS / Event Sourcing** para analítica (ms-reporter consume todos los eventos)
- **Outbox Pattern** en ms-order y ms-inventory (transacción BD + evento atómico)
- **Cache-Aside** para lecturas de catálogo (Redis)
- **Circuit Breaker** para llamadas a servicios externos
- **Anti-Corruption Layer (ACL)** en ms-payment y ms-provider
- **gRPC** síncrono: ms-order → ms-inventory (reserva stock), ms-cart → ms-catalog (precio actual)

## Kafka Topics

`product-created`, `product-updated`, `order-created`, `order-confirmed`, `order-cancelled`, `stock-reserved`, `stock-released`, `stock-depleted`, `payment-processed`, `payment-failed`, `cart-abandoned`, `shipping-dispatched`, `stock-received`

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
- Infraestructura local: `docker compose up` ( PostgreSQL + LocalStack)

## NO Incluido Aquí

Guías detalladas de testing → usar skill/agent `TestScaffolder`
Scaffolding paso a paso → usar skill `scaffold-tasks`
