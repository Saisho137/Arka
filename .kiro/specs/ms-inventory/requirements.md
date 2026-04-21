# Documento de Requisitos — ms-inventory

## Introducción

El microservicio `ms-inventory` es el dueño del dominio de Disponibilidad Física y Reservas dentro de la plataforma B2B Arka. Su responsabilidad principal es gestionar el stock en tiempo real por SKU, prevenir sobreventas mediante lock pesimista (`SELECT ... FOR UPDATE`) en PostgreSQL 17, administrar reservas temporales con expiración de 15 minutos, y mantener trazabilidad completa de movimientos de stock. Este servicio resuelve el problema CRÍTICO #1 de Arka (sobreventa por concurrencia) y cubre la HU2 (Actualizar stock de productos) de la Fase 1 (MVP). Expone un servidor gRPC para que `ms-order` reserve stock de forma síncrona, consume eventos de Kafka (`ProductCreated`, `OrderCancelled`), y publica eventos de dominio al tópico `inventory-events` mediante el Transactional Outbox Pattern.

## Glosario

- **Stock**: Registro de disponibilidad física de un producto identificado por SKU, con campos quantity, reserved_quantity y available_quantity (generado como quantity - reserved_quantity)
- **SKU**: Stock Keeping Unit — identificador único alfanumérico que vincula un producto del catálogo con su registro de stock en inventario
- **Reserva**: Bloqueo temporal de una cantidad de stock asociada a una orden de compra, con expiración de 15 minutos y estados PENDING, CONFIRMED, EXPIRED o RELEASED
- **Lock_Pesimista**: Mecanismo de bloqueo a nivel de fila en PostgreSQL 17 mediante `SELECT ... FOR UPDATE` que previene race conditions durante la reserva de stock
- **Movimiento_De_Stock**: Registro de auditoría en la tabla `stock_movements` que documenta cada cambio en el stock con tipo, cantidad, referencia y razón
- **Tipo_Movimiento**: Enumeración de tipos de movimiento: RESTOCK, SHRINKAGE, ORDER_RESERVE, ORDER_CONFIRM, RESERVATION_RELEASE, PRODUCT_CREATION
- **Outbox_Events**: Tabla PostgreSQL donde se insertan eventos de dominio atómicamente dentro de la misma transacción que la escritura de negocio, para ser publicados a Kafka por un relay asíncrono
- **Processed_Events**: Tabla PostgreSQL que almacena el eventId de cada evento consumido para garantizar idempotencia y evitar procesamiento duplicado
- **Evento_De_Dominio**: Mensaje publicado al tópico `inventory-events` de Kafka con sobre estándar (eventId, eventType, timestamp, source, correlationId, payload)
- **Administrador**: Personal interno de Arka con rol ADMIN que gestiona el stock manualmente
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM con rol CUSTOMER que consulta disponibilidad de stock
- **API_Gateway**: Punto de entrada único que valida JWT, inyecta `X-User-Email` y enruta tráfico a la VPC privada
- **Controlador_REST**: Entry-point `@RestController` con retornos `Mono`/`Flux` que expone los endpoints HTTP del servicio
- **Servidor_gRPC**: Entry-point gRPC que expone el servicio `ReserveStock` para que `ms-order` reserve stock de forma síncrona
- **Umbral_Crítico**: Nivel de stock por producto (campo `depletion_threshold` en la tabla `stock`) por debajo del cual se emite un evento `StockDepleted` como alerta de reabastecimiento. Cada producto tiene su propio umbral según su volumen de ventas y disponibilidad típica (default: 10)
- **Relay_Outbox**: Proceso asíncrono que consulta la tabla Outbox_Events cada 5 segundos y publica los eventos pendientes a Kafka

## Requisitos

### Requisito 1: Actualizar stock manualmente

