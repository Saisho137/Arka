# Implementation Plan: ms-cart

## Overview

Implementación incremental del microservicio de Gestión de Carritos de Compra para la plataforma B2B Arka. Se sigue la Clean Architecture del Scaffold Bancolombia 4.2.0 con Java 21, Spring WebFlux reactivo, MongoDB (Reactive Mongo Drivers), Apache Kafka (sin Outbox Pattern) y gRPC client para consultar precios a ms-catalog. El objetivo principal es gestionar carritos temporales de clientes B2B, detectar abandono automáticamente mediante un scheduler periódico, y validar precios en tiempo real durante el checkout. Cada tarea construye sobre las anteriores, integrando tests unitarios (JUnit 5 + StepVerifier) como subtareas cercanas a la implementación. Los UseCases se organizan por entidad de dominio (1 UseCase por entidad con múltiples métodos), no por operación individual.

## Tasks

- [x] 1. Configurar proyecto base con Scaffold Plugin
  - [x] 1.1 Generar estructura base del proyecto ms-cart
    - Ejecutar `./gradlew cleanArchitecture --package=com.arka --type=reactive` desde el directorio raíz del monorepo
    - Configurar `gradle.properties` con `reactive=true`, `package=com.arka`, `systemProp.version=4.2.0`
    - Verificar que se generan los módulos: `domain/model`, `domain/usecase`, `infrastructure/driven-adapters`, `infrastructure/entry-points`, `applications/app-service`
    - _Requisitos: Todos (estructura base)_

  - [x] 1.2 Configurar dependencias de MongoDB Reactive en build.gradle
    - Agregar `spring-boot-starter-data-mongodb-reactive` en `main.gradle`
    - Agregar `reactor-test` para testing reactivo
    - Agregar `de.flapdoodle.embed:de.flapdoodle.embed.mongo` para tests embebidos
    - Versiones según `reusability.md` (Spring BOM para starters)
    - _Requisitos: Todos (MongoDB como base de datos)_

  - [x] 1.3 Configurar dependencias de Kafka Producer (reactor-kafka)
    - Agregar `io.projectreactor.kafka:reactor-kafka:1.3.25` en `main.gradle`
    - Agregar `spring-kafka-test` para testing
    - _Requisitos: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

  - [x] 1.4 Configurar dependencias de gRPC client
    - Agregar `grpc-netty-shaded`, `grpc-protobuf`, `grpc-stub` en `main.gradle`
    - Configurar plugin `com.google.protobuf` para generación de código
    - Crear directorio `src/main/proto/` para archivos `.proto`
    - _Requisitos: 3.1, 8.1_

  - [x] 1.5 Configurar Springdoc OpenAPI
    - Agregar `org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2` en `main.gradle`
    - _Requisitos: Documentación de API_

- [x] 2. Definir entidades de dominio, Value Objects, enums y excepciones
  - [x] 2.1 Crear el record `Cart` en `domain/model`
    - Crear `Cart` record con compact constructor: validación de `customerId` no nulo, `status` no nulo, `createdAt` y `lastModifiedAt` no nulos, `items` inicializado como lista inmutable (List.copyOf o List.of si null)
    - Métodos de consulta: `isEmpty()`, `isCheckedOut()`, `isAbandoned()`, `calculateTotal()` (suma de unitPrice \* quantity de todos los items)
    - Usar `@Builder(toBuilder = true)`
    - Paquete: `com.arka.model.cart`
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 14.1_

  - [x] 2.2 Crear el record `CartItem` en `domain/model`
    - Crear `CartItem` record con compact constructor: validación de `sku`, `productName`, `unitPrice` y `addedAt` no nulos, `quantity > 0`, `unitPrice >= 0`
    - Usar `@Builder`
    - Paquete: `com.arka.model.cart`
    - _Requisitos: 3.1, 3.9, 8.2_

  - [x] 2.3 Crear el enum `CartStatus` en `domain/model`
    - Crear enum `CartStatus` con valores: `ACTIVE`, `ABANDONED`, `CHECKED_OUT`
    - Paquete: `com.arka.model.cart`
    - _Requisitos: 1.1, 3.4, 3.5, 9.2, 9.3_

  - [x] 2.4 Crear records de checkout en `domain/model`
    - Crear `CheckoutResponse` record con campos: `cartId`, `priceChanges` (List<PriceChange>), `totalAmount`, `status` (CheckoutStatus)
    - Crear `PriceChange` record con campos: `sku`, `oldPrice`, `newPrice`
    - Crear enum `CheckoutStatus` con valores: `READY`, `PRICE_CHANGED`
    - Usar `@Builder` en los records
    - Paquete: `com.arka.model.cart`
    - _Requisitos: 8.3, 8.4, 8.8_

  - [x] 2.5 Crear records de eventos de dominio en `domain/model`
    - Crear `CartAbandonedEvent` record con campos: `cartId`, `customerId`, `itemCount`, `totalAmount`, `abandonedAt`, `lastModifiedAt`
    - Crear `DomainEventEnvelope` record con campos: `eventId`, `eventType`, `timestamp`, `source` (constante "ms-cart"), `correlationId`, `payload`
    - Usar `@Builder` en los records
    - Paquete: `com.arka.model.event`
    - _Requisitos: 9.4, 9.5, 10.2, 10.3_

  - [x] 2.6 Crear jerarquía de excepciones de dominio
    - Crear `DomainException` abstracta con `getHttpStatus()` y `getCode()`
    - Crear subclases: `CartNotFoundException` (404, CART_NOT_FOUND), `CartAlreadyCheckedOutException` (409, CART_ALREADY_CHECKED_OUT), `ProductNotFoundException` (404, PRODUCT_NOT_FOUND), `CartItemNotFoundException` (404, CART_ITEM_NOT_FOUND), `EmptyCartException` (400, EMPTY_CART), `CatalogServiceUnavailableException` (503, CATALOG_SERVICE_UNAVAILABLE)
    - Paquete: `com.arka.model.commons.exception`
    - _Requisitos: 2.2, 3.3, 4.2, 4.3, 5.3, 6.2, 7.2, 8.5, 8.6, 8.7, 12.3, 12.4_

  - [ ]\* 2.7 Escribir tests unitarios para entidades de dominio
    - Test para `Cart`: validación de campos no nulos, `isEmpty()`, `isCheckedOut()`, `isAbandoned()`, `calculateTotal()`
    - Test para `CartItem`: validación de campos no nulos, `quantity > 0`, `unitPrice >= 0`
    - Test para transiciones de estado de `CartStatus`
    - _Requisitos: 1.1, 3.1_

