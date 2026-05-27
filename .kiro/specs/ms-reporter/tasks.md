# Plan de Implementación: ms-reporter

## Visión General

Implementación incremental del microservicio `ms-reporter` siguiendo Clean Architecture (Bancolombia Scaffold 4.2.0). Se construye desde el dominio hacia afuera: modelo → casos de uso → adaptadores → entry-points → consumidor Kafka → jobs. Java 21, Spring Boot 4.0.3, Spring MVC + Virtual Threads (paradigma imperativo), PostgreSQL 17 (JDBC), Apache Kafka (spring-kafka `@KafkaListener`), AWS S3, Apache PDFBox, jqwik para PBT.

**DIFERENCIA CLAVE vs otros servicios Arka:** `ms-reporter` es imperativo (`reactive=false`). No usa WebFlux, R2DBC, reactor-kafka ni `Mono`/`Flux`. Usa Spring MVC, JDBC (`JdbcTemplate`), `@KafkaListener` (spring-kafka) y retornos síncronos.

**REGLA CRÍTICA DE IMPLEMENTACIÓN:** Todos los módulos nuevos (Model, UseCase, Driven Adapter, Entry Point, Helper) DEBEN generarse usando las tareas Gradle del plugin Bancolombia Scaffold. La creación manual de estructura de módulos está PROHIBIDA. Ejecutar siempre desde la raíz de `ms-reporter/`:

```bash
# Generar Model + Gateway interface
./gradlew generateModel --name=<Name>

# Generar UseCase
./gradlew generateUseCase --name=<Name>

# Generar Driven Adapter
./gradlew generateDrivenAdapter --type=<type>

# Generar Entry Point
./gradlew generateEntryPoint --type=<type>

# Generar Helper
./gradlew generateHelper --name=<Name>

# Validar estructura
./gradlew validateStructure
```

Ver `.agents/skills/scaffold-tasks/SKILL.md` para referencia completa de comandos y tipos disponibles.

**REUTILIZACIÓN Y VERSIONADO:** Antes de implementar patrones transversales o agregar dependencias, consultar **`.kiro/steering/reusability.md`**. Aunque ms-reporter es imperativo, varios patrones de `ms-inventory` son reutilizables con adaptación de paradigma:

| # | Componente de reusability.md | Aplica a ms-reporter | Adaptación requerida |
|---|---|---|---|
| 1 | **Outbox Domain Model** (`DomainEventEnvelope`) | SÍ (para publicar `StockAlertGenerated`) | Mismo record, `MS_SOURCE = "ms-reporter"` |
| 3 | **ProcessedEvents (idempotencia)** | SÍ (misma interfaz port) | JDBC `JdbcTemplate` INSERT en vez de `DatabaseClient` R2DBC |
| 5 | **Kafka Producer Config** | SÍ (conceptos: acks=all, retries, idempotent) | `KafkaTemplate` (spring-kafka sync) en vez de `KafkaSender` (reactor-kafka) |
| 6 | **Kafka Consumer** (patrón: eventType switch, idempotencia, skip desconocidos) | SÍ (lógica de discriminación) | `@KafkaListener` (spring-kafka) en vez de `KafkaReceiver` (reactor-kafka) |
| 7 | **Controller → Handler → UseCase** | SÍ (mismo patrón capas) | `ResponseEntity<T>` sync en vez de `Mono<ResponseEntity<T>>` |
| 8 | **GlobalExceptionHandler** | SÍ (misma estructura) | `MethodArgumentNotValidException` en vez de `WebExchangeBindException` |
| 9 | **Spring Profiles** (3 YAMLs) | SÍ (100% reutilizable) | BD JDBC, sin R2DBC |
| 10 | **Springdoc/OpenAPI** (`@Bean OpenAPI`) | SÍ | `springdoc-openapi-starter-webmvc-ui` en vez de `webflux-ui` |

**NO aplica a ms-reporter:** #2 (OutboxRelayUseCase — no usa Outbox Pattern completo), #4 (R2dbcOutboxAdapter), #11 (MongoDB). El Outbox Pattern NO se usa en ms-reporter; la publicación de `StockAlertGenerated` es directa vía `KafkaTemplate.send()`.

**Versionado Unificado (reusability.md):** Mismas versiones en todo el monorepo. Si viene del Spring BOM, NO especificar versión. Versiones explícitas: `jqwik:1.9.2`, `archunit:1.4.1`, `lombok:1.18.42`, `springdoc-openapi-starter-webmvc-ui:3.0.2`.

