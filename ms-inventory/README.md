# ms-inventory

Microservicio dueño del Bounded Context **Disponibilidad Física y Reservas** dentro de la plataforma B2B Arka. Resuelve el problema crítico #1 del negocio: **prevención de sobreventa por concurrencia** mediante lock pesimista (`SELECT ... FOR UPDATE`) en PostgreSQL 17.

---

## Stack Tecnológico

| Componente        | Tecnología                                          |
| ----------------- | --------------------------------------------------- |
| Lenguaje          | Java 21                                             |
| Framework         | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)   |
| Base de datos     | PostgreSQL 17 — acceso reactivo con **R2DBC**       |
| Mensajería        | Apache Kafka 8 (KRaft)                              |
| Comunicación sync | gRPC (Protobuf) — servidor en puerto `9090`         |
| Build             | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0      |
| Lombok            | 1.18.42                                             |
| API Docs          | Springdoc / OpenAPI (Swagger UI)                    |
| Calidad           | JaCoCo · PiTest · SonarQube · ArchUnit · BlockHound |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Gestión de stock en tiempo real por SKU (quantity, reserved_quantity, available_quantity)
- Reserva de stock con **lock pesimista** para prevenir race conditions (flujo gRPC)
- Reservas temporales con expiración automática de **15 minutos**
- Trazabilidad completa de movimientos de stock (RESTOCK, SHRINKAGE, ORDER_RESERVE, RESERVATION_RELEASE, PRODUCT_CREATION)
- Publicación confiable de eventos de dominio a Kafka mediante **Transactional Outbox Pattern**
- Consumo idempotente de eventos de otros servicios (`ProductCreated`, `OrderCancelled`)

---

## Estructura de Módulos

```text
ms-inventory/
├── applications/app-service/          # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml           # Config base
│       ├── application-local.yaml     # Perfil local (IntelliJ)
│       └── application-docker.yaml    # Perfil Docker Compose
├── domain/
│   ├── model/                         # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── stock/                 # Stock + StockRepository port
│   │       ├── stockreservation/      # StockReservation, ReservationStatus
│   │       ├── stockmovement/         # StockMovement, MovementType
│   │       ├── outboxevent/           # OutboxEvent, payloads, EventType
│   │       ├── processedevent/        # ProcessedEvent (idempotencia)
│   │       └── commons/              # DomainException, TransactionalGateway port
│   └── usecase/                       # Lógica de negocio
│       └── com/arka/usecase/
│           ├── stock/                 # StockUseCase (getBySku, updateStock, reserveStock, processProductCreated)
│           ├── stockreservation/      # StockReservationUseCase (expireReservations, processOrderCancelled)
│           └── outboxrelay/           # OutboxRelayUseCase (fetchPendingEvents, markAsPublished)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/          # Adapters R2DBC: Stock, Reservation, Movement, Outbox, ProcessedEvent
│   │   └── kafka-producer/            # KafkaOutboxRelay (scheduler cada 5s)
│   └── entry-points/
│       ├── reactive-web/              # StockController (REST), StockHandler, GlobalExceptionHandler
│       ├── grpc-inventory/            # GrpcStockService + inventory.proto
│       ├── kafka-consumer/            # KafkaEventConsumer (product-events, order-events)
│       └── scheduler/                 # ExpiredReservationScheduler (cada 60s)
└── deployment/Dockerfile              # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/inventory` — Puerto HTTP: `8082`

| Método | Ruta             | Descripción                                | Roles           | Códigos HTTP       |
| ------ | ---------------- | ------------------------------------------ | --------------- | ------------------ |
| `PUT`  | `/{sku}/stock`   | Actualizar cantidad de stock manualmente   | ADMIN           | 200, 400, 404, 409 |
| `GET`  | `/{sku}`         | Consultar disponibilidad de stock por SKU  | ADMIN, CUSTOMER | 200, 404           |
| `GET`  | `/{sku}/history` | Historial paginado de movimientos de stock | ADMIN           | 200, 404           |

**Parámetros de paginación** en `GET /{sku}/history`: `page` (default `0`), `size` (default `20`, máx `100`).

**Documentación interactiva:** `http://localhost:8082/swagger-ui.html`  
**Especificación OpenAPI:** `http://localhost:8082/api-docs`

---

## Puerto gRPC

Puerto: `9090`

```protobuf
service InventoryService {
  rpc ReserveStock (ReserveStockRequest) returns (ReserveStockResponse);
}
```

Consumido exclusivamente por `ms-order` para reservar stock de forma síncrona antes de confirmar una orden. Usa lock pesimista (`SELECT ... FOR UPDATE`) dentro de una transacción R2DBC ultra-corta.

---

## Eventos Kafka

**Tópico productor:** `inventory-events` (partition key = SKU)

| EventType            | Trigger                                                            |
| -------------------- | ------------------------------------------------------------------ |
| `StockReserved`      | Reserva exitosa vía gRPC                                           |
| `StockReserveFailed` | Stock insuficiente en reserva gRPC                                 |
| `StockReleased`      | Reserva liberada (expiración o cancelación de orden)               |
| `StockUpdated`       | Actualización manual de stock vía REST                             |
| `StockDepleted`      | Stock disponible cae por debajo del umbral (`depletion_threshold`) |

