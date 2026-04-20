# Plan de Implementación: ms-catalog

## Visión General

Implementación incremental del microservicio de Catálogo Maestro de Productos para la plataforma B2B Arka. Se sigue la Clean Architecture del Scaffold Bancolombia 4.2.0 con Java 21, Spring WebFlux reactivo, MongoDB, Redis (Cache-Aside) y Kafka (Outbox Pattern). Cada tarea construye sobre las anteriores.

**REGLA CRÍTICA DE IMPLEMENTACIÓN:** Todos los módulos nuevos (Model, UseCase, Driven Adapter, Entry Point, Helper) DEBEN generarse usando las tareas Gradle del plugin Bancolombia Scaffold. La creación manual de estructura de módulos está PROHIBIDA. Ejecutar siempre desde la raíz de `ms-catalog/`:

```bash
# Generar Model + Gateway interface
./gradlew generateModel --name=<Name>  # o ./gradlew gm --name=<Name>

# Generar UseCase
./gradlew generateUseCase --name=<Name>  # o ./gradlew guc --name=<Name>

# Generar Driven Adapter
./gradlew generateDrivenAdapter --type=<type>  # o ./gradlew gda --type=<type>

# Generar Entry Point
./gradlew generateEntryPoint --type=<type>  # o ./gradlew gep --type=<type>

# Validar estructura
./gradlew validateStructure
```

Ver `.agents/skills/scaffold-tasks/SKILL.md` para referencia completa de comandos y tipos disponibles.

**REUTILIZACIÓN Y VERSIONADO:** Antes de implementar patrones transversales o agregar dependencias, consultar **`.kiro/steering/reusability.md`**. Define los componentes reutilizables de `ms-inventory` (Outbox, Kafka Producer/Consumer, Controller→Handler, GlobalExceptionHandler, Spring Profiles, Springdoc), las versiones exactas de todas las librerías y qué adaptar por dominio.

## Tareas