**Event Envelope alineado (docs/03-kafka-eventos.md):** ms-reporter consume el Sobre_Estándar (`eventId`, `eventType`, `timestamp`, `source`, `correlationId`, `payload`) emitido por los 8 servicios. Para publicar `StockAlertGenerated`, usa el MISMO formato de envelope con `source = "ms-reporter"`.

## Tareas

- [ ] 1. Configurar estructura del proyecto y esquema de base de datos
  - [ ] 1.1 Actualizar script SQL de inicialización con tablas completas del diseño
    - Actualizar `postgresql-scripts/init_reporter.sql` para incluir: `event_store` (con particionamiento por fecha, índice GIN en payload, índice compuesto en event_type+timestamp, índice en correlation_id), `sales_summary` (PK compuesta sku+week_start_date), `report_metadata`, `stock_alerts`, `rebuild_jobs`, `processed_events`, `report_orders`, `report_inventory`
    - Agregar particiones mensuales para `event_store` (2026-01 a 2026-12)
    - Agregar todos los índices definidos en el diseño
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5, 2.7, 3.6, 6.1, 6.5, 7.3_
  - [ ] 1.2 Configurar `application.yaml` con conexión JDBC a PostgreSQL (`db_reporter`), propiedades de Kafka (bootstrap-servers, consumer group `reporter-service-group`, 7 tópicos), propiedades S3 (bucket, region, endpoint para LocalStack), intervalo del job de alertas
    - **OBLIGATORIO (reusability.md #9):** Copiar estructura de 3 YAMLs de `ms-inventory/applications/app-service/src/main/resources/` — adaptar: JDBC en vez de R2DBC, puerto 8087, `db_reporter`, schedulers sin defaults inline (§D.6)
    - Configurar perfiles `default`, `local` y `docker`
    - Externalizar intervalo del job: `scheduler.stock-alert.cron: 0 0 2 * * *` (cada 24h a las 2am)
    - Configurar `spring.threads.virtual.enabled: true`
    - Configurar `spring.kafka.consumer.group-id: reporter-service-group`
    - Configurar `spring.kafka.consumer.enable-auto-commit: false`
    - Configurar Springdoc/OpenAPI (reusability.md #10): `springdoc.api-docs.path`, `springdoc.swagger-ui.path/enabled`
    - _Requisitos: 1.1, 1.7, 12.1, 12.2, 12.3_
  - [ ] 1.3 Agregar dependencias en `build.gradle` / `main.gradle`
    - **OBLIGATORIO (reusability.md — Versionado Unificado):** Seguir tabla de versiones. Mismas versiones en todo el monorepo.
    - **En `main.gradle` (subprojects):** Agregar `net.jqwik:jqwik:1.9.2` y `com.tngtech.archunit:archunit:1.4.1` en testImplementation
    - **En `app-service/build.gradle`:** Agregar `springdoc-openapi-starter-webmvc-ui:3.0.2` (MVC, NO `webflux-ui`), `jackson-databind`
    - **NOTA:** Las demás dependencias se agregarán en sus módulos específicos cuando se creen con Scaffold:
      - JDBC + PostgreSQL → tarea 5.1 (`jdbc-postgresql` module)
      - Kafka consumer → tarea 7.1 (`kafka-consumer` module)
      - AWS S3 → tarea 5.6 (`s3-storage` module)
      - Spring Web MVC → tarea 6.1 (`api-rest` module)
      - PDF generation → tarea 5.8 (`pdf-generator` helper)
      - CSV generation → tarea 5.7 (`csv-generator` helper)
    - _Requisitos: transversal_
  - [ ] 1.4 Verificar configuración de Spring Profiles (local/docker)
    - **OBLIGATORIO (reusability.md #9):** Verificar alineamiento con patrón de ms-inventory: `${SPRING_PROFILES_ACTIVE:local}` como default
    - Verificar `application-local.yaml` con hosts `localhost` y puertos mapeados (PostgreSQL 5435, Kafka 9092, LocalStack 4566)
    - Verificar `application-docker.yaml` con hostnames de contenedores (`arka-db-reporter`, `arka-kafka`)
    - _Estándar: Spring Profiles (reusability.md #9), docs/06-patrones-y-estandares.md_

- [ ] 2. Implementar modelo de dominio (`domain/model`)
  - [ ] 2.1 Crear el record `EventStoreEntry` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateModel --name=EventStore`
    - Record con compact constructor: validación de `eventId`, `eventType`, `source`, `payload` no nulos; default `timestamp` (Instant.now())
    - Usar `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.eventstore`
    - _Requisitos: 2.1, 2.6_
  - [ ] 2.2 Crear el record `ReportMetadata` y enum `ReportStatus` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateModel --name=Report`
    - `ReportMetadata` record con defaults: `status` (PROCESSING), `requestedAt` (Instant.now())
    - `ReportStatus` enum: PROCESSING, COMPLETED, FAILED
    - Paquete: `com.arka.model.report`
    - _Requisitos: 3.6, 5.1, 5.2_
  - [ ] 2.3 Crear el record `SalesSummary` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateModel --name=Sales`
    - Record con compact constructor: `average_order_value` calculado como `totalRevenue / totalOrders`; default `lastUpdatedAt` (Instant.now())
    - Usar `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.sales`
    - _Requisitos: 6.1, 6.4, 6.5_
  - [ ] 2.4 Crear el record `StockAlert` y enum `AlertStatus` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateModel --name=Alert`
    - `StockAlert` record con defaults: `alertStatus` (ACTIVE), `createdAt` (Instant.now())
    - `AlertStatus` enum: ACTIVE, RESOLVED
    - Paquete: `com.arka.model.alert`
    - _Requisitos: 7.3, 7.5_
  - [ ] 2.5 Crear el record `KpiResult` y `TopSellingProduct` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateModel --name=Kpi`
    - Records con `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.kpi`
    - _Requisitos: 8.1_
  - [ ] 2.6 Crear el record `RebuildJob` y enum `RebuildStatus` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateModel --name=Rebuild`
    - `RebuildJob` record con defaults: `status` (IN_PROGRESS), `startedAt` (Instant.now())
    - `RebuildStatus` enum: IN_PROGRESS, COMPLETED, FAILED
    - Paquete: `com.arka.model.rebuild`
    - _Requisitos: 9.3, 9.5, 9.6_
  - [ ] 2.7 Crear el record `EventEnvelope` (sobre estándar consumido de Kafka) en `domain/model`
    - **OBLIGATORIO (docs/03-kafka-eventos.md — Event Envelope):** Alinear campos exactamente con el Sobre_Estándar del ecosistema Arka: `eventId` (UUID), `eventType` (String PascalCase), `timestamp` (Instant), `source` (String ms-name), `correlationId` (UUID nullable), `payload` (Object/Map)
    - Ubicar junto a EventStoreEntry en `com.arka.model.eventstore`
    - Record con validación: `eventId`, `eventType`, `payload` no nulos
    - _Requisitos: 1.6_
  - [ ] 2.8 Crear las interfaces de gateway (ports)
    - `EventStoreRepository`: save, findByCorrelationId, findByEventTypeAndTimestampRange, countByEventTypeAndTimestampRange, findAllOrderedByTimestamp
    - `ReportMetadataRepository`: save, findById, updateStatus
    - `SalesSummaryRepository`: upsert, decrementSales, findByWeekRange, findTopSellingByWeekRange, truncate
    - `StockAlertRepository`: save, findActiveAlertBySku, resolveAlert
    - `ProcessedEventRepository`: exists(UUID), save(UUID)
    - `FileStorageGateway`: upload, generatePresignedUrl
    - `EventPublisherGateway`: publish(DomainEventEnvelope) — para publicar StockAlertGenerated a Kafka
    - **OBLIGATORIO (reusability.md #3):** Copiar firma de `ProcessedEventRepository` de `ms-inventory/domain/model/processedevent/gateways/` — misma interfaz (`exists`, `save`), adaptando retornos a síncronos (`boolean` y `void` en vez de `Mono<Boolean>` y `Mono<Void>`)
    - **IMPORTANTE:** Retornos síncronos (no `Mono`/`Flux`) — paradigma imperativo
    - Ubicar en `com.arka.model.<aggregate>.gateways`
    - _Requisitos: transversal_
  - [ ] 2.9 Crear la jerarquía de excepciones de dominio
    - **OBLIGATORIO (reusability.md #8):** Copiar estructura base de `ms-inventory/domain/model/commons/exception/DomainException.java` — abstract class con `getHttpStatus()` y `getCode()`, misma convención de docs/06-patrones-y-estandares.md §2
    - `DomainException` (abstract) con `getHttpStatus()` y `getCode()`
    - Subclases: `ReportNotFoundException` (404, REPORT_NOT_FOUND), `InvalidDateRangeException` (400, INVALID_DATE_RANGE), `InvalidReadModelException` (400, INVALID_READ_MODEL), `StorageServiceUnavailableException` (503, STORAGE_UNAVAILABLE), `ReportGenerationException` (500, REPORT_GENERATION_FAILED), `AccessDeniedException` (403, ACCESS_DENIED)
    - Paquete: `com.arka.model.commons.exception`
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

- [ ] 3. Checkpoint — Verificar compilación del modelo de dominio
  - Ejecutar `./gradlew compileJava` y `./gradlew test` desde ms-reporter/
  - Asegurar que todos los tests pasan

- [ ] 4. Implementar casos de uso (`domain/usecase`)
  - [ ] 4.1 Implementar `EventConsumptionUseCase`: verificar idempotencia (processed_events), almacenar evento en Event Store, proyectar a Read Models según eventType (OrderConfirmed → incrementar sales_summary; OrderCancelled → decrementar; StockUpdated → actualizar report_inventory; PriceChanged → actualizar product_name). Todo en una transacción JDBC.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-reporter && ./gradlew generateUseCase --name=EventConsumption`
    - Eventos desconocidos: almacenar en Event Store + log WARN, sin proyectar a Read Models
    - Idempotencia: verificar `processedEventRepository.exists(eventId)` antes de procesar
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 6.2, 6.3, 6.6, 6.7_
  - [ ]\* 4.2 Escribir test de propiedad para idempotencia en consumo
    - **Propiedad 1: Idempotencia en consumo de eventos**
    - Generar eventos, procesarlos dos veces, verificar que el segundo intento se descarta sin duplicar en Event Store ni modificar Read Models.
    - **Valida: Requisitos 1.2, 1.3, 1.5**
  - [ ]\* 4.3 Escribir test de propiedad para preservación de estructura de evento
    - **Propiedad 2: Evento almacenado preserva estructura original**
    - Generar eventos aleatorios con payloads variados, verificar que se almacenan sin transformaciones.
    - **Valida: Requisitos 2.1, 2.6**
  - [ ]\* 4.4 Escribir test de propiedad para eventos desconocidos
    - **Propiedad 3: Eventos desconocidos se almacenan sin fallar**
    - Generar eventos con eventTypes aleatorios no reconocidos, verificar almacenamiento en Event Store sin modificar Read Models.
    - **Valida: Requisitos 1.4**
  - [ ]\* 4.5 Escribir test de propiedad para consistencia de sales_summary
    - **Propiedad 4: Read Model sales_summary consistencia con Event Store**
    - Generar secuencias de OrderConfirmed y OrderCancelled, verificar que sales_summary refleja estado neto correcto.
    - **Valida: Requisitos 6.1, 6.2, 6.3, 6.4, 6.5, 6.6**
  - [ ] 4.6 Implementar `ReportGenerationUseCase`: iniciar reporte (insertar metadata con PROCESSING), generar CSV/PDF de forma streaming en Virtual Thread (@Async), comprimir con GZIP, subir a S3, actualizar metadata a COMPLETED o FAILED. Validar fecha inicio <= fecha fin.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-reporter && ./gradlew generateUseCase --name=ReportGeneration`
    - Para CSV: consultar sales_summary por rango, escribir incrementalmente con streaming si > 10MB
    - Para PDF: crear documento con PDFBox (título, tabla, gráfico top 10, resumen KPIs)
    - Validar tamaño PDF < 500MB antes de subir
    - Rango sin datos: CSV vacío con solo encabezados (HTTP 200)
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7_
  - [ ]\* 4.7 Escribir test de propiedad para contenido CSV correcto
    - **Propiedad 5: Reporte CSV contiene datos correctos del período**
    - Generar datos de sales_summary y rango de fechas, verificar que CSV contiene exactamente los registros del período.
    - **Valida: Requisitos 3.1, 3.2**
  - [ ] 4.8 Implementar `KpiCalculationUseCase`: calcular KPIs de negocio para un rango de fechas consultando Event Store y sales_summary. Fórmulas: total_revenue (suma), total_orders (conteo OrderConfirmed), average_order_value (total_revenue/total_orders), conversion_rate, cart_abandonment_rate, average_delivery_time_days, top_selling_products (top 10).
    - **CRÍTICO**: Generar con Scaffold: `cd ms-reporter && ./gradlew generateUseCase --name=KpiCalculation`
    - conversion_rate = OrderConfirmed / (CartAbandoned + OrderConfirmed)
    - cart_abandonment_rate = CartAbandoned / (CartAbandoned + OrderConfirmed)
    - average_delivery_time_days = AVG(ShippingDispatched.timestamp - OrderConfirmed.timestamp)
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_
  - [ ]\* 4.9 Escribir test de propiedad para KPIs calculados correctamente
    - **Propiedad 9: KPIs calculados correctamente para cualquier rango**
    - Generar eventos con montos y cantidades aleatorios, verificar que las fórmulas se aplican correctamente.
    - **Valida: Requisitos 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8**
  - [ ] 4.10 Implementar `StockAlertUseCase`: ejecutar análisis de patrones de consumo, calcular tasa diaria, detectar SKUs en riesgo (días_para_agotamiento <= 7), generar alertas ACTIVE, resolver alertas cuando stock se repone.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-reporter && ./gradlew generateUseCase --name=StockAlert`
    - Tasa consumo = total_quantity_OrderConfirmed_30días / 30
    - Alerta si: tasa * 7 >= stock_actual - threshold
    - Ignorar SKUs con < 7 días de historial
    - Resolver alertas existentes si stock actual supera threshold + 7 * tasa_consumo
    - _Requisitos: 7.1, 7.2, 7.3, 7.5, 7.6, 7.7_
  - [ ]\* 4.11 Escribir test de propiedad para alertas de stock bajo
    - **Propiedad 8: Alertas de stock bajo se generan correctamente**
    - Generar patrones de consumo y niveles de stock aleatorios, verificar que las alertas se generan cuando el criterio se cumple.
    - **Valida: Requisitos 7.1, 7.2, 7.3, 7.4**
  - [ ] 4.12 Implementar `EventTraceUseCase`: consultar eventos por correlationId ordenados por timestamp ascendente.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-reporter && ./gradlew generateUseCase --name=EventTrace`
    - Retornar lista vacía si no hay eventos para el correlationId
    - _Requisitos: 10.1, 10.2, 10.3, 10.4_
  - [ ]\* 4.13 Escribir test de propiedad para consulta por correlationId
    - **Propiedad 11: Consulta por correlationId retorna eventos ordenados**
    - Generar eventos con mismo correlationId y timestamps variados, verificar orden temporal ascendente.
    - **Valida: Requisitos 10.1, 10.2, 10.3, 10.4**
  - [ ] 4.14 Implementar `ReadModelRebuildUseCase`: validar nombre de Read Model, insertar rebuild_job, truncar tabla del Read Model, reproducir eventos en lotes de 1000, actualizar status a COMPLETED o FAILED.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-reporter && ./gradlew generateUseCase --name=ReadModelRebuild`
    - Read Models soportados: sales_summary, product_performance, customer_orders
    - Procesamiento por lotes de 1000 para evitar OOM
    - En caso de fallo: revertir (truncar Read Model) y marcar como FAILED
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_
  - [ ]\* 4.15 Escribir test de propiedad para reconstrucción idempotente
    - **Propiedad 10: Reconstrucción de Read Model produce resultado idéntico**
    - Generar secuencias de eventos, comparar resultado incremental vs reconstrucción completa.
    - **Valida: Requisitos 9.1, 9.4**

- [ ] 5. Checkpoint — Verificar compilación y tests de casos de uso
  - Ejecutar `./gradlew compileJava` y `./gradlew test`
  - Asegurar que todos los tests pasan

- [ ] 6. Implementar adaptadores de infraestructura (`infrastructure/driven-adapters`)
  - [ ] 6.1 Implementar `JdbcEventStoreAdapter` (implementa `EventStoreRepository`): operaciones con `NamedParameterJdbcTemplate`, mapeo con `RowMapper`, consultas por correlationId, eventType+rango, paginación por lotes
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateDrivenAdapter --type=generic --name=jdbc-postgresql`
    - **Agregar dependencias:** `spring-boot-starter-jdbc`, `org.postgresql:postgresql` (driver JDBC)
    - UPSERT para Read Models con `ON CONFLICT ... DO UPDATE`
    - _Requisitos: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7_
  - [ ] 6.2 Implementar `JdbcReportMetadataAdapter` (implementa `ReportMetadataRepository`): save, findById, updateStatus
    - Ubicar en el mismo módulo `jdbc-postgresql`
    - _Requisitos: 3.6, 5.1, 5.2_
  - [ ] 6.3 Implementar `JdbcSalesSummaryAdapter` (implementa `SalesSummaryRepository`): UPSERT con ON CONFLICT (sku, week_start_date), decrementSales, findByWeekRange, findTopSelling, truncate
    - Ubicar en el mismo módulo `jdbc-postgresql`
    - _Requisitos: 6.1, 6.2, 6.3, 6.5_
  - [ ] 6.4 Implementar `JdbcStockAlertAdapter` (implementa `StockAlertRepository`): save, findActiveAlertBySku, resolveAlert
    - Ubicar en el mismo módulo `jdbc-postgresql`
    - _Requisitos: 7.3, 7.5_
  - [ ] 6.5 Implementar `JdbcProcessedEventAdapter` (implementa `ProcessedEventRepository`): exists, save
    - **OBLIGATORIO (reusability.md #3):** Adaptar patrón de `ms-inventory/infrastructure/driven-adapters/r2dbc-postgresql/.../processedevent/R2dbcProcessedEventAdapter.java` — misma lógica (INSERT explícito, NO `repository.save()` porque el UUID viene de Kafka, §2.2 Excepción UUIDs de Fuentes Externas), pero con `JdbcTemplate` en vez de `DatabaseClient`
    - Ubicar en el mismo módulo `jdbc-postgresql`
    - _Requisitos: 1.2, 1.3, 1.5_
  - [ ] 6.6 Implementar `S3FileStorageAdapter` (implementa `FileStorageGateway`): upload con multipart (> 5MB), generatePresignedUrl con expiración de 1 hora
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateDrivenAdapter --type=s3`
    - Usar AWS SDK v2 (`S3Client`, `S3Presigner`)
    - Configurar endpoint para LocalStack en perfil local
    - _Requisitos: 3.4, 3.5, 4.4, 4.5, 5.3_
  - [ ] 6.7 Implementar `CsvReportGenerator` (helper de generación CSV): escritura streaming con BufferedWriter, encabezados fijos, compresión GZIP
    - **CRÍTICO**: Generar helper con Scaffold: `cd ms-reporter && ./gradlew generateHelper --name=CsvGenerator`
    - Streaming: escribir por lotes sin cargar todo en memoria
    - Soporte GZIP compression antes de retornar Path del archivo
    - _Requisitos: 3.1, 3.3, 3.4_
  - [ ] 6.8 Implementar `PdfReportGenerator` (helper de generación PDF): crear documento con Apache PDFBox (título, tabla de ventas, gráfico top 10, resumen KPIs), streaming write, compresión GZIP
    - **CRÍTICO**: Generar helper con Scaffold: `cd ms-reporter && ./gradlew generateHelper --name=PdfGenerator`
    - **Agregar dependencia:** `org.apache.pdfbox:pdfbox:3.0.4`
    - Streaming: incrementalSave para documentos grandes
    - Validar tamaño < 500MB
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.6, 4.7_
  - [ ] 6.9 Implementar `JdbcRebuildJobAdapter`: save, updateStatus (para rebuild_jobs)
    - Ubicar en el mismo módulo `jdbc-postgresql`
    - _Requisitos: 9.3, 9.5, 9.6_

- [ ] 7. Checkpoint — Verificar compilación y tests de adaptadores
  - Ejecutar `./gradlew compileJava` y `./gradlew test`
  - Asegurar que todos los tests pasan

- [ ] 8. Implementar entry-points (`infrastructure/entry-points`)
  - [ ] 8.1 Crear los DTOs de request: `GenerateReportRequest`, `RebuildReadModelRequest` con Bean Validation (`@NotNull`, `@NotBlank`)
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateEntryPoint --type=restmvc`
    - **Agregar dependencias:** `spring-boot-starter-web`, `spring-boot-starter-validation`
    - Records con `@Builder(toBuilder = true)`
    - _Requisitos: 3.8, 9.2_
  - [ ] 8.2 Crear los DTOs de response: `ReportResponse`, `KPIResponse`, `TopSellingProduct`, `EventTraceResponse`, `EventEntry`, `RebuildResponse`, `StockAlertResponse`, `ErrorResponse`
    - Records con `@Builder(toBuilder = true)`
    - _Requisitos: 3.5, 5.1, 5.2, 8.1, 10.2, 9.3, 11.6_
  - [ ] 8.3 Crear los mappers manuales: `ReportMapper`, `KpiMapper`, `EventMapper`, `AlertMapper`
    - Sin MapStruct, usar `@Builder` para construir objetos destino
    - _Requisitos: transversal_
  - [ ] 8.4 Implementar `ReportHandler` y `ReportController` (`@RestController`)
    - **OBLIGATORIO (reusability.md #7):** Seguir patrón Controller → Handler → UseCase de `ms-inventory`: `StockController` → `StockHandler` → `StockUseCase` (§4.2 de docs/06-patrones-y-estandares.md). Adaptación: retornos `ResponseEntity<T>` síncronos en vez de `Mono<ResponseEntity<T>>`
    - `ReportController`: thin — solo anotaciones HTTP, `@Valid`, extraer `X-User-Email`/`X-User-Role` de headers
    - `ReportHandler` (`@Component`): orquesta UseCase + mapeo + ResponseEntity
    - Endpoints: POST /reports/sales/weekly (202), GET /reports/{reportId} (200)
    - Validar rol ADMIN en todos los endpoints
    - Anotaciones Springdoc (reusability.md #10): `@Tag`, `@Operation`, `@ApiResponse`
    - _Requisitos: 3.1, 3.5, 3.8, 4.5, 5.1, 5.5, 5.6_
  - [ ] 8.5 Implementar `MetricsHandler` y `MetricsController` (`@RestController`)
    - Endpoint: GET /metrics/kpis (200) con query params startDate, endDate
    - Validar rol ADMIN
    - _Requisitos: 8.1, 8.9_
  - [ ] 8.6 Implementar `EventTraceHandler` y `EventTraceController` (`@RestController`)
    - Endpoint: GET /events/trace/{correlationId} (200)
    - Validar rol ADMIN
    - _Requisitos: 10.1, 10.5_
  - [ ] 8.7 Implementar `AdminHandler` y `AdminController` (`@RestController`)
    - Endpoint: POST /admin/rebuild-read-models (202)
    - Validar rol ADMIN
    - _Requisitos: 9.1, 9.7_
  - [ ] 8.8 Implementar `GlobalExceptionHandler` (`@ControllerAdvice`): traducir excepciones de dominio a `ErrorResponse` con códigos HTTP correctos (400, 403, 404, 503, 500). No exponer detalles internos en 500.
    - **OBLIGATORIO (reusability.md #8):** Copiar y adaptar `ms-inventory/infrastructure/entry-points/reactive-web/.../api/handler/GlobalExceptionHandler.java` como base — misma estructura `ErrorResponse(code, message)`, mismos principios. Adaptación: manejar `MethodArgumentNotValidException` (Spring MVC) en vez de `WebExchangeBindException` (WebFlux). Agregar handlers para excepciones específicas de ms-reporter.
    - Manejar `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, todas las `DomainException` y `Exception` genérica
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_
  - [ ]\* 8.9 Escribir test de propiedad para estructura de ErrorResponse
    - **Propiedad 12: Respuestas de error tienen estructura correcta**
    - Generar excepciones de distintos tipos, verificar campos code y message presentes, y HTTP status correcto.
    - **Valida: Requisitos 11.1, 11.2, 11.3, 11.4, 11.5, 11.6**

- [ ] 9. Checkpoint — Verificar compilación y tests de entry-points
  - Ejecutar `./gradlew compileJava` y `./gradlew test`
  - Asegurar que todos los tests pasan

- [ ] 10. Implementar consumidor Kafka y jobs periódicos
  - [ ] 10.1 Implementar `KafkaEventConsumer`: consumer suscrito a los 7 tópicos Kafka con `@KafkaListener` (spring-kafka). Deserializar Sobre_Estándar, delegar a `EventConsumptionUseCase`. Commit manual de offsets después de persistir.
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateEntryPoint --type=generic --name=kafka-consumer`
    - **Agregar dependencias:** `spring-kafka`, `jackson-databind` (versiones del Spring BOM, reusability.md — Versionado Unificado)
    - **OBLIGATORIO (reusability.md #6 — adaptación imperativa):** Reutilizar lógica conceptual de `ms-inventory/infrastructure/entry-points/kafka-consumer/KafkaEventConsumer.java`: switch por `eventType`, verificación de idempotencia via `processedEventRepository.exists()`, skip de eventos desconocidos con log WARN. Adaptación: usar `@KafkaListener` (spring-kafka imperative) en vez de `KafkaReceiver` (reactor-kafka reactive, §B.12)
    - Usar `@KafkaListener(topics = {...}, groupId = "reporter-service-group")` — paradigma imperativo
    - Configurar `AckMode.MANUAL_IMMEDIATE` para commit manual (`Acknowledgment.acknowledge()` tras persistir)
    - Deserializar JSON a `EventEnvelope` con Jackson — alineado al Sobre_Estándar (docs/03-kafka-eventos.md)
    - Error handling: log ERROR + skip para errores irrecuperables de deserialización (misma tolerancia que reusability.md #6)
    - _Requisitos: 1.1, 1.4, 1.6, 1.7_
  - [ ] 10.2 Implementar `StockAlertScheduler`: job periódico que ejecuta cada 24 horas el análisis de stock bajo y publica evento `StockAlertGenerated` a Kafka tópico `reporter-events`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-reporter && ./gradlew generateDrivenAdapter --type=generic --name=kafka-producer` (para publicar StockAlertGenerated)
    - **OBLIGATORIO (reusability.md #1 + #5 — adaptación imperativa):** Copiar estructura de `DomainEventEnvelope` de `ms-inventory/domain/model/outboxevent/DomainEventEnvelope.java` con `MS_SOURCE = "ms-reporter"`. Para Kafka Producer Config, adaptar conceptos de `ms-inventory/infrastructure/driven-adapters/kafka-producer/KafkaProducerConfig.java` (acks=all, retries=3, idempotent) usando `KafkaTemplate` (spring-kafka síncrono) en vez de `KafkaSender` (reactor-kafka)
    - `@Scheduled(cron = "${scheduler.stock-alert.cron}")` — intervalo externalizado (docs/06-patrones-y-estandares.md §D.6: sin defaults inline)
    - Delegar a `StockAlertUseCase.analyzeStockPatterns()`
    - Publicar evento `StockAlertGenerated` con `KafkaTemplate.send(topic, key, envelope)` usando el formato Sobre_Estándar del ecosistema
    - Partition key = SKU del producto en riesgo
    - _Requisitos: 7.1, 7.4_
  - [ ] 10.3 Configurar `AsyncConfig` para Virtual Threads: `TaskExecutor` con Virtual Thread factory para `@Async` de generación de reportes
    - Configurar en `applications/app-service`
    - Habilitar `@EnableAsync`
    - _Requisitos: 12.4, 12.5_

- [ ] 11. Integración final y configuración de Spring
  - [ ] 11.1 Configurar beans de Spring en `app-service`: inyección de dependencias para todos los UseCases, adaptadores JDBC, S3 adapter, Kafka consumer, schedulers
    - **Actualizar `app-service/build.gradle`:** Agregar referencias a módulos de infraestructura: `implementation project(':jdbc-postgresql')`, `implementation project(':s3-storage')`, `implementation project(':kafka-consumer')`, `implementation project(':kafka-producer')`, `implementation project(':api-rest')`, `implementation project(':csv-generator')`, `implementation project(':pdf-generator')`
    - **OBLIGATORIO (reusability.md #10):** Crear `OpenApiConfig` con metadata del servicio (`@Bean OpenAPI`) — copiar patrón de `ms-inventory/applications/.../config/OpenApiConfig.java`, adaptar título y descripción para ms-reporter
    - _Requisitos: transversal_
  - [ ] 11.2 Configurar transacciones JDBC: `@Transactional` en UseCases que requieren atomicidad (EventConsumptionUseCase, ReadModelRebuildUseCase)
    - **NOTA (paradigma imperativo):** A diferencia de los servicios reactivos, ms-reporter puede usar `@Transactional` de Spring directamente en los UseCase methods ya que no hay conflicto con Clean Architecture en contexto imperativo con Virtual Threads
    - Alternativamente, usar `TransactionTemplate` para control programático
    - _Requisitos: 1.5, 6.6, 9.4_

- [ ] 12. Checkpoint final — Verificar compilación completa y tests
  - Ejecutar `./gradlew build` (compila + tests + pitest)
  - Verificar que todos los tests pasan
  - Ejecutar `./gradlew validateStructure`

---

## Notas

- **CRÍTICO**: Todos los módulos DEBEN generarse con el plugin Scaffold de Bancolombia. La creación manual está PROHIBIDA. Después de cada generación, ejecutar `./gradlew validateStructure`.
- **PARADIGMA IMPERATIVO**: ms-reporter NO usa WebFlux, R2DBC, reactor-kafka, `Mono`/`Flux`, `StepVerifier` ni `BlockHound`. Usa Spring MVC, JDBC, `@KafkaListener`, `@Async`, Virtual Threads y retornos síncronos.
- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests de propiedades validan propiedades universales de correctitud (jqwik)
