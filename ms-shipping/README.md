# ms-shipping

Microservicio dueño del Bounded Context **Logística y Envíos** dentro de la plataforma B2B Arka. Implementa el **Anti-Corruption Layer (ACL)** para 3 carriers (DHL, FedEx, Legacy), gestiona la generación de guías de envío con Resilience4j (Circuit Breaker + Retry + Bulkhead), almacena labels PDF en S3 y publica eventos de despacho a Kafka mediante el **Transactional Outbox Pattern**.

---

## Stack Tecnológico

| Componente    | Tecnología                                          |
| ------------- | --------------------------------------------------- |
| Lenguaje      | Java 21                                             |
| Framework     | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)   |
| Base de datos | PostgreSQL 17 — acceso reactivo con **R2DBC**       |
| Mensajería    | Apache Kafka 8 (KRaft) — reactor-kafka              |
| Almacenamiento| AWS S3 (LocalStack) — labels PDF                    |
| Resiliencia   | Resilience4j (Circuit Breaker, Retry, Bulkhead)     |
| Build         | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0      |
| Lombok        | 1.18.42                                             |
| API Docs      | Springdoc / OpenAPI (Swagger UI)                    |
| Calidad       | JaCoCo · PiTest · SonarQube · ArchUnit · BlockHound |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Generación automática de guías de envío al confirmar una orden (consume `OrderConfirmed` de Kafka)
- ACL con Strategy + Factory para 3 carriers: **DHL**, **FedEx**, **Legacy** — aislados con Resilience4j
- Almacenamiento de labels PDF en AWS S3 (LocalStack en desarrollo)
- Tracking de paquetes con actualizaciones vía **webhooks** de carriers (HMAC-SHA256 firmados)
- Máquina de estados: `PENDING → LABEL_GENERATED → IN_TRANSIT → DELIVERED` | `→ FAILED` (con retry)
- Publicación confiable de eventos `ShippingDispatched` a Kafka mediante **Transactional Outbox Pattern**
- Consumo idempotente de eventos vía tabla `processed_events` (previene duplicados)
- Control de acceso por rol: operaciones administrativas restringidas a `ADMIN`

---

## Estructura de Módulos

```text
ms-shipping/
├── applications/app-service/          # Main Spring Boot, configuración, DI, health indicators
│   └── src/main/resources/
│       ├── application.yaml           # Config base (Resilience4j, carriers, scheduler)
│       ├── application-local.yaml     # Perfil local (IntelliJ / terminal)
│       ├── application-docker.yaml    # Perfil Docker Compose
│       └── schema.sql                 # DDL: shipments, outbox_events, processed_events
├── domain/
│   ├── model/                         # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── shipment/              # Shipment, DeliveryAddress, ShippingResult, ShippingStatus, Carrier
│   │       ├── outboxevent/           # OutboxEvent, DomainEventEnvelope, EventType, OutboxStatus
│   │       ├── processedevent/        # ProcessedEvent (idempotencia Kafka)
│   │       ├── events/gateways/       # EventsGateway port
│   │       └── commons/exception/     # DomainException + 4 subclases
│   └── usecase/                       # Lógica de negocio (7 use cases)
│       └── com/arka/usecase/
│           ├── processshipment/       # ProcessShipmentUseCase (idempotente, genera label, guarda outbox)
│           ├── getshipment/           # GetShipmentUseCase
│           ├── listshipments/         # ListShipmentsUseCase (paginado + filtros)
│           ├── updateshipmentstatus/  # UpdateShipmentStatusUseCase (transiciones de estado)
│           ├── processwebhook/        # ProcessWebhookUseCase (tracking updates de carriers)
│           ├── retryshipment/         # RetryShipmentUseCase (reintentar envíos FAILED)
│           └── outboxrelay/           # OutboxRelayUseCase (fetchPendingEvents, markAsPublished)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/          # Adapters R2DBC: Shipment, Outbox, ProcessedEvent repos
│   │   ├── carrier-factory/           # DHL, FedEx, Legacy adapters + Resilience4j wrappers
│   │   ├── s3-repository/            # S3Adapter (label PDF storage via S3AsyncClient)
│   │   ├── kafka-producer/            # KafkaOutboxRelay (scheduler cada 5s)
│   │   └── async-event-bus/           # ReactiveEventsGateway (domain events)
│   ├── entry-points/
│   │   ├── reactive-web/             # ApiRest (shipments), WebhookController (carrier tracking)
│   │   └── kafka-consumer/            # KafkaEventConsumer (order-events → OrderConfirmed)
│   └── helpers/
│       └── metrics/                   # MicrometerMetricPublisher (AWS SDK metrics)
└── deployment/Dockerfile              # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/api/v1` — Puerto HTTP: `8088`

