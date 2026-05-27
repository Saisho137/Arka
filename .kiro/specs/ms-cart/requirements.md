# Documento de Requisitos — ms-cart

## Introducción

El microservicio `ms-cart` es el dueño del dominio de Carritos de Compra Temporales dentro de la plataforma B2B Arka. Su responsabilidad principal es gestionar carritos de compra para clientes B2B, permitiendo agregar, modificar y eliminar items antes de proceder al checkout, detectar carritos abandonados automáticamente mediante un job periódico, y validar precios actualizados consultando a `ms-catalog` vía gRPC durante el checkout. Este servicio cubre la HU8 (Carritos abandonados con detección automática) de la Fase 2 y resuelve el problema de pérdida de ventas por abandono de carritos. Utiliza MongoDB con Reactive Mongo Drivers para almacenar carritos como documentos, publica eventos de dominio al tópico `cart-events` de Kafka, y expone endpoints REST para que clientes B2B gestionen sus carritos de compra.

## Glosario

- **Carrito**: Documento MongoDB que representa un carrito de compra temporal de un cliente B2B, con campos cartId (UUID), customerId (String), items (List<CartItem>), createdAt, lastModifiedAt y status (ACTIVE, ABANDONED, CHECKED_OUT)
- **CartItem**: Subdocumento embebido en el Carrito que representa un producto agregado, con campos sku (String), productName (String), quantity (int), unitPrice (BigDecimal) y addedAt (Instant)
- **CartStatus**: Enumeración de estados del carrito: ACTIVE (carrito en uso), ABANDONED (detectado como abandonado por inactividad), CHECKED_OUT (carrito procesado en checkout, inmutable)
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM con rol CUSTOMER que gestiona carritos de compra
- **Administrador**: Personal interno de Arka con rol ADMIN que puede consultar carritos para análisis
- **Abandono_De_Carrito**: Condición donde un carrito con status ACTIVE no ha sido modificado (campo lastModifiedAt) por un período configurable (default 30 minutos)
- **Scheduler_Abandono**: Job periódico (cada 5 minutos configurable) que detecta carritos abandonados y publica eventos CartAbandoned
- **Checkout**: Operación que valida precios actuales de todos los items del carrito consultando a ms-catalog vía gRPC y retorna un resumen con precios actualizados
- **Precio_Autoritativo**: Precio actual de un producto obtenido de ms-catalog vía gRPC durante el checkout, que puede diferir del unitPrice almacenado en el CartItem
- **Cliente_gRPC**: Cliente reactivo que consume el servicio GetProductInfo de ms-catalog para obtener precio y nombre actualizados de un producto por SKU
- **Evento_De_Dominio**: Mensaje publicado al tópico `cart-events` de Kafka con sobre estándar (eventId, eventType, timestamp, source, correlationId, payload)
- **Kafka_Producer**: Componente que publica eventos al tópico `cart-events` usando `KafkaSender` de reactor-kafka con partition key igual al cartId
- **Controlador_REST**: Entry-point `@RestController` con retornos `Mono`/`Flux` que expone los endpoints HTTP del servicio
- **Mutación_Atómica**: Operación MongoDB que utiliza operadores `$push`, `$pull` o `findAndModify` para modificar subdocumentos de forma atómica sin race conditions
- **ReactiveMongoTemplate**: Cliente reactivo de MongoDB que permite ejecutar operaciones con operadores de actualización atómicos
- **Índice_MongoDB**: Índice creado en la colección `carts` para optimizar consultas por customerId, status y lastModifiedAt
- **Umbral_Abandono**: Duración configurable en YAML (default 30 minutos) que determina cuándo un carrito ACTIVE se considera abandonado
- **Intervalo_Scheduler**: Duración configurable en YAML (default 5 minutos) que determina la frecuencia de ejecución del Scheduler_Abandono

## Requisitos

### Requisito 1: Crear carrito vacío

