# Plan de Implementación: ms-inventory

## Visión General

Implementación incremental del microservicio de Gestión de Stock y Reservas para la plataforma B2B Arka. Se sigue la Clean Architecture del Scaffold Bancolombia 4.2.0 con Java 21, Spring WebFlux reactivo, PostgreSQL 17 (R2DBC), Apache Kafka (Transactional Outbox Pattern) y gRPC. El objetivo principal es resolver el problema CRÍTICO #1 de Arka: sobreventa por acceso concurrente al stock. Cada tarea construye sobre las anteriores, integrando tests de propiedades (jqwik) y unitarios (JUnit 5 + StepVerifier) como subtareas cercanas a la implementación.

## Tareas

- [ ] 1. Definir entidades de dominio, Value Objects, enums y excepciones
  - [ ] 1.1 Crear el record `Stock` en `domain/model`
    - Crear `Stock` record con compact constructor: validación de `sku` y `productId` no nulos, `quantity >= 0`, `reservedQuantity >= 0`, cálculo de `availableQuantity = quantity - reservedQuantity`, default `version = 1`
    - Usar `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.stock`
    - _Requisitos: 1.1, 2.1, 10.1, 10.2, 10.3_

  - [ ] 1.2 Crear el record `StockReservation` y enum `ReservationStatus` en `domain/model`
    - Crear `StockReservation` record con compact constructor: validación de `sku` y `orderId` no nulos, `quantity > 0`, defaults para `status` (PENDING), `createdAt` (Instant.now()), `expiresAt` (Instant.now() + 15 min)
    - Crear enum `ReservationStatus` con valores: `PENDING`, `CONFIRMED`, `EXPIRED`, `RELEASED`
    - Usar `@Builder(toBuilder = true)` en el record
    - Paquete: `com.arka.model.reservation`
    - _Requisitos: 4.2, 4.3, 5.1, 5.2_

  - [ ] 1.3 Crear el record `StockMovement` y enum `MovementType` en `domain/model`
    - Crear `StockMovement` record con compact constructor: validación de `sku` y `movementType` no nulos, default `createdAt` (Instant.now())
    - Crear enum `MovementType` con valores: `MANUAL_ADJUSTMENT`, `ORDER_RESERVE`, `ORDER_CONFIRM`, `RESERVATION_RELEASE`, `PRODUCT_CREATION`
    - Usar `@Builder(toBuilder = true)` en el record
    - Paquete: `com.arka.model.movement`
    - _Requisitos: 1.5, 3.4, 4.6, 5.3, 6.3, 7.2_

  - [ ] 1.4 Crear el record `OutboxEvent` y records de eventos de dominio en `domain/model`
    - Crear `OutboxEvent` record con defaults en compact constructor: `id` (UUID.randomUUID()), `status` ("PENDING"), `topic` ("inventory-events"), `createdAt` (Instant.now())
    - Crear `DomainEventEnvelope` record con campos: eventId, eventType, timestamp, source ("ms-inventory"), correlationId, payload
    - Crear payloads: `StockReservedPayload`, `StockReserveFailedPayload`, `StockReleasedPayload`, `StockUpdatedPayload`, `StockDepletedPayload`
    - Usar `@Builder(toBuilder = true)` en todos los records
    - Paquete: `com.arka.model.outbox`
    - _Requisitos: 8.1, 8.2, 8.7, 8.8, 8.9, 8.10, 8.11, 8.12_

  - [ ] 1.5 Crear jerarquía de excepciones de dominio
    - Crear `DomainException` abstracta con `getHttpStatus()` y `getCode()`
    - Crear subclases: `StockNotFoundException` (404, STOCK_NOT_FOUND), `InsufficientStockException` (409, INSUFFICIENT_STOCK), `InvalidStockQuantityException` (409, INVALID_STOCK_QUANTITY), `OptimisticLockException` (409, CONCURRENT_MODIFICATION), `StockConstraintViolationException` (409, STOCK_CONSTRAINT_VIOLATION)
    - Paquete: `com.arka.model.commons.exception`
    - _Requisitos: 1.3, 1.4, 1.8, 10.4, 11.1, 11.3_

  - [ ]\* 1.6 Escribir tests de propiedades para validación de entidades de dominio
    - **Propiedad 2: Validación rechaza entrada inválida** — Generar requests con `quantity` nulo o negativo y verificar que el sistema rechaza con HTTP 400 sin modificar estado
    - **Valida: Requisitos 1.2**

  - [ ]\* 1.7 Escribir test de propiedad para invariante de stock no negativo
    - **Propiedad 3: Invariante de stock no negativo** — Generar operaciones que intentan establecer `quantity < 0` o `reservedQuantity < 0` y verificar que el compact constructor lanza excepción. Verificar que `availableQuantity == quantity - reservedQuantity` siempre
    - **Valida: Requisitos 1.3, 10.1, 10.2, 10.3, 10.4, 10.5**