### Shipments

| Método | Ruta                                | Descripción                              | Roles  | Códigos HTTP       |
| ------ | ----------------------------------- | ---------------------------------------- | ------ | ------------------ |
| `GET`  | `/shipments/{orderId}`              | Obtener envío por ID de orden            | ANY    | 200, 404           |
| `GET`  | `/shipments`                        | Listar envíos (paginado + filtros)       | ADMIN  | 200, 403           |
| `PUT`  | `/shipments/{orderId}/status`       | Actualizar estado del envío              | ADMIN  | 200, 400, 404, 409 |
| `POST` | `/shipments/retry/{orderId}`        | Reintentar envío fallido                 | ADMIN  | 200, 404, 409      |

### Webhooks (Carrier Tracking)

| Método | Ruta                                | Descripción                              | Auth   | Códigos HTTP       |
| ------ | ----------------------------------- | ---------------------------------------- | ------ | ------------------ |
| `POST` | `/webhooks/{carrier}/tracking`      | Recibir actualización de tracking        | HMAC   | 200, 400, 401      |

**Parámetros de query** en `GET /shipments`: `status` (opcional), `carrier` (opcional), `page` (default `0`), `size` (default `20`).

**Headers requeridos:**
- REST: `X-User-Email`, `X-User-Role` (`ADMIN` requerido para list/update/retry)
- Webhooks: `X-Webhook-Signature` (HMAC-SHA256 del body con secreto del carrier)

**Documentación interactiva:** `http://localhost:8088/swagger-ui.html`  
**Especificación OpenAPI:** `http://localhost:8088/api-docs`

### Ejemplos cURL

```bash
# Consultar envío por orden
curl -s "http://localhost:8088/api/v1/shipments/550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .

# Listar envíos con filtros (ADMIN)
curl -s "http://localhost:8088/api/v1/shipments?status=LABEL_GENERATED&carrier=DHL&page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .

# Actualizar estado manualmente (ADMIN)
curl -s -X PUT "http://localhost:8088/api/v1/shipments/550e8400-e29b-41d4-a716-446655440000/status" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{"newStatus": "IN_TRANSIT"}' | jq .

# Reintentar envío fallido (ADMIN)
curl -s -X POST "http://localhost:8088/api/v1/shipments/retry/550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq .

# Simular webhook de DHL (HMAC firmado)
BODY='{"trackingNumber":"DHL-1234567890","status":"IN_TRANSIT","deliveryDate":null}'
SIGNATURE=$(echo -n "$BODY" | openssl dgst -sha256 -hmac "test-dhl-webhook-secret" | awk '{print $2}')
curl -s -X POST "http://localhost:8088/api/v1/webhooks/DHL/tracking" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Signature: $SIGNATURE" \
  -d "$BODY" | jq .
```

---

## Máquina de Estados del Envío

```
PENDING ──▶ LABEL_GENERATED ──▶ IN_TRANSIT ──▶ DELIVERED
   │
   ▼
 FAILED ──▶ (retry) ──▶ PENDING
```

