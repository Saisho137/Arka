# Requirements Document

## Introduction

El microservicio `ms-shipping` es el dueño del dominio de Logística y Despacho dentro de la plataforma B2B Arka. Su responsabilidad principal es actuar como Anti-Corruption Layer (ACL) para integrar múltiples operadores logísticos externos (DHL, FedEx) y sistemas legacy de logística, aislar el dominio de las particularidades de los SDKs externos, y gestionar el ciclo de vida completo del despacho de órdenes confirmadas. Este servicio es un componente crítico de la Saga Secuencial orquestada por `ms-order` en la Fase 3 del proyecto. Consume eventos `OrderConfirmed` del tópico `order-events`, genera etiquetas de envío con el operador logístico seleccionado, actualiza el tracking de entregas, y publica eventos `ShippingDispatched` al tópico `shipping-events` mediante el Transactional Outbox Pattern. Implementa patrones de resiliencia (Circuit Breaker, Bulkhead, Retry) con Resilience4j para manejar fallos de operadores logísticos externos, y utiliza PostgreSQL 17 con R2DBC para persistencia reactiva. Los SDKs bloqueantes de los operadores se aíslan con `Schedulers.boundedElastic()` para no bloquear el EventLoop de Netty.

## Glossary

- **Envío**: Registro de un despacho logístico almacenado en la tabla `shipments` de PostgreSQL 17, con campos id, order_id, carrier, tracking_number, shipping_label_url, status, delivery_address, estimated_delivery_date, actual_delivery_date, failure_reason, created_at y updated_at
- **Tracking_Number**: Identificador único generado por el operador logístico externo (DHL, FedEx) que se almacena en el campo tracking_number de la tabla shipments con constraint UNIQUE para garantizar idempotencia
- **Carrier**: Enumeración de operadores logísticos soportados: DHL, FEDEX, LEGACY
- **Shipping_Status**: Enumeración de estados de envío: PENDING, LABEL_GENERATED, IN_TRANSIT, DELIVERED, FAILED
- **Operador_Logístico**: Servicio externo de logística (DHL, FedEx) o sistema legacy integrado mediante SDK bloqueante
- **ACL**: Anti-Corruption Layer — patrón arquitectónico que aísla el dominio de las particularidades de APIs y SDKs externos mediante adaptadores específicos
- **Strategy_Pattern**: Patrón de diseño que permite seleccionar en runtime el algoritmo de procesamiento de envío según el Carrier especificado
- **Factory_Pattern**: Patrón de diseño que crea instancias de ShippingCarrier según el Carrier, encapsulando la lógica de selección de operador
- **Circuit_Breaker**: Patrón de resiliencia implementado con Resilience4j que previene cascadas de fallos al abrir el circuito cuando el 50% de las llamadas a un operador fallan, manteniéndolo abierto por 30 segundos
- **Bulkhead**: Patrón de resiliencia que aísla recursos por operador para que el fallo de uno no afecte a los demás
- **Retry_Policy**: Política de reintentos con backoff exponencial (2s, 4s, 8s) para llamadas fallidas a operadores, máximo 3 reintentos
- **Schedulers_BoundedElastic**: Scheduler de Project Reactor diseñado para operaciones bloqueantes, con pool de threads elástico y límite de 10x el número de CPUs
- **Outbox_Events**: Tabla PostgreSQL donde se insertan eventos de dominio atómicamente dentro de la misma transacción que la escritura de negocio, para ser publicados a Kafka por un relay asíncrono
- **Processed_Events**: Tabla PostgreSQL que almacena el event_id de cada evento consumido para garantizar idempotencia y evitar procesamiento duplicado
- **Evento_De_Dominio**: Mensaje publicado al tópico `shipping-events` de Kafka con sobre estándar (eventId, eventType, timestamp, source, correlationId, payload)
- **Relay_Outbox**: Proceso asíncrono que consulta la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
- **Administrador**: Personal interno de Arka con rol ADMIN que gestiona envíos y puede actualizar el estado de entregas manualmente
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM con rol CUSTOMER que consulta el estado de sus envíos
- **API_Gateway**: Punto de entrada único que valida JWT, inyecta `X-User-Email` y enruta tráfico a la VPC privada
- **Controlador_REST**: Entry-point `@RestController` con retornos `Mono`/`Flux` que expone los endpoints HTTP del servicio
- **Saga_Secuencial**: Patrón de coordinación distribuida donde ms-order actúa como orquestador pasivo, emitiendo eventos que desencadenan acciones en ms-shipping
- **Idempotencia**: Propiedad que garantiza que procesar el mismo evento o realizar la misma operación múltiples veces produce el mismo resultado que hacerlo una sola vez
- **Timeout**: Tiempo máximo de espera de 30 segundos para cada llamada a un operador logístico antes de considerarla fallida
- **Dirección_De_Entrega**: Objeto con campos street, city, state, postal_code y country que representa la dirección de destino del envío
- **Etiqueta_De_Envío**: Documento PDF generado por el operador logístico que contiene el código de barras y la información del envío, almacenado en AWS S3 con URL en shipping_label_url
- **Webhook**: Endpoint HTTP expuesto por ms-shipping para recibir notificaciones de actualización de estado desde los operadores logísticos