- [ ] 2. Definir ports (gateway interfaces)
  - [ ] 2.1 Crear interfaz `StockRepository` en `domain/model/stock/gateways`
    - Métodos: `findBySku(String sku)`, `findBySkuForUpdate(String sku)` (SELECT FOR UPDATE), `save(Stock stock)`, `updateQuantity(String sku, int newQuantity, long expectedVersion)`, `updateReservedQuantity(String sku, int newReservedQuantity)`
    - Todos retornan `Mono<Stock>`
    - _Requisitos: 1.1, 1.8, 2.1, 4.1, 4.3_

  - [ ] 2.2 Crear interfaz `StockReservationRepository` en `domain/model/reservation/gateways`
    - Métodos: `save(StockReservation)`, `findBySkuAndOrderIdAndStatus(String sku, UUID orderId, ReservationStatus status)`, `findExpiredPending(Instant now)`, `updateStatus(UUID id, ReservationStatus status)`
    - Retornos: `Mono<StockReservation>` y `Flux<StockReservation>`
    - _Requisitos: 4.2, 4.5, 5.1, 7.1_

  - [ ] 2.3 Crear interfaz `StockMovementRepository` en `domain/model/movement/gateways`
    - Métodos: `save(StockMovement)`, `findBySkuOrderByCreatedAtDesc(String sku, int page, int size)`
    - Retornos: `Mono<StockMovement>` y `Flux<StockMovement>`
    - _Requisitos: 1.5, 3.1, 3.4_

  - [ ] 2.4 Crear interfaz `OutboxEventRepository` en `domain/model/outbox/gateways`
    - Métodos: `save(OutboxEvent)`, `findPending(int limit)`, `markAsPublished(UUID id)`
    - _Requisitos: 8.1, 8.4, 8.5_

  - [ ] 2.5 Crear interfaz `ProcessedEventRepository` en `domain/model/processedevent/gateways`
    - Métodos: `exists(UUID eventId)`, `save(UUID eventId)`
    - _Requisitos: 9.1, 9.3, 9.4_

- [ ] 3. Implementar casos de uso — Consultas (GetStock, GetStockHistory)
  - [ ] 3.1 Implementar `GetStockUseCase`
    - Consultar `stockRepository.findBySku(sku)`
    - Si no existe, lanzar `StockNotFoundException`
    - Retornar `Mono<Stock>` con todos los campos
    - _Requisitos: 2.1, 2.2_

  - [ ] 3.2 Implementar `GetStockHistoryUseCase`
    - Consultar `stockMovementRepository.findBySkuOrderByCreatedAtDesc(sku, page, size)`
    - Retornar `Flux<StockMovement>` paginado y ordenado por `created_at` descendente
    - _Requisitos: 3.1, 3.2, 3.3, 3.4_

  - [ ]\* 3.3 Escribir test de propiedad para consulta de stock con campos completos
    - **Propiedad 8: Consulta de stock retorna campos completos** — Generar stocks aleatorios, consultar por SKU y verificar que todos los campos (id, sku, productId, quantity, reservedQuantity, availableQuantity, version, updatedAt) son no nulos
    - **Valida: Requisitos 2.1**

  - [ ]\* 3.4 Escribir test de propiedad para historial ordenado descendentemente
    - **Propiedad 9: Historial retorna movimientos ordenados descendentemente** — Generar movimientos con timestamps variados, consultar historial y verificar que para cada par consecutivo `created_at[i] >= created_at[i+1]`
    - **Valida: Requisitos 3.1**

  - [ ]\* 3.5 Escribir test de propiedad para campos completos de movimientos
    - **Propiedad 10: Movimientos contienen todos los campos requeridos** — Generar movimientos de stock y verificar que contienen: id (UUID no nulo), sku (no vacío), movement_type (válido), quantity_change, previous_quantity >= 0, new_quantity >= 0, created_at (no nulo)
    - **Valida: Requisitos 3.4**