| Desde             | Hacia             | Actor         | Trigger                                |
| ----------------- | ----------------- | ------------- | -------------------------------------- |
| `PENDING`         | `LABEL_GENERATED` | Sistema       | Label generado exitosamente por carrier |
| `PENDING`         | `FAILED`          | Sistema       | Error irrecuperable en generación      |
| `LABEL_GENERATED` | `IN_TRANSIT`      | ADMIN/Webhook | Update manual o webhook del carrier    |
| `IN_TRANSIT`      | `DELIVERED`       | ADMIN/Webhook | Confirmación de entrega                |
| `FAILED`          | `PENDING`         | ADMIN         | Retry manual (`POST /retry/{orderId}`) |

---

## Eventos Kafka

**Tópico productor:** `shipping-events` (partition key = `orderId`)

| EventType           | Trigger                                    | Payload principal                                |
| ------------------- | ------------------------------------------ | ------------------------------------------------ |
| `ShippingDispatched`| Label generado y almacenado en S3          | `orderId`, `trackingNumber`, `carrier`, `labelUrl`, `estimatedDeliveryDate` |

**Tópicos consumidores:**

| Tópico          | Evento procesado  | Acción                                              |
| --------------- | ----------------- | --------------------------------------------------- |
| `order-events`  | `OrderConfirmed`  | Genera label con carrier preferido, guarda en S3    |

Todos los consumidores son **idempotentes** via tabla `processed_events` (PK = `event_id`).

### Evento de Entrada — OrderConfirmed (JSON)

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "OrderConfirmed",
  "timestamp": "2026-05-27T10:00:00Z",
  "source": "ms-order",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "preferredCarrier": "DHL",
    "deliveryAddress": {
      "street": "Calle 123 #45-67",
      "city": "Bogotá",
      "state": "Cundinamarca",
      "postalCode": "11001",
      "country": "CO",
      "recipientName": "Juan Pérez"
    }
  }
}
```

### Evento de Salida — ShippingDispatched (JSON)

```json
{
  "eventId": "990e8400-e29b-41d4-a716-446655440099",
  "eventType": "SHIPPING_DISPATCHED",
  "timestamp": "2026-05-27T10:01:00Z",
  "source": "ms-shipping",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "orderId": "550e8400-e29b-41d4-a716-446655440000",
    "trackingNumber": "DHL-1234567890",
    "carrier": "DHL",
    "labelUrl": "https://s3.localhost.localstack.cloud:4566/arka-shipping-labels/labels/...",
    "estimatedDeliveryDate": "2026-06-01T00:00:00Z"
  }
}
```

---

## Esquema de Base de Datos (PostgreSQL 17 — `db_shipping`)

| Tabla              | Descripción                                                                                                |
| ------------------ | ---------------------------------------------------------------------------------------------------------- |
| `shipments`        | Envío por orden. Status: `PENDING`, `LABEL_GENERATED`, `IN_TRANSIT`, `DELIVERED`, `FAILED`. Address JSONB. |
| `outbox_events`    | Eventos de dominio pendientes de publicar a Kafka. Estados: `PENDING`, `PUBLISHED`.                        |
| `processed_events` | Registro de eventos Kafka ya procesados para garantizar idempotencia.                                      |

**Índices:**
- `shipments`: `order_id`, `tracking_number` (UNIQUE), `status`, `carrier`, `created_at DESC`
- `outbox_events`: `status + created_at` WHERE `status='PENDING'` (partial index)
- `processed_events`: `processed_at`

---

## Patrones Clave

- **Transactional Outbox**: escritura de negocio + evento en la misma transacción R2DBC. Relay asíncrono publica a Kafka cada 5s.
- **ACL (Anti-Corruption Layer)**: Strategy + Factory para 3 carriers. Cada carrier es un adapter independiente, desacoplado del dominio.
- **Circuit Breaker** (Resilience4j): 50% failure threshold, 10 calls mínimo, 30s open state, 3 calls half-open — por carrier.
- **Retry** (Resilience4j): 3 intentos máx, backoff exponencial (2s base, multiplicador x2) — por carrier.
- **Bulkhead** (Resilience4j): 10 llamadas concurrentes máx — por carrier. Previene que un carrier lento agote todos los threads.
- **Idempotencia**: tabla `processed_events` garantiza procesamiento exactamente-una-vez de eventos Kafka.
- **Webhook HMAC**: validación de firma `X-Webhook-Signature` con secreto por carrier. Previene webhooks falsificados.
- **S3 Storage**: labels PDF almacenados con key `labels/{orderId}/{carrier}-{timestamp}.pdf`.
- **Health Indicators**: `R2dbcHealthIndicator` (SELECT 1) + `S3HealthIndicator` (headBucket) para `/actuator/health`.

---

## Cómo Levantar el Servicio

### Opción 1: Local (IntelliJ / terminal)

```bash
# 1. Levantar infraestructura (desde raíz del monorepo)
docker compose up -d localstack kafka kafka-ui