- [x] 3. Definir ports (gateway interfaces)
  - [x] 3.1 Crear interfaz `CartRepository` en `domain/model/cart/gateways`
    - Métodos: `save(Cart)`, `findById(UUID)`, `findByIdAndCustomerId(UUID, String)`, `findByCustomerId(String)`, `findByCustomerIdAndStatus(String, CartStatus)`, `addItem(UUID, CartItem)`, `updateItemQuantity(UUID, String, int)`, `removeItem(UUID, String)`, `clearItems(UUID)`, `deleteById(UUID)`, `markAsCheckedOut(UUID)`, `findAbandonedCarts(Instant)`, `markAsAbandoned(UUID)`
    - Retornos: `Mono<Cart>`, `Flux<Cart>`, `Mono<Void>`
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 9.2, 14.1_

  - [x] 3.2 Crear interfaz `ProductPriceGateway` en `domain/model/cart/gateways`
    - Método: `getProductInfo(String sku)` retorna `Mono<ProductInfo>`
    - Crear record `ProductInfo` con campos: `sku`, `name`, `price`
    - Usar `@Builder` en el record
    - Paquete: `com.arka.model.cart.gateways`
    - _Requisitos: 3.1, 8.1, 8.2_

  - [x] 3.3 Crear interfaz `CartEventPublisher` en `domain/model/cart/gateways`
    - Método: `publishCartAbandoned(CartAbandonedEvent)` retorna `Mono<Void>`
    - Paquete: `com.arka.model.cart.gateways`
    - _Requisitos: 9.4, 10.1, 10.3_