- [ ] 4. Implementar caso de uso — Actualización manual de stock (UpdateStock)
  - [ ] 4.1 Implementar `UpdateStockUseCase`
    - Consultar stock por SKU con `stockRepository.findBySku(sku)`
    - Validar que `newQuantity >= reservedQuantity` (si no, lanzar `InvalidStockQuantityException`)
    - Actualizar con lock optimista: `stockRepository.updateQuantity(sku, newQuantity, expectedVersion)`
    - Si version mismatch, lanzar `OptimisticLockException`
    - Registrar `StockMovement` de tipo `MANUAL_ADJUSTMENT` con previous/new quantity y razón
    - Insertar `OutboxEvent` de tipo `StockUpdated` en la misma transacción
    - Si `availableQuantity <= threshold`, insertar `OutboxEvent` adicional de tipo `StockDepleted`
    - _Requisitos: 1.1, 1.3, 1.5, 1.6, 1.7, 1.8_

  - [ ]\* 4.2 Escribir test de propiedad para round trip de actualización
    - **Propiedad 1: Round trip de actualización de stock** — Generar SKUs existentes y cantidades válidas (>= 0), actualizar y consultar, verificar que quantity coincide y `availableQuantity == quantity - reservedQuantity`
    - **Valida: Requisitos 1.1, 2.1**

  - [ ]\* 4.3 Escribir test de propiedad para lock optimista
    - **Propiedad 7: Lock optimista previene actualizaciones perdidas** — Generar pares de actualizaciones concurrentes al mismo SKU con la misma versión, verificar que exactamente una tiene éxito (409 la otra) y la versión incrementa en 1
    - **Valida: Requisitos 1.8**

  - [ ]\* 4.4 Escribir test de propiedad para operaciones producen movimientos
    - **Propiedad 4: Operaciones producen movimientos correctos** — Generar actualizaciones manuales exitosas y verificar que existe un `StockMovement` con `MANUAL_ADJUSTMENT`, previous/new quantity correctos
    - **Valida: Requisitos 1.5**

  - [ ]\* 4.5 Escribir test de propiedad para eventos outbox en actualización
    - **Propiedad 5: Operaciones producen eventos outbox correctos** — Generar actualizaciones exitosas y verificar que existe un `OutboxEvent` con `StockUpdated`, status PENDING, topic `inventory-events`, partition key = SKU
    - **Valida: Requisitos 1.6**

  - [ ]\* 4.6 Escribir test de propiedad para StockDepleted en umbral
    - **Propiedad 6: StockDepleted se emite al alcanzar umbral crítico** — Generar stocks con quantities cerca del umbral, actualizar y verificar que se emite `StockDepleted` si `availableQuantity <= threshold`, y no se emite si está por encima
    - **Valida: Requisitos 1.7, 4.10**

- [ ] 5. Implementar caso de uso — Reserva de stock (ReserveStock, camino crítico)
  - [ ] 5.1 Implementar `ReserveStockUseCase`
    - Adquirir lock pesimista: `stockRepository.findBySkuForUpdate(sku)`
    - Verificar idempotencia: `reservationRepository.findBySkuAndOrderIdAndStatus(sku, orderId, PENDING)` — si existe, retornar reserva existente sin modificar stock
    - Si `availableQuantity >= quantity`: incrementar `reservedQuantity`, crear `StockReservation` (PENDING, expiresAt = NOW()+15min), registrar `StockMovement` (ORDER_RESERVE), insertar `OutboxEvent` (StockReserved)
    - Si `availableQuantity < quantity`: insertar `OutboxEvent` (StockReserveFailed), retornar respuesta fallida sin modificar stock
    - Si `availableQuantity <= threshold` post-reserva: insertar `OutboxEvent` adicional (StockDepleted)
    - Todo dentro de una única transacción R2DBC ultra-corta
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 4.10_

  - [ ]\* 5.2 Escribir test de propiedad para reserva exitosa
    - **Propiedad 11: Reserva exitosa cuando stock suficiente** — Generar stocks con `availableQuantity >= quantity`, reservar y verificar que `reservedQuantity` incrementa exactamente en la cantidad, reserva PENDING con `expiresAt` ~15min, response `success = true` con `reservationId` válido
    - **Valida: Requisitos 4.2, 4.3**

  - [ ]\* 5.3 Escribir test de propiedad para reserva fallida
    - **Propiedad 12: Reserva fallida no modifica stock** — Generar stocks con `availableQuantity < quantity`, intentar reservar y verificar que `quantity` y `reservedQuantity` no cambian, response `success = false` con razón descriptiva
    - **Valida: Requisitos 4.4**

  - [ ]\* 5.4 Escribir test de propiedad para reserva duplicada idempotente
    - **Propiedad 13: Reserva duplicada es idempotente** — Generar pares (sku, orderId) con reserva PENDING existente, enviar segunda solicitud y verificar que retorna `success = true` con mismo `reservationId`, sin crear reserva adicional ni modificar `reservedQuantity`
    - **Valida: Requisitos 4.5**