## Requirements

### Requirement 1: Consume OrderConfirmed event to initiate shipment

**User Story:** As ms-shipping, I want to consume OrderConfirmed events published by ms-order to initiate the shipment logistics process and complete the Sequential Saga flow.

#### Acceptance Criteria

1. THE ms-shipping SHALL implementar un consumer de Kafka suscrito al consumer group `shipping-service-group` que procese activamente eventos del tópico `order-events`
2. WHEN ms-shipping recibe un evento con eventType igual a OrderConfirmed, THE ms-shipping SHALL extraer del payload los campos orderId, customerId, deliveryAddress y preferredCarrier
3. WHEN ms-shipping recibe un evento OrderConfirmed, THE ms-shipping SHALL verificar en la tabla Processed_Events si el eventId ya fue procesado antes de ejecutar la lógica de negocio (idempotencia)
4. WHEN el eventId ya existe en Processed_Events, THE ms-shipping SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
5. WHEN ms-shipping recibe un evento con eventType desconocido, THE ms-shipping SHALL ignorar el evento y registrar un log de nivel WARN (tolerancia a evolución del esquema)
6. WHEN el evento OrderConfirmed se procesa exitosamente, THE ms-shipping SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción que la operación de negocio
7. THE ms-shipping SHALL diseñar el consumer con manejo de errores mediante `onErrorResume()` para que un evento fallido no detenga el procesamiento de eventos subsiguientes

### Requirement 2: Generar etiqueta de envío con operador logístico mediante ACL

**User Story:** Como ms-shipping, quiero generar etiquetas de envío con el operador logístico especificado para obtener el tracking number y el documento de envío necesario para el despacho físico.

#### Acceptance Criteria

1. WHEN ms-shipping procesa un evento OrderConfirmed con preferredCarrier igual a DHL, THE ms-shipping SHALL invocar el SDK de DHL envuelto en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty
2. WHEN ms-shipping procesa un evento OrderConfirmed con preferredCarrier igual a FEDEX, THE ms-shipping SHALL invocar el SDK de FedEx envuelto en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty
3. WHEN ms-shipping procesa un evento OrderConfirmed con preferredCarrier igual a LEGACY, THE ms-shipping SHALL invocar el sistema legacy de logística envuelto en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty
4. THE ms-shipping SHALL implementar el patrón Strategy + Factory para seleccionar en runtime la implementación de ShippingCarrier según el Carrier especificado
5. WHEN el operador genera la etiqueta exitosamente, THE ms-shipping SHALL crear un registro de Envío con status LABEL_GENERATED, almacenar el Tracking_Number retornado por el operador, subir la Etiqueta_De_Envío a AWS S3, almacenar la URL en shipping_label_url, e insertar un Evento_De_Dominio de tipo ShippingDispatched en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
6. WHEN el operador falla al generar la etiqueta, THE ms-shipping SHALL crear un registro de Envío con status FAILED, almacenar el failure_reason retornado por el operador, y NO insertar evento en Outbox_Events
7. WHEN el operador retorna un Tracking_Number que ya existe en la tabla shipments (violación de constraint UNIQUE), THE ms-shipping SHALL tratar la operación como idempotente, retornar el Envío existente sin crear uno nuevo, y NO insertar un nuevo evento en Outbox_Events
8. THE ms-shipping SHALL aplicar un timeout de 30 segundos a cada llamada a operador mediante el operador `timeout()` de Reactor
9. WHEN el timeout se excede, THE ms-shipping SHALL crear un registro de Envío con status FAILED y failure_reason indicando timeout, sin insertar evento en Outbox_Events
10. THE ms-shipping SHALL calcular el estimated_delivery_date basándose en la fecha actual más el tiempo de entrega estimado retornado por el operador logístico

