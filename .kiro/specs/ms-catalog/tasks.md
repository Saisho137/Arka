# Plan de Implementación: ms-catalog

## Visión General

Implementación incremental del microservicio de Catálogo Maestro de Productos para la plataforma B2B Arka. Se sigue la Clean Architecture del Scaffold Bancolombia 4.2.0 con Java 21, Spring WebFlux reactivo, MongoDB, Redis (Cache-Aside) y Kafka (Outbox Pattern). Cada tarea construye sobre las anteriores, integrando tests de propiedades (jqwik) y unitarios (JUnit 5 + StepVerifier) como subtareas cercanas a la implementación.

## Tareas

- [ ] 1. Definir entidades de dominio, Value Objects y excepciones
  - [ ] 1.1 Crear los records `Product`, `CategoryRef`, `Review` en `domain/model`
    - Crear `Product` record con compact constructor (validación de SKU, nombre, precio > 0, lista inmutable de reviews)
    - Crear `CategoryRef` record (Value Object embebido) con validación de id y name no nulos
    - Crear `Review` record con validación de userId, rating 1-5, comment no nulo, createdAt con default `Instant.now()`
    - Usar `@Builder(toBuilder = true)` en todos los records
    - Paquete: `com.arka.model.product`
    - _Requisitos: 1.1, 1.2, 6.1, 6.2_

  - [ ] 1.2 Crear el record `Category` en `domain/model`
    - Crear `Category` record con compact constructor (validación de nombre no nulo)
    - Usar `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.category`
    - _Requisitos: 5.1, 5.3_

  - [ ] 1.3 Crear el record `OutboxEvent` y enum `OutboxStatus` en `domain/model`
    - Crear `OutboxEvent` record con defaults en compact constructor (eventId UUID, status PENDING, createdAt, topic "product-events")
    - Crear enum `OutboxStatus` con valores `PENDING`, `PUBLISHED`
    - Paquete: `com.arka.model.outbox`
    - _Requisitos: 7.1, 7.2_

  - [ ] 1.4 Crear records de eventos de dominio: `DomainEventEnvelope`, `ProductCreatedPayload`, `ProductUpdatedPayload`, `PriceChangedPayload`
    - `DomainEventEnvelope` con campos: eventId, eventType, timestamp, source ("ms-catalog"), correlationId, payload
    - Payloads específicos para cada tipo de evento
    - Paquete: `com.arka.model.outbox`
    - _Requisitos: 1.5, 1.6, 7.2, 7.3, 7.6_

  - [ ] 1.5 Crear jerarquía de excepciones de dominio
    - Crear `DomainException` abstracta con `getHttpStatus()` y `getCode()`
    - Crear subclases: `ProductNotFoundException` (404), `DuplicateSkuException` (409), `CategoryNotFoundException` (400), `DuplicateCategoryException` (409), `InvalidReviewException` (400)
    - Paquete: `com.arka.model.commons.exception` o dentro de cada agregado según corresponda
    - _Requisitos: 9.1, 9.2, 9.3_

  - [ ]\* 1.6 Escribir tests de propiedades para validación de entidades de dominio
    - **Propiedad 2: Validación rechaza entrada inválida** — Generar requests con campos faltantes o precio ≤ 0 y verificar que el compact constructor lanza excepción
    - **Valida: Requisitos 1.2, 1.3, 3.3**

  - [ ]\* 1.7 Escribir tests de propiedades para reseñas inválidas
    - **Propiedad 15: Datos de reseña inválidos son rechazados** — Generar reseñas con userId null, comment null o rating fuera de 1-5 y verificar que el constructor lanza excepción
    - **Valida: Requisitos 6.2, 6.3**

- [ ] 2. Definir ports (gateway interfaces)
  - [ ] 2.1 Crear interfaz `ProductRepository` en `domain/model/product/gateways`
    - Métodos: `save`, `findById`, `findBySku`, `findAllActive(page, size)`, `update`, `deactivate`, `addReview`
    - Todos retornan `Mono<Product>` o `Flux<Product>`
    - _Requisitos: 1.1, 2.1, 2.2, 3.1, 4.1, 6.1_

  - [ ] 2.2 Crear interfaz `CategoryRepository` en `domain/model/category/gateways`
    - Métodos: `save`, `findById`, `findByName`, `findAll`
    - _Requisitos: 5.1, 5.4_

  - [ ] 2.3 Crear interfaz `OutboxEventRepository` en `domain/model/outbox/gateways`
    - Métodos: `save`, `findPending`, `markAsPublished`
    - _Requisitos: 7.1, 7.4_

  - [ ] 2.4 Crear interfaz `ProductCachePort` en `domain/model/product/gateways`
    - Métodos: `get(key)`, `put(key, product)`, `evict(key)`, `evictProductListCache()`
    - _Requisitos: 2.4, 8.1, 8.2_

