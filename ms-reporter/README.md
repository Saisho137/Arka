# ms-reporter

Microservicio dueño del Bounded Context **Reportes, Analítica y Event Sourcing** dentro de la plataforma B2B Arka. Implementa **CQRS / Event Sourcing**: consume eventos de los 7 tópicos Kafka del ecosistema, almacena en un Event Store particionado por fecha, proyecta Read Models (ventas, inventario, productos), genera reportes CSV/PDF bajo demanda, calcula KPIs de negocio, detecta alertas de stock bajo y permite trazar correlaciones entre eventos distribuidos.

---

## Stack Tecnológico

| Componente     | Tecnología                                                |
| -------------- | --------------------------------------------------------- |
| Lenguaje       | Java 21                                                   |
| Framework      | Spring Boot 4.0.3 — **Spring MVC + Virtual Threads**      |
| Base de datos  | PostgreSQL 17 — acceso con **JDBC** (NamedParameterJdbcTemplate) |
| Mensajería     | Apache Kafka 8 (KRaft) — spring-kafka (`@KafkaListener`)  |
| Almacenamiento | AWS S3 (LocalStack) — reportes CSV/PDF comprimidos GZIP   |
| PDF            | Apache PDFBox 3.0.4                                       |
| Build          | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0            |
| Lombok         | 1.18.42                                                   |
| API Docs       | Springdoc / OpenAPI (`springdoc-openapi-starter-webmvc-ui:3.0.2`) |
| Calidad        | JaCoCo · PiTest · ArchUnit · jqwik (PBT)                 |

> **DIFERENCIA CLAVE:** `reactive=false` en `gradle.properties`. Este es el único servicio del monorepo que usa paradigma **imperativo** (MVC + Virtual Threads). No usa WebFlux, R2DBC, reactor-kafka ni `Mono`/`Flux`. Justificación: generación de archivos CSV/PDF de 500MB+ es CPU-bound, donde Virtual Threads ofrecen mejor ergonomía.

---

## Responsabilidades del Servicio

- **Event Store**: almacena todos los eventos del ecosistema en tabla particionada por mes (12 particiones 2026)
- **Consumo idempotente** de 7 tópicos Kafka con deduplicación por `eventId` (tabla `processed_events`)
- **Proyección a Read Models**: incrementa/decrementa `sales_summary` (OrderConfirmed/OrderCancelled), actualiza inventario (StockUpdated), nombres de producto (PriceChanged)
- **Generación de reportes CSV/PDF** bajo demanda (async con Virtual Threads), compresión GZIP, upload a S3
- **Cálculo de KPIs**: total_revenue, total_orders, average_order_value, conversion_rate, cart_abandonment_rate, average_delivery_time_days, top 10 productos
- **Detección de alertas de stock bajo**: análisis de patrones de consumo (30 días), genera alertas si días_para_agotamiento ≤ 7
- **Publicación de eventos** `StockAlertGenerated` al tópico `reporter-events`
- **Reconstrucción de Read Models**: replay completo desde Event Store por lotes de 1000
- **Trazabilidad**: consulta de cadena de eventos por `correlationId`
- Control de acceso: todos los endpoints restringidos a rol `ADMIN`

---

## Estructura de Módulos

