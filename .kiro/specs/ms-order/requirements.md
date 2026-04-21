# Documento de Requisitos — ms-order

## Introducción

El microservicio `ms-order` es el dueño del dominio de Gestión de Pedidos y actúa como orquestador pasivo de la Saga Secuencial dentro de la plataforma B2B Arka. Su responsabilidad principal es gestionar el ciclo de vida completo de las órdenes de compra: creación con múltiples productos, validación síncrona de stock vía gRPC contra `ms-inventory`, máquina de estados del pedido (sealed interface con pattern matching en Java 21), auditoría de transiciones y publicación de eventos de dominio al tópico `order-events` de Kafka mediante el Transactional Outbox Pattern. Este servicio cubre la HU4 (Registrar una orden de compra) de la Fase 1 (MVP). En Fase 1, el pago se gestiona como proceso externo B2B (facturación diferida a 30-60 días), por lo que las órdenes con stock reservado transicionan automáticamente a CONFIRMADO. La respuesta al cliente es 202 Accepted dado que los procesos asíncronos posteriores (notificaciones, eventos) aún están en cola.

## Glosario

- **Orden**: Registro de un pedido de compra B2B almacenado en la tabla `orders` de PostgreSQL 17, con campos id, customer_id, status, total_amount, customer_email, shipping_address, notes, created_at y updated_at
- **Item_De_Orden**: Línea de detalle de una Orden almacenada en la tabla `order_items`, con campos id, order_id, product_id, sku, product_name, quantity, unit_price y subtotal (columna generada: quantity \* unit_price)
- **Estado_De_Orden**: Sealed interface de Java 21 que modela los estados posibles del pedido: PENDIENTE_RESERVA, PENDIENTE_PAGO, CONFIRMADO, EN_DESPACHO, ENTREGADO y CANCELADO
- **PENDIENTE_RESERVA**: Estado efímero mientras se verifica la disponibilidad de stock vía gRPC contra ms-inventory
- **PENDIENTE_PAGO**: Estado que indica que el stock fue reservado exitosamente y la orden está esperando confirmación de pago desde ms-payment (Fase 2). En Fase 1, este estado no se usa y las órdenes transicionan directamente a CONFIRMADO
- **CONFIRMADO**: Estado que indica que el stock fue reservado exitosamente y el pago fue procesado. En Fase 1, el pago B2B es offline (facturación diferida 30-60 días) y las órdenes transicionan directamente desde PENDIENTE_RESERVA. En Fase 2, se alcanza después de recibir el evento PaymentProcessed desde ms-payment
- **EN_DESPACHO**: Estado asignado por el Administrador cuando la mercancía se despacha al almacén del Cliente_B2B
- **ENTREGADO**: Estado terminal asignado por el Administrador cuando confirma la recepción de la mercancía en el almacén del Cliente_B2B
- **CANCELADO**: Estado terminal alcanzado por stock insuficiente (fail-fast vía gRPC) o cancelación manual por Administrador o Cliente_B2B
- **Historial_De_Estados**: Registro de auditoría en la tabla `order_state_history` que documenta cada transición de estado con previous_status, new_status, changed_by y reason
- **Transición_Válida**: Cambio de estado permitido por la máquina de estados. Las transiciones válidas en Fase 1 son: PENDIENTE_RESERVA→CONFIRMADO, PENDIENTE_RESERVA→CANCELADO, CONFIRMADO→EN_DESPACHO, CONFIRMADO→CANCELADO, EN_DESPACHO→ENTREGADO. En Fase 2 se agregan: PENDIENTE_RESERVA→PENDIENTE_PAGO, PENDIENTE_PAGO→CONFIRMADO, PENDIENTE_PAGO→CANCELADO
- **Estado_Terminal**: Estado desde el cual no se permite ninguna transición. ENTREGADO y CANCELADO son estados terminales
- **Outbox_Events**: Tabla PostgreSQL donde se insertan eventos de dominio atómicamente dentro de la misma transacción que la escritura de negocio, para ser publicados a Kafka por un relay asíncrono
- **Processed_Events**: Tabla PostgreSQL que almacena el event_id de cada evento consumido para garantizar idempotencia y evitar procesamiento duplicado
- **Evento_De_Dominio**: Mensaje publicado al tópico `order-events` de Kafka con sobre estándar (eventId, eventType, timestamp, source, correlationId, payload)
- **Relay_Outbox**: Proceso asíncrono que consulta la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
- **Cliente_gRPC**: Componente de infraestructura que invoca el servicio ReserveStock de ms-inventory de forma síncrona para reservar stock antes de persistir la Orden
- **Administrador**: Personal interno de Arka con rol ADMIN que gestiona estados de pedidos (despacho, entrega, cancelación)
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM con rol CUSTOMER que crea órdenes de compra y consulta su estado
- **API_Gateway**: Punto de entrada único que valida JWT, inyecta `X-User-Email` y enruta tráfico a la VPC privada
- **Controlador_REST**: Entry-point `@RestController` con retornos `Mono`/`Flux` que expone los endpoints HTTP del servicio
- **Saga_Secuencial**: Patrón de coordinación distribuida donde ms-order actúa como orquestador pasivo, emitiendo eventos que desencadenan acciones en otros microservicios

