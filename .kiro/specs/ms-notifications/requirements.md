# Documento de Requisitos — ms-notifications

## Introducción

El microservicio `ms-notifications` es el motor pasivo de notificaciones transaccionales de la plataforma B2B Arka. Su responsabilidad exclusiva es consumir eventos de dominio desde múltiples tópicos de Apache Kafka, mapear los datos del evento a plantillas de email almacenadas en MongoDB, y enviar correos electrónicos transaccionales mediante AWS SES. Este servicio no expone endpoints REST — opera de forma puramente event-driven como consumidor "Catch-All". Implementa idempotencia garantizada mediante un índice único sobre `eventId` en la colección `notification_history` de MongoDB para evitar envío de correos duplicados por reintentos de Kafka (at-least-once delivery). Cubre la HU6 (Notificación de cambio de estado del pedido) de la Fase 1 (MVP). El SDK de AWS SES es bloqueante y se envuelve con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty. La colección `notification_history` tiene un TTL Index de 90 días para limpieza automática.

## Glosario

- **Evento_De_Dominio**: Mensaje recibido desde un tópico de Kafka con el sobre estándar que contiene los campos eventId (UUID), eventType, timestamp, source, correlationId y payload
- **Sobre_Estándar**: Formato unificado de todos los eventos del ecosistema Arka: { eventId, eventType, timestamp, source, correlationId, payload }
- **Plantilla**: Documento almacenado en la colección `templates` de MongoDB con campos \_id, eventType (índice único), subject (cadena con variables {{variable}}), bodyTemplate (HTML con variables {{variable}}), active y createdAt
- **Variable_De_Plantilla**: Marcador con formato `{{nombreVariable}}` dentro del subject o bodyTemplate de una Plantilla que se sustituye por valores extraídos del payload del Evento_De_Dominio
- **Historial_De_Notificación**: Documento almacenado en la colección `notification_history` de MongoDB con campos \_id, eventId (índice único para idempotencia), eventType, orderId, customerEmail, status (SENT o FAILED), processedAt y createdAt
- **Consumidor_Kafka**: Entry-point reactivo suscrito al consumer group `notification-service-group` que recibe eventos de los tópicos `order-events` e `inventory-events` (Fase 1), con extensibilidad para `cart-events`, `shipping-events` y `provider-events` (fases posteriores)
- **Servicio_SES**: Componente de infraestructura (driven-adapter) que envuelve el SDK bloqueante de AWS SES con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para enviar correos sin bloquear el EventLoop
- **Backoff_Exponencial**: Estrategia de reintentos donde el tiempo de espera entre intentos crece exponencialmente (ejemplo: 1s, 2s, 4s) ante fallos transitorios del Servicio_SES
- **TTL_Index**: Índice de MongoDB configurado en la colección `notification_history` con `expireAfterSeconds: 7776000` (90 días) que elimina automáticamente documentos antiguos
- **Idempotencia**: Garantía de que un Evento_De_Dominio con el mismo eventId se procesa una única vez, verificada mediante el índice único sobre eventId en la colección `notification_history`
- **Administrador**: Personal interno de Arka con rol ADMIN que recibe alertas de stock bajo
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM que recibe notificaciones sobre el estado de sus pedidos
- **Motor_De_Plantillas**: Componente del dominio (usecase) que resuelve las Variables_De_Plantilla sustituyendo los marcadores `{{variable}}` por los valores correspondientes del payload del Evento_De_Dominio

## Requisitos

### Requisito 1: Consumo de eventos de Kafka con idempotencia

**Historia de Usuario:** Como plataforma Arka, quiero que ms-notifications consuma eventos de dominio desde Kafka de forma idempotente para garantizar que cada notificación se envíe exactamente una vez, incluso ante reintentos de red.

#### Criterios de Aceptación