- [x] 4. Implementar `CartUseCase` — operaciones CRUD y checkout
  - [x] 4.1 Generar `CartUseCase` con Scaffold e implementar métodos de consulta
    - Generar con `./gradlew generateUseCase --name=Cart`
    - Implementar `createCart(String customerId)`: crear carrito vacío con status ACTIVE, items vacío, createdAt y lastModifiedAt igual a NOW(), guardar en repository
    - Implementar `getCart(UUID cartId, String customerId, boolean isAdmin)`: si admin, buscar por ID; si no, buscar por ID y customerId; lanzar `CartNotFoundException` si no existe
    - Implementar `getCartsByCustomer(String customerId, CartStatus status, boolean isAdmin)`: filtrar por customerId y opcionalmente por status, ordenar por lastModifiedAt descendente
    - Inyectar dependencias: `CartRepository`, `ProductPriceGateway`, `CartEventPublisher`
    - _Requisitos: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6, 14.7_

  - [x] 4.2 Implementar método `addItem(UUID cartId, String sku, int quantity, String customerId)` en `CartUseCase`
    - Buscar carrito por ID y customerId, lanzar `CartNotFoundException` si no existe
    - Si carrito está CHECKED_OUT, lanzar `CartAlreadyCheckedOutException`
    - Consultar precio y nombre del producto a `ProductPriceGateway` (gRPC a ms-catalog)
    - Si producto no existe, lanzar `ProductNotFoundException`
    - Crear `CartItem` con precio y nombre del gRPC, addedAt = NOW()
    - Agregar item al carrito usando `CartRepository.addItem()` (operador $push o $inc si SKU ya existe)
    - Si carrito estaba ABANDONED, cambiar status a ACTIVE
    - Retornar carrito actualizado
    - _Requisitos: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10_

  - [x] 4.3 Implementar métodos de modificación de items en `CartUseCase`
    - Implementar `removeItem(UUID cartId, String sku, String customerId)`: buscar carrito, validar no CHECKED_OUT, verificar que item existe, eliminar con `CartRepository.removeItem()` (operador $pull)
    - Implementar `updateItemQuantity(UUID cartId, String sku, int newQuantity, String customerId)`: buscar carrito, validar no CHECKED_OUT, verificar que item existe, actualizar con `CartRepository.updateItemQuantity()` (operador $set con filtro posicional)
    - Implementar `clearCart(UUID cartId, String customerId)`: buscar carrito, validar no CHECKED_OUT, vaciar items con `CartRepository.clearItems()` (operador $set con lista vacía)
    - Implementar `deleteCart(UUID cartId, String customerId, boolean isAdmin)`: buscar carrito (por ID si admin, por ID y customerId si no), eliminar con `CartRepository.deleteById()`
    - _Requisitos: 4.1, 4.2, 4.3, 5.1, 5.2, 5.3, 5.4, 5.5, 6.1, 6.2, 6.3, 7.1, 7.2, 7.3, 7.4, 7.5_

  - [x] 4.4 Implementar método `checkout(UUID cartId, String customerId)` en `CartUseCase`
    - Buscar carrito por ID y customerId, lanzar `CartNotFoundException` si no existe
    - Si carrito está vacío, lanzar `EmptyCartException`
    - Si carrito está CHECKED_OUT, lanzar `CartAlreadyCheckedOutException`
    - Para cada item del carrito, consultar precio actual a `ProductPriceGateway` (gRPC a ms-catalog)
    - Si algún SKU no existe en ms-catalog, lanzar `ProductNotFoundException`
    - Comparar `unitPrice` almacenado vs precio actual retornado por gRPC
    - Si todos los precios coinciden: marcar carrito como CHECKED_OUT con `CartRepository.markAsCheckedOut()`, retornar `CheckoutResponse` con status READY y priceChanges vacío
    - Si hay diferencias de precio: retornar `CheckoutResponse` con status PRICE_CHANGED y lista de cambios, SIN marcar como CHECKED_OUT
    - Calcular `totalAmount` con precios actuales en ambos casos
    - _Requisitos: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8, 8.9_

  - [x] 4.5 Implementar método `detectAbandonedCarts(Duration threshold)` en `CartUseCase`
    - Calcular `cutoffTime = Instant.now().minus(threshold)`
    - Consultar carritos abandonados con `CartRepository.findAbandonedCarts(cutoffTime)`
    - Para cada carrito: marcar como ABANDONED con `CartRepository.markAsAbandoned()`, crear `CartAbandonedEvent`, publicar con `CartEventPublisher.publishCartAbandoned()`
    - Procesar cada carrito de forma independiente con `onErrorResume()` para que el fallo en uno no afecte a los demás
    - Retornar `Mono<Integer>` con cantidad de carritos procesados
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8_

  - [ ]\* 4.6 Escribir tests unitarios para `CartUseCase`
    - Test para `createCart`: verificar que se crea con status ACTIVE, items vacío, timestamps correctos
    - Test para `getCart`: verificar que retorna carrito correcto, lanza excepción si no existe o no pertenece al usuario
    - Test para `addItem`: verificar que consulta precio a gRPC, agrega item, actualiza lastModifiedAt, maneja duplicados con incremento de quantity
    - Test para `removeItem`, `updateItemQuantity`, `clearCart`: verificar validaciones y operaciones correctas
    - Test para `checkout`: verificar comparación de precios, transición a CHECKED_OUT solo si precios coinciden
    - Test para `detectAbandonedCarts`: verificar que marca como ABANDONED y publica eventos
    - Usar Mockito para mocks de repositories y gateways, StepVerifier para verificación de publishers
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 9.1_

- [x] 5. Checkpoint — Verificar dominio y casos de uso core
  - Asegurar que todos los tests pasan. BUILD SUCCESSFUL.