## Requisitos

### Requisito 1: Crear orden de compra con múltiples productos

**Historia de Usuario:** Como Cliente_B2B, quiero registrar una orden de compra con múltiples productos para realizar mi pedido, validando la disponibilidad de stock en tiempo real.

#### Criterios de Aceptación

1. WHEN el Cliente_B2B envía una solicitud POST /orders con customerId, customerEmail, shippingAddress e items (cada uno con productId, sku y quantity), THE Controlador_REST SHALL validar los campos requeridos mediante Bean Validation y rechazar la solicitud con código HTTP 400 si algún campo es inválido
2. WHEN la solicitud es válida, THE ms-order SHALL crear una Orden en estado PENDIENTE_RESERVA y llamar al Cliente_gRPC para invocar ReserveStock en ms-inventory por cada Item_De_Orden
3. WHEN ms-inventory confirma la reserva de stock para todos los items vía gRPC, THE ms-order SHALL persistir la Orden con estado PENDIENTE_PAGO (Fase 2) o CONFIRMADO (Fase 1), los Items_De_Orden con sus unit_price y subtotal, un registro en Historial_De_Estados con la transición PENDIENTE_RESERVA→PENDIENTE_PAGO (Fase 2) o PENDIENTE_RESERVA→CONFIRMADO (Fase 1), y un Evento_De_Dominio de tipo OrderCreated en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
4. WHEN ms-inventory rechaza la reserva de stock para algún item vía gRPC por stock insuficiente, THE ms-order SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando el SKU y la cantidad disponible, sin persistir la Orden
5. THE ms-order SHALL calcular el total_amount de la Orden como la suma de los subtotales de todos los Items_De_Orden
6. WHEN la Orden se persiste exitosamente, THE Controlador_REST SHALL retornar código HTTP 202 (Accepted) con el orderId, status (PENDIENTE_PAGO en Fase 2 o CONFIRMADO en Fase 1), items con sus precios, totalAmount y createdAt
7. THE ms-order SHALL incluir en el payload del Evento_De_Dominio OrderCreated los campos: orderId, customerId, customerEmail, items (con sku, quantity, unitPrice) y totalAmount
8. WHEN la solicitud contiene una lista de items vacía, THE Controlador_REST SHALL rechazar la solicitud con código HTTP 400 y un mensaje indicando que se requiere al menos un item
9. WHEN ocurre un error de comunicación con ms-inventory vía gRPC, THE ms-order SHALL retornar código HTTP 503 (Service Unavailable) con un mensaje indicando que el servicio de inventario no está disponible

### Requisito 2: Consultar detalle de una orden

**Historia de Usuario:** Como Cliente_B2B o Administrador, quiero consultar el detalle de una orden por su identificador para conocer el estado actual, los productos y el monto total del pedido.

#### Criterios de Aceptación

1. WHEN un usuario autenticado envía una solicitud GET /orders/{id}, THE Controlador_REST SHALL retornar la Orden con sus Items_De_Orden, incluyendo orderId, customerId, status, totalAmount, shippingAddress, notes, items y createdAt con código HTTP 200
2. WHEN un usuario solicita una Orden con un id que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER y ADMIN
4. WHEN un Cliente_B2B solicita una Orden que pertenece a otro customerId, THE Controlador_REST SHALL retornar código HTTP 403 (Forbidden)

### Requisito 3: Listar órdenes con filtros

**Historia de Usuario:** Como Cliente_B2B o Administrador, quiero listar órdenes con filtros por estado y cliente para consultar el historial de pedidos de forma eficiente.