**Historia de Usuario:** Como Cliente_B2B, quiero crear un carrito de compra vacío para comenzar a agregar productos que deseo comprar.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud POST /carts con su customerId, THE Controlador_REST SHALL crear un nuevo Carrito en MongoDB con status ACTIVE, items vacío, createdAt y lastModifiedAt igual a NOW(), y retornar el Carrito creado con código HTTP 201
2. THE Controlador_REST SHALL validar que el campo customerId esté presente y no sea vacío mediante Bean Validation
3. THE ms-cart SHALL generar un cartId único (UUID) para cada Carrito creado
4. THE ms-cart SHALL permitir que un Cliente_B2B tenga múltiples carritos ACTIVE simultáneamente
5. THE Carrito creado SHALL tener el campo items inicializado como lista vacía

### Requisito 2: Consultar carrito por ID

**Historia de Usuario:** Como Cliente_B2B, quiero consultar el contenido de mi carrito por su ID para revisar los productos agregados antes de proceder al checkout.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud GET /carts/{cartId}, THE Controlador_REST SHALL retornar el Carrito correspondiente con todos sus items con código HTTP 200
2. WHEN el Cliente_B2B solicita un carrito que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. WHEN el Cliente_B2B solicita un carrito que pertenece a otro customerId, THE Controlador_REST SHALL retornar código HTTP 403 (Forbidden) con un mensaje indicando que el carrito no pertenece al usuario autenticado
4. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER (solo sus propios carritos) y ADMIN (cualquier carrito)
5. THE Carrito retornado SHALL incluir los campos: cartId, customerId, items (con sku, productName, quantity, unitPrice, addedAt), status, createdAt y lastModifiedAt

### Requisito 3: Agregar item al carrito

**Historia de Usuario:** Como Cliente_B2B, quiero agregar un producto a mi carrito especificando SKU y cantidad para acumular items antes de realizar el pedido.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud POST /carts/{cartId}/items con sku y quantity válidos, THE ms-cart SHALL consultar el precio y nombre del producto a ms-catalog vía gRPC usando el Cliente_gRPC
2. WHEN ms-catalog retorna el precio y nombre del producto, THE ms-cart SHALL agregar un nuevo CartItem al array items del Carrito usando el operador `$push` de MongoDB con los campos sku, productName, quantity, unitPrice (del gRPC) y addedAt igual a NOW()
3. WHEN ms-catalog retorna que el SKU no existe, THE ms-cart SHALL retornar código HTTP 404 con un mensaje indicando que el producto no fue encontrado
4. WHEN el Cliente_B2B intenta agregar un item a un carrito con status CHECKED_OUT, THE ms-cart SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que el carrito ya fue procesado
5. WHEN el Cliente_B2B intenta agregar un item a un carrito con status ABANDONED, THE ms-cart SHALL permitir la operación y cambiar el status del carrito a ACTIVE
6. THE ms-cart SHALL validar que quantity sea un entero positivo mayor que 0 mediante Bean Validation
7. THE ms-cart SHALL validar que sku no sea vacío mediante Bean Validation
8. WHEN un item se agrega exitosamente, THE ms-cart SHALL actualizar el campo lastModifiedAt del Carrito a NOW() usando Mutación_Atómica
9. WHEN ya existe un CartItem con el mismo sku en el carrito, THE ms-cart SHALL incrementar la quantity del item existente en lugar de crear un item duplicado, usando el operador `$inc` de MongoDB
10. THE ms-cart SHALL retornar el Carrito actualizado con código HTTP 200 después de agregar el item

### Requisito 4: Eliminar item del carrito

**Historia de Usuario:** Como Cliente_B2B, quiero eliminar un producto específico de mi carrito para corregir errores o cambiar de opinión sobre un item.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud DELETE /carts/{cartId}/items/{sku}, THE ms-cart SHALL eliminar el CartItem con el sku especificado del array items usando el operador `$pull` de MongoDB
2. WHEN el Cliente_B2B intenta eliminar un item de un carrito con status CHECKED_OUT, THE ms-cart SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que el carrito ya fue procesado
3. WHEN el Cliente_B2B intenta eliminar un item que no existe en el carrito, THE ms-cart SHALL retornar código HTTP 404 con un mensaje indicando que el item no fue encontrado
4. WHEN un item se elimina exitosamente, THE ms-cart SHALL actualizar el campo lastModifiedAt del Carrito a NOW() usando Mutación_Atómica
5. THE ms-cart SHALL retornar el Carrito actualizado con código HTTP 200 después de eliminar el item