- [x] 6. Implementar driven adapter — MongoDB Repository
  - [x] 6.1 Generar módulo MongoDB con Scaffold
    - Ejecutar `./gradlew generateDrivenAdapter --type=mongodb` desde `ms-cart/`
    - Verificar que se crea el módulo en `infrastructure/driven-adapters/mongo-repository/`
    - _Requisitos: Todos (MongoDB como base de datos)_

  - [x] 6.2 Crear `CartDocument` y `CartItemDocument` (MongoDB DTOs)
    - Crear `CartDocument` con anotación `@Document(collection = "carts")`, campos: `id` (UUID con @Id), `customerId`, `items` (List<CartItemDocument>), `status` (String), `createdAt`, `lastModifiedAt`
    - Crear `CartItemDocument` con campos: `sku`, `productName`, `quantity`, `unitPrice`, `addedAt`
    - Usar Lombok `@Data` y `@Builder` en ambos
    - Crear método estático `fromDomain(CartItem)` en `CartItemDocument`
    - Paquete: `com.arka.mongo.cart`
    - _Requisitos: 1.1, 3.1_

  - [x] 6.3 Crear `CartMapper` para conversión entre dominio y documento
    - Crear clase `CartMapper` con métodos estáticos: `toDocument(Cart)` y `toDomain(CartDocument)`
    - Método privado `toCartItem(CartItemDocument)` para mapear items
    - Usar `@NoArgsConstructor(access = AccessLevel.PRIVATE)` y `final` en la clase
    - Paquete: `com.arka.mongo.cart`
    - _Requisitos: 1.1, 2.1_

  - [x] 6.4 Implementar `MongoCartAdapter` que implementa `CartRepository`
    - Inyectar `ReactiveMongoTemplate` como dependencia
    - Implementar `save()`: convertir a documento, guardar con `mongoTemplate.save()`, convertir a dominio
    - Implementar `findById()`: buscar por ID con `mongoTemplate.findById()`, mapear a dominio
    - Implementar `findByIdAndCustomerId()`: crear Query con criterios `_id` y `customerId`, ejecutar con `mongoTemplate.findOne()`
    - Implementar `findByCustomerId()` y `findByCustomerIdAndStatus()`: crear Query con criterios, ordenar por `lastModifiedAt` descendente, ejecutar con `mongoTemplate.find()`
    - Implementar `deleteById()`: crear Query, ejecutar `mongoTemplate.remove()`, retornar `Mono<Void>`
    - Paquete: `com.arka.mongo.cart`
    - _Requisitos: 1.1, 2.1, 7.1, 14.1, 14.2, 14.3, 14.4_

  - [x] 6.5 Implementar operaciones atómicas de mutación en `MongoCartAdapter`
    - Implementar `addItem()`: verificar si SKU existe con `mongoTemplate.exists()`, si existe usar operador `$inc` para incrementar quantity, si no usar operador `$push` para agregar item, actualizar `lastModifiedAt` y cambiar status a ACTIVE si estaba ABANDONED, usar `findAndModify` con `returnNew(true)`
    - Implementar `updateItemQuantity()`: crear Query con filtro posicional `items.$.sku`, usar operador `$set` para actualizar quantity, actualizar `lastModifiedAt`, usar `findAndModify` con `returnNew(true)`
    - Implementar `removeItem()`: crear Query, usar operador `$pull` para eliminar item del array, actualizar `lastModifiedAt`, usar `findAndModify` con `returnNew(true)`
    - Implementar `clearItems()`: crear Query, usar operador `$set` para establecer items como lista vacía, actualizar `lastModifiedAt`, usar `findAndModify` con `returnNew(true)`
    - Implementar `markAsCheckedOut()`: crear Query, usar operador `$set` para cambiar status a CHECKED_OUT, actualizar `lastModifiedAt`, usar `findAndModify` con `returnNew(true)`
    - Implementar `findAbandonedCarts()`: crear Query con criterios `status=ACTIVE` y `lastModifiedAt < threshold`, ejecutar con `mongoTemplate.find()`
    - Implementar `markAsAbandoned()`: crear Query, usar operador `$set` para cambiar status a ABANDONED, usar `findAndModify` con `returnNew(true)`
    - _Requisitos: 3.8, 3.9, 4.1, 5.1, 6.1, 8.9, 9.2, 9.3_

  - [ ]\* 6.6 Escribir tests de integración para `MongoCartAdapter`
    - Usar `@DataMongoTest` con MongoDB embebido (Flapdoodle)
    - Test para `save()` y `findById()`: verificar round trip
    - Test para `addItem()`: verificar que agrega item nuevo y que incrementa quantity si SKU ya existe
    - Test para `updateItemQuantity()`, `removeItem()`, `clearItems()`: verificar operaciones atómicas
    - Test para `findAbandonedCarts()`: verificar que retorna solo carritos ACTIVE con lastModifiedAt anterior al threshold
    - Usar StepVerifier para verificación de publishers
    - _Requisitos: 1.1, 3.1, 4.1, 5.1, 6.1, 9.2_

- [x] 7. Implementar driven adapter — gRPC Client para ms-catalog
  - [x] 7.1 Crear archivo `.proto` para el servicio de catálogo
    - Definir `CatalogService` con RPC `GetProductInfo(GetProductInfoRequest) returns (GetProductInfoResponse)`
    - `GetProductInfoRequest`: `sku` (string)
    - `GetProductInfoResponse`: `sku` (string), `name` (string), `price` (string, representación decimal)
    - Ubicar en `infrastructure/entry-points/grpc-client/src/main/proto/catalog.proto`
    - Configurar generación de código protobuf en `build.gradle` del módulo
    - _Requisitos: 3.1, 8.1_

  - [x] 7.2 Generar módulo gRPC client con Scaffold
    - Ejecutar `./gradlew generateDrivenAdapter --type=generic --name=grpc-client` desde `ms-cart/`
    - Agregar dependencias de gRPC en `build.gradle` del módulo
    - Configurar plugin `com.google.protobuf` para generación de stubs
    - _Requisitos: 3.1, 8.1_

  - [x] 7.3 Implementar `GrpcProductPriceAdapter` que implementa `ProductPriceGateway`
    - Inyectar `CatalogServiceGrpc.CatalogServiceStub` como dependencia
    - Implementar `getProductInfo(String sku)`: crear `GetProductInfoRequest`, invocar stub con `StreamObserver`, convertir a `Mono` con `Mono.create()`, mapear response a `ProductInfo` (convertir price de String a BigDecimal)
    - Manejar errores gRPC: si status es NOT_FOUND, retornar `Mono.empty()`; si es otro error, lanzar `CatalogServiceUnavailableException`
    - Paquete: `com.arka.grpc.catalog`
    - _Requisitos: 3.1, 3.2, 3.3, 8.1, 8.2, 12.4_

  - [x] 7.4 Crear `GrpcClientConfig` para configuración del canal gRPC
    - Crear bean `ManagedChannel` con `ManagedChannelBuilder.forAddress(host, port).usePlaintext().build()`
    - Crear bean `CatalogServiceGrpc.CatalogServiceStub` usando el canal
    - Externalizar `grpc.catalog.host` y `grpc.catalog.port` en `application.yaml` (default: localhost:9091)
    - Registrar shutdown hook para cerrar el canal al detener la aplicación
    - Paquete: `com.arka.grpc.config`
    - _Requisitos: 3.1, 8.1_

  - [ ]\* 7.5 Escribir tests unitarios para `GrpcProductPriceAdapter`
    - Usar Mockito para mock del stub gRPC
    - Test para respuesta exitosa: verificar que mapea correctamente a `ProductInfo`
    - Test para NOT_FOUND: verificar que retorna `Mono.empty()`
    - Test para error de conexión: verificar que lanza `CatalogServiceUnavailableException`
    - Usar StepVerifier para verificación de publishers
    - _Requisitos: 3.1, 3.2, 3.3, 12.4_

