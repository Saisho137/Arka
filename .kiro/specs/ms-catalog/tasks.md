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

  - [ ]* 1.6 Escribir tests de propiedades para validación de entidades de dominio
    - **Propiedad 2: Validación rechaza entrada inválida** — Generar requests con campos faltantes o precio ≤ 0 y verificar que el compact constructor lanza excepción
    - **Valida: Requisitos 1.2, 1.3, 3.3**

  - [ ]* 1.7 Escribir tests de propiedades para reseñas inválidas
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

- [ ] 3. Implementar casos de uso del dominio — Categorías
  - [ ] 3.1 Implementar `CreateCategoryUseCase`
    - Validar unicidad de nombre vía `categoryRepository.findByName()`
    - Si existe, lanzar `DuplicateCategoryException`
    - Persistir categoría con `categoryRepository.save()`
    - _Requisitos: 5.1, 5.2, 5.3_

  - [ ] 3.2 Implementar `ListCategoriesUseCase`
    - Retornar `categoryRepository.findAll()`
    - _Requisitos: 5.4_

  - [ ]* 3.3 Escribir test de propiedad para unicidad de nombre de categoría
    - **Propiedad 12: Unicidad de nombre de categoría** — Generar pares de categorías con mismo nombre y verificar que la segunda creación es rechazada con error de conflicto
    - **Valida: Requisitos 5.2, 5.3**

  - [ ]* 3.4 Escribir tests unitarios para `CreateCategoryUseCase` y `ListCategoriesUseCase`
    - Tests con StepVerifier: creación exitosa, nombre duplicado (409), nombre vacío (400)
    - Mockito para ports
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

- [ ] 4. Implementar casos de uso del dominio — Productos (CRUD + Outbox)
  - [ ] 4.1 Implementar `CreateProductUseCase`
    - Validar unicidad de SKU vía `productRepository.findBySku()`
    - Verificar existencia de categoría vía `categoryRepository.findById()`
    - Persistir producto + insertar `OutboxEvent` (ProductCreated) atómicamente
    - Invalidar caché de lista vía `productCachePort.evictProductListCache()`
    - _Requisitos: 1.1, 1.4, 1.5, 1.6, 1.7, 5.5_

  - [ ]* 4.2 Escribir test de propiedad para round trip de creación
    - **Propiedad 1: Round trip de creación de producto** — Generar productos válidos aleatorios, crear y consultar por ID, verificar que SKU, nombre, precio y categoría coinciden
    - **Valida: Requisitos 1.1, 2.2**

  - [ ]* 4.3 Escribir test de propiedad para unicidad de SKU
    - **Propiedad 3: Unicidad de SKU** — Generar pares de productos con mismo SKU y verificar que la segunda creación es rechazada con error 409
    - **Valida: Requisitos 1.4**

  - [ ]* 4.4 Escribir test de propiedad para categoría inexistente
    - **Propiedad 13: Creación de producto con categoría inexistente es rechazada** — Generar categoryIds aleatorios no existentes y verificar rechazo
    - **Valida: Requisitos 5.5**

  - [ ] 4.5 Implementar `GetProductUseCase` con Cache-Aside
    - Consultar primero `productCachePort.get(key)`
    - En cache miss: consultar `productRepository.findById()`, almacenar en caché con `productCachePort.put(key, product)`
    - Si no existe, lanzar `ProductNotFoundException`
    - _Requisitos: 2.2, 2.3, 2.4, 2.5, 2.6_

  - [ ] 4.6 Implementar `ListProductsUseCase` con Cache-Aside
    - Consultar caché paginada, en miss consultar `productRepository.findAllActive(page, size)` y almacenar
    - Retornar solo productos activos
    - _Requisitos: 2.1, 2.4, 2.5, 2.6_

  - [ ]* 4.7 Escribir test de propiedad para Cache-Aside round trip
    - **Propiedad 7: Round trip de Cache-Aside** — Generar productos, simular miss→hit, verificar que la segunda consulta retorna desde caché sin consultar MongoDB
    - **Valida: Requisitos 2.4, 2.5, 2.6, 8.1, 8.5**

  - [ ]* 4.8 Escribir test de propiedad para listado solo activos
    - **Propiedad 8: Listado retorna solo productos activos** — Generar mezcla de productos activos/inactivos y verificar que el listado solo retorna activos
    - **Valida: Requisitos 2.1, 4.5**

  - [ ] 4.9 Implementar `UpdateProductUseCase`
    - Buscar producto existente, aplicar cambios con `toBuilder()`
    - Insertar `OutboxEvent` (ProductUpdated) atómicamente
    - Si el precio cambió, insertar `OutboxEvent` adicional (PriceChanged) con oldPrice y newPrice
    - Invalidar caché individual y de lista
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [ ]* 4.10 Escribir test de propiedad para actualización preserva producto
    - **Propiedad 9: Actualización preserva producto y refleja cambios** — Generar updates válidos y verificar que el producto retornado tiene los nuevos valores, mismo ID y updatedAt posterior
    - **Valida: Requisitos 3.1**

  - [ ]* 4.11 Escribir test de propiedad para PriceChanged
    - **Propiedad 10: Cambio de precio emite evento PriceChanged adicional** — Generar updates con precio diferente y verificar que se inserta evento PriceChanged con oldPrice y newPrice además de ProductUpdated
    - **Valida: Requisitos 3.5**

  - [ ] 4.12 Implementar `DeactivateProductUseCase`
    - Buscar producto, marcar `active = false` con `toBuilder()`
    - Insertar `OutboxEvent` (ProductUpdated) atómicamente
    - Invalidar caché individual y de lista
    - _Requisitos: 4.1, 4.2, 4.3, 4.4_

  - [ ]* 4.13 Escribir test de propiedad para soft delete
    - **Propiedad 11: Soft delete preserva documento con active=false** — Generar productos activos, desactivar y verificar que el documento sigue existiendo con active=false
    - **Valida: Requisitos 4.1**

  - [ ] 4.14 Implementar `AddReviewUseCase`
    - Verificar existencia de producto
    - Agregar reseña como subdocumento vía `productRepository.addReview()`
    - _Requisitos: 6.1, 6.4, 6.5_

  - [ ]* 4.15 Escribir test de propiedad para agregar reseña
    - **Propiedad 14: Agregar reseña incrementa lista y asigna createdAt** — Generar reseñas válidas, agregar y verificar que la lista incrementa en 1 y createdAt no es nulo
    - **Valida: Requisitos 6.1, 6.5**

  - [ ]* 4.16 Escribir tests de propiedades para eventos outbox e invalidación de caché
    - **Propiedad 4: Operaciones de escritura producen eventos outbox** — Verificar que crear, actualizar y desactivar generan eventos PENDING en outbox
    - **Propiedad 5: Completitud del sobre y payload de eventos** — Verificar campos requeridos del envelope y payload por tipo
    - **Propiedad 6: Escrituras invalidan caché** — Verificar que cada escritura invoca evict en caché individual y de lista
    - **Valida: Requisitos 1.5, 1.6, 1.7, 3.4, 3.6, 4.3, 4.4, 7.1, 7.2, 7.3, 7.6, 8.2**