### Requirement 3: Implementar Circuit Breaker para resiliencia ante fallos de operadores

**User Story:** Como plataforma Arka, quiero que ms-shipping implemente Circuit Breaker para prevenir cascadas de fallos cuando un operador logístico está experimentando problemas.

#### Acceptance Criteria

1. THE ms-shipping SHALL configurar un Circuit Breaker de Resilience4j por cada operador logístico (DHL, FEDEX, LEGACY) con failure-rate-threshold de 50%
2. WHEN el 50% de las llamadas a un operador fallan en una ventana de 10 requests, THE Circuit_Breaker SHALL transicionar al estado OPEN y rechazar llamadas subsiguientes sin invocar el operador por 30 segundos
3. WHILE el Circuit_Breaker está en estado OPEN, THE ms-shipping SHALL retornar inmediatamente un error indicando que el operador no está disponible, crear un registro de Envío con status FAILED y failure_reason indicando circuit breaker open, sin insertar evento en Outbox_Events
4. WHEN el Circuit_Breaker transiciona al estado HALF_OPEN después de 30 segundos, THE ms-shipping SHALL permitir 3 llamadas de prueba al operador
5. WHEN las 3 llamadas de prueba son exitosas, THE Circuit_Breaker SHALL transicionar al estado CLOSED y reanudar operación normal
6. WHEN alguna de las 3 llamadas de prueba falla, THE Circuit_Breaker SHALL transicionar nuevamente al estado OPEN por otros 30 segundos
7. THE ms-shipping SHALL registrar transiciones de estado del Circuit_Breaker con nivel INFO en el log para monitoreo

### Requirement 4: Implementar Retry Policy con backoff exponencial

**User Story:** Como ms-shipping, quiero reintentar llamadas fallidas a operadores con backoff exponencial para manejar fallos transitorios sin sobrecargar los operadores.

#### Acceptance Criteria

1. THE ms-shipping SHALL configurar una Retry_Policy de Resilience4j con máximo 3 reintentos y backoff exponencial (2s, 4s, 8s)
2. WHEN una llamada a operador falla con error transitorio (timeout, conexión rechazada, error 5xx), THE ms-shipping SHALL reintentar la llamada después de 2 segundos
3. WHEN el primer reintento falla, THE ms-shipping SHALL reintentar después de 4 segundos
4. WHEN el segundo reintento falla, THE ms-shipping SHALL reintentar después de 8 segundos
5. WHEN el tercer reintento falla, THE ms-shipping SHALL considerar la operación como fallida definitivamente, crear un registro de Envío con status FAILED, sin insertar evento en Outbox_Events
6. WHEN una llamada a operador falla con error no transitorio (error 4xx de validación, credenciales inválidas, dirección inválida), THE ms-shipping SHALL NO reintentar y fallar inmediatamente
7. THE ms-shipping SHALL registrar cada reintento con nivel WARN en el log indicando el número de intento y el tiempo de espera

### Requirement 5: Implementar Bulkhead para aislamiento de recursos por operador

**User Story:** Como plataforma Arka, quiero que ms-shipping implemente Bulkhead para aislar recursos por operador de forma que el fallo de uno no afecte a los demás.

#### Acceptance Criteria

1. THE ms-shipping SHALL configurar un Bulkhead de Resilience4j por cada operador logístico con límite de 10 llamadas concurrentes
2. WHEN el número de llamadas concurrentes a un operador alcanza el límite de 10, THE ms-shipping SHALL rechazar llamadas adicionales sin invocar el operador hasta que se liberen recursos
3. WHEN una llamada es rechazada por Bulkhead, THE ms-shipping SHALL crear un registro de Envío con status FAILED y failure_reason indicando bulkhead full, sin insertar evento en Outbox_Events
4. THE ms-shipping SHALL registrar rechazos por Bulkhead con nivel WARN en el log para monitoreo de capacidad