### Requisito 5: Actualizar cantidad de item en el carrito

**Historia de Usuario:** Como Cliente_B2B, quiero modificar la cantidad de un producto en mi carrito para ajustar la cantidad sin tener que eliminar y volver a agregar el item.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud PUT /carts/{cartId}/items/{sku} con una nueva quantity válida, THE ms-cart SHALL actualizar el campo quantity del CartItem correspondiente usando el operador `$set` de MongoDB con filtro posicional
2. WHEN el Cliente_B2B intenta actualizar la cantidad de un item en un carrito con status CHECKED_OUT, THE ms-cart SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que el carrito ya fue procesado
3. WHEN el Cliente_B2B intenta actualizar la cantidad de un item que no existe en el carrito, THE ms-cart SHALL retornar código HTTP 404 con un mensaje indicando que el item no fue encontrado
4. THE ms-cart SHALL validar que quantity sea un entero positivo mayor que 0 mediante Bean Validation
5. WHEN la cantidad se actualiza exitosamente, THE ms-cart SHALL actualizar el campo lastModifiedAt del Carrito a NOW() usando Mutación_Atómica
6. THE ms-cart SHALL retornar el Carrito actualizado con código HTTP 200 después de actualizar la cantidad

### Requisito 6: Vaciar carrito

**Historia de Usuario:** Como Cliente_B2B, quiero vaciar completamente mi carrito para empezar de nuevo sin tener que eliminar items uno por uno.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud DELETE /carts/{cartId}/items, THE ms-cart SHALL vaciar el array items del Carrito usando el operador `$set` de MongoDB con items igual a lista vacía
2. WHEN el Cliente_B2B intenta vaciar un carrito con status CHECKED_OUT, THE ms-cart SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que el carrito ya fue procesado
3. WHEN el carrito se vacía exitosamente, THE ms-cart SHALL actualizar el campo lastModifiedAt del Carrito a NOW() usando Mutación_Atómica
4. THE ms-cart SHALL retornar el Carrito actualizado con código HTTP 200 después de vaciar los items

### Requisito 7: Eliminar carrito

**Historia de Usuario:** Como Cliente_B2B, quiero eliminar completamente un carrito que ya no necesito para mantener mi lista de carritos limpia.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud DELETE /carts/{cartId}, THE ms-cart SHALL eliminar el documento del Carrito de MongoDB
2. WHEN el Cliente_B2B intenta eliminar un carrito que no existe, THE ms-cart SHALL retornar código HTTP 404 con un mensaje descriptivo
3. WHEN el Cliente_B2B intenta eliminar un carrito que pertenece a otro customerId, THE ms-cart SHALL retornar código HTTP 403 (Forbidden) con un mensaje indicando que el carrito no pertenece al usuario autenticado
4. THE ms-cart SHALL permitir eliminar carritos en cualquier status (ACTIVE, ABANDONED, CHECKED_OUT)
5. THE ms-cart SHALL retornar código HTTP 204 (No Content) después de eliminar el carrito exitosamente

### Requisito 8: Checkout de carrito con validación de precios

