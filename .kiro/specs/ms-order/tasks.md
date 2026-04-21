# Plan de Implementación: ms-order

## Visión General

Implementación incremental del microservicio `ms-order` siguiendo Clean Architecture (Bancolombia Scaffold). Se construye desde el dominio hacia afuera: modelo → casos de uso → adaptadores → entry-points → outbox relay → consumidor Kafka. Cada tarea integra lo construido en la anterior, sin código huérfano. Java 21, Spring Boot 4.0.3, Spring WebFlux, R2DBC, gRPC, Kafka, jqwik para PBT.

**REGLA CRÍTICA DE IMPLEMENTACIÓN:** Todos los módulos nuevos (Model, UseCase, Driven Adapter, Entry Point, Helper) DEBEN generarse usando las tareas Gradle del plugin Bancolombia Scaffold. La creación manual de estructura de módulos está PROHIBIDA. Ejecutar siempre desde la raíz de `ms-order/`:

```bash
# Generar Model + Gateway interface
./gradlew generateModel --name=<Name>

# Generar UseCase
./gradlew generateUseCase --name=<Name>

# Generar Driven Adapter
./gradlew generateDrivenAdapter --type=<type>

# Generar Entry Point
./gradlew generateEntryPoint --type=<type>

# Validar estructura
./gradlew validateStructure
```

Ver `.agents/skills/scaffold-tasks/SKILL.md` para referencia completa de comandos y tipos disponibles.

**REUTILIZACIÓN Y VERSIONADO:** Antes de implementar patrones transversales o agregar dependencias, consultar **`.kiro/steering/reusability.md`**. Define los componentes reutilizables de `ms-inventory` (Outbox, Kafka Producer/Consumer, R2DBC adapters, ProcessedEvents, Controller→Handler, GlobalExceptionHandler, Spring Profiles, Springdoc), las versiones exactas de todas las librerías y qué adaptar por dominio.

## Tareas