### Requirement 6: Publicación de eventos mediante Transactional Outbox Pattern

**User Story:** Como sistema distribuido, quiero garantizar la publicación confiable de eventos de dominio a Kafka para que ms-order reaccione a los despachos exitosos sin pérdida de datos.

#### Acceptance Criteria

1. THE ms-shipping SHALL insertar cada Evento_De_Dominio en la tabla Outbox_Events de PostgreSQL dentro de la misma transacción R2DBC que la escritura del Envío
2. THE Evento_De_Dominio SHALL seguir el sobre estándar con los campos: eventId (UUID), eventType, timestamp, source ("ms-shipping"), correlationId y payload
3. THE ms-shipping SHALL publicar eventos al tópico `shipping-events` de Kafka usando el orderId como partition key para garantizar orden causal por orden
4. THE Relay_Outbox SHALL consultar la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
5. WHEN un Evento_De_Dominio se publica exitosamente a Kafka, THE Relay_Outbox SHALL actualizar el campo status del evento en Outbox_Events de PENDING a PUBLISHED
6. IF el Relay_Outbox falla al enviar un evento a Kafka, THEN THE ms-shipping SHALL mantener el evento con status PENDING para reintento en el siguiente ciclo del relay
7. THE ms-shipping SHALL soportar un tipo de evento: ShippingDispatched
8. THE Evento_De_Dominio ShippingDispatched SHALL contener en su payload: orderId, trackingNumber, carrier, estimatedDeliveryDate, shippingLabelUrl y timestamp

### Requirement 7: Consultar estado de envío por orderId

**User Story:** Como Cliente_B2B o Administrador, quiero consultar el estado de envío de una orden por su orderId para verificar el tracking number y la fecha estimada de entrega.

#### Acceptance Criteria

1. WHEN un usuario autenticado envía una solicitud GET /shipments/{orderId}, THE Controlador_REST SHALL retornar el Envío asociado al orderId incluyendo id, orderId, carrier, trackingNumber, shippingLabelUrl, status, deliveryAddress, estimatedDeliveryDate, actualDeliveryDate, failureReason, createdAt y updatedAt con código HTTP 200
2. WHEN un usuario solicita un Envío con un orderId que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. THE Controlador_REST SHALL permitir el acceso a usuarios con rol CUSTOMER (solo sus propias órdenes) y ADMIN (todas las órdenes)
4. WHEN un Cliente_B2B solicita un Envío de una orden que no le pertenece, THE Controlador_REST SHALL retornar código HTTP 403 (Forbidden)

### Requirement 8: Listar envíos con filtros

**User Story:** Como Administrador, quiero listar envíos con filtros por estado y operador para consultar el historial de despachos de forma eficiente.

#### Acceptance Criteria

1. WHEN el Administrador envía una solicitud GET /shipments, THE Controlador_REST SHALL retornar una lista paginada de Envíos ordenados por fecha de creación descendente con código HTTP 200
2. WHERE el parámetro de consulta status está presente, THE Controlador_REST SHALL filtrar los Envíos por el Shipping_Status especificado
3. WHERE el parámetro de consulta carrier está presente, THE Controlador_REST SHALL filtrar los Envíos por el Carrier especificado
4. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
5. WHEN el parámetro status contiene un valor que no corresponde a un Shipping_Status válido, THE Controlador_REST SHALL retornar código HTTP 400 con un mensaje indicando los estados válidos
6. WHEN el parámetro carrier contiene un valor que no corresponde a un Carrier válido, THE Controlador_REST SHALL retornar código HTTP 400 con un mensaje indicando los operadores válidos

### Requirement 9: Actualizar estado de envío manualmente

**User Story:** Como Administrador, quiero actualizar manualmente el estado de un envío para marcar entregas completadas o registrar fallos de entrega.

#### Acceptance Criteria