- [x] 1. Definir entidades de dominio, Value Objects y excepciones
  - [x] 1.1 Crear Value Objects: `SKU`, `CategoryId`, `Money` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateModel --name=ValueObjects`
    - Esto crea automáticamente la estructura en `domain/model/src/main/java/com/arka/valueobjects/` y registra el módulo en `settings.gradle`
    - Crear `SKU` record con validación de valor no nulo y no vacío
    - Crear `CategoryId` record con validación de valor no nulo y no vacío
    - Crear `Money` record con validación de amount >= 0, currency no nulo/vacío, y monedas soportadas (COP, USD, PEN, CLP)
    - Implementar método `isGreaterThan(Money other)` en `Money` para comparar montos de la misma moneda
    - Implementar método `isPositive()` en `Money` para validar que amount > 0
    - Usar `@Builder(toBuilder = true)` en todos los records
    - Paquete: `com.arka.valueobjects`
    - _Requisitos: 1.2, 1.3, 1.4, 1.5_

  - [x] 1.2 Crear los records `Product` y `Review` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateModel --name=Product`
    - Esto crea automáticamente la estructura en `domain/model/src/main/java/com/arka/model/product/` y registra el módulo en `settings.gradle`
    - Reemplazar la clase generada por `Product` record con compact constructor
    - Validaciones en compact constructor:
      - SKU, nombre, categoryId, price no nulos
      - Nombre no vacío
      - Price debe ser positivo (`price.isPositive()`)
      - Si cost no es nulo, price debe ser mayor a cost (`price.isGreaterThan(cost)`)
      - Lista de reviews inmutable (copiar si no es nula, o `List.of()`)
    - Implementar método `addReview(Review newReview)` que retorna nueva instancia con review agregada usando `toBuilder()`
    - Crear `Review` record con validación de userId no nulo/vacío, rating 1-5, comment no nulo/vacío
    - Review debe asignar `reviewId` (UUID) y `createdAt` (Instant.now()) automáticamente si son nulos
    - Usar `@Builder(toBuilder = true)` en ambos records
    - Paquete: `com.arka.model.product`
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 6.1, 6.2, 6.5_

  - [x] 1.3 Crear el record `Category` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateModel --name=Category`
    - Reemplazar la clase generada por `Category` record con compact constructor (validación de nombre no nulo)
    - Usar `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.category`
    - _Requisitos: 5.1, 5.3_

  - [x] 1.4 Crear el record `OutboxEvent` y enum `OutboxStatus` en `domain/model`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateModel --name=OutboxEvent`
    - **OBLIGATORIO:** Seguir `reusability.md` componente **#1** (Outbox Domain Model) para estructura, enums (`EventType`, `OutboxStatus`) y métodos de dominio
    - Reemplazar la clase generada por `OutboxEvent` record con defaults en compact constructor (eventId UUID, status PENDING, createdAt, topic "product-events")
    - Crear enum `OutboxStatus` con valores `PENDING`, `PUBLISHED` en el mismo paquete
    - Paquete: `com.arka.model.outbox`
    - _Requisitos: 7.1, 7.2_

  - [x] 1.5 Crear records de eventos de dominio: `DomainEventEnvelope`, `ProductCreatedPayload`, `ProductUpdatedPayload`, `PriceChangedPayload`
    - `DomainEventEnvelope` con campos: eventId, eventType, timestamp, source, correlationId, payload
    - **OBLIGATORIO (reusability.md #1):** Copiar `ms-inventory/domain/model/outboxevent/DomainEventEnvelope.java` — incluir constante `public static final String MS_SOURCE = "ms-catalog"` y defaults en compact constructor
    - Payloads específicos para cada tipo de evento (incluyendo cost, price, currency en payloads de productos)
    - Paquete: `com.arka.model.outbox`
    - _Requisitos: 1.8, 7.2, 7.3, 7.6_

  - [x] 1.6 Crear jerarquía de excepciones de dominio
    - Crear `DomainException` abstracta con `getHttpStatus()` y `getCode()`
    - Crear subclases: `ProductNotFoundException` (404), `DuplicateSkuException` (409), `CategoryNotFoundException` (400), `DuplicateCategoryException` (409), `InvalidReviewException` (400), `InvalidPriceException` (400), `InvalidCurrencyException` (400)
    - Paquete: `com.arka.model.commons.exception` o dentro de cada agregado según corresponda
    - _Requisitos: 9.1, 9.2, 9.3_

- [x] 2. Definir ports (gateway interfaces)
  - [x] 2.1 Crear interfaz `ProductRepository` en `domain/model/product/gateways`
    - Métodos: `save`, `findById`, `findBySku`, `findAllActive(page, size)`, `update`, `deactivate`, `addReview`
    - Todos retornan `Mono<Product>` o `Flux<Product>`
    - _Requisitos: 1.1, 2.1, 2.2, 3.1, 4.1, 6.1_

  - [x] 2.2 Crear interfaz `CategoryRepository` en `domain/model/category/gateways`
    - Métodos: `save`, `findById`, `findByName`, `findAll`
    - _Requisitos: 5.1, 5.4_

  - [x] 2.3 Crear interfaz `OutboxEventRepository` en `domain/model/outbox/gateways`
    - Métodos: `save`, `findPending`, `markAsPublished`
    - _Requisitos: 7.1, 7.4_

  - [x] 2.4 Crear interfaz `ProductCachePort` en `domain/model/product/gateways`
    - Métodos: `get(key)`, `put(key, product)`, `evict(key)`, `evictProductListCache()`
    - _Requisitos: 2.4, 8.1, 8.2_

- [x] 3. Implementar `CategoryUseCase` — Gestión de categorías
  - [x] 3.1 Generar `CategoryUseCase` con Scaffold e implementar métodos
    - **CRÍTICO**: Generar con Scaffold: `cd ms-catalog && ./gradlew generateUseCase --name=Category`
    - Esto crea automáticamente `domain/usecase/src/main/java/com/arka/usecase/category/CategoryUseCase.java` y registra el módulo en `settings.gradle`
    - Implementar `create(cmd)`: validar nombre único vía `categoryRepository.findByName()`, si existe lanzar `DuplicateCategoryException`, persistir con `categoryRepository.save()`
    - Implementar `listAll()`: retornar `categoryRepository.findAll()`
    - Inyectar dependencia: `CategoryRepository`
    - _Requisitos: 5.1, 5.2, 5.3, 5.4_

- [x] 4. Implementar `ProductUseCase` — CRUD completo + Outbox + Cache
  - [x] 4.1 Generar `ProductUseCase` con Scaffold e implementar métodos
    - **CRÍTICO**: Generar con Scaffold: `cd ms-catalog && ./gradlew generateUseCase --name=Product`
    - Esto crea automáticamente `domain/usecase/src/main/java/com/arka/usecase/product/ProductUseCase.java` y registra el módulo en `settings.gradle`
    - Implementar `create(cmd)`:
      - Validar SKU único vía `productRepository.findBySku()`
      - Verificar categoría vía `categoryRepository.findById()`
      - Validar que price > cost usando `Money.isGreaterThan()`
      - Validar moneda soportada (COP, USD, PEN, CLP)
      - Persistir + insertar `OutboxEvent` (ProductCreated con cost, price, currency) atómicamente
      - Invalidar caché de lista vía `productCachePort.evictProductListCache()`
    - Implementar `getById(id)`: Cache-Aside (consultar `productCachePort.get(key)`, en miss consultar `productRepository.findById()` y almacenar en caché con `productCachePort.put(key, product)`, si no existe lanzar `ProductNotFoundException`)
    - Implementar `listActive(page, size)`: Cache-Aside paginado (consultar caché, en miss consultar `productRepository.findAllActive(page, size)` y almacenar, retornar solo productos activos)
    - Implementar `update(id, cmd)`:
      - Buscar producto
      - Validar que price > cost usando `Money.isGreaterThan()`
      - Aplicar cambios con `toBuilder()`
      - Insertar `OutboxEvent` (ProductUpdated con cost, price, currency) atómicamente
      - Si precio cambió insertar `OutboxEvent` adicional (PriceChanged con oldPrice, newPrice, currency)
      - Invalidar caché individual y de lista
    - Implementar `deactivate(id)`: buscar producto, marcar `active = false` con `toBuilder()`, insertar `OutboxEvent` (ProductUpdated) atómicamente, invalidar caché individual y de lista
    - Implementar `addReview(productId, review)`: verificar existencia de producto, agregar reseña como subdocumento vía `productRepository.addReview()`
    - Crear `JsonSerializer` interfaz funcional (port para serialización de payloads)
    - Inyectar dependencias: `ProductRepository`, `CategoryRepository`, `OutboxEventRepository`, `ProductCachePort`, `JsonSerializer`
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 2.1, 2.2, 2.4, 2.5, 2.6, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 4.1, 4.2, 4.3, 4.4, 6.1, 6.4, 6.5_

- [x] 5. Implementar `OutboxRelayUseCase` — Lógica de relay
  - [x] 5.1 Generar `OutboxRelayUseCase` con Scaffold e implementar métodos
    - **CRÍTICO**: Generar con Scaffold: `cd ms-catalog && ./gradlew generateUseCase --name=OutboxRelay`
    - **OBLIGATORIO:** Seguir `reusability.md` componente **#2** (OutboxRelayUseCase) — copiar lógica de ms-inventory
    - Esto crea automáticamente `domain/usecase/src/main/java/com/arka/usecase/outboxrelay/OutboxRelayUseCase.java` y registra el módulo en `settings.gradle`
    - Implementar `fetchPendingEvents()`: consultar eventos PENDING vía `outboxEventRepository.findPending()`
    - Implementar `markAsPublished(event)`: actualizar status a PUBLISHED vía `outboxEventRepository.markAsPublished()`
    - Inyectar dependencia: `OutboxEventRepository`
    - _Requisitos: 7.4, 7.5_

- [x] 6. Checkpoint — Verificar dominio y casos de uso
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [x] 7. Implementar Handlers — Patrón Controller → Handler → UseCase
  - [x] 7.1 Implementar `ProductHandler`
    - **OBLIGATORIO:** Seguir `reusability.md` componente **#7** (Controller → Handler → UseCase)
    - Crear `@Component` con métodos para cada operación
    - `create(request)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - `getById(id)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - `listActive(page, size)`: UseCase → Mapper → `Flux<ProductResponse>` (sin `collectList()`)
    - `update(id, request)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - `deactivate(id)`: UseCase → Mapper → `Mono<ResponseEntity<Void>>`
    - `addReview(productId, request)`: UseCase → Mapper → `Mono<ResponseEntity<ProductResponse>>`
    - Inyectar dependencia: `ProductUseCase`
    - _Estándar: §4.2 (Controller → Handler → UseCase)_

  - [x] 7.2 Implementar `CategoryHandler`
    - **OBLIGATORIO:** Seguir `reusability.md` componente **#7** (Controller → Handler → UseCase)
    - Crear `@Component` con métodos para cada operación
    - `create(request)`: UseCase → Mapper → `Mono<ResponseEntity<CategoryResponse>>`
    - `listAll()`: UseCase → Mapper → `Flux<CategoryResponse>`
    - Inyectar dependencia: `CategoryUseCase`
    - _Estándar: §4.2_

- [x] 8. Implementar driven adapters — MongoDB
  - [x] 8.1 Implementar `MongoProductAdapter` que implementa `ProductRepository`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateDrivenAdapter --type=mongodb`
    - Esto crea automáticamente la estructura en `infrastructure/driven-adapters/mongo-repository/` y registra el módulo en `settings.gradle`
    - Renombrar el adapter generado a `MongoProductAdapter` y configurar para implementar `ProductRepository`
    - Usar `ReactiveMongoTemplate` para todas las operaciones
    - Implementar `addReview` con operación `$push` atómica en el array de reviews
    - Implementar `deactivate` con `$set` de `active = false`
    - Implementar `findAllActive` con filtro `active = true` y paginación
    - Crear documentos MongoDB (data classes) con campos: sku (String), name, description, cost (BigDecimal), price (BigDecimal), currency (String), categoryId (String), active, reviews (array de subdocumentos con reviewId, userId, rating, comment, createdAt)
    - Crear mappers estáticos para convertir entre dominio (Product con VOs SKU, Money, CategoryId) y documento MongoDB
    - Configurar índices: `{ sku: 1 }` (unique), `{ categoryId: 1 }`, `{ active: 1 }`
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 3.1, 4.1, 6.1_

  - [x] 8.2 Implementar `MongoCategoryAdapter` que implementa `CategoryRepository`
    - **NOTA**: Reutilizar el mismo módulo `mongo-repository` generado en 8.1
    - Crear `MongoCategoryAdapter` en el mismo módulo para implementar `CategoryRepository`
    - Usar `ReactiveMongoTemplate`
    - Crear documento MongoDB y mapper estático
    - Configurar índice: `{ name: 1 }` (unique)
    - _Requisitos: 5.1, 5.4_

  - [x] 8.3 Implementar `MongoOutboxAdapter` que implementa `OutboxEventRepository`
    - **NOTA**: Reutilizar el mismo módulo `mongo-repository` generado en 8.1
    - Crear `MongoOutboxAdapter` en el mismo módulo para implementar `OutboxEventRepository`
    - Usar `ReactiveMongoTemplate`
    - `findPending`: consultar por `status = PENDING` ordenado por `createdAt`
    - `markAsPublished`: actualizar `status` a `PUBLISHED`
    - Configurar índices: `{ status: 1, createdAt: 1 }`, `{ eventId: 1 }` (unique)
    - _Requisitos: 7.1, 7.4_

- [ ] 9. Implementar driven adapter — Redis (Cache-Aside)
  - [ ] 9.1 Implementar `RedisCacheAdapter` que implementa `ProductCachePort`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateDrivenAdapter --type=redis --mode=template`
    - Esto crea automáticamente la estructura en `infrastructure/driven-adapters/redis/` con `ReactiveRedisTemplate` y registra el módulo en `settings.gradle`
    - Renombrar el adapter generado a `RedisCacheAdapter` y configurar para implementar `ProductCachePort`
    - Usar `ReactiveRedisTemplate` con serialización JSON
    - `get(key)`: consultar Redis, retornar `Mono.empty()` en cache miss
    - `put(key, product)`: almacenar con TTL de 1 hora
    - `evict(key)`: eliminar entrada individual
    - `evictProductListCache()`: eliminar todas las claves con patrón `products:page:*`
    - Implementar resiliencia: capturar excepciones de conexión con `onErrorResume()`, log WARN, retornar `Mono.empty()`
    - _Requisitos: 8.1, 8.2, 8.4, 8.5_

- [ ] 10. Implementar driven adapter — Kafka Outbox Relay con `reactor-kafka`
  - [ ] 10.1 Crear módulo `kafka-producer` en `infrastructure/driven-adapters/`
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateDrivenAdapter --type=generic --name=kafka-producer`
    - Agregar dependencias: `reactor-kafka:1.3.25`, `spring-kafka`, `jackson-databind` (mismas versiones que `ms-inventory/infrastructure/driven-adapters/kafka-producer/build.gradle`)
    - **OBLIGATORIO (reusability.md #5):** Copiar y adaptar los 2 archivos de `ms-inventory/infrastructure/driven-adapters/kafka-producer/src/main/java/com/arka/kafka/`:
      - `KafkaProducerConfig.java` → copiar tal cual (bean `KafkaSender<String, String>`, acks=all, retries=3)
      - `KafkaOutboxRelay.java` → copiar y adaptar: `TOPIC = "product-events"`, `DomainEventEnvelope.MS_SOURCE = "ms-catalog"`, inyectar `OutboxRelayUseCase`
    - _Estándar: §B.11 (Kafka con reactor-kafka directo)_

  - [ ] 10.2 Implementar `KafkaOutboxRelay` (ya copiado en 10.1)
    - Adaptar la copia de `ms-inventory/.../KafkaOutboxRelay.java`:
      - `TOPIC = "product-events"` (era `"inventory-events"`)
      - `DomainEventEnvelope.MS_SOURCE` → constante del `DomainEventEnvelope` de ms-catalog
      - El patrón `relay() → fetchPendingEvents → publishAndMark → buildEnvelopeJson → send` es idéntico
      - `@Scheduled(fixedDelayString = "${scheduler.outbox-relay.interval}")` — sin default inline
      - `onErrorResume` mantiene PENDING para reintento — idéntico a ms-inventory
    - _Requisitos: 7.3, 7.4, 7.5, 7.7_
    - _Estándar: §B.11, §D.6 (Schedulers externalizados)_

  - [ ] 10.3 Implementar `KafkaProducerConfig` (ya copiado en 10.1)
    - Copiar tal cual de `ms-inventory/.../KafkaProducerConfig.java` — no requiere cambios
    - Bean `KafkaSender<String, String>`: `ACKS_CONFIG = "all"`, `RETRIES_CONFIG = 3`
    - _Estándar: §B.11_

- [ ] 11. Checkpoint — Verificar driven adapters
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 12. Implementar entry points — DTOs, Mappers y Controladores REST
  - [ ] 12.1 Crear DTOs de request y response con Bean Validation
    - **CRÍTICO**: Generar módulo con Scaffold: `cd ms-catalog && ./gradlew generateEntryPoint --type=webflux --router=false`
    - Esto crea automáticamente la estructura en `infrastructure/entry-points/reactive-web/` con controladores REST y registra el módulo en `settings.gradle`
    - Crear DTOs en el paquete de DTOs del módulo generado:
    - `CreateProductRequest`: `@NotBlank` sku, name, currency; `@NotNull @Positive` cost, price; `@NotBlank` categoryId
    - `UpdateProductRequest`: `@NotBlank` name, currency; `@NotNull @Positive` cost, price; `@NotBlank` categoryId
    - `CreateCategoryRequest`: `@NotBlank` name; description opcional
    - `AddReviewRequest`: `@NotBlank` userId, comment; `@NotNull @Min(1) @Max(5)` rating
    - `ProductResponse` (con cost, price, currency, categoryId, categoryName separados), `CategoryResponse`, `ReviewResponse` (con reviewId), `ErrorResponse`
    - Todos con `@Builder(toBuilder = true)`
    - _Requisitos: 1.2, 1.3, 1.4, 1.5, 3.3, 5.3, 6.2, 6.3, 6.5, 9.5_

  - [ ] 12.2 Crear mappers estáticos: `ProductMapper`, `CategoryMapper`, `ReviewMapper`
    - Crear en el paquete de mappers del módulo `reactive-web` generado en 12.1
    - Métodos estáticos para convertir request→comando/dominio y dominio→response
    - Usar `@Builder` al construir objetos destino
    - _Requisitos: 1.1, 2.1, 2.2_

  - [ ] 12.3 Implementar `ProductController`
    - **OBLIGATORIO:** Seguir `reusability.md` componentes **#7** (Controller → Handler) y **#10** (Springdoc/OpenAPI)
    - Crear en el módulo `reactive-web` generado en 12.1, reemplazando el controlador de ejemplo
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
    - **OBLIGATORIO:** Seguir `reusability.md` componentes **#7** (Controller → Handler) y **#10** (Springdoc/OpenAPI)
    - Crear en el módulo `reactive-web` generado en 12.1
    - `POST /categories` → `CategoryHandler.create()` → 201 Created
    - `GET /categories` → `CategoryHandler.listAll()` → 200 OK
    - Anotaciones Springdoc: `@Tag`, `@Operation`, `@ApiResponse`
    - _Requisitos: 5.1, 5.4_
    - _Estándar: §4.2, §D.2_

  - [ ] 12.5 Implementar `ReviewController`
    - **OBLIGATORIO:** Seguir `reusability.md` componentes **#7** (Controller → Handler) y **#10** (Springdoc/OpenAPI)
    - Crear en el módulo `reactive-web` generado en 12.1
    - `POST /products/{id}/reviews` → `ProductHandler.addReview()` → 200 OK
    - Anotaciones Springdoc: `@Tag`, `@Operation`, `@ApiResponse`
    - _Requisitos: 6.1, 6.4_
    - _Estándar: §4.2, §D.2_

  - [ ] 12.6 Implementar `GlobalExceptionHandler` con `@ControllerAdvice`
    - **OBLIGATORIO (reusability.md #8):** Copiar de `ms-inventory` y adaptar subclases de `DomainException`
    - Crear en el módulo `reactive-web` generado en 12.1
    - Manejar `WebExchangeBindException` → 400 con campos inválidos
    - Manejar `DomainException` subclases → HTTP status y código según subclase
    - Manejar `Exception` genérica → 500, log ERROR, mensaje genérico sin detalles internos
    - Retornar `ErrorResponse(code, message)` en todos los casos
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5_

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
    - **OBLIGATORIO:** Seguir `reusability.md` componente **#9** (Spring Profiles)
    - Crear `application-local.yaml` con hosts `localhost` y puertos mapeados
    - Crear `application-docker.yaml` con hostnames de contenedores (`arka-mongodb`, `arka-redis`, `arka-kafka`) y puertos internos
    - Configurar `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` en `application.yaml`
    - _Estándar: §B.10 (Spring Profiles)_

  - [ ] 13.3 Configurar beans de inyección de dependencias
    - **OBLIGATORIO:** Seguir `reusability.md` componente **#10** (Springdoc/OpenAPI) y tabla de **Versionado Unificado** para todas las dependencias
    - Registrar use cases, handlers, adapters y ports en la configuración de Spring
    - Crear `OpenApiConfig` con metadata del servicio (`@Bean OpenAPI`)
    - Agregar dependencias en `build.gradle`: reactor-test, mockito, `springdoc-openapi-starter-webflux-ui:3.0.2`, `reactor-kafka:1.3.25`
    - _Estándares: §D.2 (OpenAPI), §B.11 (Kafka)_

- [ ] 14. Checkpoint final — Verificar integración completa
  - Asegurar que todos los tests pasan (unitarios), preguntar al usuario si surgen dudas.

## Notas

- **CRÍTICO**: Todos los módulos DEBEN generarse con el plugin Scaffold de Bancolombia. La creación manual está PROHIBIDA.
- Después de cada generación con Scaffold, ejecutar `./gradlew validateStructure` para verificar la arquitectura.
- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests unitarios validan ejemplos específicos y edge cases con JUnit 5 + Mockito + StepVerifier
- Todas las entidades usan `record` con `@Builder(toBuilder = true)` según estándares de Arka
- Para patrones transversales y versiones de dependencias, consultar `.kiro/steering/reusability.md`