**Historia de Usuario:** Como Administrador, quiero actualizar la cantidad de productos en stock mediante el endpoint PUT /inventory/{sku}/stock para reflejar la mercancía recibida en bodega y evitar sobreventas.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud PUT /inventory/{sku}/stock con una cantidad válida, THE Controlador_REST SHALL actualizar el campo quantity del Stock en PostgreSQL y retornar el Stock actualizado con código HTTP 200
2. THE Controlador_REST SHALL validar que el campo quantity esté presente y sea un entero no negativo mediante Bean Validation
3. WHEN el Administrador envía una cantidad que resultaría en un available_quantity negativo (quantity < reserved_quantity), THE Controlador_REST SHALL rechazar la solicitud con código HTTP 409 (Conflict) y un mensaje indicando que la cantidad no puede ser menor que la cantidad reservada
4. WHEN el Administrador intenta actualizar el Stock de un SKU que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
5. WHEN el Stock se actualiza exitosamente, THE ms-inventory SHALL registrar un Movimiento_De_Stock de tipo RESTOCK (si la cantidad aumenta) o SHRINKAGE (si la cantidad disminuye) con la cantidad anterior, la cantidad nueva y la razón proporcionada por el Administrador
6. WHEN el Stock se actualiza exitosamente, THE ms-inventory SHALL insertar un Evento_De_Dominio de tipo StockUpdated en la tabla Outbox_Events dentro de la misma transacción de base de datos
7. WHEN el available_quantity resultante es menor o igual al Umbral_Crítico del producto (campo `depletion_threshold` del registro de Stock) después de la actualización, THE ms-inventory SHALL insertar un Evento_De_Dominio adicional de tipo StockDepleted en la tabla Outbox_Events dentro de la misma transacción
8. THE ms-inventory SHALL utilizar control de concurrencia optimista (campo version) para prevenir actualizaciones perdidas en el ajuste manual de stock

### Requisito 2: Consultar disponibilidad de stock

**Historia de Usuario:** Como Cliente_B2B o Administrador, quiero consultar la disponibilidad de stock de un producto por SKU para conocer la cantidad disponible antes de realizar un pedido.

#### Criterios de Aceptación

1. WHEN un usuario autenticado envía una solicitud GET /inventory/{sku}, THE Controlador_REST SHALL retornar el Stock del SKU solicitado incluyendo quantity, reserved_quantity y available_quantity con código HTTP 200
2. WHEN un usuario solicita el Stock de un SKU que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER y ADMIN

### Requisito 3: Consultar historial de movimientos de stock

**Historia de Usuario:** Como Administrador, quiero consultar el historial de movimientos de stock de un SKU para tener trazabilidad completa de todos los cambios realizados.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /inventory/{sku}/history, THE Controlador_REST SHALL retornar una lista paginada de Movimientos_De_Stock del SKU solicitado ordenados por fecha descendente con código HTTP 200
2. WHEN el Administrador solicita el historial de un SKU que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
4. THE Movimiento_De_Stock SHALL contener los campos: id, sku, movement_type, quantity_change, previous_quantity, new_quantity, reference_id, reason y created_at

### Requisito 4: Reservar stock mediante gRPC

**Historia de Usuario:** Como ms-order, quiero reservar stock de forma síncrona mediante gRPC para garantizar que la cantidad solicitada esté disponible antes de confirmar una orden de compra.

#### Criterios de Aceptación

1. WHEN ms-order envía una solicitud ReserveStock con sku, orderId y quantity válidos, THE Servidor_gRPC SHALL adquirir un Lock_Pesimista sobre la fila del Stock correspondiente al SKU
2. WHILE el Lock_Pesimista está activo sobre la fila del Stock, THE ms-inventory SHALL verificar que available_quantity sea mayor o igual a la quantity solicitada
3. WHEN el available_quantity es suficiente, THE ms-inventory SHALL incrementar reserved_quantity en la cantidad solicitada, crear una Reserva con status PENDING y expires_at igual a NOW() + 15 minutos, y retornar un ReserveStockResponse exitoso con el reservationId
4. WHEN el available_quantity es insuficiente, THE ms-inventory SHALL retornar un ReserveStockResponse fallido con la cantidad disponible y la razón del rechazo, sin modificar el Stock
5. WHEN ya existe una Reserva con el mismo sku y orderId en estado PENDING, THE ms-inventory SHALL retornar el ReserveStockResponse exitoso con el reservationId existente sin crear una reserva duplicada (idempotencia)
6. WHEN una Reserva se crea exitosamente, THE ms-inventory SHALL registrar un Movimiento_De_Stock de tipo ORDER_RESERVE con la cantidad reservada y el orderId como reference_id
7. WHEN una Reserva se crea exitosamente, THE ms-inventory SHALL insertar un Evento_De_Dominio de tipo StockReserved en la tabla Outbox_Events dentro de la misma transacción
8. WHEN una Reserva falla por stock insuficiente, THE ms-inventory SHALL insertar un Evento_De_Dominio de tipo StockReserveFailed en la tabla Outbox_Events dentro de la misma transacción
9. THE ms-inventory SHALL ejecutar la verificación de stock, creación de Reserva, registro de Movimiento_De_Stock e inserción de Evento_De_Dominio dentro de una única transacción R2DBC ultra-corta
10. WHEN el available_quantity resultante después de la reserva es menor o igual al Umbral_Crítico del producto (campo `depletion_threshold`), THE ms-inventory SHALL insertar un Evento_De_Dominio adicional de tipo StockDepleted en la tabla Outbox_Events dentro de la misma transacción

