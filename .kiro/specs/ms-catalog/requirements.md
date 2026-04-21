# Documento de Requisitos — ms-catalog

## Introducción

El microservicio `ms-catalog` es el dueño del Bounded Context **Catálogo Maestro de Productos** dentro de la plataforma B2B Arka. Su responsabilidad principal es gestionar el ciclo de vida completo de productos y categorías, almacenar reseñas como subdocumentos anidados en MongoDB, publicar eventos de dominio al tópico `product-events` de Kafka mediante el Outbox Pattern con operaciones atómicas de MongoDB, y optimizar lecturas con el patrón Cache-Aside usando Redis (TTL 1h). Este servicio cubre la HU1 (Registrar productos en el sistema) de la Fase 1 (MVP).

El servicio es 100% reactivo (Spring WebFlux + Project Reactor), usa MongoDB como almacenamiento primario con drivers reactivos, expone endpoints REST para administración y consulta, expone un servidor gRPC para consulta síncrona de información de productos desde ms-order y ms-cart (Fase 2), y sigue estrictamente la Clean Architecture del Scaffold Bancolombia 4.2.0.

## Glosario

- **Catálogo**: Conjunto maestro de productos y categorías gestionados por ms-catalog
- **Producto**: Entidad principal del catálogo con atributos descriptivos (SKU, nombre, descripción, precio, categoría) y reseñas anidadas como subdocumentos en MongoDB
- **Categoría**: Entidad maestra que agrupa productos por tipo (ej. GPUs, Procesadores, Memorias RAM)
- **SKU**: Stock Keeping Unit — identificador único alfanumérico asignado a cada producto que vincula el catálogo con el inventario
- **Reseña**: Subdocumento anidado dentro del documento de producto en MongoDB, con userId, rating (1-5), comentario y fecha de creación
- **Administrador**: Personal interno de Arka con rol ADMIN que gestiona catálogo e inventario
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM que compra accesorios para PC al por mayor, con rol CUSTOMER
- **API_Gateway**: Punto de entrada único que valida JWT, inyecta X-User-Email y enruta tráfico a la VPC privada
- **Outbox_Events**: Colección MongoDB donde se insertan eventos de dominio atómicamente junto con la operación de negocio mediante operaciones atómicas de MongoDB (no transacciones multi-documento)
- **Cache_Redis**: Caché en memoria que implementa el patrón Cache-Aside con TTL de 1 hora para lecturas de productos individuales y listas paginadas
- **Soft_Delete**: Desactivación lógica de un producto marcando el campo active como false sin eliminar el documento de MongoDB
- **Controlador_REST**: Entry-point `@RestController` con retornos `Mono`/`Flux` que expone los endpoints HTTP del servicio
- **Relay_Outbox**: Proceso asíncrono que consulta la colección `outbox_events` cada 5 segundos y publica los eventos pendientes a Kafka usando `reactor-kafka` directo
- **Evento_De_Dominio**: Mensaje publicado al tópico `product-events` de Kafka con sobre estándar (eventId, eventType, timestamp, source, correlationId, payload)
- **Servidor_gRPC**: Entry-point que expone el servicio CatalogService con método GetProductInfo para consulta síncrona de información de productos desde otros microservicios (ms-order, ms-cart)
- **CatalogService**: Servicio gRPC que permite consultar información de productos (SKU, nombre, precio) de forma síncrona para garantizar consistencia de precios durante checkout

## Requisitos

### Requisito 1: Registrar un nuevo producto en el catálogo