- [x] 8. Implementar driven adapter — Kafka Producer para eventos de abandono
  - [x] 8.1 Generar módulo Kafka producer con Scaffold
    - Ejecutar `./gradlew generateDrivenAdapter --type=generic --name=kafka-producer` desde `ms-cart/`
    - Agregar dependencia `io.projectreactor.kafka:reactor-kafka:1.3.25` en `build.gradle` del módulo
    - _Requisitos: 9.4, 10.1, 10.2, 10.3_

  - [x] 8.2 Crear `KafkaProducerConfig` para configuración de `KafkaSender`
    - Crear bean `KafkaSender<String, String>` con `SenderOptions` configurado con: bootstrap.servers, acks=all, retries=3, enable.idempotence=true, key.serializer=StringSerializer, value.serializer=StringSerializer
    - Externalizar `kafka.bootstrap-servers` en `application.yaml` (default: localhost:9092)
    - Paquete: `com.arka.kafka.config`
    - _Requisitos: 10.1, 10.4_

  - [x] 8.3 Implementar `KafkaCartEventPublisher` que implementa `CartEventPublisher`
    - Inyectar `KafkaSender<String, String>` y `ObjectMapper` como dependencias
    - Implementar `publishCartAbandoned(CartAbandonedEvent event)`: crear `DomainEventEnvelope` con eventId (UUID), eventType ("CartAbandoned"), timestamp (NOW()), source ("ms-cart"), correlationId (UUID), payload (event), serializar envelope a JSON con ObjectMapper, crear `SenderRecord` con topic "cart-events", key (cartId.toString()), value (JSON), enviar con `kafkaSender.send()`, retornar `Mono<Void>`
    - Manejar errores: log ERROR si falla publicación, retornar `Mono.error()` para propagar excepción
    - Paquete: `com.arka.kafka.publisher`
    - _Requisitos: 9.4, 9.5, 10.1, 10.2, 10.3, 10.5_

  - [ ]\* 8.4 Escribir tests unitarios para `KafkaCartEventPublisher`
    - Usar Mockito para mock de `KafkaSender`
    - Test para publicación exitosa: verificar que crea envelope correcto, serializa a JSON, envía con key=cartId
    - Test para error de publicación: verificar que propaga excepción
    - Usar StepVerifier para verificación de publishers
    - _Requisitos: 9.4, 10.1, 10.2, 10.3_