- [ ] 6. Checkpoint — Verificar dominio y casos de uso core
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 7. Implementar casos de uso — Expiración de reservas y consumidores Kafka
  - [ ] 7.1 Implementar `ExpireReservationsUseCase`
    - Consultar `reservationRepository.findExpiredPending(Instant.now())`
    - Por cada reserva expirada (transacción individual): cambiar status a EXPIRED, decrementar `reservedQuantity` del stock, registrar `StockMovement` (RESERVATION_RELEASE), insertar `OutboxEvent` (StockReleased, razón "RESERVATION_EXPIRED")
    - Cada reserva en su propia transacción R2DBC para aislamiento de fallos
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5_

  - [ ]\* 7.2 Escribir test de propiedad para expiración de reservas
    - **Propiedad 14: Reservas expiradas se liberan correctamente** — Generar reservas PENDING con `expiresAt` en el pasado, ejecutar expiración y verificar que status cambia a EXPIRED, `reservedQuantity` decrementa exactamente en la cantidad, `availableQuantity` incrementa en la misma cantidad
    - **Valida: Requisitos 5.2**

  - [ ] 7.3 Implementar `ProcessProductCreatedUseCase`
    - Verificar idempotencia: `processedEventRepository.exists(eventId)` — si existe, ignorar (log DEBUG)
    - Crear registro de stock: `quantity = initialStock`, `reservedQuantity = 0`, `productId` del evento
    - Registrar `StockMovement` (PRODUCT_CREATION)
    - Insertar `eventId` en `processed_events` dentro de la misma transacción
    - _Requisitos: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]\* 7.4 Escribir test de propiedad para ProductCreated
    - **Propiedad 15: ProductCreated crea stock con valores iniciales correctos** — Generar eventos ProductCreated válidos con SKU nuevo, verificar que stock tiene `quantity = initialStock`, `reservedQuantity = 0`, `availableQuantity = initialStock`, `productId` correcto
    - **Valida: Requisitos 6.1**

  - [ ] 7.5 Implementar `ProcessOrderCancelledUseCase`
    - Verificar idempotencia: `processedEventRepository.exists(eventId)` — si existe, ignorar (log DEBUG)
    - Buscar reserva PENDING por orderId: si no existe, ignorar (log WARN)
    - Si existe: cambiar status a RELEASED, decrementar `reservedQuantity`, registrar `StockMovement` (RESERVATION_RELEASE), insertar `OutboxEvent` (StockReleased, razón "ORDER_CANCELLED")
    - Insertar `eventId` en `processed_events` dentro de la misma transacción
    - _Requisitos: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6_

  - [ ]\* 7.6 Escribir test de propiedad para OrderCancelled
    - **Propiedad 17: OrderCancelled libera reserva pendiente** — Generar eventos OrderCancelled con reserva PENDING asociada, verificar que status cambia a RELEASED, `reservedQuantity` decrementa, evento StockReleased emitido con razón "ORDER_CANCELLED"
    - **Valida: Requisitos 7.2, 7.3**

  - [ ]\* 7.7 Escribir test de propiedad para idempotencia de consumidores
    - **Propiedad 16: Idempotencia de consumidores Kafka** — Generar eventos (ProductCreated u OrderCancelled) procesados exitosamente, reenviar el mismo eventId y verificar que no se ejecuta lógica de negocio, no se modifica stock, no se crean movimientos ni eventos outbox adicionales
    - **Valida: Requisitos 6.4, 6.5, 7.5, 7.6, 9.1, 9.2**