### Requisito 5: Liberar reservas expiradas

**Historia de Usuario:** Como plataforma Arka, quiero que las reservas de stock que excedan los 15 minutos se liberen automáticamente para que el stock bloqueado vuelva a estar disponible para otros clientes.

#### Criterios de Aceptación

1. THE ms-inventory SHALL ejecutar un job periódico cada 60 segundos que identifique todas las Reservas con status PENDING cuyo campo expires_at sea anterior a la fecha y hora actual
2. WHEN el job identifica Reservas expiradas, THE ms-inventory SHALL cambiar el status de cada Reserva a EXPIRED y decrementar el reserved_quantity del Stock correspondiente en la cantidad de la Reserva
3. WHEN una Reserva se libera por expiración, THE ms-inventory SHALL registrar un Movimiento_De_Stock de tipo RESERVATION_RELEASE con la cantidad liberada y el orderId como reference_id
4. WHEN una Reserva se libera por expiración, THE ms-inventory SHALL insertar un Evento_De_Dominio de tipo StockReleased en la tabla Outbox_Events con la razón "RESERVATION_EXPIRED"
5. THE ms-inventory SHALL procesar cada Reserva expirada dentro de su propia transacción R2DBC para evitar que el fallo de una liberación afecte a las demás
6. WHEN el available_quantity resultante después de la liberación supera el Umbral_Crítico, THE ms-inventory SHALL registrar un log de nivel INFO indicando que el stock del SKU ha vuelto a niveles normales

### Requisito 6: Consumir evento ProductCreated para crear registro de stock

**Historia de Usuario:** Como ms-inventory, quiero reaccionar al evento ProductCreated publicado por ms-catalog para crear automáticamente el registro de stock inicial de un nuevo producto.

#### Criterios de Aceptación

1. WHEN ms-inventory recibe un evento de tipo ProductCreated del tópico `product-events`, THE ms-inventory SHALL crear un registro de Stock con el sku, product_id, quantity igual al initialStock del payload y reserved_quantity en 0
2. WHEN ms-inventory recibe un evento ProductCreated con un SKU que ya tiene registro de Stock, THE ms-inventory SHALL ignorar el evento y registrar un log de nivel WARN
3. WHEN el registro de Stock se crea exitosamente, THE ms-inventory SHALL registrar un Movimiento_De_Stock de tipo PRODUCT_CREATION con quantity_change igual al initialStock
4. WHEN ms-inventory recibe un evento ProductCreated, THE ms-inventory SHALL verificar en la tabla Processed_Events si el eventId ya fue procesado antes de ejecutar la lógica de negocio (idempotencia)
5. WHEN el eventId ya existe en Processed_Events, THE ms-inventory SHALL ignorar el evento sin error y registrar un log de nivel DEBUG
6. WHEN el evento se procesa exitosamente, THE ms-inventory SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción que la creación del Stock

### Requisito 7: Consumir evento OrderCancelled para liberar reserva

**Historia de Usuario:** Como ms-inventory, quiero reaccionar al evento OrderCancelled publicado por ms-order para liberar la reserva de stock asociada a la orden cancelada y devolver la disponibilidad.

#### Criterios de Aceptación

