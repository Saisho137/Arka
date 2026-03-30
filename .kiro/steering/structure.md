# Arka — Structure & Code Conventions

## Monorepo Layout

```text
Arka/
├── compose.yaml        # Docker Compose: PG×3, Kafka, Kafka UI, LocalStack, Traefik
├── .env                # Ports, credentials, hostnames
├── localstack/         # CloudFormation for local AWS
├── docs/               # Architecture docs, backlog, standards
└── ms-<name>/          # Each microservice (9 total)
```

## Microservice Internal Structure (Clean Architecture — Scaffold)

```text
ms-<name>/
├── applications/app-service/       # Spring Boot main, config, DI
├── domain/
│   ├── model/                      # Entities (records), VOs, Gateway ports
│   │   └── com/arka/model/<aggregate>/
│   │       ├── <Entity>.java       # @Builder(toBuilder=true) record
│   │       └── gateways/<Entity>Repository.java
│   └── usecase/                    # Business logic
│       └── com/arka/usecase/
├── infrastructure/
│   ├── driven-adapters/            # Repo impls, external clients (R2DBC, Kafka, REST)
│   ├── entry-points/               # Controllers (@RestController), Kafka consumers
│   └── helpers/                    # Shared utilities
├── deployment/Dockerfile           # amazoncorretto:21-alpine, non-root appuser
├── build.gradle / main.gradle / settings.gradle / gradle.properties
```

## Module Dependencies

```text
:app-service    → :model, :usecase, driven-adapters, entry-points
:usecase        → :model
:model          → (no project deps — pure domain)
driven-adapters → :model
entry-points    → :model, :usecase
```

## Package Naming (base: `com.arka`)

- Entities: `com.arka.model.<aggregate>` (e.g. `com.arka.model.stock`)
- Ports: `com.arka.model.<aggregate>.gateways`
- Value Objects: `com.arka.valueobjects`
- Use Cases: `com.arka.usecase`

## Domain Modeling (from docs/patrones-y-estandares-codigo.md)

- Records as default for entities, VOs, commands, events, DTOs — `@Builder(toBuilder = true)`
- Classes with Lombok only when inheritance or framework mutability required
- Validation in compact constructors (null checks, invariants)
- `@Builder.Default` does NOT work on records — defaults go in compact constructor
- Immutable copies: `with*()` methods for common mutations, `toBuilder()` for complex
- Sealed interfaces for state machines (e.g. `OrderStatus`) + switch pattern matching (Java 21)
- Enums for simple finite sets (e.g. `MovementType`, `ReservationStatus`)
- Ports (gateway interfaces) live in `model/<aggregate>/gateways/` — never in usecase or infra
- DI: `@RequiredArgsConstructor` (Lombok) — constructor injection only, no field injection

## Mapping Between Layers

- No MapStruct — manual `*Mapper` classes with static methods
- Always use `@Builder` when constructing target objects in mappers
- Mappers live in the layer that needs the transformation (entry-points, driven-adapters)

## Controllers

- `@RestController` with `Mono`/`Flux` returns (no Router Functions)
- `@Valid` for DTO validation at entry-points
- `@ControllerAdvice` (`GlobalExceptionHandler`) for centralized error handling
- API versioning: `/api/v1/<resource>`
- Springdoc/OpenAPI for documentation

## Business Logic Patterns (UseCase layer)

- RuleEngine (sync): `BusinessRule<T,R>` returning `Optional<R>` — pure CPU, no I/O
- RuleEngine (reactive): `ReactiveBusinessRule<T,R>` returning `Mono<Optional<R>>` — for rules needing I/O
- Mixed pattern: sync engine first (fast-fail), then reactive engine if passes
- Strategy + Factory with `Supplier<T>` for runtime-extensible behaviors (payment gateways, notification senders, logistics operators)
- switch pattern matching for sealed interfaces (compile-time exhaustive); Strategy+Factory for infra extensions

## Error Handling

- Domain exceptions extend `DomainException` with HTTP status + error code
- `@ControllerAdvice` maps to standardized `ErrorResponse`
- Reactive chains: `switchIfEmpty(Mono.error(...))`, `onErrorResume()`, `retryWhen()` — never try/catch around publishers

## Kafka Conventions

- 1 topic per bounded context: `<domain>-events`
- Partition key = aggregate root ID
- Standard envelope: `{ eventId, eventType, timestamp, source, correlationId, payload }`
- Consumers filter by `eventType`, ignore unknown with WARN log
- Idempotency: `processed_events` table/collection (unique eventId)
- Outbox Pattern: event inserted in same DB transaction, relay polls every 5s
- MongoDB services use `outbox_events` collection with atomic ops

## Testing

- JUnit 5 + Mockito for UseCase unit tests (no Spring context)
- StepVerifier (reactor-test) for reactive publisher verification
- BlockHound for detecting blocking calls in WebFlux services
- ArchUnit for Clean Architecture layer validation
- `@ConfigurationPropertiesScan` on main application class

## Logging

- SLF4J (`LoggerFactory`) — never `System.out.println`
- `CommandLineRunner` logs startup confirmation
- `correlationId` in distributed operation logs
- WARN for ignored Kafka events; ERROR only for unrecoverable failures