- [x] 9. Implementar entry point — REST Controller y Handler
  - [x] 9.1 Generar módulo REST con Scaffold
    - Ejecutar `./gradlew generateEntryPoint --type=webflux` desde `ms-cart/`
    - Verificar que se crea el módulo en `infrastructure/entry-points/reactive-web/`
    - _Requisitos: Todos (REST API)_

  - [x] 9.2 Crear DTOs de request y response con Bean Validation
    - `CreateCartRequest`: `@NotBlank String customerId`
    - `AddItemRequest`: `@NotBlank String sku`, `@Positive int quantity`
    - `UpdateItemQuantityRequest`: `@Positive int quantity`
    - `CartResponse`: `UUID cartId`, `String customerId`, `List<CartItemResponse> items`, `String status`, `Instant createdAt`, `Instant lastModifiedAt`
    - `CartItemResponse`: `String sku`, `String productName`, `int quantity`, `BigDecimal unitPrice`, `Instant addedAt`
    - `CheckoutResponseDto`: `UUID cartId`, `List<PriceChangeDto> priceChanges`, `BigDecimal totalAmount`, `String status`
    - `PriceChangeDto`: `String sku`, `BigDecimal oldPrice`, `BigDecimal newPrice`
    - `ErrorResponse`: `String code`, `String message`
    - Todos con `@Builder(toBuilder = true)`
    - Paquete: `com.arka.api.dto`
    - _Requisitos: 1.2, 3.6, 3.7, 5.4, 8.8, 12.2_

  - [x] 9.3 Crear `CartDtoMapper` para conversión entre dominio y DTOs
    - Crear clase `CartDtoMapper` con métodos estáticos: `toResponse(Cart)`, `toCartItemResponse(CartItem)`, `toCheckoutResponse(CheckoutResponse)`, `toPriceChangeDto(PriceChange)`
    - Usar `@NoArgsConstructor(access = AccessLevel.PRIVATE)` y `final` en la clase
    - Paquete: `com.arka.api.mapper`
    - _Requisitos: 1.1, 2.1, 8.8_

  - [x] 9.4 Implementar `CartHandler` como componente de lógica de negocio
    - Inyectar `CartUseCase` como dependencia
    - Implementar métodos que delegan a `CartUseCase` y mapean resultados a DTOs: `createCart(CreateCartRequest)`, `getCart(UUID, String, boolean)`, `addItem(UUID, AddItemRequest, String)`, `removeItem(UUID, String, String)`, `updateItemQuantity(UUID, String, UpdateItemQuantityRequest, String)`, `clearCart(UUID, String)`, `deleteCart(UUID, String, boolean)`, `checkout(UUID, String)`, `getCartsByCustomer(String, CartStatus, boolean)`
    - Todos los métodos retornan `Mono<ResponseEntity<T>>` o `Mono<ResponseEntity<Void>>`
    - Usar `@Component` y `@RequiredArgsConstructor`
    - Paquete: `com.arka.api.handler`
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 14.1_

  - [x] 9.5 Implementar `CartController` con endpoints REST
    - Inyectar `CartHandler` como dependencia
    - `POST /api/v1/carts` → `handler.createCart()` → 201 Created con `CartResponse`
    - `GET /api/v1/carts/{cartId}` → `handler.getCart()` → 200 OK con `CartResponse`
    - `POST /api/v1/carts/{cartId}/items` → `handler.addItem()` → 200 OK con `CartResponse`
    - `DELETE /api/v1/carts/{cartId}/items/{sku}` → `handler.removeItem()` → 200 OK con `CartResponse`
    - `PUT /api/v1/carts/{cartId}/items/{sku}` → `handler.updateItemQuantity()` → 200 OK con `CartResponse`
    - `DELETE /api/v1/carts/{cartId}/items` → `handler.clearCart()` → 200 OK con `CartResponse`
    - `DELETE /api/v1/carts/{cartId}` → `handler.deleteCart()` → 204 No Content
    - `POST /api/v1/carts/{cartId}/checkout` → `handler.checkout()` → 200 OK con `CheckoutResponseDto`
    - `GET /api/v1/carts` → `handler.getCartsByCustomer()` → 200 OK con `Flux<CartResponse>`
    - Usar `@Valid` en requests, extraer `customerId` de header `X-User-Email`, extraer `isAdmin` de header `X-User-Role` (comparar con "ADMIN")
    - Anotar con `@Tag(name = "Carts")` a nivel de clase
    - Anotar cada endpoint con `@Operation(summary = "...")` y `@ApiResponses` para documentación OpenAPI
    - Paquete: `com.arka.api`
    - _Requisitos: 1.1, 2.1, 2.3, 3.1, 3.10, 4.1, 4.2, 5.1, 5.5, 6.1, 6.3, 7.1, 7.5, 8.1, 14.1, 14.5_

  - [ ]\* 9.6 Escribir tests unitarios para `CartController`
    - Usar `@WebFluxTest(CartController.class)` con mock de `CartHandler`
    - Test para cada endpoint: verificar que delega correctamente al handler, retorna código HTTP correcto, mapea DTOs correctamente
    - Test para validación de Bean Validation: verificar que retorna 400 para requests inválidos
    - Usar WebTestClient para invocar endpoints
    - _Requisitos: 1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 14.1_

- [x] 10. Implementar entry point — Scheduler de detección de abandono
  - [x] 10.1 Generar módulo scheduler con Scaffold
    - Ejecutar `./gradlew generateHelper --name=scheduler` desde `ms-cart/`
    - Verificar que se crea el módulo en `infrastructure/helpers/scheduler/`
    - _Requisitos: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6, 9.7, 9.8_

  - [x] 10.2 Crear `AbandonmentConfig` para configuración externalizada
    - Crear clase `@ConfigurationProperties(prefix = "cart.abandonment")` con campos: `Duration checkInterval`, `Duration threshold`
    - Validar con Bean Validation: `@NotNull` en ambos campos
    - Usar `@Validated` en la clase
    - Paquete: `com.arka.scheduler.config`
    - _Requisitos: 13.1, 13.2, 13.3, 13.4_

  - [x] 10.3 Implementar `AbandonmentScheduler` con job periódico
    - Inyectar `CartUseCase` y `AbandonmentConfig` como dependencias
    - Implementar método `@Scheduled(fixedDelayString = "${cart.abandonment.check-interval}")` que invoca `cartUseCase.detectAbandonedCarts(config.threshold())`
    - Sin default inline en `@Scheduled` — si la propiedad no existe en YAML, Spring falla al startup (fail-fast)
    - Log INFO al inicio y fin de cada ejecución indicando cantidad de carritos procesados
    - Log ERROR si falla la ejecución del scheduler
    - Usar `@Component` y `@RequiredArgsConstructor`
    - Paquete: `com.arka.scheduler`
    - _Requisitos: 9.1, 9.2, 9.6, 9.7, 9.8, 13.1, 13.2_

  - [ ]\* 10.4 Escribir tests unitarios para `AbandonmentScheduler`
    - Usar Mockito para mock de `CartUseCase`
    - Test para ejecución exitosa: verificar que invoca `detectAbandonedCarts()` con threshold correcto, log INFO
    - Test para error: verificar que log ERROR si falla
    - _Requisitos: 9.1, 9.6, 9.7, 9.8_