```text
ms-reporter/
├── applications/app-service/          # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml           # Config base (JDBC, Kafka, S3, scheduler)
│       ├── application-local.yaml     # Perfil local (IntelliJ / terminal)
│       └── application-docker.yaml    # Perfil Docker Compose
├── domain/
│   ├── model/                         # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── eventstore/            # EventStoreEntry, EventEnvelope, DomainEventEnvelope
│   │       ├── report/                # ReportMetadata, ReportStatus
│   │       ├── sales/                 # SalesSummary
│   │       ├── alert/                 # StockAlert, AlertStatus
│   │       ├── kpi/                   # KpiResult, TopSellingProduct
│   │       ├── rebuild/               # RebuildJob, RebuildStatus
│   │       └── commons/exception/     # DomainException + 6 subclases
│   └── usecase/                       # Lógica de negocio (6 use cases)
│       └── com/arka/usecase/
│           ├── eventconsumption/       # EventConsumptionUseCase (idempotencia + proyección)
│           ├── reportgeneration/       # ReportGenerationUseCase (CSV/PDF + S3)
│           ├── kpicalculation/         # KpiCalculationUseCase
│           ├── stockalert/             # StockAlertUseCase (detección patrones)
│           ├── eventtrace/             # EventTraceUseCase (correlationId)
│           └── readmodelrebuild/       # ReadModelRebuildUseCase (replay Event Store)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── jdbc-postgresql/           # 6 adapters JDBC: EventStore, ReportMetadata,
│   │   │                              #   SalesSummary, StockAlert, ProcessedEvent, RebuildJob
│   │   ├── s3-repository/            # S3Adapter (upload multipart, presigned URLs)
│   │   └── kafka-producer/            # KafkaEventPublisherAdapter (StockAlertGenerated)
│   ├── entry-points/
│   │   ├── api-rest/                  # Controllers, Handlers, DTOs, GlobalExceptionHandler
│   │   └── kafka-consumer/            # KafkaEventConsumer (7 tópicos, manual ack)
│   └── helpers/
│       ├── csv-generator/             # CsvReportGenerator (streaming + GZIP)
│       ├── pdf-generator/             # PdfReportGenerator (PDFBox + GZIP)
│       └── metrics/                   # MicrometerMetricPublisher (AWS SDK metrics)
└── deployment/Dockerfile              # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Puerto HTTP: `8087` — Todos los endpoints requieren `X-User-Role: ADMIN`

### Reports

| Método | Ruta                     | Descripción                        | Códigos HTTP  |
| ------ | ------------------------ | ---------------------------------- | ------------- |
| `POST` | `/reports/sales/weekly`  | Generar reporte semanal de ventas  | 202, 400, 403 |
| `GET`  | `/reports/{reportId}`    | Consultar estado + URL de descarga | 200, 403, 404 |

### Metrics

| Método | Ruta            | Descripción                    | Códigos HTTP  |
| ------ | --------------- | ------------------------------ | ------------- |
| `GET`  | `/metrics/kpis` | KPIs de negocio por rango      | 200, 400, 403 |

### Events

| Método | Ruta                            | Descripción                          | Códigos HTTP |
| ------ | ------------------------------- | ------------------------------------ | ------------ |
| `GET`  | `/events/trace/{correlationId}` | Trazar eventos por correlación       | 200, 403     |

### Admin

| Método | Ruta                         | Descripción                       | Códigos HTTP  |
| ------ | ---------------------------- | --------------------------------- | ------------- |
| `POST` | `/admin/rebuild-read-models` | Reconstruir un Read Model          | 202, 400, 403 |

**Headers requeridos:** `X-User-Role` (debe ser `ADMIN`), `X-User-Email` (opcional, logging).

**Query params** en `GET /metrics/kpis`: `startDate` (ISO date, requerido), `endDate` (ISO date, requerido).

**Documentación interactiva:** `http://localhost:8087/swagger-ui.html`
**Especificación OpenAPI:** `http://localhost:8087/api-docs`

### Ejemplos cURL

```bash
# Generar reporte semanal de ventas (CSV)
curl -s -X POST "http://localhost:8087/reports/sales/weekly" \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMIN" \
  -H "X-User-Email: admin@arka.com" \
  -d '{"startDate": "2026-05-01", "endDate": "2026-05-27", "format": "CSV"}' | jq .

# Consultar estado del reporte
curl -s "http://localhost:8087/reports/550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Role: ADMIN" | jq .

# Obtener KPIs del mes
curl -s "http://localhost:8087/metrics/kpis?startDate=2026-05-01&endDate=2026-05-31" \
  -H "X-User-Role: ADMIN" | jq .

# Trazar eventos por correlationId (seguir flujo de una orden)
curl -s "http://localhost:8087/events/trace/550e8400-e29b-41d4-a716-446655440000" \
  -H "X-User-Role: ADMIN" | jq .

# Reconstruir Read Model sales_summary desde Event Store
curl -s -X POST "http://localhost:8087/admin/rebuild-read-models" \
  -H "Content-Type: application/json" \
  -H "X-User-Role: ADMIN" \
  -d '{"readModelName": "sales_summary"}' | jq .
```