**Historia de Usuario:** Como Cliente_B2B, quiero realizar el checkout de mi carrito para validar los precios actuales de todos los productos antes de crear la orden de compra.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud POST /carts/{cartId}/checkout, THE ms-cart SHALL consultar el precio actual de cada item del carrito a ms-catalog vía gRPC usando el Cliente_gRPC
2. WHEN ms-catalog retorna los precios actuales, THE ms-cart SHALL comparar el unitPrice almacenado en cada CartItem con el Precio_Autoritativo retornado por gRPC
3. WHEN todos los precios coinciden, THE ms-cart SHALL cambiar el status del Carrito a CHECKED_OUT y retornar un CheckoutResponse con priceChanges vacío y totalAmount calculado con código HTTP 200
4. WHEN uno o más precios han cambiado, THE ms-cart SHALL retornar un CheckoutResponse con priceChanges conteniendo los items con precio modificado (sku, oldPrice, newPrice) y totalAmount calculado con los precios nuevos, sin cambiar el status del carrito a CHECKED_OUT
5. WHEN el Cliente_B2B intenta hacer checkout de un carrito vacío (items.size() == 0), THE ms-cart SHALL retornar código HTTP 400 (Bad Request) con un mensaje indicando que el carrito está vacío
6. WHEN el Cliente_B2B intenta hacer checkout de un carrito con status CHECKED_OUT, THE ms-cart SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que el carrito ya fue procesado
7. WHEN ms-catalog retorna que uno o más SKUs no existen, THE ms-cart SHALL retornar código HTTP 404 con un mensaje indicando los SKUs no encontrados
8. THE CheckoutResponse SHALL contener los campos: cartId, priceChanges (List<PriceChange>), totalAmount (BigDecimal calculado con precios actuales) y status (READY si no hay cambios, PRICE_CHANGED si hay cambios)
9. THE ms-cart SHALL actualizar el campo lastModifiedAt del Carrito a NOW() cuando el checkout es exitoso (status cambia a CHECKED_OUT)

### Requisito 9: Detectar carritos abandonados automáticamente

**Historia de Usuario:** Como plataforma Arka, quiero detectar automáticamente carritos abandonados para publicar eventos que permitan a otros servicios enviar recordatorios o generar analítica de abandono.

#### Criterios de Aceptación

1. THE ms-cart SHALL ejecutar el Scheduler_Abandono cada 5 minutos (configurable en YAML con propiedad `cart.abandonment.check-interval`)
2. WHEN el Scheduler_Abandono se ejecuta, THE ms-cart SHALL consultar en MongoDB todos los Carritos con status ACTIVE cuyo campo lastModifiedAt sea anterior a NOW() - Umbral_Abandono (default 30 minutos, configurable en YAML con propiedad `cart.abandonment.threshold`)
3. WHEN el Scheduler_Abandono identifica carritos abandonados, THE ms-cart SHALL cambiar el status de cada Carrito a ABANDONED usando actualización atómica
4. WHEN un Carrito se marca como ABANDONED, THE ms-cart SHALL publicar un Evento_De_Dominio de tipo CartAbandoned al tópico `cart-events` de Kafka con el cartId como partition key
5. THE Evento_De_Dominio CartAbandoned SHALL contener en su payload: cartId, customerId, itemCount (cantidad de items en el carrito), totalAmount (suma de unitPrice * quantity de todos los items), abandonedAt (Instant) y lastModifiedAt
6. THE ms-cart SHALL procesar cada carrito abandonado de forma independiente para que el fallo en uno no afecte a los demás
7. WHEN el Scheduler_Abandono falla al publicar un evento CartAbandoned a Kafka, THE ms-cart SHALL registrar un log de nivel ERROR con el cartId y mantener el status del carrito como ACTIVE para reintento en el siguiente ciclo
8. THE ms-cart SHALL registrar un log de nivel INFO al inicio y fin de cada ejecución del Scheduler_Abandono indicando la cantidad de carritos procesados

### Requisito 10: Publicación de eventos a Kafka sin Outbox Pattern

**Historia de Usuario:** Como sistema distribuido, quiero publicar eventos de carritos abandonados a Kafka para que otros servicios puedan reaccionar y enviar notificaciones o generar analítica.

#### Criterios de Aceptación

1. THE ms-cart SHALL publicar eventos al tópico `cart-events` de Kafka usando `KafkaSender` de reactor-kafka 1.3.25
2. THE Evento_De_Dominio SHALL seguir el sobre estándar con los campos: eventId (UUID), eventType, timestamp, source ("ms-cart"), correlationId y payload
3. THE ms-cart SHALL usar el cartId como partition key al publicar eventos a Kafka para garantizar orden de eventos del mismo carrito
4. THE Kafka_Producer SHALL configurarse con acks=all, retries=3 e idempotence=true para garantizar entrega confiable
5. THE ms-cart SHALL publicar eventos de forma asíncrona sin bloquear la operación de negocio (fire-and-forget con log de error en caso de fallo)
6. THE ms-cart SHALL soportar un tipo de evento: CartAbandoned
7. THE ms-cart NO SHALL usar Transactional Outbox Pattern porque MongoDB no requiere coordinación transaccional entre escritura de carrito y publicación de evento (operaciones atómicas de MongoDB son suficientes)