- [ ] 8. Checkpoint — Verificar todos los casos de uso
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 9. Crear esquema SQL de PostgreSQL 17
  - [ ] 9.1 Crear script de migración con las tablas `stock`, `stock_reservations`, `stock_movements`, `outbox_events` y `processed_events`
    - Tabla `stock`: id UUID PK, sku VARCHAR(100) UNIQUE NOT NULL, product_id UUID NOT NULL, quantity INTEGER NOT NULL CHECK >= 0, reserved_quantity INTEGER NOT NULL DEFAULT 0 CHECK >= 0, available_quantity GENERATED ALWAYS AS (quantity - reserved_quantity) STORED, updated_at TIMESTAMPTZ, version BIGINT NOT NULL DEFAULT 1
    - Tabla `stock_reservations`: id UUID PK, sku VARCHAR(100) NOT NULL, order_id UUID NOT NULL, quantity INTEGER NOT NULL CHECK > 0, status VARCHAR(20) DEFAULT 'PENDING', created_at TIMESTAMPTZ, expires_at TIMESTAMPTZ NOT NULL
    - Tabla `stock_movements`: id UUID PK, sku VARCHAR(100) NOT NULL, movement_type VARCHAR(30) NOT NULL, quantity_change INTEGER, previous_quantity INTEGER, new_quantity INTEGER, reference_id UUID, reason TEXT, created_at TIMESTAMPTZ
    - Tabla `outbox_events`: id UUID PK, event_type VARCHAR(50) NOT NULL, topic VARCHAR(100) DEFAULT 'inventory-events', payload JSONB NOT NULL, partition_key VARCHAR(100), status VARCHAR(20) DEFAULT 'PENDING', created_at TIMESTAMPTZ
    - Tabla `processed_events`: event_id UUID PK, processed_at TIMESTAMPTZ
    - Crear índices: `idx_stock_sku`, `idx_reservations_status_expires`, `idx_reservations_sku_order`, `idx_movements_sku_created`, `idx_outbox_status_created`
    - Ubicar en `applications/app-service/src/main/resources/` (schema.sql o migración Flyway)
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5_

- [ ] 10. Implementar driven adapters — R2DBC (PostgreSQL 17)
  - [ ] 10.1 Implementar `R2dbcStockAdapter` que implementa `StockRepository`
    - Usar `R2dbcEntityTemplate` o `DatabaseClient` para todas las operaciones
    - `findBySkuForUpdate`: ejecutar `SELECT * FROM stock WHERE sku = ? FOR UPDATE` con `DatabaseClient`
    - `updateQuantity`: ejecutar `UPDATE stock SET quantity = ?, version = version + 1, updated_at = NOW() WHERE sku = ? AND version = ?` — retornar `Mono.empty()` si 0 rows afectadas (version mismatch)
    - `updateReservedQuantity`: ejecutar `UPDATE stock SET reserved_quantity = ?, updated_at = NOW() WHERE sku = ?`
    - Crear data class interna y mapper estático para convertir entre dominio y row
    - _Requisitos: 1.1, 1.8, 4.1_

  - [ ] 10.2 Implementar `R2dbcStockReservationAdapter` que implementa `StockReservationRepository`
    - `findExpiredPending`: `SELECT * FROM stock_reservations WHERE status = 'PENDING' AND expires_at < ?`
    - `findBySkuAndOrderIdAndStatus`: consulta por los tres campos
    - `updateStatus`: `UPDATE stock_reservations SET status = ? WHERE id = ?`
    - _Requisitos: 4.2, 4.5, 5.1, 7.1_

  - [ ] 10.3 Implementar `R2dbcStockMovementAdapter` que implementa `StockMovementRepository`
    - `findBySkuOrderByCreatedAtDesc`: consulta paginada con `OFFSET` y `LIMIT`, ordenada por `created_at DESC`
    - _Requisitos: 1.5, 3.1_

  - [ ] 10.4 Implementar `R2dbcOutboxAdapter` que implementa `OutboxEventRepository`
    - `findPending`: `SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at LIMIT ?`
    - `markAsPublished`: `UPDATE outbox_events SET status = 'PUBLISHED' WHERE id = ?`
    - _Requisitos: 8.1, 8.4, 8.5_

  - [ ] 10.5 Implementar `R2dbcProcessedEventAdapter` que implementa `ProcessedEventRepository`
    - `exists`: `SELECT COUNT(*) FROM processed_events WHERE event_id = ?`
    - `save`: `INSERT INTO processed_events (event_id, processed_at) VALUES (?, NOW())`
    - _Requisitos: 9.1, 9.3, 9.4_