1. WHEN el Administrador envía una solicitud PUT /shipments/{orderId}/status con un Shipping_Status válido, THE Controlador_REST SHALL actualizar el campo status del Envío en PostgreSQL y retornar el Envío actualizado con código HTTP 200
2. THE Controlador_REST SHALL validar que el campo status esté presente y sea un Shipping_Status válido mediante Bean Validation
3. WHEN el Administrador actualiza el status a DELIVERED, THE ms-shipping SHALL establecer el campo actual_delivery_date con la fecha y hora actual
4. WHEN el Administrador actualiza el status a FAILED, THE ms-shipping SHALL requerir el campo failure_reason en el request body
5. WHEN el Administrador intenta actualizar el Envío de un orderId que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
6. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
7. WHEN el status se actualiza exitosamente a IN_TRANSIT o DELIVERED, THE ms-shipping SHALL registrar un log de nivel INFO con el orderId y el nuevo estado

### Requirement 10: Recibir actualizaciones de tracking mediante webhook

**User Story:** Como ms-shipping, quiero recibir notificaciones de actualización de estado desde los operadores logísticos mediante webhooks para mantener el tracking actualizado en tiempo real.

#### Acceptance Criteria

1. THE ms-shipping SHALL exponer un endpoint POST /webhooks/tracking que acepte notificaciones de actualización de estado desde operadores logísticos
2. WHEN ms-shipping recibe una notificación webhook con un trackingNumber válido, THE ms-shipping SHALL buscar el Envío asociado al trackingNumber en la tabla shipments
3. WHEN existe un Envío con el trackingNumber, THE ms-shipping SHALL actualizar el campo status del Envío según el estado reportado por el operador
4. WHEN el operador reporta estado "delivered", THE ms-shipping SHALL actualizar el status a DELIVERED y establecer actual_delivery_date con la fecha reportada por el operador
5. WHEN el operador reporta estado "in_transit", THE ms-shipping SHALL actualizar el status a IN_TRANSIT
6. WHEN no existe un Envío con el trackingNumber reportado, THE ms-shipping SHALL registrar un log de nivel WARN y retornar código HTTP 404
7. THE ms-shipping SHALL validar la autenticidad de las notificaciones webhook mediante firma HMAC usando un secret compartido con cada operador
8. WHEN la firma HMAC es inválida, THE ms-shipping SHALL rechazar la notificación con código HTTP 401 (Unauthorized)
9. THE ms-shipping SHALL retornar código HTTP 200 para notificaciones procesadas exitosamente, independientemente de si el estado cambió o no (idempotencia)
10. THE ms-shipping SHALL implementar el parseo de notificaciones webhook mediante un Parser por cada operador (DHLWebhookParser, FedExWebhookParser, LegacyWebhookParser) que normalice el formato específico del operador al modelo de dominio

### Requirement 11: Gestión de credenciales de operadores mediante AWS Secrets Manager

**User Story:** Como plataforma Arka, quiero que las credenciales de los operadores logísticos se almacenen en AWS Secrets Manager para garantizar seguridad y rotación de secretos.

#### Acceptance Criteria

1. THE ms-shipping SHALL recuperar las credenciales de DHL, FedEx y el sistema legacy desde AWS Secrets Manager al iniciar la aplicación
2. THE ms-shipping SHALL utilizar LocalStack en el perfil `local` para simular AWS Secrets Manager en desarrollo
3. WHEN ms-shipping no puede recuperar las credenciales de un operador desde Secrets Manager, THE ms-shipping SHALL fallar al iniciar la aplicación con un mensaje de error descriptivo
4. THE ms-shipping SHALL cachear las credenciales en memoria después de recuperarlas para evitar llamadas repetidas a Secrets Manager en cada transacción
5. THE ms-shipping SHALL configurar las credenciales mediante las propiedades `shipping.carriers.dhl.api-key`, `shipping.carriers.fedex.api-key` y `shipping.carriers.legacy.api-key` en application.yaml con valores de referencia a Secrets Manager
6. THE ms-shipping SHALL recuperar también los webhook secrets desde Secrets Manager para validar las notificaciones de los operadores

### Requirement 12: Manejo centralizado de errores

**User Story:** Como consumidor de la API de envíos, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Acceptance Criteria