- [ ] 3. Implementar `CategoryUseCase` — Gestión de categorías
  - [ ] 3.1 Generar `CategoryUseCase` con Scaffold e implementar métodos
    - Generar con `./gradlew generateUseCase --name=Category`
    - Implementar `create(cmd)`: validar nombre único vía `categoryRepository.findByName()`, si existe lanzar `DuplicateCategoryException`, persistir con `categoryRepository.save()`
    - Implementar `listAll()`: retornar `categoryRepository.findAll()`
    - Inyectar dependencia: `CategoryRepository`
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

  - [ ]\* 3.2 Escribir tests de propiedades para CategoryUseCase
    - **Propiedad 12: Unicidad de nombre de categoría** — Generar pares de categorías con mismo nombre y verificar que la segunda creación es rechazada con error de conflicto
    - **Propiedad 13: Creación de producto con categoría inexistente es rechazada** — Generar categoryIds aleatorios no existentes y verificar rechazo
    - **Valida: Requisitos 5.2, 5.3, 5.5**

  - [ ]\* 3.3 Escribir tests unitarios para CategoryUseCase
    - Tests con StepVerifier: creación exitosa, nombre duplicado (409), nombre vacío (400), listado completo
    - Mockito para ports
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

- [ ] 4. Implementar `ProductUseCase` — CRUD completo + Outbox + Cache
  - [ ] 4.1 Generar `ProductUseCase` con Scaffold e implementar métodos
    - Generar con `./gradlew generateUseCase --name=Product`
    - Implementar `create(cmd)`: validar SKU único vía `productRepository.findBySku()`, verificar categoría vía `categoryRepository.findById()`, persistir + insertar `OutboxEvent` (ProductCreated) atómicamente, invalidar caché de lista vía `productCachePort.evictProductListCache()`
    - Implementar `getById(id)`: Cache-Aside (consultar `productCachePort.get(key)`, en miss consultar `productRepository.findById()` y almacenar en caché con `productCachePort.put(key, product)`, si no existe lanzar `ProductNotFoundException`)
    - Implementar `listActive(page, size)`: Cache-Aside paginado (consultar caché, en miss consultar `productRepository.findAllActive(page, size)` y almacenar, retornar solo productos activos)
    - Implementar `update(id, cmd)`: buscar producto, aplicar cambios con `toBuilder()`, insertar `OutboxEvent` (ProductUpdated) atómicamente, si precio cambió insertar `OutboxEvent` adicional (PriceChanged) con oldPrice y newPrice, invalidar caché individual y de lista
    - Implementar `deactivate(id)`: buscar producto, marcar `active = false` con `toBuilder()`, insertar `OutboxEvent` (ProductUpdated) atómicamente, invalidar caché individual y de lista
    - Implementar `addReview(productId, review)`: verificar existencia de producto, agregar reseña como subdocumento vía `productRepository.addReview()`
    - Crear `JsonSerializer` interfaz funcional (port para serialización de payloads)
    - Inyectar dependencias: `ProductRepository`, `CategoryRepository`, `OutboxEventRepository`, `ProductCachePort`, `JsonSerializer`
    - _Requisitos: 1.1, 1.4, 1.5, 1.6, 1.7, 2.1, 2.2, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 6.1, 6.4, 6.5_

  - [ ]\* 4.2 Escribir tests de propiedades para ProductUseCase
    - **Propiedad 1: Round trip de creación de producto** — Generar productos válidos aleatorios, crear y consultar por ID, verificar que SKU, nombre, precio y categoría coinciden
    - **Propiedad 2: Validación rechaza entrada inválida** — Generar requests con campos faltantes o precio ≤ 0 y verificar rechazo sin modificar estado
    - **Propiedad 3: Unicidad de SKU** — Generar pares de productos con mismo SKU y verificar que la segunda creación es rechazada con error 409
    - **Propiedad 4: Operaciones de escritura producen eventos outbox** — Verificar que crear, actualizar y desactivar generan eventos PENDING en outbox
    - **Propiedad 5: Completitud del sobre y payload de eventos** — Verificar campos requeridos del envelope y payload por tipo
    - **Propiedad 6: Escrituras invalidan caché** — Verificar que cada escritura invoca evict en caché individual y de lista
    - **Propiedad 7: Round trip de Cache-Aside** — Generar productos, simular miss→hit, verificar que la segunda consulta retorna desde caché sin consultar MongoDB
    - **Propiedad 8: Listado retorna solo productos activos** — Generar mezcla de productos activos/inactivos y verificar que el listado solo retorna activos
    - **Propiedad 9: Actualización preserva producto y refleja cambios** — Generar updates válidos y verificar que el producto retornado tiene los nuevos valores, mismo ID y updatedAt posterior
    - **Propiedad 10: Cambio de precio emite evento PriceChanged adicional** — Generar updates con precio diferente y verificar que se inserta evento PriceChanged con oldPrice y newPrice además de ProductUpdated
    - **Propiedad 11: Soft delete preserva documento con active=false** — Generar productos activos, desactivar y verificar que el documento sigue existiendo con active=false
    - **Propiedad 14: Agregar reseña incrementa lista y asigna createdAt** — Generar reseñas válidas, agregar y verificar que la lista incrementa en 1 y createdAt no es nulo
    - **Propiedad 15: Datos de reseña inválidos son rechazados** — Generar reseñas con userId null, comment null o rating fuera de 1-5 y verificar rechazo
    - **Valida: Requisitos 1.1-1.7, 2.1-2.6, 3.1-3.6, 4.1-4.5, 6.1-6.5, 7.1-7.3, 7.6, 8.1-8.2, 8.5**