#### Criterios de Aceptación

1. WHEN un usuario autenticado envía una solicitud GET /orders, THE Controlador_REST SHALL retornar una lista paginada de Órdenes ordenadas por fecha de creación descendente con código HTTP 200
2. WHERE el parámetro de consulta status está presente, THE Controlador_REST SHALL filtrar las Órdenes por el Estado_De_Orden especificado
3. WHERE el parámetro de consulta customerId está presente, THE Controlador_REST SHALL filtrar las Órdenes por el customerId especificado
4. WHEN un Cliente_B2B envía la solicitud, THE Controlador_REST SHALL filtrar automáticamente las Órdenes por el customerId del usuario autenticado, ignorando el parámetro customerId si se proporciona
5. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER y ADMIN
6. WHEN el parámetro status contiene un valor que no corresponde a un Estado_De_Orden válido, THE Controlador_REST SHALL retornar código HTTP 400 con un mensaje indicando los estados válidos

### Requisito 4: Máquina de estados del pedido

**Historia de Usuario:** Como plataforma Arka, quiero que las transiciones de estado del pedido estén controladas por una máquina de estados para garantizar que solo se permitan cambios válidos y se mantenga la integridad del ciclo de vida de la orden.

#### Criterios de Aceptación

1. THE ms-order SHALL modelar el Estado_De_Orden como una sealed interface de Java 21 con los records: PendingReserve, PendingPayment, Confirmed, InShipment, Delivered y Cancelled, habilitando pattern matching exhaustivo en compile-time
2. THE ms-order SHALL permitir únicamente las siguientes Transiciones_Válidas: PENDIENTE_RESERVA→PENDIENTE_PAGO (Fase 2: gRPC exitoso con ms-payment disponible), PENDIENTE_RESERVA→CONFIRMADO (Fase 1: gRPC exitoso sin ms-payment), PENDIENTE_RESERVA→CANCELADO (stock insuficiente), PENDIENTE_PAGO→CONFIRMADO (PaymentProcessed recibido), PENDIENTE_PAGO→CANCELADO (PaymentFailed recibido), CONFIRMADO→EN_DESPACHO (Administrador marca despacho), CONFIRMADO→CANCELADO (Administrador o Cliente_B2B cancela), EN_DESPACHO→ENTREGADO (Administrador confirma entrega)
3. WHEN se intenta una transición no incluida en las Transiciones_Válidas, THE ms-order SHALL rechazar la operación con una excepción de dominio que se traduzca a código HTTP 409 (Conflict) con un mensaje indicando el estado actual y el estado destino inválido
4. THE ms-order SHALL tratar ENTREGADO y CANCELADO como Estados_Terminales desde los cuales no se permite ninguna transición
5. WHEN una Transición_Válida se ejecuta exitosamente, THE ms-order SHALL registrar un Historial_De_Estados con previous_status, new_status, changed_by (userId) y reason dentro de la misma transacción R2DBC
6. WHEN una Transición_Válida se ejecuta exitosamente, THE ms-order SHALL insertar un Evento_De_Dominio correspondiente en la tabla Outbox_Events dentro de la misma transacción R2DBC

### Requisito 5: Cambiar estado de una orden (despacho y entrega)

**Historia de Usuario:** Como Administrador, quiero cambiar el estado de una orden a EN_DESPACHO o ENTREGADO para reflejar el avance logístico del pedido hacia el almacén del Cliente_B2B.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud PUT /orders/{id}/status con el campo newStatus igual a EN_DESPACHO, THE Controlador_REST SHALL transicionar la Orden de CONFIRMADO a EN_DESPACHO y retornar la Orden actualizada con código HTTP 200
2. WHEN el Administrador envía una solicitud PUT /orders/{id}/status con el campo newStatus igual a ENTREGADO, THE Controlador_REST SHALL transicionar la Orden de EN_DESPACHO a ENTREGADO y retornar la Orden actualizada con código HTTP 200
3. WHEN el Administrador intenta cambiar el estado de una Orden que está en un Estado_Terminal, THE Controlador_REST SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que la orden está en un estado terminal
4. WHEN el Administrador intenta una transición no válida según la máquina de estados, THE Controlador_REST SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando la transición inválida
5. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
6. WHEN la transición se ejecuta exitosamente, THE ms-order SHALL insertar un Evento_De_Dominio de tipo OrderStatusChanged en la tabla Outbox_Events con los campos orderId, previousStatus, newStatus y customerEmail dentro de la misma transacción R2DBC
7. WHEN el Administrador intenta cambiar el estado de una Orden con un id que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo

### Requisito 6: Cancelar una orden

**Historia de Usuario:** Como Cliente_B2B o Administrador, quiero cancelar una orden que aún no ha sido despachada para liberar el stock reservado y detener el proceso de pedido.

#### Criterios de Aceptación

1. WHEN un usuario autenticado envía una solicitud PUT /orders/{id}/cancel con un campo reason, THE Controlador_REST SHALL transicionar la Orden a CANCELADO si el estado actual es CONFIRMADO, y retornar la Orden actualizada con código HTTP 200
1. WHEN un usuario intenta cancelar una Orden que está en estado EN_DESPACHO, ENTREGADO o CANCELADO, THE Controlador_REST SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que la orden no puede ser cancelada en su estado actual
2. WHEN un usuario intenta cancelar una Orden que está en estado PENDIENTE_PAGO (Fase 2), THE Controlador_REST SHALL transicionar la Orden a CANCELADO y retornar la Orden actualizada con código HTTP 200
3. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER y ADMIN
4. WHEN un Cliente_B2B intenta cancelar una Orden que pertenece a otro customerId, THE Controlador_REST SHALL retornar código HTTP 403 (Forbidden)
5. WHEN la cancelación se ejecuta exitosamente, THE ms-order SHALL insertar un Evento_De_Dominio de tipo OrderCancelled en la tabla Outbox_Events con los campos orderId, customerId, customerEmail y reason dentro de la misma transacción R2DBC
6. WHEN un usuario intenta cancelar una Orden con un id que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
7. THE ms-order SHALL incluir la reason proporcionada por el usuario en el registro de Historial_De_Estados y en el payload del Evento_De_Dominio OrderCancelled

### Requisito 7: Publicación de eventos mediante Transactional Outbox Pattern

**Historia de Usuario:** Como sistema distribuido, quiero garantizar la publicación confiable de eventos de dominio a Kafka para que otros microservicios (ms-notifications, ms-inventory, ms-reporter) reaccionen a cambios en las órdenes sin pérdida de datos.

#### Criterios de Aceptación

1. THE ms-order SHALL insertar cada Evento_De_Dominio en la tabla Outbox_Events de PostgreSQL dentro de la misma transacción R2DBC que la escritura de negocio
2. THE Evento_De_Dominio SHALL seguir el sobre estándar con los campos: eventId (UUID), eventType, timestamp, source ("ms-order"), correlationId y payload
3. THE ms-order SHALL publicar eventos al tópico `order-events` de Kafka usando el orderId como partition key para garantizar orden causal por orden
4. THE Relay_Outbox SHALL consultar la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
5. WHEN un Evento_De_Dominio se publica exitosamente a Kafka, THE Relay_Outbox SHALL actualizar el campo status del evento en Outbox_Events de PENDING a PUBLISHED
6. IF el Relay_Outbox falla al enviar un evento a Kafka, THEN THE ms-order SHALL mantener el evento con status PENDING para reintento en el siguiente ciclo del relay
7. THE ms-order SHALL soportar cinco tipos de eventos: OrderCreated, OrderConfirmed, OrderStatusChanged, OrderCancelled y PaymentRequested (Fase 2)
8. THE Evento_De_Dominio OrderCreated SHALL contener en su payload: orderId, customerId, customerEmail, items (con sku, quantity, unitPrice) y totalAmount
9. THE Evento_De_Dominio OrderConfirmed SHALL contener en su payload: orderId, customerId, customerEmail, items (con sku, quantity, unitPrice) y totalAmount
10. THE Evento_De_Dominio OrderStatusChanged SHALL contener en su payload: orderId, previousStatus, newStatus y customerEmail
11. THE Evento_De_Dominio OrderCancelled SHALL contener en su payload: orderId, customerId, customerEmail y reason
12. THE Evento_De_Dominio PaymentRequested (Fase 2) SHALL contener en su payload: orderId, customerId, totalAmount y paymentMethod

### Requisito 8: Consumo de eventos de Kafka (Fase 2+)

**Historia de Usuario:** Como ms-order, quiero consumir eventos de ms-payment para completar el flujo de pago y transicionar las órdenes de PENDIENTE_PAGO a CONFIRMADO o CANCELADO según el resultado del procesamiento de pago.