1. THE ms-shipping SHALL implementar un GlobalExceptionHandler mediante `@ControllerAdvice` que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE ms-shipping SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de dominio (DomainException), THE ms-shipping SHALL retornar el código HTTP correspondiente con un ErrorResponse estandarizado
4. WHEN ocurre un error de comunicación con un operador logístico, THE ms-shipping SHALL retornar código HTTP 503 (Service Unavailable) con un ErrorResponse indicando la indisponibilidad del operador
5. WHEN ocurre un error inesperado, THE ms-shipping SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
6. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)
7. WHEN un Envío no se encuentra por su orderId, THE ms-shipping SHALL retornar código HTTP 404 con un ErrorResponse descriptivo

### Requirement 13: Constraint de tracking_number único a nivel de base de datos

**User Story:** Como plataforma Arka, quiero garantizar que no se registren envíos duplicados mediante un constraint UNIQUE en tracking_number como última línea de defensa contra despachos dobles.

#### Acceptance Criteria

1. THE tabla shipments de PostgreSQL SHALL tener un constraint UNIQUE en el campo tracking_number para prevenir despachos duplicados
2. IF una operación intenta insertar un Envío con un tracking_number que ya existe, THEN PostgreSQL SHALL rechazar la operación y THE ms-shipping SHALL tratar el evento como idempotente sin retornar error
3. WHEN se detecta una violación de constraint UNIQUE en tracking_number, THE ms-shipping SHALL registrar un log de nivel INFO indicando que el envío ya fue procesado previamente
4. THE ms-shipping SHALL validar la unicidad de tracking_number a nivel de aplicación antes de ejecutar la operación en base de datos como primera línea de defensa

### Requirement 14: Parseo y validación de respuestas de operadores

**User Story:** Como ms-shipping, quiero parsear y validar las respuestas de los operadores logísticos para extraer correctamente el Tracking_Number y la etiqueta de envío.

#### Acceptance Criteria

1. THE ms-shipping SHALL implementar un Parser por cada operador (DHLResponseParser, FedExResponseParser, LegacyResponseParser) que extraiga el Tracking_Number, la etiqueta de envío y el tiempo estimado de entrega de la respuesta
2. WHEN un operador retorna una respuesta exitosa, THE Parser SHALL extraer el Tracking_Number, el documento PDF de la etiqueta y el estimated_delivery_date, y retornar un ShippingResult con status SUCCESS
3. WHEN un operador retorna una respuesta de error, THE Parser SHALL extraer el código de error y el mensaje, y retornar un ShippingResult con status FAILED y el failure_reason
4. WHEN un operador retorna una respuesta con formato inválido, THE Parser SHALL retornar un ShippingResult con status FAILED y failure_reason indicando error de parseo
5. THE ms-shipping SHALL implementar un Pretty_Printer por cada operador que formatee los ShippingResult de vuelta al formato del operador para logging y auditoría
6. FOR ALL ShippingResult válidos, parsear la respuesta del operador, formatear con Pretty_Printer y parsear nuevamente SHALL producir un ShippingResult equivalente (round-trip property)

### Requirement 15: Almacenamiento de etiquetas de envío en AWS S3

**User Story:** Como plataforma Arka, quiero almacenar las etiquetas de envío en AWS S3 para garantizar disponibilidad y durabilidad de los documentos de despacho.

#### Acceptance Criteria

1. WHEN ms-shipping recibe una etiqueta de envío en formato PDF desde un operador logístico, THE ms-shipping SHALL subir el documento a AWS S3 en el bucket `arka-shipping-labels`
2. THE ms-shipping SHALL generar la clave del objeto S3 con el formato `{orderId}/{trackingNumber}.pdf` para organización y trazabilidad
3. WHEN la subida a S3 es exitosa, THE ms-shipping SHALL almacenar la URL pública del objeto en el campo shipping_label_url del Envío
4. WHEN la subida a S3 falla, THE ms-shipping SHALL registrar un log de nivel ERROR y retornar un error indicando fallo en almacenamiento de etiqueta
5. THE ms-shipping SHALL utilizar LocalStack en el perfil `local` para simular AWS S3 en desarrollo
6. THE ms-shipping SHALL configurar el bucket S3 mediante la propiedad `shipping.s3.bucket-name` en application.yaml
7. THE ms-shipping SHALL establecer permisos de lectura pública en los objetos subidos para que los clientes puedan descargar las etiquetas sin autenticación adicional