---

## Eventos Kafka

### Consumo (7 tópicos)

Consumer group: `reporter-service-group` · Commit manual (`AckMode.MANUAL_IMMEDIATE`)

| Tópico             | Eventos procesados                                    | Proyección a Read Model         |
| ------------------ | ----------------------------------------------------- | ------------------------------- |
| `order-events`     | `OrderConfirmed`, `OrderCancelled`                    | `sales_summary` (+/- ventas)    |
| `inventory-events` | `StockUpdated`                                        | `report_inventory`              |
| `product-events`   | `PriceChanged`                                        | product name update             |
| `cart-events`      | `CartAbandoned`                                       | KPI: cart_abandonment_rate      |
| `payment-events`   | `PaymentProcessed`, `PaymentFailed`                   | Event Store (sin proyección)    |
| `shipping-events`  | `ShippingDispatched`                                  | KPI: delivery_time calculation  |
| `provider-events`  | `PurchaseOrderCreated`                                | Event Store (sin proyección)    |

Todos los eventos se almacenan en el Event Store independientemente del `eventType`. Eventos desconocidos: almacenados + log WARN, sin proyección a Read Models.

### Producción

**Tópico:** `reporter-events` (partition key = SKU)

| EventType             | Trigger                                        | Payload                                              |
| --------------------- | ---------------------------------------------- | ---------------------------------------------------- |
| `StockAlertGenerated` | Job @Scheduled (cron `0 0 2 * * *`, 2am)      | `sku`, `currentStock`, `dailyConsumptionRate`, `daysToDepletion`, `alertStatus` |

### Envelope estándar Kafka (JSON)

```json
{
  "eventId": "990e8400-e29b-41d4-a716-446655440099",
  "eventType": "StockAlertGenerated",
  "timestamp": "2026-05-27T02:00:00Z",
  "source": "ms-reporter",
  "correlationId": null,
  "payload": { ... }
}
```

---

## Tablas PostgreSQL (`db_reporter`)

| Tabla               | Descripción                                                          |
| ------------------- | -------------------------------------------------------------------- |
| `event_store`       | Eventos brutos (particionada por mes, índice GIN en payload)         |
| `sales_summary`     | Read Model agregado: ventas por SKU + semana (PK compuesta)          |
| `report_metadata`   | Estado y metadatos de reportes generados (PROCESSING/COMPLETED/FAILED) |
| `stock_alerts`      | Alertas de stock bajo (ACTIVE/RESOLVED)                              |
| `rebuild_jobs`      | Jobs de reconstrucción de Read Models                                |
| `processed_events`  | UUIDs de eventos procesados (deduplicación/idempotencia)             |
| `report_orders`     | Read Model de órdenes para reportes                                  |
| `report_inventory`  | Read Model de inventario para reportes                               |

**Índices destacados:**
- `event_store`: GIN en `payload`, compuesto en `(event_type, timestamp)`, en `correlation_id`
- `sales_summary`: PK compuesta `(sku, week_start_date)`
- Particionamiento: 12 particiones mensuales (2026-01 a 2026-12) por `timestamp`

---

## Patrones Clave

- **Event Sourcing + CQRS**: Event Store como fuente de verdad, Read Models como proyecciones optimizadas para consulta. Rebuild disponible bajo demanda.
- **Consumo idempotente**: tabla `processed_events` con INSERT explícito (UUID viene de Kafka, fuente externa). Mismo patrón de ms-inventory adaptado a JDBC.
- **Streaming de archivos**: `BufferedWriter` por lotes para CSV, `incrementalSave` de PDFBox para PDF — sin cargar 500MB+ en memoria.
- **Virtual Threads** (`spring.threads.virtual.enabled: true`): reportes CPU-bound ejecutan en Virtual Thread Pool vía `@Async`, liberando el thread del request.
- **Job periódico externalizado**: `@Scheduled(cron = "${scheduler.stock-alert.cron}")` — sin defaults hardcodeados.
- **Controller → Handler → UseCase**: capa thin de controllers (solo anotaciones HTTP), handlers orquestan mapeo + validación de rol + delegación.
- **Domain Exception Hierarchy**: `DomainException` abstracta con `getHttpStatus()` + `getCode()`, mapeada en `GlobalExceptionHandler`.
- **Outbox-less publishing**: `StockAlertGenerated` se publica directamente vía `KafkaTemplate` (evento informativo, no-crítico). No requiere Outbox Pattern.