1. WHEN ms-inventory recibe un evento de tipo OrderCancelled del tópico `order-events`, THE ms-inventory SHALL buscar la Reserva asociada al orderId del payload con status PENDING
2. WHEN existe una Reserva PENDING para el orderId, THE ms-inventory SHALL cambiar el status de la Reserva a RELEASED, decrementar el reserved_quantity del Stock correspondiente y registrar un Movimiento_De_Stock de tipo RESERVATION_RELEASE
3. WHEN existe una Reserva PENDING para el orderId, THE ms-inventory SHALL insertar un Evento_De_Dominio de tipo StockReleased en la tabla Outbox_Events con la razón "ORDER_CANCELLED" dentro de la misma transacción
4. WHEN no existe una Reserva PENDING para el orderId del evento OrderCancelled, THE ms-inventory SHALL ignorar el evento y registrar un log de nivel WARN
5. WHEN ms-inventory recibe un evento OrderCancelled, THE ms-inventory SHALL verificar en la tabla Processed_Events si el eventId ya fue procesado antes de ejecutar la lógica de negocio (idempotencia)
6. WHEN el eventId ya existe en Processed_Events, THE ms-inventory SHALL ignorar el evento sin error y registrar un log de nivel DEBUG

### Requisito 8: Publicación de eventos mediante Transactional Outbox Pattern

**Historia de Usuario:** Como sistema distribuido, quiero garantizar la publicación confiable de eventos de dominio a Kafka para que otros microservicios reaccionen a cambios en el inventario sin pérdida de datos.

#### Criterios de Aceptación

1. THE ms-inventory SHALL insertar cada Evento_De_Dominio en la tabla Outbox_Events de PostgreSQL dentro de la misma transacción R2DBC que la escritura de negocio
2. THE Evento_De_Dominio SHALL seguir el sobre estándar con los campos: eventId (UUID), eventType, timestamp, source ("ms-inventory"), correlationId y payload
3. THE ms-inventory SHALL publicar eventos al tópico `inventory-events` de Kafka usando el SKU como partition key
4. THE Relay_Outbox SHALL consultar la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
5. WHEN un Evento_De_Dominio se publica exitosamente a Kafka, THE Relay_Outbox SHALL actualizar el campo status del evento en Outbox_Events de PENDING a PUBLISHED
6. IF el Relay_Outbox falla al enviar un evento a Kafka, THEN THE ms-inventory SHALL mantener el evento con status PENDING para reintento en el siguiente ciclo del relay
7. THE ms-inventory SHALL soportar cinco tipos de eventos: StockReserved, StockReserveFailed, StockReleased, StockUpdated y StockDepleted
8. THE Evento_De_Dominio StockReserved SHALL contener en su payload: sku, orderId, quantity y reservationId
9. THE Evento_De_Dominio StockReserveFailed SHALL contener en su payload: sku, orderId, requestedQuantity, availableQuantity y reason
10. THE Evento_De_Dominio StockReleased SHALL contener en su payload: sku, orderId, quantity y reason
11. THE Evento_De_Dominio StockUpdated SHALL contener en su payload: sku, previousQuantity, newQuantity y movementType
12. THE Evento_De_Dominio StockDepleted SHALL contener en su payload: sku, currentQuantity y threshold

### Requisito 9: Idempotencia en consumidores de Kafka

**Historia de Usuario:** Como ms-inventory, quiero garantizar que los eventos consumidos de Kafka se procesen exactamente una vez para evitar duplicación de operaciones como reservas dobles o liberaciones dobles de stock.

#### Criterios de Aceptación

1. THE ms-inventory SHALL verificar la existencia del eventId en la tabla Processed_Events antes de procesar cualquier evento consumido de Kafka
2. WHEN el eventId ya existe en Processed_Events, THE ms-inventory SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
3. WHEN un evento se procesa exitosamente, THE ms-inventory SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción que la operación de negocio
4. THE tabla Processed_Events SHALL tener el campo event_id como clave primaria (UUID) para garantizar unicidad a nivel de base de datos
5. THE ms-inventory SHALL filtrar eventos consumidos por el campo eventType del sobre estándar e ignorar tipos de evento desconocidos con un log de nivel WARN

### Requisito 10: Constraint de stock no negativo a nivel de base de datos