- [ ] 5. Implementar `OutboxRelayUseCase` — Lógica de relay
  - [ ] 5.1 Generar `OutboxRelayUseCase` con Scaffold e implementar métodos
    - Generar con `./gradlew generateUseCase --name=OutboxRelay`
    - Implementar `fetchPendingEvents()`: consultar eventos PENDING vía `outboxEventRepository.findPending()`
    - Implementar `markAsPublished(event)`: actualizar status a PUBLISHED vía `outboxEventRepository.markAsPublished()`
    - Inyectar dependencia: `OutboxEventRepository`
    - _Requisitos: 7.4, 7.5_

- [ ] 6. Checkpoint — Verificar dominio y casos de uso
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 7. Implementar Handlers — Patrón Controller → Handler → UseCase
  - [ ] 7.1 Implementar `ProductHandler`
    - Crear `@Component` con métodos para cada operación
    - `create(request)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - `getById(id)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - `listActive(page, size)`: UseCase → Mapper → `Flux<ProductResponse>` (sin `collectList()`)
    - `update(id, request)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - `deactivate(id)`: UseCase → Mapper → `Mono<ResponseEntity<Void>>`
    - `addReview(productId, request)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - Inyectar dependencia: `ProductUseCase`
    - _Estándar: §4.2 (Controller → Handler → UseCase)_

  - [ ] 7.2 Implementar `CategoryHandler`
    - Crear `@Component` con métodos para cada operación
    - `create(request)`: UseCase → Mapper → `Mono<ResponseEntity<CategoryResponse>>`
    - `listAll()`: UseCase → Mapper → `Flux<CategoryResponse>`
    - Inyectar dependencia: `CategoryUseCase`
    - _Estándar: §4.2_

- [ ] 8. Implementar driven adapters — MongoDB
  - [ ] 8.1 Implementar `MongoProductAdapter` que implementa `ProductRepository`
    - Usar `ReactiveMongoTemplate` para todas las operaciones
    - Implementar `addReview` con operación `$push` atómica en el array de reviews
    - Implementar `deactivate` con `$set` de `active = false`
    - Implementar `findAllActive` con filtro `active = true` y paginación
    - Crear documentos MongoDB (data classes) y mappers estáticos para convertir entre dominio y documento
    - Configurar índices: `{ sku: 1 }` (unique), `{ "category.id": 1 }`, `{ active: 1 }`
    - _Requisitos: 1.1, 2.1, 2.2, 3.1, 4.1, 6.1_

  - [ ] 8.2 Implementar `MongoCategoryAdapter` que implementa `CategoryRepository`
    - Usar `ReactiveMongoTemplate`
    - Crear documento MongoDB y mapper estático
    - Configurar índice: `{ name: 1 }` (unique)
    - _Requisitos: 5.1, 5.4_

  - [ ] 8.3 Implementar `MongoOutboxAdapter` que implementa `OutboxEventRepository`
    - Usar `ReactiveMongoTemplate`
    - `findPending`: consultar por `status = PENDING` ordenado por `createdAt`
    - `markAsPublished`: actualizar `status` a `PUBLISHED`
    - Configurar índices: `{ status: 1, createdAt: 1 }`, `{ eventId: 1 }` (unique)
    - _Requisitos: 7.1, 7.4_