**Historia de Usuario:** Como Administrador, quiero registrar nuevos productos con sus características (SKU, nombre, descripción, precio y categoría) para que los Clientes_B2B puedan visualizarlos y comprarlos.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud POST /products con datos válidos (incluyendo costo y precio), THE Controlador_REST SHALL crear el Producto en MongoDB y retornar el Producto creado con código HTTP 201
2. THE Controlador_REST SHALL validar que los campos nombre, precio, costo, moneda (currency), SKU y categoría estén presentes en la solicitud mediante Bean Validation
3. WHEN el Administrador envía un precio menor o igual a cero, THE Controlador_REST SHALL rechazar la solicitud con código HTTP 400 y un mensaje descriptivo del error
4. WHEN el Administrador envía un precio menor o igual al costo, THE Controlador_REST SHALL rechazar la solicitud con código HTTP 400 y un mensaje indicando que el precio debe ser mayor al costo
5. WHEN el Administrador envía una moneda no soportada (diferente de COP, USD, PEN, CLP), THE Controlador_REST SHALL rechazar la solicitud con código HTTP 400 y un mensaje indicando las monedas válidas
6. WHEN el Administrador envía un SKU que ya existe en el catálogo, THE Catálogo SHALL rechazar la solicitud con código HTTP 409 (Conflict) y un mensaje indicando que el SKU ya está registrado
7. WHEN un Producto se crea exitosamente, THE Catálogo SHALL insertar un Evento_De_Dominio de tipo ProductCreated en la colección Outbox_Events dentro de la misma operación atómica en MongoDB
8. THE Evento_De_Dominio ProductCreated SHALL contener en su payload: productId, sku, name, cost, price, currency, categoryId e initialStock
9. WHEN un Producto se crea exitosamente, THE Cache_Redis SHALL invalidar las entradas de caché relacionadas con la lista de productos paginada

### Requisito 2: Consultar productos del catálogo

**Historia de Usuario:** Como Cliente_B2B o Administrador, quiero listar y consultar productos del catálogo para conocer la oferta disponible de accesorios para PC.

#### Criterios de Aceptación

1. WHEN un usuario autenticado envía una solicitud GET /products, THE Controlador_REST SHALL retornar una lista paginada de Productos activos con código HTTP 200
2. WHEN un usuario autenticado envía una solicitud GET /products/{id}, THE Controlador_REST SHALL retornar el Producto correspondiente con código HTTP 200
3. WHEN un usuario solicita un Producto con un ID que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
4. WHEN se consulta un Producto o la lista de Productos, THE Catálogo SHALL verificar primero en Cache_Redis antes de consultar MongoDB (patrón Cache-Aside)
5. WHEN ocurre un cache miss en Cache_Redis, THE Catálogo SHALL consultar MongoDB, almacenar el resultado en Cache_Redis con TTL de 1 hora y retornar el resultado al cliente
6. WHEN ocurre un cache hit en Cache_Redis, THE Catálogo SHALL retornar el resultado directamente desde Cache_Redis sin consultar MongoDB

### Requisito 3: Actualizar un producto existente

**Historia de Usuario:** Como Administrador, quiero actualizar las características de un producto existente para mantener la información del catálogo precisa y actualizada.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud PUT /products/{id} con datos válidos (incluyendo costo y precio), THE Controlador_REST SHALL actualizar el Producto en MongoDB y retornar el Producto actualizado con código HTTP 200
2. WHEN el Administrador intenta actualizar un Producto que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. THE Controlador_REST SHALL aplicar las mismas validaciones de campos obligatorios, precio positivo, precio mayor al costo y moneda válida que en la creación del Producto
4. WHEN un Producto se actualiza exitosamente, THE Catálogo SHALL insertar un Evento_De_Dominio de tipo ProductUpdated en la colección Outbox_Events dentro de la misma operación atómica en MongoDB
5. WHEN el precio de un Producto cambia durante la actualización, THE Catálogo SHALL insertar un Evento_De_Dominio adicional de tipo PriceChanged en la colección Outbox_Events con oldPrice, newPrice y currency
6. WHEN un Producto se actualiza exitosamente, THE Cache_Redis SHALL invalidar la entrada de caché del Producto actualizado y las entradas de lista relacionadas

### Requisito 4: Desactivar un producto (Soft Delete)

