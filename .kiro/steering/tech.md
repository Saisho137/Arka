# Arka — Tech Stack & Build

## Stack

| Component     | Technology                                                |
| ------------- | --------------------------------------------------------- |
| Language      | Java 21                                                   |
| Framework     | Spring Boot 4.0.3                                         |
| Reactive      | Spring WebFlux + Project Reactor (default for 8 services) |
| Imperative    | Spring MVC + Virtual Threads (ms-reporter only)           |
| Messaging     | Apache Kafka 8 (KRaft, no ZooKeeper)                      |
| Relational DB | PostgreSQL 17 (R2DBC reactive, JDBC ms-reporter)          |
| Document DB   | MongoDB (Reactive Drivers)                                |
| Cache         | Redis (Cache-Aside)                                       |
| Sync comms    | gRPC (Protobuf, inter-service)                            |
| Resiliency    | Resilience4j (Circuit Breaker, Bulkhead, Retry)           |
| Cloud         | AWS (API Gateway, S3, SES, Secrets Manager)               |
| Local AWS     | LocalStack (CloudFormation)                               |
| Build         | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0            |
| Load Balancer | Traefik                                                   |
| Lombok        | 1.18.42                                                   |
| API Docs      | Springdoc / OpenAPI                                       |

## Paradigm Rules

- `reactive=true` in `gradle.properties` for all services EXCEPT ms-reporter (`reactive=false`)
- Blocking SDKs: wrap with `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`
- Never `Optional` in reactive chains → use `Mono.justOrEmpty()`, `switchIfEmpty()`, `defaultIfEmpty()`
- `Optional` valid only in: record compact constructors, ms-reporter code, pure utility methods without I/O, sync RuleEngine rules
- Never `synchronized`/`Lock` in reactive beans → Reactor manages concurrency
- `ConcurrentHashMap` only for mutable maps initialized post-startup

## Build Commands (run from `ms-<name>/`)

```bash
./gradlew build                          # Build
./gradlew bootRun                        # Run
./gradlew test                           # Tests (JUnit 5)
./gradlew pitest                         # Mutation testing (PiTest)
./gradlew jacocoMergedReport             # Coverage (JaCoCo)
./gradlew sonar                          # SonarQube analysis
./gradlew validateStructure              # Validate Clean Architecture layers
```

## Scaffold Code Generation (from `ms-<name>/`)

```bash
./gradlew generateModel --name=X         # (gm)  Entity + Gateway interface
./gradlew generateUseCase --name=X       # (guc) UseCase class
./gradlew generateDrivenAdapter --type=X # (gda) r2dbc, mongo, kafka, restconsumer, secrets
./gradlew generateEntryPoint --type=X    # (gep) webflux, kafka, graphql
./gradlew generateHelper --name=X        # (gh)  Helper module
```

## Local Infrastructure (`docker compose up -d` from repo root)

| Service                | Port    | Details          |
| ---------------------- | ------- | ---------------- |
| PostgreSQL (orders)    | 5432    | db_orders        |
| PostgreSQL (inventory) | 5433    | db_inventory     |
| PostgreSQL (payment)   | 5434    | db_payment       |
| Kafka (KRaft)          | 9092    | Message broker   |
| Kafka UI               | 8080    | Topic dashboard  |
| LocalStack             | 4566    | Secrets + API GW |
| Traefik                | 80/8090 | LB + dashboard   |

## Quality Tools

- JaCoCo: mandatory coverage (XML + HTML)
- PiTest: mutation testing on `com.arka.*` (JUnit 5)
- SonarQube: static analysis in build
- BlockHound: detects blocking calls in reactive tests
- ArchUnit: validates Clean Architecture layer deps