- [ ] 9. Implementar driven adapter — Redis (Cache-Aside)
  - [ ] 9.1 Implementar `RedisCacheAdapter` que implementa `ProductCachePort`
    - Usar `ReactiveRedisTemplate` con serialización JSON
    - `get(key)`: consultar Redis, retornar `Mono.empty()` en cache miss
    - `put(key, product)`: almacenar con TTL de 1 hora
    - `evict(key)`: eliminar entrada individual
    - `evictProductListCache()`: eliminar todas las claves con patrón `products:page:*`
    - Implementar resiliencia: capturar excepciones de conexión con `onErrorResume()`, log WARN, retornar `Mono.empty()`
    - _Requisitos: 8.1, 8.2, 8.4, 8.5_

  - [ ]\* 9.2 Escribir test de propiedad para fallback Redis→MongoDB
    - **Propiedad 17: Fallback a MongoDB cuando Redis no está disponible** — Simular Redis caído y verificar que el sistema continúa operando consultando MongoDB
    - **Valida: Requisitos 8.4**

- [ ] 10. Implementar driven adapter — Kafka Outbox Relay con `reactor-kafka`
  - [ ] 10.1 Crear módulo manual `kafka-producer` en `infrastructure/driven-adapters/`
    - Registrar en `settings.gradle`
    - Agregar dependencias: `reactor-kafka:1.3.25`, `spring-kafka`, `jackson-databind`
    - _Estándar: §B.11 (Kafka con reactor-kafka directo)_

  - [ ] 10.2 Implementar `KafkaOutboxRelay`
    - `@Scheduled(fixedDelayString = "${scheduler.outbox-relay.interval}")` — sin default inline
    - Consultar eventos PENDING vía `OutboxRelayUseCase.fetchPendingEvents()`
    - Publicar con `KafkaSender` al tópico `product-events` usando `productId` como partition key
    - Marcar como PUBLISHED tras ack exitoso vía `OutboxRelayUseCase.markAsPublished()`
    - `onErrorResume` mantiene PENDING para reintento
    - _Requisitos: 7.3, 7.4, 7.5, 7.7_
    - _Estándar: §B.11, §D.6 (Schedulers externalizados)_

  - [ ] 10.3 Implementar `KafkaProducerConfig`
    - Bean `KafkaSender<String, String>` con configuración de producer
    - `ACKS_CONFIG = "all"`, `RETRIES_CONFIG = 3`, `ENABLE_IDEMPOTENCE_CONFIG = true`
    - _Estándar: §B.11_

  - [ ]\* 10.4 Escribir test de propiedad para transición de estado del relay
    - **Propiedad 16: Transición de estado del relay outbox** — Generar eventos PENDING, simular publicación exitosa/fallida y verificar transición a PUBLISHED o permanencia en PENDING
    - **Valida: Requisitos 7.4, 7.5**