- [x] 1. Configurar estructura del proyecto y esquema de base de datos
  - [x] 1.1 Crear script SQL de inicialización con las tablas `orders`, `order_items`, `order_state_history`, `outbox_events` y `processed_events` según el esquema del diseño (índices, columna generada `subtotal`, constraints)
    - Incluir `CREATE TABLE`, índices y constraints CHECK
    - Ubicado en `postgresql-scripts/init_orders.sql`
    - _Requisitos: 1.3, 4.5, 7.1, 8.3_
  - [x] 1.2 Configurar `application.yml` con conexión R2DBC a PostgreSQL (`order_db`), propiedades de Kafka (bootstrap-servers, consumer group `order-service-group`, tópicos) y propiedades gRPC del cliente ms-inventory
    - Configurar perfiles `default` y `local`
    - Externalizar intervalo del relay: `scheduler.outbox-relay.interval: 5000` (sin default inline, §D.6)
    - Configurar Springdoc/OpenAPI: `springdoc.api-docs.path`, `springdoc.swagger-ui.path/enabled` (§D.2)
    - _Requisitos: 7.3, 8.1, 9.1_
  - [x] 1.3 Agregar dependencias en `build.gradle`: R2DBC PostgreSQL, Spring Kafka (reactive), gRPC client (protobuf), jqwik, reactor-test, Lombok, `springdoc-openapi-starter-webflux-ui:3.0.2`, `reactor-kafka:1.3.25`
    - **OBLIGATORIO:** Seguir tabla de **Versionado Unificado** en `reusability.md` para todas las versiones
    - Verificar compatibilidad con Spring Boot 4.0.3 y Scaffold 4.2.0
    - **En `main.gradle` (subprojects):** Agregar `net.jqwik:jqwik:1.9.2` y `com.tngtech.archunit:archunit:1.4.1` en testImplementation ✅
    - **En `app-service/build.gradle`:** Agregar solo `springdoc-openapi-starter-webflux-ui:3.0.2` y `jackson-databind` ✅
    - **NOTA:** Las demás dependencias se agregarán en sus módulos específicos cuando se creen con Scaffold:
      - R2DBC + PostgreSQL → tarea 6.1 (`r2dbc-postgresql` module)
      - Kafka producer (reactor-kafka:1.3.25) → tarea 10.1 (`kafka-producer` module)
      - Kafka consumer → tarea 10.3 (`kafka-consumer` module)
      - gRPC client → tarea 6.6 (`grpc-inventory` module)
      - WebFlux → tarea 8.4 (`reactive-web` module)
    - _Requisitos: transversal_
  - [x] 1.4 Configurar Spring Profiles (local/docker)
    - Crear `application-local.yaml` con hosts `localhost` y puertos mapeados (PostgreSQL, Kafka)
    - Crear `application-docker.yaml` con hostnames de contenedores (`arka-postgres`, `arka-kafka`) y puertos internos
    - Configurar `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` en `application.yaml`
    - **OBLIGATORIO (reusability.md #9):** Copiar estructura de `ms-inventory/applications/app-service/src/main/resources/`
    - _Estándar: §B.10 (Spring Profiles)_

- [x] 2. Implementar modelo de dominio (`domain/model`)
  - [x] 2.1 Crear la sealed interface `OrderStatus` con los records `PendingReserve`, `Confirmed`, `InShipment`, `Delivered` y `Cancelled`, cada uno con método `value()` que retorna el String correspondiente (PENDIENTE_RESERVA, CONFIRMADO, EN_DESPACHO, ENTREGADO, CANCELADO)
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-order && ./gradlew generateModel --name=Order`
    - Incluir pattern matching exhaustivo en Java 21
    - Ubicar en `com.arka.model.order`
    - _Requisitos: 4.1, 4.7_
  - [x] 2.2 Crear la clase `OrderStateTransition` con el mapa de transiciones válidas y métodos `isValidTransition(from, to)` e `isTerminal(status)`, incluyendo los estados terminales ENTREGADO y CANCELADO
    - Mapa inmutable con `Map.of()`
    - _Requisitos: 4.2, 4.3, 4.4_

  - [ ]\* 2.3 Escribir test de propiedad para la máquina de estados (OrderStateTransition)
    - **Propiedad 7: Máquina de estados acepta solo transiciones válidas**
    - Generar todos los pares (from, to) de estados posibles y verificar que solo las 5 transiciones válidas son aceptadas; el resto rechazadas. ENTREGADO y CANCELADO no tienen transiciones de salida.
    - **Valida: Requisitos 4.2, 4.3, 4.4, 5.1, 5.2, 5.3, 5.4, 6.1, 6.2, 10.3**
  - [x] 2.4 Crear los records de dominio: `Order` (con validación en compact constructor, `@Builder(toBuilder=true)`), `OrderItem` (con cálculo de subtotal), `OrderStateHistory`, `ReserveStockResult`
    - Validaciones: `Objects.requireNonNull` para campos requeridos, `quantity > 0`, defaults para `status`, `createdAt`, `updatedAt`
    - Ubicar en `com.arka.model.order`
    - _Requisitos: 1.1, 1.5, 4.5_
  - [ ]\* 2.5 Escribir test de propiedad para la invariante de total_amount
    - **Propiedad 4: Invariante de total_amount**
    - Generar listas de items con precios (BigDecimal positivos) y cantidades (int positivos) aleatorios, verificar que la suma de `quantity * unitPrice` por item es exactamente igual al `totalAmount` calculado.
    - **Valida: Requisitos 1.5**
  - [x] 2.6 Crear el record `OutboxEvent` con defaults para `id`, `status`, `topic` y `createdAt` en compact constructor
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-order && ./gradlew generateModel --name=OutboxEvent`
    - **OBLIGATORIO (reusability.md #1):** Copiar `ms-inventory/domain/model/outboxevent/` — crear `EventType` enum (ORDER_CONFIRMED, ORDER_STATUS_CHANGED, ORDER_CANCELLED, ORDER_CREATED), `OutboxStatus` enum (PENDING, PUBLISHED), y métodos de dominio `isPending()`, `isPublished()`, `markAsPublished()` idénticos a ms-inventory
    - Ubicar en `com.arka.model.outbox`
    - _Requisitos: 7.1, 7.2_
  - [x] 2.7 Crear los records de eventos de dominio: `DomainEventEnvelope`, `OrderCreatedPayload`, `OrderConfirmedPayload`, `OrderStatusChangedPayload`, `OrderCancelledPayload`, `OrderItemPayload`
    - **OBLIGATORIO (reusability.md #1):** Copiar `ms-inventory/domain/model/outboxevent/DomainEventEnvelope.java` — incluir constante `public static final String MS_SOURCE = "ms-order"` y defaults en compact constructor
    - `EventType` enum con `value()` method que retorna el string PascalCase para el envelope Kafka (e.g., `ORDER_CONFIRMED.value()` → `"OrderConfirmed"`)
    - `OrderCreatedPayload` incluido por Req 7.8 (orderId, customerId, items, totalAmount) — usado en Fase 2+
    - Ubicar en `com.arka.model.outboxevent`
    - _Requisitos: 7.7, 7.8, 7.9, 7.10, 7.11_
  - [x] 2.8 Crear las interfaces de gateway (ports): `OrderRepository`, `OrderItemRepository`, `OrderStateHistoryRepository`, `OutboxEventRepository`, `ProcessedEventRepository`, `InventoryClient`, `CatalogClient`
    - **OBLIGATORIO (reusability.md #1, #3):** Copiar firmas de `OutboxEventRepository` y `ProcessedEventRepository` de ms-inventory (`domain/model/outboxevent/gateways/` y `domain/model/processedevent/gateways/`)
    - `CatalogClient`: port para consultar precio y nombre del producto por SKU vía gRPC a ms-catalog. Retorna `Mono<ProductInfo>` donde `ProductInfo(sku, productName, unitPrice)`
    - Retornos reactivos (`Mono`/`Flux`) según el diseño
    - Ubicar en `com.arka.model.<aggregate>.gateways`
    - _Requisitos: transversal_
  - [x] 2.9 Crear la jerarquía de excepciones de dominio: `DomainException` (abstract), `OrderNotFoundException`, `InvalidStateTransitionException`, `InsufficientStockException`, `InventoryServiceUnavailableException`, `AccessDeniedException`, `InvalidOrderStatusException`
    - **OBLIGATORIO (reusability.md #8):** Copiar estructura de `ms-inventory/domain/model/commons/exception/DomainException.java` como base (abstract con `getHttpStatus()` y `getCode()`)
    - Cada una con `getHttpStatus()` y `getCode()` según la tabla del diseño
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5, 10.7, 10.8_

- [x] 3. Checkpoint — Verificar compilación del modelo de dominio
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [x] 4. Implementar casos de uso (`domain/usecase`)
     > **Nota de implementación:** Aplicando la convención §3 (1 UseCase/entidad, cohesión por agregado), se generaron `OrderUseCase` (`./gradlew generateUseCase --name=Order`) con 6 métodos públicos y `OutboxRelayUseCase` (`./gradlew generateUseCase --name=OutboxRelay`). Se creó la interfaz `JsonSerializer` en `domain/usecase` y se agregó `CatalogServiceUnavailableException` al modelo (extensión de 2.9). Compilación y Sonar sin errores.
  - [x] 4.1 Implementar `CreateOrderUseCase`: crear Order en estado PENDIENTE_RESERVA, consultar `CatalogClient.getProductInfo(sku)` por cada item para obtener precio y nombre autoritativo, invocar `InventoryClient.reserveStock()` por cada item, persistir Order con estado CONFIRMADO, items con precios de catálogo, historial PENDIENTE_RESERVA→CONFIRMADO y evento OrderConfirmed en outbox, todo en transacción R2DBC atómica
    - **CRÍTICO**: Generar con Scaffold: `cd ms-order && ./gradlew generateUseCase --name=CreateOrder`
    - **Fuente de precio:** ms-catalog vía gRPC (`CatalogClient.getProductInfo(sku)`) — NO del frontend, NO de ms-inventory. ms-catalog es la fuente autoritativa de `unitPrice` y `productName`
    - Calcular `totalAmount` como suma de subtotales (`quantity * unitPrice` de catálogo)
    - Si algún item falla por stock insuficiente, acumular todos los fallos y lanzar `InsufficientStockException` con detalle de SKUs
    - Si gRPC de ms-inventory falla por comunicación, lanzar `InventoryServiceUnavailableException`
    - Si gRPC de ms-catalog falla o producto no encontrado, lanzar excepción apropiada (CatalogServiceUnavailableException o similar)
    - _Requisitos: 1.2, 1.3, 1.4, 1.5, 1.7, 1.9, 9.1, 9.2, 9.3, 9.5, 9.6_

  - [ ]\* 4.2 Escribir test de propiedad para creación exitosa de orden
    - **Propiedad 2: Creación exitosa produce todos los artefactos correctos**
    - Generar órdenes válidas con gRPC exitoso (mock), verificar que se persisten: Order con CONFIRMADO, N OrderItems con precios correctos, historial PENDIENTE_RESERVA→CONFIRMADO, evento OrderConfirmed en outbox con payload completo.
    - **Valida: Requisitos 1.2, 1.3, 1.7, 7.9, 9.1, 9.2**
  - [ ]\* 4.3 Escribir test de propiedad para stock insuficiente
    - **Propiedad 3: Stock insuficiente aborta sin persistir**
    - Generar órdenes donde gRPC rechaza items aleatorios, verificar que no se persiste Order, ni items, ni historial, ni eventos outbox.
    - **Valida: Requisitos 1.4, 9.3, 9.6**
  - [x] 4.4 Implementar `GetOrderUseCase`: consultar orden por ID con sus items, validar acceso (CUSTOMER solo ve sus propias órdenes, ADMIN ve todas)
    - **CRÍTICO**: Generar con Scaffold: `cd ms-order && ./gradlew generateUseCase --name=GetOrder`
    - Lanzar `OrderNotFoundException` si no existe
    - Lanzar `AccessDeniedException` si CUSTOMER accede a orden ajena
    - _Requisitos: 2.1, 2.2, 2.3, 2.4_
  - [ ]\* 4.5 Escribir test de propiedad para control de acceso
    - **Propiedad 10: Control de acceso para Cliente_B2B**
    - Generar pares (orden, customerId) donde customerId no coincide con el de la orden, verificar que consulta y cancelación retornan 403 sin modificar estado.
    - **Valida: Requisitos 2.4, 6.4, 10.8**
  - [x] 4.6 Implementar `ListOrdersUseCase`: listar órdenes paginadas con filtros por status y customerId, ordenadas por `created_at` DESC. CUSTOMER ve solo sus órdenes (filtro automático por customerId)
    - **CRÍTICO**: Generar con Scaffold: `cd ms-order && ./gradlew generateUseCase --name=ListOrders`
    - Validar que el status proporcionado sea un Estado_De_Orden válido, lanzar `InvalidOrderStatusException` si no
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_
  - [ ]\* 4.7 Escribir test de propiedad para listado ordenado y filtrado
    - **Propiedad 11: Listado de órdenes ordenado y filtrado correctamente**
    - Generar órdenes con timestamps y estados variados, verificar orden descendente por `created_at` y que los filtros se aplican correctamente. CUSTOMER siempre filtrado por su customerId.
    - **Valida: Requisitos 3.1, 3.2, 3.3, 3.4**
  - [x] 4.8 Implementar `ChangeOrderStatusUseCase`: validar transición de estado (CONFIRMADO→EN_DESPACHO, EN_DESPACHO→ENTREGADO) usando `OrderStateTransition`, actualizar orden, registrar historial y emitir evento OrderStatusChanged en outbox. Solo ADMIN.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-order && ./gradlew generateUseCase --name=ChangeOrderStatus`
    - Lanzar `InvalidStateTransitionException` si la transición no es válida
    - Lanzar `OrderNotFoundException` si la orden no existe
    - _Requisitos: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_
  - [x] 4.9 Implementar `CancelOrderUseCase`: validar que la orden está en estado cancelable (CONFIRMADO), transicionar a CANCELADO con reason, registrar historial y emitir evento OrderCancelled en outbox. CUSTOMER solo sus órdenes, ADMIN cualquiera.
    - **CRÍTICO**: Generar con Scaffold: `cd ms-order && ./gradlew generateUseCase --name=CancelOrder`
    - Lanzar `InvalidStateTransitionException` si estado no permite cancelación
    - Lanzar `AccessDeniedException` si CUSTOMER intenta cancelar orden ajena
    - Lanzar `OrderNotFoundException` si la orden no existe
    - _Requisitos: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7_
  - [ ]\* 4.10 Escribir test de propiedad para historial de auditoría en transiciones
    - **Propiedad 8: Transiciones válidas producen historial de auditoría completo**
    - Generar transiciones válidas aleatorias, verificar que se crea registro en historial con previous_status, new_status, changed_by y reason (si aplica).
    - **Valida: Requisitos 4.5, 6.7**
  - [ ]\* 4.11 Escribir test de propiedad para eventos outbox en transiciones
    - **Propiedad 9: Transiciones válidas producen eventos outbox correctos**
    - Generar transiciones válidas, verificar que se crea evento en outbox con eventType correcto, status=PENDING, topic="order-events", partition_key=orderId y payload con campos requeridos.
    - **Valida: Requisitos 4.6, 5.6, 6.5, 7.2, 7.3, 7.8, 7.9, 7.10, 7.11**
  - [x] 4.12 Implementar `ProcessExternalEventUseCase`: verificar idempotencia (processed_events), procesar eventos de payment-events y shipping-events (infraestructura base para Fase 2+). Ignorar eventTypes desconocidos con log WARN.
    - **Implementado (Fase 1):** `processExternalEvent(UUID eventId)` con patrón de idempotencia completo. Routing real (PaymentProcessed, PaymentFailed, ShippingDispatched) en tarea F2.3–F2.7.

- [ ] 5. Checkpoint — Verificar compilación y tests de casos de uso
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 6. Implementar adaptadores de infraestructura (`infrastructure/driven-adapters`)
  - [ ] 6.1 Implementar `R2dbcOrderAdapter` (implementa `OrderRepository`): operaciones CRUD con `DatabaseClient` de R2DBC, incluyendo `findByFilters` con filtros dinámicos y paginación
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-order && ./gradlew generateDrivenAdapter --type=r2dbc`
    - **Agregar dependencias en `r2dbc-postgresql/build.gradle`:** `spring-boot-starter-data-r2dbc`, `r2dbc-postgresql` (versiones del Spring BOM)
    - **OBLIGATORIO (reusability.md #4):** Copiar patrón DTO + Mapper + SpringDataRepository de `ms-inventory/infrastructure/driven-adapters/r2dbc-postgresql/.../stock/`
    - Mapeo manual de filas a records con `@Builder`
    - _Requisitos: 1.3, 2.1, 3.1, 5.1_
  - [ ] 6.2 Implementar `R2dbcOrderItemAdapter` (implementa `OrderItemRepository`): `saveAll` batch y `findByOrderId`
    - _Requisitos: 1.3, 2.1_
  - [ ] 6.3 Implementar `R2dbcOrderStateHistoryAdapter` (implementa `OrderStateHistoryRepository`): `save` y `findByOrderId`
    - _Requisitos: 4.5_
  - [ ] 6.4 Implementar `R2dbcOutboxAdapter` (implementa `OutboxEventRepository`): `save`, `findPending(limit)` y `markAsPublished(id)`
    - **OBLIGATORIO (reusability.md #4):** Copiar y adaptar `ms-inventory/infrastructure/driven-adapters/r2dbc-postgresql/.../outbox/`: `R2dbcOutboxAdapter`, `OutboxEventDTO`, `OutboxEventDTOMapper`, `SpringDataOutboxRepository`
    - `findPending` ordena por `created_at` ASC con LIMIT
    - _Requisitos: 7.1, 7.4, 7.5_
  - [ ] 6.5 Implementar `R2dbcProcessedEventAdapter` (implementa `ProcessedEventRepository`): `exists(eventId)` y `save(eventId)`
    - **OBLIGATORIO (reusability.md #3):** Copiar `ms-inventory/.../processedevent/R2dbcProcessedEventAdapter.java` — usa `DatabaseClient` con INSERT explícito (NO `repository.save()`) porque el `event_id` viene de Kafka (siempre no nulo, §2.2 Excepción UUIDs de Fuentes Externas)
    - _Requisitos: 8.3, 8.4, 8.5_
  - [ ] 6.6 Implementar `GrpcInventoryClient` (implementa `InventoryClient`): invocar `ReserveStock` de ms-inventory vía gRPC stub, traducir respuestas y errores gRPC a tipos de dominio (`ReserveStockResult`, `InventoryServiceUnavailableException`)
    - Integrar de forma reactiva sin bloquear EventLoop
    - Manejar `UNAVAILABLE`, timeout y errores inesperados
    - **Nota:** `ReserveStockResult` NO incluye `unitPrice` — alineado con el proto real de ms-inventory. El precio viene de `CatalogClient`
    - _Requisitos: 9.1, 9.4, 9.5_
  - [ ] 6.7 Implementar `GrpcCatalogClient` (implementa `CatalogClient`): invocar `GetProductInfo` de ms-catalog vía gRPC stub, traducir respuesta a `ProductInfo(sku, productName, unitPrice)` de dominio
    - Crear módulo `grpc-catalog` en `infrastructure/driven-adapters/` (módulo manual, gRPC sin tipo Scaffold)
    - Copiar el `catalog.proto` de ms-catalog (`infrastructure/entry-points/grpc-catalog/src/main/proto/catalog.proto`) al directorio `src/main/proto/` del módulo
    - Configurar `build.gradle` con plugin `com.google.protobuf` (misma config que `grpc-inventory` de ms-inventory pero como client stub)
    - Dependencias: `net.devh:grpc-client-spring-boot-starter:3.1.0.RELEASE`, `io.grpc:grpc-stub`, `io.grpc:grpc-protobuf`, `javax.annotation:javax.annotation-api:1.3.2`
    - Mapear `GetProductInfoResponse` → `ProductInfo` de dominio (parsear `unitPrice` de String a `BigDecimal`)
    - Manejar errores: `NOT_FOUND` → `ProductNotFoundException`, `UNAVAILABLE` → excepción de servicio no disponible
    - Configurar en `application.yaml`: `grpc.client.ms-catalog.address: static://localhost:9091` (local) / `static://ms-catalog:9090` (docker)
    - _Requisitos: 1.5 (precio autoritativo)_

  - [ ]\* 6.8 Escribir test de propiedad para error de comunicación gRPC
    - **Propiedad 6: Error de comunicación gRPC retorna 503**
    - Simular errores gRPC variados (timeout, conexión rechazada, UNAVAILABLE), verificar que se lanza `InventoryServiceUnavailableException` sin persistir datos.
    - **Valida: Requisitos 1.9, 9.4, 10.4**

- [ ] 7. Checkpoint — Verificar compilación y tests de adaptadores
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 8. Implementar entry-points (`infrastructure/entry-points`)
  - [ ] 8.1 Crear los DTOs de request: `CreateOrderRequest`, `OrderItemRequest`, `ChangeStatusRequest`, `CancelOrderRequest` con Bean Validation (`@NotNull`, `@NotBlank`, `@NotEmpty`, `@Positive`, `@Valid`)
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-order && ./gradlew generateEntryPoint --type=webflux --router=false`
    - **Agregar dependencias en `reactive-web/build.gradle`:** `spring-boot-starter-webflux`, `spring-boot-starter-validation` (versiones del Spring BOM)
    - Records con `@Builder(toBuilder=true)`
    - _Requisitos: 1.1, 1.8_
  - [ ] 8.2 Crear los DTOs de response: `OrderResponse`, `OrderItemResponse`, `OrderSummaryResponse`, `ErrorResponse`
    - Records con `@Builder(toBuilder=true)`
    - _Requisitos: 1.6, 2.1, 10.6_
  - [ ] 8.3 Crear los mappers manuales: `OrderMapper` (métodos estáticos para convertir entre DTOs y comandos/entidades de dominio)
    - Sin MapStruct, usar `@Builder` para construir objetos destino
    - _Requisitos: transversal_
  - [ ] 8.4 Implementar `OrderHandler` (`@Component`) y `OrderController` (`@RestController`)
    - **OBLIGATORIO (reusability.md #7):** Seguir patrón Controller → Handler → UseCase de `ms-inventory`: `StockController` → `StockHandler` → `StockUseCase` (§4.2)
    - `OrderHandler`: orquesta llamada a UseCase + mapeo vía `OrderMapper` + wrapping en `ResponseEntity`/`Flux`
    - `OrderController`: thin — solo anotaciones HTTP (`@Valid`, `@PathVariable`), extraer `X-User-Email`/`X-User-Role` de headers, delegar a `OrderHandler`
    - Endpoints: POST /orders (202), GET /orders/{id} (200), GET /orders (200), PUT /orders/{id}/status (200), PUT /orders/{id}/cancel (200)
    - Retornos `Mono`/`Flux`, `@Valid` en requests
    - Anotaciones Springdoc: `@Tag`, `@Operation`, `@ApiResponse` (§D.2)
    - _Requisitos: 1.1, 1.6, 2.1, 2.3, 3.1, 3.5, 5.5, 6.3_
  - [ ]\* 8.5 Escribir test de propiedad para validación de entrada
    - **Propiedad 1: Validación rechaza entrada inválida**
    - Generar requests con campos nulos, vacíos, items vacíos, quantity <= 0. Verificar que se rechaza con HTTP 400 sin invocar gRPC ni persistir datos.
    - **Valida: Requisitos 1.1, 1.8, 10.2**
  - [ ]\* 8.6 Escribir test de propiedad para campos completos en respuestas
    - **Propiedad 5: Respuestas contienen todos los campos requeridos**
    - Generar órdenes aleatorias, consultar y verificar que la respuesta contiene todos los campos requeridos: orderId, customerId, status, totalAmount, shippingAddress, items (no vacía), createdAt.
    - **Valida: Requisitos 1.6, 2.1**
  - [ ] 8.7 Implementar `GlobalExceptionHandler` (`@ControllerAdvice`): traducir excepciones de dominio a `ErrorResponse` con códigos HTTP correctos (400, 403, 404, 409, 503, 500). No exponer detalles internos en 500.
    - **OBLIGATORIO (reusability.md #8):** Copiar `ms-inventory/.../api/handler/GlobalExceptionHandler.java` como base — manejar `WebExchangeBindException`, `ServerWebInputException`, `DomainException`, `IllegalArgumentException`, `Exception` genérica
    - Agregar handlers específicos para las excepciones de ms-order
    - Manejar `WebExchangeBindException`, todas las `DomainException` y `Exception` genérica
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8_
  - [ ]\* 8.8 Escribir test de propiedad para estructura de ErrorResponse
    - **Propiedad 15: Respuestas de error tienen estructura correcta**
    - Generar excepciones de distintos tipos (validación, dominio, inesperada), verificar que la respuesta contiene `code` (no vacío) y `message` (no vacío) con el código HTTP correcto. Las 500 no exponen stack trace.
    - **Valida: Requisitos 10.5, 10.6**

- [ ] 9. Checkpoint — Verificar compilación y tests de entry-points
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 10. Implementar Outbox Relay y consumidor Kafka
  - [ ] 10.1 Implementar `KafkaOutboxRelay`: proceso scheduled (cada 5s) que consulta `outbox_events` con status PENDING, publica a Kafka (tópico `order-events`, partition key = orderId) y marca como PUBLISHED. Si falla la publicación, mantener PENDING para reintento.
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-order && ./gradlew generateDrivenAdapter --type=generic --name=kafka-producer`
    - Agregar dependencias: `reactor-kafka:1.3.25`, `spring-kafka`, `jackson-databind` (mismas versiones que ms-inventory)
    - **OBLIGATORIO (reusability.md #5):** Copiar y adaptar los 2 archivos de `ms-inventory/infrastructure/driven-adapters/kafka-producer/`:
      - `KafkaProducerConfig.java` → copiar tal cual (bean `KafkaSender<String, String>`, acks=all, retries=3)
      - `KafkaOutboxRelay.java` → copiar y adaptar: `TOPIC = "order-events"`, `DomainEventEnvelope.MS_SOURCE = "ms-order"`, inyectar `OutboxRelayUseCase`
    - Usar `reactor-kafka` `KafkaSender` (NO ReactiveKafkaProducer — fue eliminado en spring-kafka 4.0, §B.11)
    - Procesar en lotes (LIMIT 100 vía `OutboxRelayUseCase.BATCH_SIZE`)
    - _Requisitos: 7.3, 7.4, 7.5, 7.6_
    - _Estándar: §B.11 (reactor-kafka directo), §D.6 (Schedulers externalizados)_
  - [ ]\* 10.2 Escribir test de propiedad para transición del relay outbox
    - **Propiedad 12: Transición de estado del relay outbox**
    - Generar eventos PENDING, simular publicación exitosa y fallida, verificar que los exitosos transicionan a PUBLISHED y los fallidos permanecen PENDING.
    - **Valida: Requisitos 7.5, 7.6**
  - [ ] 10.3 Implementar `KafkaEventConsumer`: consumer suscrito a `payment-events` y `shipping-events` (consumer group `order-service-group`). Deserializar sobre estándar, filtrar por eventType. Ignorar tipos desconocidos con log WARN. Delegar a `ProcessExternalEventUseCase`.
    - **CRÍTICO**: Generar módulo manualmente como `kafka-consumer` en `infrastructure/entry-points/` (o con Scaffold: `cd ms-order && ./gradlew generateEntryPoint --type=generic --name=kafka-consumer`)
    - **Agregar dependencias en `kafka-consumer/build.gradle`:** `spring-kafka`, `reactor-kafka:1.3.25`, `jackson-databind` (mismas versiones que ms-inventory)
    - **OBLIGATORIO (reusability.md #6):** Copiar y adaptar los 3 archivos de `ms-inventory/infrastructure/entry-points/kafka-consumer/`:
      - `KafkaConsumerConfig.java` → crear beans `KafkaReceiver<String, String>` por tópico (`paymentEventsReceiver`, `shippingEventsReceiver`) con consumer group `order-service-group`
      - `KafkaEventConsumer.java` → adaptar: `startConsuming()`, switch por eventType (PaymentProcessed, PaymentFailed, ShippingDispatched para Fase 2+), per-message `acknowledge()`, `onErrorResume` para errores irrecuperables, retry con backoff exponencial
      - `KafkaConsumerLifecycle.java` → copiar tal cual: `@EventListener(ApplicationReadyEvent.class)` que invoca `kafkaEventConsumer.startConsuming()`
    - **IMPORTANTE (§B.12):** `ReactiveKafkaConsumerTemplate` fue eliminado en spring-kafka 4.0 (Spring Boot 4.0.3). Usar `KafkaReceiver` de reactor-kafka directamente.
    - Infraestructura base lista para Fase 2+ (PaymentProcessed, PaymentFailed, ShippingDispatched)
    - _Requisitos: 8.1, 8.2, 8.6_
  - [ ]\* 10.4 Escribir test de propiedad para eventos con eventType desconocido
    - **Propiedad 13: Eventos con eventType desconocido son ignorados**
    - Generar eventos con eventTypes aleatorios no reconocidos, verificar que se ignoran sin excepciones, sin modificar órdenes y sin insertar en processed_events.
    - **Valida: Requisitos 8.2**
  - [ ]\* 10.5 Escribir test de propiedad para idempotencia en consumo de eventos
    - **Propiedad 14: Idempotencia en consumo de eventos**
    - Generar eventos, procesarlos una vez (registrar eventId en processed_events), enviar el mismo evento de nuevo, verificar que se descarta sin ejecutar lógica de negocio ni crear artefactos adicionales.
    - **Valida: Requisitos 8.4**

- [ ] 11. Integración final y configuración de Spring
  - [ ] 11.1 Configurar beans de Spring en `app-service`: inyección de dependencias para todos los UseCases, adaptadores R2DBC, cliente gRPC, relay outbox y consumidor Kafka. Agregar `@ConfigurationPropertiesScan` y `CommandLineRunner` de log de inicio.
    - **Actualizar `app-service/build.gradle`:** Agregar referencias a los módulos de infraestructura creados: `implementation project(':reactive-web')`, `implementation project(':r2dbc-postgresql')`, `implementation project(':kafka-producer')`, `implementation project(':kafka-consumer')`, `implementation project(':grpc-inventory')`, `implementation project(':grpc-catalog')` (si existen)
    - Crear `OpenApiConfig` con metadata del servicio (`@Bean OpenAPI`) — **OBLIGATORIO (reusability.md #10):** copiar patrón de `ms-inventory` (§D.2)
    - _Requisitos: transversal_
  - [ ] 11.2 Inyectar `TransactionalGateway` en los UseCases que requieren atomicidad (CreateOrderUseCase, ChangeOrderStatusUseCase, CancelOrderUseCase, ProcessExternalEventUseCase) y envolver el pipeline reactivo con `transactionalGateway.executeInTransaction(pipeline)` para garantizar que escritura de negocio + outbox + historial ocurren en la misma transacción R2DBC
    - **OBLIGATORIO (reusability.md #1):** Copiar patrón de `ms-inventory` `StockUseCase.java` — usar `TransactionalGateway.executeInTransaction()`, NO `@Transactional` (Spring annotation no pertenece a la capa de dominio según Clean Architecture)
    - `TransactionalGateway` port ya está en `com.arka.model.commons.gateways.TransactionalGateway`
    - _Requisitos: 1.3, 4.5, 4.6, 7.1_

- [ ] 12. Checkpoint final — Verificar que todos los tests pasan
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

## Fase 2+ — Tareas Pendientes (ms-order Completo)

Estas tareas representan lo que falta para un `ms-order` completo más allá del MVP Fase 1.

- [ ] 13. Fase 2 — Integración ms-payment (Saga de 4 pasos)
  - [ ] 13.1 Agregar estado `PENDIENTE_PAGO` a `OrderStatus` y actualizar `OrderStateTransition`
    - Nueva sealed permit `PendingPayment` con `value()` = `"PENDIENTE_PAGO"`
    - Nuevas transiciones: PENDIENTE_RESERVA → PENDIENTE_PAGO (reemplaza el salto directo a CONFIRMADO); PENDIENTE_PAGO → CONFIRMADO | CANCELADO
    - Actualizar tests de máquina de estados (tarea 2.3)
    - _Impacto: `OrderStatus`, `OrderStateTransition`, `createOrder()`, tests de propiedades_
  - [ ] 13.2 Adaptar `createOrder()` para Fase 2: persistir en `PENDIENTE_PAGO` y emitir `OrderCreated`
    - `OrderCreatedPayload` ya definido en `domain/model/outboxevent/`
    - ms-payment consume `order-events` (OrderCreated) → procesa cobro → emite `PaymentProcessed` o `PaymentFailed` a `payment-events`
    - Eliminar emisión de `OrderConfirmed` de `createOrder()` (pasa a ser responsabilidad de `processPaymentProcessed`)
  - [ ] 13.3 Implementar `processPaymentProcessed()` en `OrderUseCase`
    - Firma: `Mono<Void> processPaymentProcessed(UUID eventId, UUID orderId)`
    - Idempotente con `processedEventRepository`
    - Transición: PENDIENTE_PAGO → CONFIRMADO; persistir historial; emitir `OrderConfirmed` en outbox
    - Actualizar `processExternalEvent(UUID eventId, String eventType, String payload)` para routing real
  - [ ] 13.4 Implementar `processPaymentFailed()` en `OrderUseCase`
    - Firma: `Mono<Void> processPaymentFailed(UUID eventId, UUID orderId, String reason)`
    - Idempotente con `processedEventRepository`
    - Transición: PENDIENTE_PAGO → CANCELADO; persistir historial con reason; emitir `OrderCancelled` en outbox
    - ms-inventory consume `OrderCancelled` (tópico `order-events`) → libera la reserva de stock (StockReleased)
  - [ ] 13.5 Actualizar `KafkaEventConsumer` para routear eventos de pago (tarea 10.3 — routing real)
    - `PaymentProcessed` → `orderUseCase.processPaymentProcessed(eventId, orderId)`
    - `PaymentFailed` → `orderUseCase.processPaymentFailed(eventId, orderId, reason)`
    - Consumir tópico `payment-events` (consumer group `order-service-group`, ya configurado en `application.yaml`)

- [ ] 14. Fase 2 — Integración ms-shipping
  - [ ] 14.1 Implementar `processShippingDispatched()` en `OrderUseCase`
    - Firma: `Mono<Void> processShippingDispatched(UUID eventId, UUID orderId)`
    - Idempotente con `processedEventRepository`
    - Transición: CONFIRMADO → EN_DESPACHO; persistir historial; emitir `OrderStatusChanged` en outbox
    - _Nota: EN_DESPACHO → ENTREGADO sigue siendo transición manual por Admin (`PUT /orders/{id}/status`)_
  - [ ] 14.2 Actualizar `KafkaEventConsumer` para routear eventos de shipping (tarea 10.3 — routing real)
    - `ShippingDispatched` → `orderUseCase.processShippingDispatched(eventId, orderId)`
    - Consumir tópico `shipping-events` (ya configurado en `application.yaml`)

- [ ] 15. Fase 3 — Analítica y Reportes (ms-reporter)
  - [ ] 15.1 Verificar completitud de payloads para Event Sourcing en ms-reporter
    - `OrderCreatedPayload`, `OrderConfirmedPayload`, `OrderStatusChangedPayload`, `OrderCancelledPayload` ya están definidos en `domain/model/outboxevent/`
    - ms-reporter consume `order-events` vía Kafka para CQRS/Event Sourcing — no requiere cambios en ms-order
    - Verificar que todos los campos requeridos por analítica están presentes en los payloads existentes

---

## Notas

- **CRÍTICO**: Todos los módulos DEBEN generarse con el plugin Scaffold de Bancolombia. La creación manual está PROHIBIDA. Después de cada generación, ejecutar `./gradlew validateStructure`.
- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests de propiedades validan propiedades universales de correctitud (jqwik)
- Los tests unitarios validan ejemplos específicos y edge cases (JUnit 5 + Mockito + StepVerifier)
- Para patrones transversales y versiones de dependencias, consultar `.kiro/steering/reusability.md`
