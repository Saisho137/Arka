# Arka — Copilot Instructions

## Project Identity

Monorepo e-commerce B2B (Colombia/LATAM). 9 microservicios Java con Clean Architecture.

## Stack

Java 21 · Spring Boot 4.0.3 (Reactive/WebFlux) · Project Reactor · Kafka 8 (KRaft) · PostgreSQL 16 · LocalStack (CloudFormation) · Gradle + Bancolombia Scaffold Plugin 4.2.0 · Lombok 1.18.42

## Microservicios

| Servicio         | Dominio                               | BD           |
| ---------------- | ------------------------------------- | ------------ |
| ms-order         | Gestión de pedidos, Saga orchestrator | db_orders    |
| ms-catalog       | Catálogo de productos                 | —            |
| ms-inventory     | Stock y reservas                      | db_inventory |
| ms-payment       | Procesamiento de pagos                | db_payment   |
| ms-shipping      | Logística y envíos                    | —            |
| ms-provider      | Proveedores B2B                       | —            |
| ms-notifications | Alertas y notificaciones              | —            |
| ms-reporter      | Reportes y analítica                  | —            |
| ms-cart          | Carrito de compras                    | —            |

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

`com.arka.<ms-name>` (ej: `com.arka.order`, `com.arka.inventory`)

## Patrones Clave

- **Saga** (coreografía Kafka): order-created → stock-reserved → payment-processed → order-confirmed
- **CQRS / Event Sourcing** para flujos de pedidos
- **Circuit Breaker** para llamadas servicios externos
- **Strangler Fig** para migración incremental

## Kafka Topics

`order-created`, `stock-reserved`, `stock-released`, `payment-processed`, `payment-failed`, `order-confirmed`, `order-cancelled`

## Reglas de Calidad

- **JaCoCo**: cobertura obligatoria (XML + HTML)
- **PiTest**: mutación obligatoria (`com.arka.*`, JUnit 5 plugin)
- **SonarQube**: integrado en build.gradle
- **BlockHound**: detectar llamadas bloqueantes en tests reactivos

## Scaffold — Tareas Gradle Principales

Ejecutar desde la raíz del microservicio (`cd ms-<name>`):

- `./gradlew generateModel --name=X` (gm) → Model + Gateway interface
- `./gradlew generateUseCase --name=X` (guc) → UseCase class
- `./gradlew generateDrivenAdapter --type=X` (gda) → r2dbc, restconsumer, kafka, secrets, etc.
- `./gradlew generateEntryPoint --type=X` (gep) → webflux, kafka, graphql, etc.
- `./gradlew generateHelper --name=X` (gh) → Helper module
- `./gradlew validateStructure` → Validar capas

## Convenciones de Código

- Siempre reactivo: retornar `Mono<T>` o `Flux<T>`, nunca bloquear
- Lombok: `@Builder`, `@Value` para modelos; `@RequiredArgsConstructor` para inyección
- Tests: JUnit 5 + `reactor-test` (StepVerifier) + mocks con Mockito
- Dockerfile: corretto 21 Alpine, usuario no-root `appuser`
- Infraestructura local: `docker compose up` ( PostgreSQL + LocalStack)

## NO Incluido Aquí

Guías detalladas de testing → usar skill/agent `TestScaffolder`
Scaffolding paso a paso → usar skill `scaffold-tasks`