- [ ] 11. Checkpoint — Verificar driven adapters
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 12. Implementar entry points — DTOs, Mappers y Controladores REST
  - [ ] 12.1 Crear DTOs de request y response con Bean Validation
    - `CreateProductRequest`: `@NotBlank` sku, name; `@NotNull @Positive` price; `@NotBlank` categoryId
    - `UpdateProductRequest`: `@NotBlank` name; `@NotNull @Positive` price; `@NotBlank` categoryId
    - `CreateCategoryRequest`: `@NotBlank` name; description opcional
    - `AddReviewRequest`: `@NotBlank` userId, comment; `@NotNull @Min(1) @Max(5)` rating
    - `ProductResponse`, `CategoryResponse`, `ReviewResponse`, `ErrorResponse`
    - Todos con `@Builder(toBuilder = true)`
    - _Requisitos: 1.2, 1.3, 3.3, 5.3, 6.2, 6.3, 9.5_

  - [ ] 12.2 Crear mappers estáticos: `ProductMapper`, `CategoryMapper`, `ReviewMapper`
    - Métodos estáticos para convertir request→comando/dominio y dominio→response
    - Usar `@Builder` al construir objetos destino
    - _Requisitos: 1.1, 2.1, 2.2_

  - [ ] 12.3 Implementar `ProductController`
    - `POST /products` → `ProductHandler.create()` → 201 Created
    - `GET /products` → `ProductHandler.listActive()` → 200 OK (paginado con query params page, size)
    - `GET /products/{id}` → `ProductHandler.getById()` → 200 OK
    - `PUT /products/{id}` → `ProductHandler.update()` → 200 OK
    - `DELETE /products/{id}` → `ProductHandler.deactivate()` → 200 OK
    - Usar `@Valid` en requests, retornos `Mono`/`Flux`
    - Anotaciones Springdoc: `@Tag`, `@Operation`, `@ApiResponse`
    - _Requisitos: 1.1, 2.1, 2.2, 3.1, 4.1_
    - _Estándar: §4.2 (Controller → Handler), §D.2 (OpenAPI)_

  - [ ] 12.4 Implementar `CategoryController`
    - `POST /categories` → `CategoryHandler.create()` → 201 Created
    - `GET /categories` → `CategoryHandler.listAll()` → 200 OK
    - Anotaciones Springdoc: `@Tag`, `@Operation`, `@ApiResponse`
    - _Requisitos: 5.1, 5.4_
    - _Estándar: §4.2, §D.2_

  - [ ] 12.5 Implementar `ReviewController`
    - `POST /products/{id}/reviews` → `ProductHandler.addReview()` → 200 OK
    - Anotaciones Springdoc: `@Tag`, `@Operation`, `@ApiResponse`
    - _Requisitos: 6.1, 6.4_
    - _Estándar: §4.2, §D.2_

  - [ ] 12.6 Implementar `GlobalExceptionHandler` con `@ControllerAdvice`
    - Manejar `WebExchangeBindException` → 400 con campos inválidos
    - Manejar `DomainException` subclases → HTTP status y código según subclase
    - Manejar `Exception` genérica → 500, log ERROR, mensaje genérico sin detalles internos
    - Retornar `ErrorResponse(code, message)` en todos los casos
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ]\* 12.7 Escribir test de propiedad para estructura de ErrorResponse
    - **Propiedad 18: Respuestas de error tienen estructura y HTTP status correctos** — Generar excepciones de distintos tipos y verificar que la respuesta contiene ErrorResponse con code y message, y el HTTP status correcto
    - **Valida: Requisitos 9.2, 9.3, 9.4, 9.5**

- [ ] 13. Configuración de Spring Boot y cableado de dependencias
  - [ ] 13.1 Configurar `application.yaml` en `app-service`
    - Configuración de MongoDB (catalog_db), Redis (host, port, TTL), Kafka (bootstrap-servers, producer config)
    - Intervalos de schedulers externalizados (sin defaults inline):
      - `scheduler.outbox-relay.interval: 5000`
    - Configuración Springdoc/OpenAPI:
      - `springdoc.api-docs.path: /api-docs`
      - `springdoc.swagger-ui.path: /swagger-ui.html`
      - `springdoc.swagger-ui.enabled: true`
    - Logging con SLF4J, `CommandLineRunner` para log de inicio
    - _Requisitos: 7.7, 8.1_
    - _Estándares: §D.2 (OpenAPI), §D.6 (Schedulers), §D.7 (Logging)_

  - [ ] 13.2 Configurar Spring Profiles (local/docker)
    - Crear `application-local.yaml` con hosts `localhost` y puertos mapeados
    - Crear `application-docker.yaml` con hostnames de contenedores (`arka-mongodb`, `arka-redis`, `arka-kafka`) y puertos internos
    - Configurar `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` en `application.yaml`
    - _Estándar: §B.10 (Spring Profiles)_

  - [ ] 13.3 Configurar beans de inyección de dependencias
    - Registrar use cases, handlers, adapters y ports en la configuración de Spring
    - Crear `OpenApiConfig` con metadata del servicio (`@Bean OpenAPI`)
    - Agregar dependencias en `build.gradle`: jqwik, reactor-test, mockito, `springdoc-openapi-starter-webflux-ui:3.0.2`, `reactor-kafka:1.3.25`
    - _Estándares: §D.2 (OpenAPI), §B.11 (Kafka)_

- [ ] 14. Checkpoint final — Verificar integración completa
  - Asegurar que todos los tests pasan (unitarios y de propiedades), preguntar al usuario si surgen dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests de propiedades validan correctitud universal con jqwik (mínimo 100 iteraciones)
- Los tests unitarios validan ejemplos específicos y edge cases con JUnit 5 + Mockito + StepVerifier
- Todas las entidades usan `record` con `@Builder(toBuilder = true)` según estándares de Arka