- [ ] 5. Checkpoint — Verificar dominio y casos de uso
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 6. Implementar driven adapters — MongoDB
  - [ ] 6.1 Implementar `MongoProductAdapter` que implementa `ProductRepository`
    - Usar `ReactiveMongoTemplate` para todas las operaciones
    - Implementar `addReview` con operación `$push` atómica en el array de reviews
    - Implementar `deactivate` con `$set` de `active = false`
    - Implementar `findAllActive` con filtro `active = true` y paginación
    - Crear documentos MongoDB (data classes) y mappers estáticos para convertir entre dominio y documento
    - Configurar índices: `{ sku: 1 }` (unique), `{ "category.id": 1 }`, `{ active: 1 }`
    - _Requisitos: 1.1, 2.1, 2.2, 3.1, 4.1, 6.1_

  - [ ] 6.2 Implementar `MongoCategoryAdapter` que implementa `CategoryRepository`
    - Usar `ReactiveMongoTemplate`
    - Crear documento MongoDB y mapper estático
    - Configurar índice: `{ name: 1 }` (unique)
    - _Requisitos: 5.1, 5.4_

  - [ ] 6.3 Implementar `MongoOutboxAdapter` que implementa `OutboxEventRepository`
    - Usar `ReactiveMongoTemplate`
    - `findPending`: consultar por `status = PENDING` ordenado por `createdAt`
    - `markAsPublished`: actualizar `status` a `PUBLISHED`
    - Configurar índices: `{ status: 1, createdAt: 1 }`, `{ eventId: 1 }` (unique)
    - _Requisitos: 7.1, 7.4_

- [ ] 7. Implementar driven adapter — Redis (Cache-Aside)
  - [ ] 7.1 Implementar `RedisCacheAdapter` que implementa `ProductCachePort`
    - Usar `ReactiveRedisTemplate` con serialización JSON
    - `get(key)`: consultar Redis, retornar `Mono.empty()` en cache miss
    - `put(key, product)`: almacenar con TTL de 1 hora
    - `evict(key)`: eliminar entrada individual
    - `evictProductListCache()`: eliminar todas las claves con patrón `products:page:*`
    - Implementar resiliencia: capturar excepciones de conexión con `onErrorResume()`, log WARN, retornar `Mono.empty()`
    - _Requisitos: 8.1, 8.2, 8.4, 8.5_

  - [ ]* 7.2 Escribir test de propiedad para fallback Redis→MongoDB
    - **Propiedad 17: Fallback a MongoDB cuando Redis no está disponible** — Simular Redis caído y verificar que el sistema continúa operando consultando MongoDB
    - **Valida: Requisitos 8.4**

