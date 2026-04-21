# ms-order

Microservicio dueño del Bounded Context **Gestión de Pedidos** dentro de la plataforma B2B Arka. Implementa el **Orquestador de la Saga Secuencial**: coordina la reserva de stock (gRPC → ms-inventory), consulta de precios autoritativos (gRPC → ms-catalog) y publica eventos de dominio a Kafka mediante el **Transactional Outbox Pattern**.

---

## Stack Tecnológico

| Componente        | Tecnología                                                |
| ----------------- | --------------------------------------------------------- |
| Lenguaje          | Java 21                                                   |
| Framework         | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)         |
| Base de datos     | PostgreSQL 17 — acceso reactivo con **R2DBC**             |
| Mensajería        | Apache Kafka 8 (KRaft)                                    |
| Comunicación sync | gRPC (Protobuf) — cliente hacia ms-inventory y ms-catalog |
| Build             | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0            |
| Lombok            | 1.18.42                                                   |
| API Docs          | Springdoc / OpenAPI (Swagger UI)                          |
| Calidad           | JaCoCo · PiTest · SonarQube · ArchUnit · BlockHound       |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Creación de órdenes B2B con validación de stock en tiempo real (gRPC → ms-inventory)
- Precio autoritativo de productos obtenido desde ms-catalog (gRPC) — nunca del frontend
- Máquina de estados: `PENDIENTE_RESERVA → CONFIRMADO → EN_DESPACHO → ENTREGADO`
- Cancelación de órdenes en estado `CONFIRMADO` (libera stock mediante evento `OrderCancelled` en Kafka)
- Auditoría completa de transiciones de estado en `order_state_history`
- Publicación confiable de eventos de dominio a Kafka mediante **Transactional Outbox Pattern**
- Consumo idempotente de eventos de otros servicios (`PaymentProcessed`, `PaymentFailed`, `ShippingDispatched`)
- Control de acceso por rol: `CUSTOMER` solo ve/cancela sus propias órdenes; `ADMIN` acceso total

---

## Estructura de Módulos

```text
ms-order/
├── applications/app-service/          # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml           # Config base
│       ├── application-local.yaml     # Perfil local (IntelliJ / terminal)
│       └── application-docker.yaml    # Perfil Docker Compose
├── domain/
│   ├── model/                         # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── order/                 # Order, OrderItem, OrderStateHistory, OrderStatus (sealed), OrderStateTransition
│   │       ├── outboxevent/           # OutboxEvent, payloads (OrderCreated/Confirmed/StatusChanged/Cancelled), EventType
│   │       ├── processedevent/        # ProcessedEvent (idempotencia Kafka)
│   │       └── commons/              # DomainException, TransactionalGateway port
│   └── usecase/                       # Lógica de negocio
│       └── com/arka/usecase/
│           ├── order/                 # OrderUseCase (createOrder, getOrder, listOrders, changeStatus, cancel, processExternalEvent)
│           └── outboxrelay/           # OutboxRelayUseCase (fetchPendingEvents, markAsPublished)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/          # Adapters R2DBC: Order, OrderItem, StateHistory, Outbox, ProcessedEvent
│   │   ├── kafka-producer/            # KafkaOutboxRelay (scheduler cada 5s)
│   │   ├── grpc-inventory/            # GrpcInventoryClient → ReserveStock
│   │   └── grpc-catalog/              # GrpcCatalogClient → GetProductInfo
│   └── entry-points/
│       ├── reactive-web/              # OrderController (REST), OrderHandler, GlobalExceptionHandler
│       └── kafka-consumer/            # KafkaEventConsumer (payment-events, shipping-events)
└── deployment/Dockerfile              # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/api/v1/orders` — Puerto HTTP: `8081`