- [ ] 11. Implementar driven adapters — Kafka Outbox Relay y Scheduler de expiración
  - [ ] 11.1 Implementar `KafkaOutboxRelay`
    - Scheduled relay que ejecuta cada 5 segundos (`@Scheduled` o `Flux.interval`)
    - Consultar eventos PENDING vía `outboxEventRepository.findPending(100)`
    - Publicar cada evento a Kafka con `ReactiveKafkaProducer` al tópico `inventory-events` usando el `partitionKey` (SKU) como key
    - Marcar como PUBLISHED tras ack exitoso
    - En caso de fallo, mantener PENDING para reintento (log WARN)
    - _Requisitos: 8.3, 8.4, 8.5, 8.6_

  - [ ]\* 11.2 Escribir test de propiedad para transición de estado del relay
    - **Propiedad 19: Transición de estado del relay outbox** — Generar eventos PENDING, simular publicación exitosa/fallida y verificar transición a PUBLISHED o permanencia en PENDING
    - **Valida: Requisitos 8.5, 8.6**

  - [ ] 11.3 Implementar `ExpiredReservationScheduler`
    - Job periódico cada 60 segundos (`@Scheduled`)
    - Delegar a `ExpireReservationsUseCase.execute()`
    - Log INFO al iniciar y finalizar cada ciclo
    - _Requisitos: 5.1, 5.5_

- [ ] 12. Implementar entry points — DTOs, Mappers y Controlador REST
  - [ ] 12.1 Crear DTOs de request y response con Bean Validation
    - `UpdateStockRequest`: `@NotNull @PositiveOrZero Integer quantity`, `String reason`
    - `StockResponse`: id, sku, productId, quantity, reservedQuantity, availableQuantity, version, updatedAt
    - `StockMovementResponse`: id, sku, movementType, quantityChange, previousQuantity, newQuantity, referenceId, reason, createdAt
    - `ErrorResponse`: code, message
    - Todos con `@Builder(toBuilder = true)`
    - Paquete: entry-points
    - _Requisitos: 1.2, 2.1, 3.4, 11.6_

  - [ ] 12.2 Crear mappers estáticos: `StockMapper`, `StockMovementMapper`
    - Métodos estáticos para convertir request→comando y dominio→response
    - Usar `@Builder` al construir objetos destino
    - _Requisitos: 1.1, 2.1, 3.1_

  - [ ] 12.3 Implementar `StockController`
    - `PUT /inventory/{sku}/stock` → `UpdateStockUseCase` → 200 OK con `StockResponse`
    - `GET /inventory/{sku}` → `GetStockUseCase` → 200 OK con `StockResponse`
    - `GET /inventory/{sku}/history` → `GetStockHistoryUseCase` → 200 OK con `Flux<StockMovementResponse>` (paginado con query params page, size)
    - Usar `@Valid` en requests, retornos `Mono`/`Flux`
    - _Requisitos: 1.1, 2.1, 2.3, 3.1, 3.3_

  - [ ] 12.4 Implementar `GlobalExceptionHandler` con `@ControllerAdvice`
    - Manejar `WebExchangeBindException` (Bean Validation) → 400 con campos inválidos
    - Manejar `DomainException` subclases → HTTP status y código según subclase
    - Manejar `DataIntegrityViolationException` (CHECK constraint PostgreSQL) → 409, STOCK_CONSTRAINT_VIOLATION
    - Manejar `Exception` genérica → 500, log ERROR, mensaje genérico sin detalles internos
    - Retornar `ErrorResponse(code, message)` en todos los casos
    - _Requisitos: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_

  - [ ]\* 12.5 Escribir test de propiedad para estructura de ErrorResponse
    - **Propiedad 20: Respuestas de error tienen estructura y HTTP status correctos** — Generar excepciones de distintos tipos (validación, dominio, inesperada) y verificar que la respuesta contiene ErrorResponse con code y message no vacíos, HTTP status correcto (400, 404, 409, 500), y que las respuestas 500 no exponen detalles internos
    - **Valida: Requisitos 11.2, 11.3, 11.4, 11.5, 11.6**