1. THE Consumidor_Kafka SHALL suscribirse al consumer group `notification-service-group` y consumir eventos de los tópicos `order-events` e `inventory-events` en Fase 1
2. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio, THE ms-notifications SHALL deserializar el Sobre_Estándar y extraer el campo eventType para determinar si el evento es relevante
3. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio con un eventType relevante, THE ms-notifications SHALL consultar la colección `notification_history` buscando un documento con el mismo eventId
4. WHEN existe un documento en `notification_history` con el eventId recibido, THE ms-notifications SHALL descartar el evento sin enviar correo y registrar un log de nivel DEBUG con el mensaje "Evento duplicado descartado: {eventId}"
5. WHEN no existe un documento en `notification_history` con el eventId recibido, THE ms-notifications SHALL proceder con el flujo de envío de notificación
6. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio con un eventType desconocido o no relevante, THE ms-notifications SHALL ignorar el evento y registrar un log de nivel WARN con el eventType recibido (tolerancia a evolución del esquema)
7. THE ms-notifications SHALL filtrar los siguientes eventTypes como relevantes en Fase 1: OrderConfirmed, OrderStatusChanged, OrderCancelled y StockDepleted

### Requisito 2: Gestión de plantillas de email en MongoDB

**Historia de Usuario:** Como plataforma Arka, quiero que las plantillas de email se almacenen como documentos JSON en MongoDB para permitir la gestión dinámica de contenido sin redespliegue del servicio.

#### Criterios de Aceptación

1. THE ms-notifications SHALL almacenar las Plantillas en la colección `templates` de MongoDB con los campos: eventType (índice único), subject, bodyTemplate (HTML), active y createdAt
2. WHEN el ms-notifications necesita enviar una notificación para un eventType, THE ms-notifications SHALL buscar la Plantilla activa correspondiente en la colección `templates` filtrando por eventType y active igual a true
3. IF no existe una Plantilla activa para el eventType recibido, THEN THE ms-notifications SHALL registrar un log de nivel ERROR indicando la ausencia de plantilla para el eventType y registrar el Historial_De_Notificación con status FAILED
4. THE ms-notifications SHALL soportar Variables_De_Plantilla con formato `{{nombreVariable}}` tanto en el campo subject como en el campo bodyTemplate de la Plantilla
5. THE Motor_De_Plantillas SHALL sustituir cada Variable_De_Plantilla por el valor correspondiente extraído del payload del Evento_De_Dominio
6. IF una Variable_De_Plantilla referenciada en la Plantilla no tiene un valor correspondiente en el payload del Evento_De_Dominio, THEN THE Motor_De_Plantillas SHALL sustituir la variable por una cadena vacía y registrar un log de nivel WARN indicando la variable faltante

### Requisito 3: Envío de emails transaccionales mediante AWS SES

**Historia de Usuario:** Como plataforma Arka, quiero enviar correos electrónicos transaccionales mediante AWS SES de forma reactiva para notificar a los clientes B2B y administradores sobre eventos relevantes sin bloquear el EventLoop.

#### Criterios de Aceptación

1. THE Servicio_SES SHALL envolver cada llamada al SDK bloqueante de AWS SES con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para ejecutar el envío en un thread pool dedicado sin bloquear el EventLoop de Netty
2. WHEN el Motor_De_Plantillas resuelve exitosamente la Plantilla con los datos del payload, THE Servicio_SES SHALL enviar un correo electrónico al destinatario especificado en el campo customerEmail del payload del Evento_De_Dominio
3. WHEN el Servicio_SES envía el correo exitosamente, THE ms-notifications SHALL insertar un Historial_De_Notificación en la colección `notification_history` con status SENT, el eventId, eventType, orderId, customerEmail y processedAt
4. IF el Servicio_SES falla al enviar el correo, THEN THE ms-notifications SHALL aplicar Backoff_Exponencial con un máximo de 3 reintentos (intervalos de 1s, 2s, 4s) antes de registrar el fallo
5. IF el envío falla tras agotar todos los reintentos, THEN THE ms-notifications SHALL insertar un Historial_De_Notificación en la colección `notification_history` con status FAILED y registrar un log de nivel ERROR con el eventId, eventType y el mensaje de error
6. THE Servicio_SES SHALL obtener las credenciales de AWS SES desde AWS Secrets Manager
7. THE ms-notifications SHALL utilizar una dirección de remitente configurable mediante propiedades de Spring Boot para el campo "from" de los correos enviados