- [x] 11. Implementar manejo centralizado de errores
  - [x] 11.1 Implementar `GlobalExceptionHandler` con `@ControllerAdvice`
    - Manejar `WebExchangeBindException` (Bean Validation) → 400 con `ErrorResponse` conteniendo campos inválidos
    - Manejar `DomainException` subclases → HTTP status y código según subclase (404, 409, 400, 503)
    - Manejar `StatusRuntimeException` de gRPC → 503 con `ErrorResponse` indicando que el servicio de catálogo no está disponible
    - Manejar `Exception` genérica → 500, log ERROR, mensaje genérico sin detalles internos
    - Retornar `ErrorResponse(code, message)` en todos los casos
    - Usar `@Slf4j` para logging
    - Paquete: `com.arka.api.handler`
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

  - [ ]\* 11.2 Escribir tests unitarios para `GlobalExceptionHandler`
    - Test para cada tipo de excepción: verificar que retorna código HTTP correcto, `ErrorResponse` con code y message correctos
    - Test para excepción genérica: verificar que retorna 500 y no expone detalles internos
    - _Requisitos: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

- [x] 12. Configurar índices MongoDB y aplicación Spring Boot
  - [x] 12.1 Implementar `MongoIndexConfig` para creación de índices
    - Crear `@Configuration` con `CommandLineRunner` que se ejecuta al inicio
    - Inyectar `ReactiveMongoTemplate` como dependencia
    - Crear índice compuesto en colección `carts` sobre campos `(customerId, status)` con `mongoTemplate.indexOps("carts").ensureIndex(IndexDefinition)`
    - Crear índice compuesto en colección `carts` sobre campos `(status, lastModifiedAt)` para optimizar consulta del scheduler
    - Log INFO al crear cada índice indicando nombre y campos
    - Paquete: `com.arka.config`
    - _Requisitos: 11.1, 11.2, 11.3, 11.4_

  - [x] 12.2 Configurar `application.yaml` con perfiles local y docker
    - Crear `application.yaml` con perfil default (local): `spring.data.mongodb.uri=mongodb://localhost:27017/cart_db`, `kafka.bootstrap-servers=localhost:9092`, `grpc.catalog.host=localhost`, `grpc.catalog.port=9091`, `cart.abandonment.check-interval=5m`, `cart.abandonment.threshold=30m`, `server.port=8084`
    - Crear `application-docker.yaml` con perfil docker: `spring.data.mongodb.uri=mongodb://mongo-cart:27017/cart_db`, `kafka.bootstrap-servers=kafka:9092`, `grpc.catalog.host=ms-catalog`, `grpc.catalog.port=9091`
    - Configurar `spring.profiles.active=${SPRING_PROFILES_ACTIVE:local}` para usar local por default
    - Ubicar en `applications/app-service/src/main/resources/`
    - _Requisitos: 13.1, 13.2, 13.3_

  - [x] 12.3 Crear `OpenApiConfig` para documentación Springdoc
    - Crear bean `OpenAPI` con metadata: título "ms-cart API", versión "1.0", descripción "Microservicio de Gestión de Carritos de Compra"
    - Configurar en `application.yaml`: `springdoc.api-docs.path=/api-docs`, `springdoc.swagger-ui.path=/swagger-ui.html`, `springdoc.swagger-ui.enabled=true`
    - Paquete: `com.arka.config`
    - _Requisitos: Documentación de API_

  - [x] 12.4 Configurar clase principal `MainApplication`
    - Anotar con `@SpringBootApplication`, `@EnableScheduling`, `@ConfigurationPropertiesScan`
    - Crear `CommandLineRunner` que log INFO al inicio indicando que el servicio está listo
    - Paquete: `com.arka`
    - _Requisitos: Todos (aplicación Spring Boot)_