| Método   | Ruta           | Descripción                                  | Roles           | Códigos HTTP       |
| -------- | -------------- | -------------------------------------------- | --------------- | ------------------ |
| `POST`   | `/`            | Crear nueva orden (reserva stock + catálogo) | CUSTOMER, ADMIN | 202, 400, 409, 503 |
| `GET`    | `/{id}`        | Consultar orden por ID con sus items         | CUSTOMER, ADMIN | 200, 403, 404      |
| `GET`    | `/`            | Listar órdenes paginadas con filtros         | CUSTOMER, ADMIN | 200, 400           |
| `PUT`    | `/{id}/status` | Cambiar estado de la orden (ADMIN)           | ADMIN           | 200, 400, 404, 409 |
| `DELETE` | `/{id}`        | Cancelar orden                               | CUSTOMER, ADMIN | 200, 403, 404, 409 |

**Parámetros de paginación** en `GET /`: `page` (default `0`), `size` (default `20`, máx `100`), `status` (opcional), `customerId` (opcional, ADMIN only).

**Headers requeridos:** `X-User-Email` (email del usuario autenticado, inyectado por API Gateway), `X-User-Role` (`ADMIN` o `CUSTOMER`, default `CUSTOMER`).

> El servicio deriva el UUID del usuario a partir del email con `UUID.nameUUIDFromBytes(email.getBytes(UTF-8))`.  
> El mock data del proyecto usa los siguientes emails y sus UUIDs derivados:
>
> - `admin@arka.com` → `2d66e954-4482-3e67-973c-7142c931083e`
> - `customer1@arka.com` → `482eae01-3840-3d80-9a3b-17333e6b32d5`
> - `customer2@arka.com` → `3e6c5f4e-ae19-32f9-a254-ba18570e280e`

**Documentación interactiva:** `http://localhost:8081/swagger-ui.html`  
**Especificación OpenAPI:** `http://localhost:8081/api-docs`

### Ejemplos cURL (verificados con mock data)

#### Listar todas las órdenes (ADMIN ve todas, ordenadas por fecha DESC)

```bash
curl -s "http://localhost:8081/api/v1/orders?page=0&size=10" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .
```

#### Filtrar órdenes por estado

```bash
curl -s "http://localhost:8081/api/v1/orders?status=CONFIRMADO" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .
```

#### CUSTOMER solo ve sus propias órdenes (filtro automático por customerId)

```bash
curl -s "http://localhost:8081/api/v1/orders" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq .
```

#### Consultar orden específica con todos sus items y subtotales

```bash
curl -s "http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .
```

#### Cambiar estado CONFIRMADO → EN_DESPACHO (solo ADMIN)

```bash
curl -s -X PUT "http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/status" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{"newStatus": "EN_DESPACHO", "reason": "Entregado a transportista DHL"}' | jq .
```

#### Cancelar orden (CONFIRMADO → CANCELADO)

```bash
# Admin cancela cualquier orden
curl -s -X PUT "http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440004/cancel" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{"reason": "Cancelación solicitada por el cliente"}' | jq .
```

#### Crear nueva orden (requiere ms-inventory y ms-catalog activos vía gRPC)

```bash
curl -s -X POST "http://localhost:8081/api/v1/orders" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{
    "customerId": "2d66e954-4482-3e67-973c-7142c931083e",
    "shippingAddress": "Calle 123 #45-67, Bogotá",
    "items": [
      {"sku": "KB-MECH-001", "quantity": 2},
      {"sku": "MS-OPT-002", "quantity": 1}
    ]
  }' | jq .
```

#### Casos de error esperados

```bash
# 403 — CUSTOMER intenta ver orden de otro customer
curl -s "http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440002" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq .
# → {"code":"ACCESS_DENIED","message":"Customer ... cannot access order ..."}

# 409 — Transición de estado inválida (estado terminal)
curl -s -X PUT "http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440003/status" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{"newStatus": "EN_DESPACHO", "reason": "test"}' | jq .
# → {"code":"INVALID_STATE_TRANSITION","message":"Cannot transition from CANCELADO to EN_DESPACHO"}

# 404 — Orden inexistente
curl -s "http://localhost:8081/api/v1/orders/00000000-0000-0000-0000-000000000000" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .
# → {"code":"ORDER_NOT_FOUND","message":"Order not found: 00000000-..."}

# 400 — Valor de status inválido en query param
curl -s "http://localhost:8081/api/v1/orders?status=INVALIDO" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .
# → {"code":"VALIDATION_ERROR","message":"arg0: must be one of: PENDIENTE_RESERVA, CONFIRMADO, ..."}
```