### Requisito 4: Procesamiento de evento OrderConfirmed

**Historia de Usuario:** Como Cliente_B2B, quiero recibir un correo electrónico de confirmación cuando mi pedido es confirmado para tener constancia de que el stock fue reservado exitosamente.

#### Criterios de Aceptación

1. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio con eventType OrderConfirmed del tópico `order-events`, THE ms-notifications SHALL extraer del payload los campos orderId, customerId, customerEmail, items (con sku, quantity, unitPrice) y totalAmount
2. WHEN el evento OrderConfirmed pasa la verificación de idempotencia, THE ms-notifications SHALL buscar la Plantilla activa con eventType "OrderConfirmed" y resolver las Variables_De_Plantilla con los datos del payload
3. WHEN la Plantilla se resuelve exitosamente, THE Servicio_SES SHALL enviar el correo de confirmación al customerEmail extraído del payload
4. THE Plantilla de OrderConfirmed SHALL incluir en el subject el orderId y en el bodyTemplate el detalle de items con sku, quantity, unitPrice y el totalAmount del pedido

### Requisito 5: Procesamiento de evento OrderStatusChanged

**Historia de Usuario:** Como Cliente_B2B, quiero recibir un correo electrónico cuando el estado de mi pedido cambia a EN_DESPACHO o ENTREGADO para estar informado del progreso logístico.

#### Criterios de Aceptación

1. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio con eventType OrderStatusChanged del tópico `order-events`, THE ms-notifications SHALL extraer del payload los campos orderId, previousStatus, newStatus y customerEmail
2. WHEN el evento OrderStatusChanged pasa la verificación de idempotencia, THE ms-notifications SHALL buscar la Plantilla activa con eventType "OrderStatusChanged" y resolver las Variables_De_Plantilla con los datos del payload
3. WHEN la Plantilla se resuelve exitosamente, THE Servicio_SES SHALL enviar el correo de actualización de estado al customerEmail extraído del payload
4. THE Plantilla de OrderStatusChanged SHALL incluir en el subject el orderId y el newStatus, y en el bodyTemplate el previousStatus y newStatus del pedido

### Requisito 6: Procesamiento de evento OrderCancelled

**Historia de Usuario:** Como Cliente_B2B, quiero recibir un correo electrónico cuando mi pedido es cancelado para conocer el motivo de la cancelación.

#### Criterios de Aceptación

1. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio con eventType OrderCancelled del tópico `order-events`, THE ms-notifications SHALL extraer del payload los campos orderId, customerId, customerEmail y reason
2. WHEN el evento OrderCancelled pasa la verificación de idempotencia, THE ms-notifications SHALL buscar la Plantilla activa con eventType "OrderCancelled" y resolver las Variables_De_Plantilla con los datos del payload
3. WHEN la Plantilla se resuelve exitosamente, THE Servicio_SES SHALL enviar el correo de cancelación al customerEmail extraído del payload
4. THE Plantilla de OrderCancelled SHALL incluir en el subject el orderId y en el bodyTemplate el reason de la cancelación

### Requisito 7: Procesamiento de evento StockDepleted

**Historia de Usuario:** Como Administrador, quiero recibir un correo electrónico de alerta cuando el stock de un producto alcanza niveles críticos para tomar decisiones de reabastecimiento oportunas.

#### Criterios de Aceptación

1. WHEN el Consumidor_Kafka recibe un Evento_De_Dominio con eventType StockDepleted del tópico `inventory-events`, THE ms-notifications SHALL extraer del payload los campos sku, productName, currentQuantity y threshold
2. WHEN el evento StockDepleted pasa la verificación de idempotencia, THE ms-notifications SHALL buscar la Plantilla activa con eventType "StockDepleted" y resolver las Variables_De_Plantilla con los datos del payload
3. WHEN la Plantilla se resuelve exitosamente, THE Servicio_SES SHALL enviar el correo de alerta a la dirección de email del Administrador configurada mediante propiedades de Spring Boot
4. THE Plantilla de StockDepleted SHALL incluir en el subject el sku del producto y en el bodyTemplate el productName, currentQuantity y threshold