---

## Cómo Levantar el Servicio

### Opción 1: Local (IntelliJ / terminal)

```bash
# 1. Levantar infraestructura (desde raíz del monorepo)
docker compose up -d arka-db-reporter kafka kafka-ui localstack

# 2. Ejecutar el servicio
cd ms-reporter
./gradlew bootRun
```

**Perfil activo:** `local` (default)
**Conexiones:** PostgreSQL `localhost:5435/db_reporter`, Kafka `localhost:9092`, S3 `localhost:4566`
**Puerto servicio:** HTTP `8087`

### Opción 2: Docker Compose

```bash
# Desde raíz del monorepo
docker compose up -d arka-db-reporter kafka kafka-ui localstack
docker compose up ms-reporter
```

**Perfil activo:** `docker` (inyectado por Compose)
**Conexiones:** PostgreSQL `arka-db-reporter:5432/db_reporter`, Kafka `arka-kafka:29092`, S3 `localstack:4566`

### Variables de Entorno Relevantes

| Variable                  | Default (local)      | Descripción                          |
| ------------------------- | -------------------- | ------------------------------------ |
| `MS_REPORTER_PORT`        | `8087`               | Puerto HTTP del servicio             |
| `JDBC_HOST`               | `localhost`          | Host PostgreSQL                      |
| `JDBC_PORT`               | `5435`               | Puerto PostgreSQL                    |
| `JDBC_DB`                 | `db_reporter`        | Nombre de la base de datos           |
| `JDBC_USER`               | `arka`               | Usuario PostgreSQL                   |
| `JDBC_PASSWORD`           | `arkaSecret2025`     | Contraseña PostgreSQL                |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092`     | Brokers Kafka                        |
| `S3_BUCKET`               | `arka-reports`       | Bucket S3 para reportes              |
| `AWS_ENDPOINT`            | `http://localhost:4566` | Endpoint S3 (LocalStack)          |
| `STOCK_ALERT_CRON`        | `0 0 2 * * *`       | Cron del job de alertas (2am)        |
| `SPRING_PROFILES_ACTIVE`  | `local`              | Perfil activo (`local` o `docker`)   |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-reporter/`:

```bash
./gradlew build                  # Compilar y empaquetar
./gradlew test                   # Tests unitarios (JUnit 5)
./gradlew jacocoMergedReport     # Reporte de cobertura JaCoCo (XML + HTML)
./gradlew pitest                 # Mutation testing con PiTest
./gradlew validateStructure      # Validar dependencias de capas (Clean Architecture)
```

---

## Consideraciones Importantes

- **Paradigma imperativo**: retornos síncronos (`ResponseEntity<T>`, `List<T>`, `void`). NO usar `Mono`/`Flux` en ningún punto del servicio.
- **Virtual Threads**: habilitados a nivel de Spring Boot (`spring.threads.virtual.enabled: true`). Cada request HTTP corre en un virtual thread. `@Async` usa un executor con Virtual Thread factory.
- **Event Store particionado**: la tabla `event_store` está particionada por `RANGE (timestamp)` con 12 particiones mensuales. Insertar sin especificar partición — PostgreSQL lo rutea automáticamente.
- **Read Models desechables**: diseñados para ser reconstruidos desde Event Store en cualquier momento vía `/admin/rebuild-read-models`. El Event Store es la fuente de verdad.
- **Archivos grandes**: el `PdfReportGenerator` valida que el output no exceda 500MB. El `CsvReportGenerator` escribe por streaming sin cargar todo en memoria.
- **Idempotencia Kafka**: si un evento con el mismo `eventId` ya fue procesado, se descarta silenciosamente sin error.
- **Jackson 3.x**: Spring Boot 4.0.3 usa `tools.jackson.core:jackson-databind` (paquete `tools.jackson.*`), NO `com.fasterxml.jackson`.
- El Dockerfile usa **multi-stage build** y corre con usuario no-root (`appuser`) sobre `amazoncorretto:21-alpine`.