- [ ] 13. Checkpoint — Verificar REST y driven adapters
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 14. Implementar entry point — Servidor gRPC
  - [ ] 14.1 Crear archivo `.proto` para el servicio de inventario
    - Definir `InventoryService` con RPC `ReserveStock(ReserveStockRequest) returns (ReserveStockResponse)`
    - `ReserveStockRequest`: sku (string), order_id (string), quantity (int32)
    - `ReserveStockResponse`: success (bool), reservation_id (string), available_quantity (int32), reason (string)
    - Ubicar en `infrastructure/entry-points/` y configurar generación de código protobuf en `build.gradle`
    - _Requisitos: 4.1, 4.3, 4.4_

  - [ ] 14.2 Implementar `GrpcStockService` que extiende la clase generada
    - Mapear `ReserveStockRequest` a comando de dominio
    - Delegar a `ReserveStockUseCase.execute(command)`
    - Mapear resultado a `ReserveStockResponse`
    - Traducir excepciones de dominio a códigos gRPC: `StockNotFoundException` → NOT_FOUND, `InsufficientStockException` → FAILED_PRECONDITION, `Exception` → INTERNAL
    - Para reservas fallidas por stock insuficiente: retornar response con `success = false` (no excepción)
    - _Requisitos: 4.1, 4.2, 4.3, 4.4, 4.5_

- [ ] 15. Implementar entry point — Consumidor Kafka
  - [ ] 15.1 Implementar `KafkaEventConsumer`
    - Suscribirse a tópicos `product-events` y `order-events`
    - Deserializar sobre estándar (`DomainEventEnvelope`) y filtrar por `eventType`
    - `ProductCreated` → delegar a `ProcessProductCreatedUseCase`
    - `OrderCancelled` → delegar a `ProcessOrderCancelledUseCase`
    - Ignorar tipos de evento desconocidos con log WARN
    - Implementar retry con backoff exponencial (3 reintentos) para errores transitorios
    - _Requisitos: 6.1, 6.4, 7.1, 7.5, 9.5_

  - [ ]\* 15.2 Escribir test de propiedad para completitud de eventos
    - **Propiedad 18: Completitud del sobre y payload de eventos** — Generar eventos de todos los tipos (StockReserved, StockReserveFailed, StockReleased, StockUpdated, StockDepleted) y verificar que el sobre contiene eventId (UUID), eventType, timestamp, source = "ms-inventory", correlationId, payload con todos los campos requeridos por tipo
    - **Valida: Requisitos 8.2, 8.7, 8.8, 8.9, 8.10, 8.11, 8.12**

- [ ] 16. Configuración de Spring Boot y cableado de dependencias
  - [ ] 16.1 Configurar `application.yml` en `app-service`
    - Configuración R2DBC: url `r2dbc:postgresql://localhost:5433/db_inventory`, username, password
    - Configuración Kafka: bootstrap-servers, producer config (serializers), consumer config (group-id, deserializers, tópicos)
    - Configuración gRPC: puerto del servidor
    - Configuración de scheduling: habilitar `@EnableScheduling`
    - Propiedad `stock.depletion.threshold` configurable (default: 10)
    - Logging con SLF4J, `CommandLineRunner` para log de inicio
    - _Requisitos: 1.7, 8.3_

  - [ ] 16.2 Configurar beans de inyección de dependencias
    - Registrar use cases, adapters y ports en la configuración de Spring
    - Asegurar que los driven adapters implementan los ports correctos
    - Configurar `TransactionalOperator` para transacciones R2DBC
    - Agregar dependencias en `build.gradle`: jqwik, reactor-test, mockito, grpc-spring-boot-starter, r2dbc-postgresql
    - _Requisitos: 4.9, 8.1_

- [ ] 17. Checkpoint final — Verificar integración completa
  - Asegurar que todos los tests pasan (unitarios y de propiedades), ejecutar `./gradlew build` desde `ms-inventory/`, preguntar al usuario si surgen dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests de propiedades validan correctitud universal con jqwik (mínimo 100 iteraciones)
- Los tests unitarios validan ejemplos específicos y edge cases con JUnit 5 + Mockito + StepVerifier
- Todas las entidades usan `record` con `@Builder(toBuilder = true)` según estándares de Arka
- El camino crítico (reserva de stock con lock pesimista) se implementa en la tarea 5 para validar tempranamente la prevención de sobreventa
- Los comandos de build se ejecutan desde `ms-inventory/`: `./gradlew build`, `./gradlew test`