### Requisito 8: Registro de historial de notificaciones con TTL

**Historia de Usuario:** Como plataforma Arka, quiero mantener un historial de notificaciones enviadas con limpieza automática a 90 días para auditoría y diagnóstico sin acumulación indefinida de datos.

#### Criterios de Aceptación

1. THE ms-notifications SHALL almacenar cada notificación procesada (exitosa o fallida) como un Historial_De_Notificación en la colección `notification_history` con los campos: eventId, eventType, orderId, customerEmail, status (SENT o FAILED), processedAt y createdAt
2. THE colección `notification_history` SHALL tener un índice único sobre el campo eventId para garantizar la Idempotencia a nivel de base de datos
3. THE colección `notification_history` SHALL tener un TTL_Index configurado con `expireAfterSeconds: 7776000` (90 días) sobre el campo createdAt para la eliminación automática de documentos antiguos
4. WHEN se inserta un Historial_De_Notificación con un eventId que ya existe en la colección, THE ms-notifications SHALL capturar la excepción de clave duplicada y tratarla como evento duplicado sin propagar el error

### Requisito 9: Estrategia de reintentos con backoff exponencial

**Historia de Usuario:** Como plataforma Arka, quiero que ms-notifications reintente el envío de correos ante fallos transitorios de AWS SES para maximizar la tasa de entrega de notificaciones.

#### Criterios de Aceptación

1. WHEN el Servicio_SES falla al enviar un correo por un error transitorio (timeout de red, throttling de SES, error HTTP 5xx), THE ms-notifications SHALL reintentar el envío aplicando Backoff_Exponencial
2. THE Backoff_Exponencial SHALL configurarse con un máximo de 3 reintentos, un intervalo base de 1 segundo y un factor multiplicador de 2 (intervalos resultantes: 1s, 2s, 4s)
3. WHEN el envío tiene éxito en cualquiera de los reintentos, THE ms-notifications SHALL registrar el Historial_De_Notificación con status SENT
4. IF el envío falla tras agotar los 3 reintentos, THEN THE ms-notifications SHALL registrar el Historial_De_Notificación con status FAILED y registrar un log de nivel ERROR
5. THE ms-notifications SHALL distinguir entre errores transitorios (reintentar) y errores permanentes como dirección de email inválida (registrar FAILED sin reintentar)

### Requisito 10: Extensibilidad para eventos de fases posteriores

**Historia de Usuario:** Como plataforma Arka, quiero que ms-notifications esté diseñado para incorporar nuevos tipos de eventos de fases posteriores sin modificar la arquitectura existente.

#### Criterios de Aceptación

1. THE Consumidor_Kafka SHALL diseñarse con extensibilidad para suscribirse a los tópicos `cart-events` (Fase 2), `shipping-events` (Fase 3) y `provider-events` (Fase 4) sin modificar la lógica de consumo existente
2. THE ms-notifications SHALL resolver el procesamiento de cada eventType mediante el patrón Strategy + Factory, donde cada estrategia extrae los campos relevantes del payload y determina el destinatario del correo
3. WHEN se agrega un nuevo eventType en una fase posterior, THE ms-notifications SHALL requerir únicamente la creación de una nueva estrategia de procesamiento y una nueva Plantilla en la colección `templates`, sin modificar el flujo principal de consumo ni la lógica de idempotencia
4. THE ms-notifications SHALL soportar los siguientes eventTypes en fases posteriores: CartAbandoned (Fase 2, tópico `cart-events`, email recordatorio al Cliente_B2B), ShippingDispatched (Fase 3, tópico `shipping-events`, email con tracking al Cliente_B2B) y PurchaseOrderCreated (Fase 4, tópico `provider-events`, email de orden de compra al proveedor)