#### Criterios de Aceptación

1. THE ms-order SHALL implementar un consumer de Kafka suscrito al consumer group `order-service-group` que procese activamente eventos de los tópicos `payment-events` y `shipping-events`
2. WHEN ms-order recibe un evento PaymentProcessed del tópico `payment-events`, THE ms-order SHALL transicionar la Orden identificada por orderId de PENDIENTE_PAGO a CONFIRMADO, registrar un Historial_De_Estados con la transición, y emitir un Evento_De_Dominio OrderConfirmed en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
3. WHEN ms-order recibe un evento PaymentFailed del tópico `payment-events`, THE ms-order SHALL transicionar la Orden identificada por orderId de PENDIENTE_PAGO a CANCELADO, registrar un Historial_De_Estados con la transición y el reason del fallo de pago, y emitir un Evento_De_Dominio OrderCancelled en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
4. WHEN ms-order recibe un evento con eventType desconocido, THE ms-order SHALL ignorar el evento y registrar un log de nivel WARN (tolerancia a evolución del esquema)
5. THE ms-order SHALL implementar la tabla Processed_Events con event_id como clave primaria (UUID) para garantizar idempotencia en el consumo de eventos
6. WHEN ms-order recibe un evento cuyo eventId ya existe en Processed_Events, THE ms-order SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
7. WHEN un evento se procesa exitosamente, THE ms-order SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción que la operación de negocio
8. WHEN ms-order recibe un evento PaymentProcessed o PaymentFailed para una Orden que no está en estado PENDIENTE_PAGO, THE ms-order SHALL ignorar el evento y registrar un log de nivel WARN indicando el estado actual de la orden
9. THE ms-order SHALL diseñar el consumer con extensibilidad para incorporar el procesamiento de ShippingDispatched (Fase 3) sin modificar la estructura existente

### Requisito 9: Validación síncrona de stock vía gRPC

**Historia de Usuario:** Como ms-order, quiero validar la disponibilidad de stock de forma síncrona mediante gRPC contra ms-inventory para garantizar que todos los productos solicitados estén disponibles antes de confirmar la orden.

#### Criterios de Aceptación

1. WHEN se crea una Orden, THE ms-order SHALL invocar el servicio ReserveStock de ms-inventory vía gRPC por cada Item_De_Orden, enviando sku, orderId y quantity
2. WHEN ms-inventory responde exitosamente para todos los items, THE ms-order SHALL proceder a persistir la Orden con estado CONFIRMADO
3. WHEN ms-inventory responde con fallo para algún item por stock insuficiente, THE ms-order SHALL abortar la creación de la Orden sin persistir datos y retornar el detalle del rechazo al Cliente_B2B (fail-fast)
4. IF el Cliente_gRPC no puede establecer conexión con ms-inventory, THEN THE ms-order SHALL retornar un error con código HTTP 503 indicando que el servicio de inventario no está disponible
5. THE ms-order SHALL ejecutar las llamadas gRPC de forma reactiva, integrándolas en la cadena de Mono/Flux sin bloquear el EventLoop de Netty
6. WHEN ms-inventory responde con fallo parcial (algunos items con stock y otros sin stock), THE ms-order SHALL reportar al Cliente_B2B todos los items con stock insuficiente en una sola respuesta de error

### Requisito 10: Manejo centralizado de errores

**Historia de Usuario:** Como consumidor de la API de órdenes, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Criterios de Aceptación

1. THE ms-order SHALL implementar un GlobalExceptionHandler mediante `@ControllerAdvice` que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE ms-order SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de transición de estado inválida, THE ms-order SHALL retornar código HTTP 409 (Conflict) con un ErrorResponse indicando el estado actual y la transición intentada
4. WHEN ocurre un error de comunicación gRPC con ms-inventory, THE ms-order SHALL retornar código HTTP 503 (Service Unavailable) con un ErrorResponse indicando la indisponibilidad del servicio
5. WHEN ocurre un error inesperado, THE ms-order SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
6. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)
7. WHEN una Orden no se encuentra por su id, THE ms-order SHALL retornar código HTTP 404 con un ErrorResponse descriptivo
8. WHEN un Cliente_B2B intenta acceder a una Orden que no le pertenece, THE ms-order SHALL retornar código HTTP 403 con un ErrorResponse indicando acceso denegado