#### Verificar Outbox y auditoría directamente en PostgreSQL

```bash
# Eventos publicados a Kafka (status PUBLISHED = relay exitoso)
docker exec arka-db-orders psql -U arka -d db_orders \
  -c "SELECT event_type, status, partition_key, created_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;"

# Historial completo de transiciones de estado
docker exec arka-db-orders psql -U arka -d db_orders \
  -c "SELECT order_id, previous_status, new_status, changed_by, reason FROM order_state_history ORDER BY created_at DESC LIMIT 10;"
```

---

## Máquina de Estados de la Orden

```
PENDIENTE_RESERVA ──▶ CONFIRMADO ──▶ EN_DESPACHO ──▶ ENTREGADO
                           │
                           ▼
                       CANCELADO
```

| Desde               | Hacia         | Actor           | Trigger                                    |
| ------------------- | ------------- | --------------- | ------------------------------------------ |
| `PENDIENTE_RESERVA` | `CONFIRMADO`  | Sistema         | Stock reservado exitosamente (createOrder) |
| `CONFIRMADO`        | `EN_DESPACHO` | ADMIN           | `PUT /{id}/status`                         |
| `EN_DESPACHO`       | `ENTREGADO`   | ADMIN           | `PUT /{id}/status`                         |
| `CONFIRMADO`        | `CANCELADO`   | ADMIN, CUSTOMER | `DELETE /{id}`                             |

Estados terminales: `ENTREGADO`, `CANCELADO` — sin transiciones de salida.

---

## Saga Secuencial (Fase 1)

```
CreateOrder ──▶ [gRPC] GetProductInfo (ms-catalog) ──▶ [gRPC] ReserveStock (ms-inventory)
           ──▶ Persistir Order (CONFIRMADO) + Items + Historial + OutboxEvent (R2DBC TX)
           ──▶ [Outbox Relay 5s] Publicar OrderConfirmed → Kafka order-events
```

Si la reserva de stock falla → `InsufficientStockException (409)`.  
Si gRPC ms-inventory no responde → `InventoryServiceUnavailableException (503)`.  
Si gRPC ms-catalog no responde → `CatalogServiceUnavailableException (503)`.

---

## Eventos Kafka

**Tópico productor:** `order-events` (partition key = `orderId`)

| EventType            | Trigger                                     |
| -------------------- | ------------------------------------------- |
| `OrderCreated`       | Orden creada (Fase 2 — pre-pago)            |
| `OrderConfirmed`     | Stock reservado y orden confirmada (Fase 1) |
| `OrderStatusChanged` | Cambio de estado manual por ADMIN           |
| `OrderCancelled`     | Cancelación por CUSTOMER o ADMIN            |

**Tópicos consumidores:**

| Tópico            | Evento procesado     | Acción                                                |
| ----------------- | -------------------- | ----------------------------------------------------- |
| `payment-events`  | `PaymentProcessed`   | Infraestructura lista (Fase 2: confirmar orden)       |
| `payment-events`  | `PaymentFailed`      | Infraestructura lista (Fase 2: cancelar orden)        |
| `shipping-events` | `ShippingDispatched` | Infraestructura lista (Fase 2: avanzar a EN_DESPACHO) |

Todos los consumidores son **idempotentes** via tabla `processed_events` (PK = `event_id`).

---

## Esquema de Base de Datos (PostgreSQL 17 — `db_orders`)

| Tabla                 | Descripción                                                                                                       |
| --------------------- | ----------------------------------------------------------------------------------------------------------------- |
| `orders`              | Orden de compra. Estados: `PENDIENTE_RESERVA`, `CONFIRMADO`, `EN_DESPACHO`, `ENTREGADO`, `CANCELADO`.             |
| `order_items`         | Líneas de la orden. `subtotal` es columna generada (`quantity * unit_price`).                                     |
| `order_state_history` | Auditoría de todas las transiciones de estado. `previous_status` y `changed_by` son nullable (transiciones auto). |
| `outbox_events`       | Eventos de dominio pendientes de publicar a Kafka. Estados: `PENDING`, `PUBLISHED`.                               |
| `processed_events`    | Registro de eventos Kafka ya procesados para garantizar idempotencia.                                             |