**Historia de Usuario:** Como Administrador, quiero desactivar un producto del catálogo para que deje de ser visible para los Clientes_B2B sin perder su información histórica.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud DELETE /products/{id}, THE Catálogo SHALL marcar el campo active del Producto como false en MongoDB en lugar de eliminar el documento
2. WHEN el Administrador intenta desactivar un Producto que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. WHEN un Producto se desactiva exitosamente, THE Catálogo SHALL insertar un Evento_De_Dominio de tipo ProductUpdated en la colección Outbox_Events
4. WHEN un Producto se desactiva exitosamente, THE Cache_Redis SHALL invalidar la entrada de caché del Producto desactivado y las entradas de lista relacionadas
5. WHILE un Producto tiene el campo active en false, THE Catálogo SHALL excluir el Producto de los resultados de listado para el rol CUSTOMER

### Requisito 5: Gestión de categorías maestras

**Historia de Usuario:** Como Administrador, quiero crear y listar categorías de productos para organizar el catálogo de forma estructurada.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud POST /categories con un nombre y descripción válidos, THE Controlador_REST SHALL crear la Categoría en MongoDB y retornarla con código HTTP 201
2. WHEN el Administrador envía un nombre de Categoría que ya existe, THE Catálogo SHALL rechazar la solicitud con código HTTP 409 (Conflict) y un mensaje indicando que la Categoría ya está registrada
3. THE Controlador_REST SHALL validar que el campo nombre de la Categoría esté presente en la solicitud
4. WHEN un usuario autenticado envía una solicitud GET /categories, THE Controlador_REST SHALL retornar la lista completa de Categorías con código HTTP 200
5. WHEN el Administrador crea un Producto referenciando una Categoría que no existe, THE Catálogo SHALL rechazar la solicitud con código HTTP 400 y un mensaje indicando que la Categoría no fue encontrada

### Requisito 6: Gestión de reseñas anidadas en productos

**Historia de Usuario:** Como Cliente_B2B, quiero agregar reseñas a los productos que he comprado para compartir mi experiencia con otros compradores.

#### Criterios de Aceptación

1. WHEN un Cliente_B2B envía una reseña para un Producto existente, THE Catálogo SHALL agregar la Reseña como subdocumento anidado dentro del documento del Producto en MongoDB
2. THE Controlador_REST SHALL validar que la Reseña contenga userId, rating (entre 1 y 5) y comment
3. WHEN un Cliente_B2B envía un rating fuera del rango 1-5, THE Controlador_REST SHALL rechazar la solicitud con código HTTP 400 y un mensaje descriptivo
4. WHEN un Cliente_B2B intenta agregar una Reseña a un Producto que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
5. WHEN se agrega una Reseña exitosamente, THE Catálogo SHALL registrar la fecha de creación (createdAt) y asignar un identificador único (reviewId) automáticamente en la Reseña

### Requisito 7: Publicación de eventos mediante Outbox Pattern con MongoDB

**Historia de Usuario:** Como sistema distribuido, quiero garantizar la publicación confiable de eventos de dominio a Kafka para que otros microservicios reaccionen a cambios en el catálogo sin pérdida de datos.

#### Criterios de Aceptación

1. THE Catálogo SHALL insertar cada Evento_De_Dominio en la colección Outbox_Events de MongoDB dentro de la misma operación atómica que la escritura de negocio mediante operaciones atómicas de MongoDB (no transacciones multi-documento)
2. THE Evento_De_Dominio SHALL seguir el sobre estándar con los campos: eventId (UUID), eventType, timestamp, source ("ms-catalog"), correlationId y payload
3. THE Catálogo SHALL publicar eventos al tópico product-events de Kafka usando productId como partition key mediante `reactor-kafka` directo (`KafkaSender`)
4. WHEN un Evento_De_Dominio se publica exitosamente a Kafka, THE Relay_Outbox SHALL actualizar el campo status del evento en Outbox_Events de PENDING a PUBLISHED
5. IF el relay de publicación falla al enviar un evento a Kafka, THEN THE Catálogo SHALL mantener el evento con status PENDING para reintento en el siguiente ciclo del relay
6. THE Catálogo SHALL soportar tres tipos de eventos: ProductCreated, ProductUpdated y PriceChanged
7. THE Relay_Outbox SHALL ejecutar cada 5 segundos con intervalo externalizado en `application.yaml` (`scheduler.outbox-relay.interval`) sin default inline en la anotación `@Scheduled`