# 2. PostgreSQL debe estar corriendo con db_shipping creada (puerto 5436)

# 3. Ejecutar el servicio
cd ms-shipping
./gradlew bootRun
```

**Perfil activo:** `local` (default)  
**Conexiones:** PostgreSQL `localhost:5436`, Kafka `localhost:9092`, S3/LocalStack `localhost:4566`  
**Puerto servicio:** HTTP `8088`

### Opción 2: Docker Compose

```bash
# Desde raíz del monorepo
docker compose up -d localstack kafka kafka-ui postgres-shipping
docker compose up ms-shipping
```

**Perfil activo:** `docker` (inyectado por Compose)  
**Conexiones:** PostgreSQL `arka-db-shipping:5432`, Kafka `kafka:29092`, S3/LocalStack `localstack:4566`

### Variables de Entorno Relevantes

| Variable                   | Default (local)             | Descripción                                |
| -------------------------- | --------------------------- | ------------------------------------------ |
| `MS_SHIPPING_PORT`         | `8088`                      | Puerto HTTP del servicio                   |
| `R2DBC_HOST`               | `localhost`                 | Host PostgreSQL                            |
| `R2DBC_PORT`               | `5436`                      | Puerto PostgreSQL                          |
| `R2DBC_DB`                 | `db_shipping`               | Nombre de la base de datos                 |
| `R2DBC_USER`               | `arka`                      | Usuario R2DBC                              |
| `R2DBC_PASSWORD`           | `arkaSecret2025`            | Contraseña R2DBC                           |
| `KAFKA_BOOTSTRAP_SERVERS`  | `localhost:9092`            | Brokers Kafka                              |
| `AWS_ENDPOINT`             | `http://localhost:4566`     | Endpoint LocalStack/S3                     |
| `DHL_WEBHOOK_SECRET`       | `test-dhl-webhook-secret`   | Secreto HMAC webhook DHL                   |
| `FEDEX_WEBHOOK_SECRET`     | `test-fedex-webhook-secret` | Secreto HMAC webhook FedEx                 |
| `LEGACY_WEBHOOK_SECRET`    | `test-legacy-webhook-secret`| Secreto HMAC webhook Legacy                |
| `SPRING_PROFILES_ACTIVE`   | `local`                     | Perfil activo (`local` o `docker`)         |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-shipping/`:

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
- Los **driven adapters NO manejan transacciones** — la transacción la define el UseCase vía `TransactionalOperator` configurado en `R2dbcTransactionConfig`.
- El **Outbox Relay** tiene su intervalo configurado en `application.yaml` (`scheduler.outbox-relay.interval: 5000`) — no hardcodeado.
- **DeliveryAddress validación**: solo acepta país `CO` (Colombia) con código postal de 5 dígitos.
- **Carriers bloqueantes** (`Schedulers.boundedElastic()`): las llamadas simuladas a APIs externas de carriers usan el scheduler elástico para no bloquear el event loop.
- El Dockerfile usa **multi-stage build** y corre con usuario no-root (`appuser`) sobre `amazoncorretto:21-alpine`.
- **BlockHound** está activo en tests para detectar llamadas bloqueantes en el contexto reactivo.
- **Kafka consumer**: offset manual (no auto-commit), retry exponencial (500ms–10s, 3 intentos). Se inicia vía `@EventListener(ApplicationReadyEvent)`.
- **Schema auto-init**: `schema.sql` se ejecuta via `ConnectionFactoryInitializer` al arrancar la aplicación.