**Tópicos consumidores:**

| Tópico           | Evento procesado | Acción                                           |
| ---------------- | ---------------- | ------------------------------------------------ |
| `product-events` | `ProductCreated` | Crea registro de stock inicial para el nuevo SKU |
| `order-events`   | `OrderCancelled` | Libera la reserva PENDING asociada a la orden    |

Todos los consumidores son **idempotentes** via tabla `processed_events` (PK = `event_id`).

---

## Esquema de Base de Datos (PostgreSQL 17 — `db_inventory`)

| Tabla                | Descripción                                                                                                                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `stock`              | Stock por SKU. `available_quantity` es columna generada (`quantity - reserved_quantity`). CHECK constraints: `quantity >= 0`, `reserved_quantity >= 0`. Lock optimista via campo `version`. |
| `stock_reservations` | Reservas temporales con TTL 15 min. Estados: `PENDING`, `CONFIRMED`, `EXPIRED`, `RELEASED`.                                                                                                 |
| `stock_movements`    | Auditoría de todos los cambios de stock. Tipos: `RESTOCK`, `SHRINKAGE`, `ORDER_RESERVE`, `ORDER_CONFIRM`, `RESERVATION_RELEASE`, `PRODUCT_CREATION`.                                        |
| `outbox_events`      | Eventos de dominio pendientes de publicar a Kafka (Outbox Pattern). Estados: `PENDING`, `PUBLISHED`.                                                                                        |
| `processed_events`   | Registro de eventos Kafka ya procesados para garantizar idempotencia.                                                                                                                       |

---

## Patrones Clave

- **Lock Pesimista** (`SELECT ... FOR UPDATE`): exclusivo para el flujo de reserva gRPC — previene race conditions en concurrencia alta.
- **Lock Optimista** (campo `version`): para actualizaciones manuales de stock vía REST.
- **Transactional Outbox**: eventos insertados en la misma transacción R2DBC que la escritura de negocio. Relay asíncrono publica a Kafka cada 5s.
- **TransactionalGateway**: port de dominio implementado en infra con `TransactionalOperator`. Los UseCases no importan Spring.
- **Idempotencia**: tabla `processed_events` garantiza procesamiento exactamente-una-vez de eventos Kafka.

---

## Cómo Levantar el Servicio

### Prerrequisitos

Levantar la infraestructura compartida desde la raíz del monorepo:

```bash
docker compose up -d arka-db-inventory kafka
```

PostgreSQL de inventario corre en el **puerto 5433** del host (mapeado al 5432 interno del contenedor `arka-db-inventory`).

### Perfil `local` (IntelliJ / terminal)

Conecta a PostgreSQL en `127.0.0.1:5432` con usuario `postgres` / contraseña `root` y a Kafka en `localhost:9092`.

```bash
cd ms-inventory
./gradlew bootRun
```

### Perfil `docker` (Docker Compose)

El perfil se inyecta automáticamente por Compose. Conecta al hostname `arka-db-inventory:5432` y a `kafka:29092`.

### Variables de Entorno Relevantes

| Variable                  | Default (local)  | Descripción                        |
| ------------------------- | ---------------- | ---------------------------------- |
| `MS_INVENTORY_PORT`       | `8082`           | Puerto HTTP del servicio           |
| `GRPC_SERVER_PORT`        | `9090`           | Puerto gRPC                        |
| `R2DBC_HOST`              | `localhost`      | Host PostgreSQL                    |
| `R2DBC_PORT`              | `5433`           | Puerto PostgreSQL                  |
| `R2DBC_DB`                | `db_inventory`   | Nombre de la base de datos         |
| `R2DBC_USER`              | `arka`           | Usuario R2DBC                      |
| `R2DBC_PASSWORD`          | `arkaSecret2025` | Contraseña R2DBC                   |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka                      |
| `SPRING_PROFILES_ACTIVE`  | `local`          | Perfil activo (`local` o `docker`) |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-inventory/`:

```bash
./gradlew build                  # Compilar y empaquetar
./gradlew test                   # Tests unitarios (JUnit 5 + StepVerifier)
./gradlew jacocoMergedReport     # Reporte de cobertura JaCoCo (XML + HTML)
./gradlew pitest                 # Mutation testing con PiTest
./gradlew sonar                  # Análisis estático SonarQube
./gradlew validateStructure      # Validar dependencias de capas (Clean Architecture)
```

---

## Consideraciones Importantes

- **Nunca usar `Optional` en cadenas reactivas** — usar `Mono.justOrEmpty()`, `switchIfEmpty()`, `defaultIfEmpty()`.
- **Nunca `synchronized`/`Lock` en beans reactivos** — Reactor gestiona la concurrencia.
- Los **driven adapters NO manejan transacciones** — la transacción la define el UseCase vía `TransactionalGateway`.
- El **Outbox Relay** y el **Scheduler de expiración** tienen sus intervalos configurados en `application.yaml` (no hardcodeados en `@Scheduled`).
- El Dockerfile usa **multi-stage build** y corre con usuario no-root (`appuser`) sobre `amazoncorretto:21-alpine`.
- **BlockHound** está activo en tests para detectar llamadas bloqueantes en el contexto reactivo.