### Requisito 8: Caché con patrón Cache-Aside y Redis

**Historia de Usuario:** Como plataforma B2B con alto volumen de lecturas, quiero optimizar las consultas al catálogo mediante caché en Redis para garantizar latencias bajas y reducir la carga en MongoDB.

#### Criterios de Aceptación

1. THE Cache_Redis SHALL almacenar resultados de consultas de productos con un TTL de 1 hora
2. WHEN se ejecuta una operación de escritura (crear, actualizar o desactivar Producto), THE Catálogo SHALL invalidar las entradas de caché afectadas en Cache_Redis
3. WHEN se publica un Evento_De_Dominio de tipo ProductUpdated o ProductCreated a Kafka, THE Catálogo SHALL invalidar las entradas de caché correspondientes en Cache_Redis
4. IF Cache_Redis no está disponible, THEN THE Catálogo SHALL continuar operando consultando directamente a MongoDB y registrar un log de nivel WARN
5. THE Catálogo SHALL utilizar Cache_Redis tanto para consultas individuales de Producto (por ID) como para consultas de listado paginado

### Requisito 9: Manejo centralizado de errores

**Historia de Usuario:** Como consumidor de la API del catálogo, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Criterios de Aceptación

1. THE Catálogo SHALL implementar un GlobalExceptionHandler mediante ControllerAdvice que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE Catálogo SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de dominio (DomainException), THE Catálogo SHALL retornar el código HTTP correspondiente con un ErrorResponse estandarizado
4. WHEN ocurre un error inesperado, THE Catálogo SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
5. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)

### Requisito 10: Servidor gRPC para consulta de información de productos (Fase 2)

**Historia de Usuario:** Como ms-cart, quiero consultar información actualizada de productos (SKU, nombre, precio) durante el checkout para garantizar que los precios mostrados al cliente sean consistentes con la fuente de verdad del catálogo.

#### Criterios de Aceptación

1. THE Catálogo SHALL exponer un servidor gRPC reactivo con el servicio CatalogService que implemente el método GetProductInfo
2. WHEN ms-cart invoca GetProductInfo con un SKU válido, THE Catálogo SHALL consultar la base de datos MongoDB (no caché) y retornar un ProductInfoResponse con los campos: sku, productName y unitPrice
3. WHEN ms-cart invoca GetProductInfo con un SKU que no existe en el catálogo, THE Catálogo SHALL retornar un error gRPC con código NOT_FOUND y un mensaje descriptivo indicando que el SKU no fue encontrado
4. WHEN ms-cart invoca GetProductInfo con un SKU que corresponde a un producto desactivado (active=false), THE Catálogo SHALL retornar un error gRPC con código NOT_FOUND
5. WHEN ocurre un error de base de datos durante la consulta, THE Catálogo SHALL retornar un error gRPC con código INTERNAL y registrar el error con nivel ERROR en el log
6. THE servidor gRPC SHALL usar Project Reactor para implementar el método de forma reactiva, retornando Mono<ProductInfoResponse>
7. THE servidor gRPC SHALL consultar el precio actual directamente desde MongoDB para garantizar consistencia, sin usar el caché de Redis
8. THE servidor gRPC SHALL configurarse en un puerto diferente al puerto HTTP REST (puerto gRPC: 9084, puerto HTTP: 8084)