---

## Patrones Clave

- **Transactional Outbox**: escritura de negocio + evento en la misma transacción R2DBC. Relay asíncrono publica a Kafka cada 5 s.
- **TransactionalGateway**: port de dominio implementado en infra con `TransactionalOperator`. Los UseCases no importan Spring.
- **Idempotencia**: tabla `processed_events` garantiza procesamiento exactamente-una-vez de eventos Kafka.
- **Saga Secuencial**: ms-order orquesta la creación consultando precios (ms-catalog) y reservando stock (ms-inventory) vía gRPC antes de persistir.

---

## Cómo Levantar el Servicio

### Opción 1: Docker Compose (recomendado)

```bash
# Desde la raíz del monorepo — levantar infraestructura base
docker compose up -d postgres-orders kafka kafka-ui

# Construir y levantar ms-order
docker compose up -d ms-order

# Ver logs
docker compose logs -f ms-order
```

**Perfil activo:** `docker`  
**Conexiones:** PostgreSQL `arka-db-orders:5432`, Kafka `kafka:29092`  
**Puerto servicio:** HTTP `8081`

### Opción 2: Local (IntelliJ / terminal)

```bash
# 1. Levantar infraestructura (desde raíz del monorepo)
docker compose up -d postgres-orders kafka kafka-ui

# 2. Ejecutar el servicio
cd ms-order
./gradlew bootRun
```

**Perfil activo:** `local` (default)  
**Conexiones:** PostgreSQL `127.0.0.1:5432/db_orders`, Kafka `localhost:9092`  
**Puerto servicio:** HTTP `8081`

### Variables de Entorno Relevantes

| Variable                  | Default (local)  | Descripción                        |
| ------------------------- | ---------------- | ---------------------------------- |
| `MS_ORDER_PORT`           | `8081`           | Puerto HTTP del servicio           |
| `R2DBC_HOST`              | `localhost`      | Host PostgreSQL                    |
| `R2DBC_PORT`              | `5432`           | Puerto PostgreSQL                  |
| `R2DBC_DB`                | `db_orders`      | Nombre de la base de datos         |
| `R2DBC_USER`              | `arka`           | Usuario R2DBC                      |
| `R2DBC_PASSWORD`          | `arkaSecret2025` | Contraseña R2DBC                   |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka                      |
| `MS_INVENTORY_GRPC_HOST`  | `localhost`      | Host gRPC ms-inventory             |
| `MS_INVENTORY_GRPC_PORT`  | `9090`           | Puerto gRPC ms-inventory           |
| `MS_CATALOG_GRPC_HOST`    | `localhost`      | Host gRPC ms-catalog               |
| `MS_CATALOG_GRPC_PORT`    | `9091`           | Puerto gRPC ms-catalog             |
| `SPRING_PROFILES_ACTIVE`  | `local`          | Perfil activo (`local` o `docker`) |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-order/`:

```bash
./gradlew build                  # Compilar, tests y empaquetar
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
- El **Outbox Relay** tiene su intervalo configurado en `application.yaml` (`scheduler.outbox-relay.interval: 5000`), no hardcodeado.
- **Precio autoritativo**: el `unitPrice` de cada `OrderItem` siempre proviene de ms-catalog (gRPC). Nunca del frontend ni de ms-inventory.
- El campo `subtotal` en `order_items` es una **columna generada** en PostgreSQL — nunca incluirlo en INSERTs.
- El Dockerfile usa **multi-stage build** y corre con usuario no-root (`appuser`) sobre `amazoncorretto:21-alpine`.
- **gRPC stubs**: inyectados por constructor (`@GrpcClient` en parámetro del constructor) para cumplir la regla ArchUnit de campos finales.