### Requisito 11: Índices MongoDB para optimización de consultas

**Historia de Usuario:** Como plataforma Arka, quiero que las consultas de carritos por customerId, status y lastModifiedAt sean eficientes para garantizar tiempos de respuesta rápidos.

#### Criterios de Aceptación

1. THE ms-cart SHALL crear un índice compuesto en la colección `carts` sobre los campos (customerId, status) al inicio de la aplicación usando `ReactiveMongoTemplate.indexOps().ensureIndex()`
2. THE ms-cart SHALL crear un índice compuesto en la colección `carts` sobre los campos (status, lastModifiedAt) al inicio de la aplicación para optimizar la consulta del Scheduler_Abandono
3. THE ms-cart SHALL crear los índices dentro de un `CommandLineRunner` que se ejecute después de que el contexto de Spring esté completamente inicializado
4. THE ms-cart SHALL registrar un log de nivel INFO al crear cada índice indicando el nombre del índice y los campos

### Requisito 12: Manejo centralizado de errores

**Historia de Usuario:** Como consumidor de la API de carritos, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Criterios de Aceptación

1. THE ms-cart SHALL implementar un GlobalExceptionHandler mediante `@ControllerAdvice` que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE ms-cart SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de dominio (DomainException), THE ms-cart SHALL retornar el código HTTP correspondiente con un ErrorResponse estandarizado
4. WHEN ocurre un error de comunicación gRPC con ms-catalog, THE ms-cart SHALL retornar código HTTP 503 (Service Unavailable) con un ErrorResponse indicando que el servicio de catálogo no está disponible
5. WHEN ocurre un error inesperado, THE ms-cart SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
6. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)

### Requisito 13: Configuración externalizada de schedulers y umbrales

**Historia de Usuario:** Como operador de la plataforma Arka, quiero configurar los intervalos del scheduler y umbrales de abandono mediante YAML para ajustar el comportamiento sin recompilar el servicio.

#### Criterios de Aceptación

1. THE ms-cart SHALL externalizar la configuración del Scheduler_Abandono en `application.yaml` con las propiedades `cart.abandonment.check-interval` (default: 5m) y `cart.abandonment.threshold` (default: 30m)
2. THE ms-cart SHALL fallar al inicio si las propiedades de configuración del scheduler no están definidas en el YAML (no defaults inline en el código)
3. THE ms-cart SHALL usar `@ConfigurationProperties` para mapear las propiedades de configuración a una clase Java con validación mediante Bean Validation
4. THE ms-cart SHALL registrar un log de nivel INFO al inicio indicando los valores de configuración cargados para el scheduler y umbrales

### Requisito 14: Consultar carritos por customerId

**Historia de Usuario:** Como Cliente_B2B, quiero consultar todos mis carritos activos para revisar carritos pendientes antes de proceder con una compra.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud GET /carts?customerId={customerId}&status={status}, THE Controlador_REST SHALL retornar una lista de Carritos que coincidan con los filtros con código HTTP 200
2. THE Controlador_REST SHALL permitir filtrar por customerId (obligatorio) y status (opcional)
3. WHEN el parámetro status no se proporciona, THE ms-cart SHALL retornar todos los carritos del customerId independientemente del status
4. WHEN el parámetro status se proporciona, THE ms-cart SHALL retornar solo los carritos del customerId con el status especificado
5. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER (solo sus propios carritos) y ADMIN (cualquier customerId)
6. THE ms-cart SHALL retornar una lista vacía con código HTTP 200 si no se encuentran carritos que coincidan con los filtros
7. THE ms-cart SHALL ordenar los carritos retornados por lastModifiedAt descendente (más recientes primero)