### Requirement 16: Logging estructurado con correlationId

**User Story:** Como equipo de operaciones, quiero que ms-shipping registre logs estructurados con correlationId para trazabilidad distribuida de transacciones de envío.

#### Acceptance Criteria

1. THE ms-shipping SHALL extraer el correlationId del sobre estándar de cada evento consumido de Kafka y propagarlo en el MDC de SLF4J
2. THE ms-shipping SHALL incluir el correlationId en todos los logs generados durante el procesamiento de un evento
3. THE ms-shipping SHALL generar un nuevo correlationId cuando procesa una solicitud HTTP que no tiene uno asociado
4. THE ms-shipping SHALL incluir en los logs de nivel INFO: inicio de procesamiento de envío, llamada a operador, resultado de generación de etiqueta, publicación de evento
5. THE ms-shipping SHALL incluir en los logs de nivel WARN: reintentos, circuit breaker open, bulkhead full, eventos desconocidos, webhooks con trackingNumber no encontrado
6. THE ms-shipping SHALL incluir en los logs de nivel ERROR: fallos inesperados, errores de parseo, violaciones de constraints, fallos de subida a S3
7. THE ms-shipping SHALL utilizar formato JSON estructurado en el perfil `docker` y formato legible en el perfil `local`

### Requirement 17: Reintentar generación de etiqueta fallida manualmente

**User Story:** Como Administrador, quiero reintentar manualmente la generación de etiqueta de un envío fallido para recuperar despachos que fallaron por errores transitorios.

#### Acceptance Criteria

1. WHEN el Administrador envía una solicitud POST /shipments/retry/{orderId}, THE Controlador_REST SHALL buscar el Envío asociado al orderId con status FAILED
2. WHEN existe un Envío FAILED para el orderId, THE ms-shipping SHALL reintentar la generación de etiqueta con el mismo operador y dirección de entrega original
3. WHEN el reintento es exitoso, THE ms-shipping SHALL actualizar el status del Envío a LABEL_GENERATED, almacenar el nuevo Tracking_Number, subir la nueva etiqueta a S3, actualizar shipping_label_url, e insertar un Evento_De_Dominio de tipo ShippingDispatched en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
4. WHEN el reintento falla, THE ms-shipping SHALL mantener el status del Envío como FAILED, actualizar el failure_reason con el nuevo error, y NO insertar un nuevo evento en Outbox_Events
5. WHEN no existe un Envío FAILED para el orderId, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
6. WHEN existe un Envío para el orderId pero su status no es FAILED, THE Controlador_REST SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que solo se pueden reintentar envíos fallidos
7. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
8. THE Controlador_REST SHALL retornar el Envío actualizado con código HTTP 200 si el reintento es exitoso

### Requirement 18: Validación de dirección de entrega

**User Story:** Como ms-shipping, quiero validar la dirección de entrega antes de generar la etiqueta para prevenir fallos de despacho por direcciones inválidas o incompletas.

#### Acceptance Criteria

1. WHEN ms-shipping procesa un evento OrderConfirmed, THE ms-shipping SHALL validar que la Dirección_De_Entrega contenga todos los campos requeridos: street, city, state, postal_code y country
2. WHEN algún campo requerido de la Dirección_De_Entrega está ausente o vacío, THE ms-shipping SHALL crear un registro de Envío con status FAILED y failure_reason indicando "Invalid delivery address: missing {field}", sin invocar al operador logístico
3. WHEN el campo country de la Dirección_De_Entrega no es "CO" (Colombia), THE ms-shipping SHALL crear un registro de Envío con status FAILED y failure_reason indicando "Delivery outside Colombia not supported", sin invocar al operador logístico
4. WHEN el campo postal_code no cumple con el formato de código postal colombiano (5 dígitos), THE ms-shipping SHALL crear un registro de Envío con status FAILED y failure_reason indicando "Invalid postal code format", sin invocar al operador logístico
5. THE ms-shipping SHALL registrar un log de nivel WARN cuando se rechaza un envío por dirección inválida, incluyendo el orderId y la razón del rechazo