- [x] 13. Checkpoint — Verificar integración completa
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [x] 14. Crear Dockerfile y configuración de despliegue
  - [x] 14.1 Crear Dockerfile para ms-cart
    - Usar imagen base `amazoncorretto:21-alpine`
    - Crear usuario no-root `appuser` con UID 1000
    - Copiar JAR de `applications/app-service/build/libs/` a `/app/app.jar`
    - Exponer puerto 8084
    - Ejecutar como usuario `appuser`
    - Comando: `java -jar /app/app.jar`
    - Ubicar en `deployment/Dockerfile`
    - _Requisitos: Despliegue en contenedor_

  - [x] 14.2 Actualizar `compose.yaml` del monorepo con servicio ms-cart
    - Agregar servicio `ms-cart` con imagen `ms-cart:latest`, puerto `8084:8084`, variables de entorno `SPRING_PROFILES_ACTIVE=docker`, dependencias `mongo-cart`, `kafka`, `ms-catalog`
    - Agregar servicio `mongo-cart` con imagen `mongo:7`, puerto `27018:27017`, volumen para persistencia
    - Configurar red compartida con otros servicios
    - _Requisitos: Despliegue local con Docker Compose_

- [x] 15. Escribir tests de integración E2E
  - [x]\* 15.1 Escribir tests E2E para flujo completo de carrito
    - Test para crear carrito, agregar items, modificar quantities, checkout exitoso
    - Test para checkout con cambio de precios
    - Test para detección de abandono
    - Usar `@SpringBootTest` con MongoDB embebido, mock de gRPC client, Kafka embebido
    - Usar WebTestClient para invocar endpoints REST
    - Usar StepVerifier para verificación de publishers
    - _Requisitos: 1.1, 3.1, 8.1, 9.1_

- [x] 16. Documentación y finalización
  - [x] 16.1 Crear README.md del microservicio
    - Descripción del servicio y responsabilidades
    - Instrucciones para ejecutar localmente: `./gradlew bootRun`
    - Instrucciones para ejecutar tests: `./gradlew test`
    - Instrucciones para construir imagen Docker: `docker build -t ms-cart:latest -f deployment/Dockerfile .`
    - Endpoints principales de la API
    - Configuración de variables de entorno
    - Ubicar en `ms-cart/README.md`
    - _Requisitos: Documentación_

  - [x] 16.2 Verificar cobertura de tests y calidad de código
    - Ejecutar `./gradlew jacocoTestReport` para generar reporte de cobertura
    - Ejecutar `./gradlew validateStructure` para validar Clean Architecture
    - Verificar que no hay warnings de compilación
    - _Requisitos: Calidad de código_

## Notes

- **Tareas marcadas con `*` son opcionales** y pueden omitirse para un MVP más rápido. Incluyen tests unitarios, tests de integración y tests E2E.
- **Cada tarea referencia requisitos específicos** para trazabilidad completa entre implementación y especificación.
- **Checkpoints aseguran validación incremental** — ejecutar tests y consultar al usuario antes de continuar.
- **Tests unitarios y de integración son complementarios** — los unitarios validan lógica de negocio aislada, los de integración validan interacción con MongoDB, gRPC y Kafka.
- **MongoDB usa operadores atómicos** (`$push`, `$pull`, `$set`, `$inc`) para mutaciones sin race conditions.
- **NO se usa Outbox Pattern** porque MongoDB garantiza atomicidad de operaciones individuales y los eventos de abandono son idempotentes.
- **gRPC es síncrono** para validación de precios en checkout (requisito de negocio: precios actualizados en tiempo real).
- **Scheduler periódico** con configuración externalizada en YAML (intervalos y umbrales ajustables sin recompilación).
- **Scaffold Plugin es OBLIGATORIO** para generar módulos (Model, UseCase, Driven Adapter, Entry Point, Helper) — nunca crear estructura manualmente.
- **Versiones unificadas** según `reusability.md` — mismas versiones en todo el monorepo.
- **Spring Profiles**: `local` (default para desarrollo) y `docker` (inyectado por Compose).

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3", "2.4", "2.5", "2.6"] },
    { "id": 2, "tasks": ["2.7", "3.1", "3.2", "3.3"] },
    { "id": 3, "tasks": ["4.1"] },
    { "id": 4, "tasks": ["4.2", "4.3", "4.4", "4.5"] },
    { "id": 5, "tasks": ["4.6"] },
    { "id": 6, "tasks": ["6.1"] },
    { "id": 7, "tasks": ["6.2", "6.3"] },
    { "id": 8, "tasks": ["6.4", "6.5"] },
    { "id": 9, "tasks": ["6.6", "7.1", "7.2", "8.1"] },
    { "id": 10, "tasks": ["7.3", "7.4", "8.2"] },
    { "id": 11, "tasks": ["7.5", "8.3"] },
    { "id": 12, "tasks": ["8.4", "9.1"] },
    { "id": 13, "tasks": ["9.2", "9.3"] },
    { "id": 14, "tasks": ["9.4"] },
    { "id": 15, "tasks": ["9.5"] },
    { "id": 16, "tasks": ["9.6", "10.1"] },
    { "id": 17, "tasks": ["10.2", "10.3"] },
    { "id": 18, "tasks": ["10.4", "11.1"] },
    { "id": 19, "tasks": ["11.2", "12.1", "12.2", "12.3", "12.4"] },
    { "id": 20, "tasks": ["14.1", "14.2"] },
    { "id": 21, "tasks": ["15.1"] },
    { "id": 22, "tasks": ["16.1", "16.2"] }
  ]
}
```