**Historia de Usuario:** Como plataforma Arka, quiero garantizar que el stock nunca sea negativo mediante un constraint a nivel de base de datos como última línea de defensa contra sobreventas.

#### Criterios de Aceptación

1. THE tabla stock de PostgreSQL SHALL tener un CHECK constraint que garantice que quantity sea mayor o igual a 0
2. THE tabla stock de PostgreSQL SHALL tener un CHECK constraint que garantice que reserved_quantity sea mayor o igual a 0
3. THE campo available_quantity de la tabla stock SHALL ser una columna generada calculada como quantity - reserved_quantity
4. IF una operación intenta establecer quantity o reserved_quantity en un valor que viole los CHECK constraints, THEN PostgreSQL SHALL rechazar la operación y THE ms-inventory SHALL traducir el error a una respuesta descriptiva con código HTTP 409 (Conflict)
5. THE ms-inventory SHALL validar las restricciones de stock no negativo a nivel de aplicación antes de ejecutar la operación en base de datos como primera línea de defensa

### Requisito 11: Manejo centralizado de errores

**Historia de Usuario:** Como consumidor de la API de inventario, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Criterios de Aceptación

1. THE ms-inventory SHALL implementar un GlobalExceptionHandler mediante `@ControllerAdvice` que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE ms-inventory SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de dominio (DomainException), THE ms-inventory SHALL retornar el código HTTP correspondiente con un ErrorResponse estandarizado
4. WHEN ocurre un error de constraint de base de datos por stock negativo, THE ms-inventory SHALL retornar código HTTP 409 (Conflict) con un ErrorResponse indicando la violación de stock
5. WHEN ocurre un error inesperado, THE ms-inventory SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
6. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)

### Requisito 12: Consumir evento OrderConfirmed para confirmar reservas de stock

**Historia de Usuario:** Como ms-inventory, quiero reaccionar al evento OrderConfirmed publicado por ms-order para confirmar definitivamente las reservas de stock asociadas a la orden, de forma que el job de expiración no las libere incorrectamente.

> **Contexto:** En Fase 1, ms-order publica `OrderConfirmed` inmediatamente tras la reserva exitosa (sin paso de pago intermedio). Sin este consumidor, todas las reservas de órdenes confirmadas permanecen en estado PENDING y el Scheduler_Expiración las destruye a los 15 minutos, liberando stock comprometido.

#### Criterios de Aceptación

1. WHEN ms-inventory recibe un evento de tipo OrderConfirmed del tópico `order-events`, THE ms-inventory SHALL buscar todas las Reservas con status PENDING asociadas al orderId del payload
2. WHEN existen Reservas PENDING para el orderId, THE ms-inventory SHALL cambiar el status de cada Reserva a CONFIRMED, impidiendo que el Scheduler_Expiración las procese (el scheduler solo procesa reservas con status PENDING cuyo expires_at sea anterior al momento actual)
3. WHEN se confirma una Reserva, THE ms-inventory SHALL registrar un Movimiento_De_Stock de tipo ORDER_CONFIRM por cada reserva confirmada, con la cantidad confirmada y el orderId como reference_id
4. WHEN no existen Reservas PENDING para el orderId del evento OrderConfirmed, THE ms-inventory SHALL ignorar el evento y registrar un log de nivel WARN indicando el orderId (escenario de confirmaciones tardías o duplicadas después de expiración)
5. WHEN ms-inventory recibe un evento OrderConfirmed, THE ms-inventory SHALL verificar en la tabla Processed_Events si el eventId ya fue procesado antes de ejecutar la lógica de negocio (idempotencia)
6. WHEN el eventId ya existe en Processed_Events, THE ms-inventory SHALL ignorar el evento sin error y registrar un log de nivel DEBUG
7. THE ms-inventory SHALL confirmar todas las Reservas PENDING del orderId e insertar el eventId en Processed_Events dentro de una única transacción R2DBC
8. THE confirmación de una Reserva NO modifica las cantidades de stock (quantity ni reserved_quantity en la tabla `stock`) — el stock ya fue contabilizado durante la reserva; el movimiento ORDER_CONFIRM es exclusivamente de auditoría con quantityChange = 0