- [ ] 8. Implementar driven adapter — Kafka Outbox Relay
  - [ ] 8.1 Implementar `KafkaOutboxRelay`
    - Scheduled relay que ejecuta cada 5 segundos (`@Scheduled` o Flux.interval)
    - Consultar eventos PENDING vía `OutboxEventRepository.findPending()`
    - Publicar cada evento a Kafka con `ReactiveKafkaProducer` al tópico `product-events` usando `productId` como partition key
    - Marcar como PUBLISHED tras ack exitoso
    - En caso de fallo, mantener PENDING para reintento en siguiente ciclo
    - Log de errores con SLF4J
    - _Requisitos: 7.3, 7.4, 7.5_

  - [ ]* 8.2 Escribir test de propiedad para transición de estado del relay
    - **Propiedad 16: Transición de estado del relay outbox** — Generar eventos PENDING, simular publicación exitosa/fallida y verificar transición a PUBLISHED o permanencia en PENDING
    - **Valida: Requisitos 7.4, 7.5**

- [ ] 9. Checkpoint — Verificar driven adapters
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 10. Implementar entry points — DTOs, Mappers y Controladores REST
  - [ ] 10.1 Crear DTOs de request y response con Bean Validation
    - `CreateProductRequest`: `@NotBlank` sku, name; `@NotNull @Positive` price; `@NotBlank` categoryId
    - `UpdateProductRequest`: `@NotBlank` name; `@NotNull @Positive` price; `@NotBlank` categoryId
    - `CreateCategoryRequest`: `@NotBlank` name; description opcional
    - `AddReviewRequest`: `@NotBlank` userId, comment; `@NotNull @Min(1) @Max(5)` rating
    - `ProductResponse`, `CategoryResponse`, `ReviewResponse`, `ErrorResponse`
    - Todos con `@Builder(toBuilder = true)`
    - _Requisitos: 1.2, 1.3, 3.3, 5.3, 6.2, 6.3, 9.5_

  - [ ] 10.2 Crear mappers estáticos: `ProductMapper`, `CategoryMapper`, `ReviewMapper`
    - Métodos estáticos para convertir request→comando/dominio y dominio→response
    - Usar `@Builder` al construir objetos destino
    - _Requisitos: 1.1, 2.1, 2.2_

  - [ ] 10.3 Implementar `ProductController`
    - `POST /products` → `CreateProductUseCase` → 201 Created
    - `GET /products` → `ListProductsUseCase` → 200 OK (paginado con query params page, size)
    - `GET /products/{id}` → `GetProductUseCase` → 200 OK
    - `PUT /products/{id}` → `UpdateProductUseCase` → 200 OK
    - `DELETE /products/{id}` → `DeactivateProductUseCase` → 200 OK
    - Usar `@Valid` en requests, retornos `Mono`/`Flux`
    - _Requisitos: 1.1, 2.1, 2.2, 3.1, 4.1_

  - [ ] 10.4 Implementar `CategoryController`
    - `POST /categories` → `CreateCategoryUseCase` → 201 Created
    - `GET /categories` → `ListCategoriesUseCase` → 200 OK
    - _Requisitos: 5.1, 5.4_

  - [ ] 10.5 Implementar `ReviewController`
    - `POST /products/{id}/reviews` → `AddReviewUseCase` → 200 OK
    - _Requisitos: 6.1, 6.4_

  - [ ] 10.6 Implementar `GlobalExceptionHandler` con `@ControllerAdvice`
    - Manejar `WebExchangeBindException` → 400 con campos inválidos
    - Manejar `DomainException` subclases → HTTP status y código según subclase
    - Manejar `Exception` genérica → 500, log ERROR, mensaje genérico sin detalles internos
    - Retornar `ErrorResponse(code, message)` en todos los casos
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ]* 10.7 Escribir test de propiedad para estructura de ErrorResponse
    - **Propiedad 18: Respuestas de error tienen estructura y HTTP status correctos** — Generar excepciones de distintos tipos y verificar que la respuesta contiene ErrorResponse con code y message, y el HTTP status correcto
    - **Valida: Requisitos 9.2, 9.3, 9.4, 9.5**

- [ ] 11. Configuración de Spring Boot y cableado de dependencias
  - [ ] 11.1 Configurar `application.yml` en `app-service`
    - Configuración de MongoDB (catalog_db), Redis (host, port, TTL), Kafka (bootstrap-servers, producer config)
    - Logging con SLF4J
    - `CommandLineRunner` para log de inicio
    - _Requisitos: 8.1_

  - [ ] 11.2 Configurar beans de inyección de dependencias
    - Registrar use cases, adapters y ports en la configuración de Spring
    - Asegurar que los driven adapters implementan los ports correctos
    - Agregar dependencias de jqwik, reactor-test y mockito en `build.gradle` de los módulos de test
    - _Requisitos: 1.1_

- [ ] 12. Checkpoint final — Verificar integración completa
  - Asegurar que todos los tests pasan (unitarios y de propiedades), preguntar al usuario si surgen dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests de propiedades validan correctitud universal con jqwik (mínimo 100 iteraciones)
- Los tests unitarios validan ejemplos específicos y edge cases con JUnit 5 + Mockito + StepVerifier
- Todas las entidades usan `record` con `@Builder(toBuilder = true)` según estándares de Arka
