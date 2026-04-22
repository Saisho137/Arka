# Documento de Requisitos — ms-payment

## Introducción

El microservicio `ms-payment` es el dueño del dominio de Procesamiento de Pagos B2B dentro de la plataforma Arka. Su responsabilidad principal es actuar como Anti-Corruption Layer (ACL) para integrar múltiples pasarelas de pago externas (Stripe, Wompi, MercadoPago), aislar el dominio de las particularidades de los SDKs externos, y garantizar idempotencia rigurosa para prevenir cobros duplicados. Este servicio es un componente crítico de la Saga Secuencial orquestada por `ms-order` en la Fase 2 del proyecto. Consume eventos `OrderCreated` del tópico `order-events`, procesa pagos con la pasarela seleccionada, y publica eventos `PaymentProcessed` o `PaymentFailed` al tópico `payment-events` mediante el Transactional Outbox Pattern. Implementa patrones de resiliencia (Circuit Breaker, Bulkhead, Retry) con Resilience4j para manejar fallos de pasarelas externas, y utiliza PostgreSQL 17 con R2DBC para persistencia reactiva. Los SDKs bloqueantes de las pasarelas se aíslan con `Schedulers.boundedElastic()` para no bloquear el EventLoop de Netty.

## Glosario

- **Pago**: Registro de una transacción de pago almacenado en la tabla `payments` de PostgreSQL 17, con campos id, order_id, transaction_id, payment_method, amount, currency, status, failure_reason, created_at y updated_at
- **Transaction_ID**: Identificador único generado por la pasarela de pago externa (Stripe, Wompi, MercadoPago) que se almacena en el campo transaction_id de la tabla payments con constraint UNIQUE para garantizar idempotencia
- **Payment_Method**: Enumeración de métodos de pago soportados: stripe, wompi, mercadopago
- **Payment_Status**: Enumeración de estados de pago: PENDING, COMPLETED, FAILED
- **Pasarela_De_Pago**: Servicio externo de procesamiento de pagos (Stripe, Wompi, MercadoPago) integrado mediante SDK bloqueante
- **ACL**: Anti-Corruption Layer — patrón arquitectónico que aísla el dominio de las particularidades de APIs y SDKs externos mediante adaptadores específicos
- **Strategy_Pattern**: Patrón de diseño que permite seleccionar en runtime el algoritmo de procesamiento de pago según el Payment_Method especificado en el evento OrderCreated
- **Factory_Pattern**: Patrón de diseño que crea instancias de PaymentGateway según el Payment_Method, encapsulando la lógica de selección de pasarela
- **Circuit_Breaker**: Patrón de resiliencia implementado con Resilience4j que previene cascadas de fallos al abrir el circuito cuando el 50% de las llamadas a una pasarela fallan, manteniéndolo abierto por 30 segundos
- **Bulkhead**: Patrón de resiliencia que aísla recursos por pasarela para que el fallo de una no afecte a las demás
- **Retry_Policy**: Política de reintentos con backoff exponencial (2s, 4s, 8s) para llamadas fallidas a pasarelas, máximo 3 reintentos
- **Schedulers_BoundedElastic**: Scheduler de Project Reactor diseñado para operaciones bloqueantes, con pool de threads elástico y límite de 10x el número de CPUs
- **Outbox_Events**: Tabla PostgreSQL donde se insertan eventos de dominio atómicamente dentro de la misma transacción que la escritura de negocio, para ser publicados a Kafka por un relay asíncrono
- **Processed_Events**: Tabla PostgreSQL que almacena el event_id de cada evento consumido para garantizar idempotencia y evitar procesamiento duplicado
- **Evento_De_Dominio**: Mensaje publicado al tópico `payment-events` de Kafka con sobre estándar (eventId, eventType, timestamp, source, correlationId, payload)
- **Relay_Outbox**: Proceso asíncrono que consulta la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
- **Administrador**: Personal interno de Arka con rol ADMIN que gestiona pagos y puede reintentar pagos fallidos manualmente
- **Cliente_B2B**: Almacén o tienda en Colombia/LATAM con rol CUSTOMER que realiza órdenes de compra
- **API_Gateway**: Punto de entrada único que valida JWT, inyecta `X-User-Email` y enruta tráfico a la VPC privada
- **Controlador_REST**: Entry-point `@RestController` con retornos `Mono`/`Flux` que expone los endpoints HTTP del servicio
- **Saga_Secuencial**: Patrón de coordinación distribuida donde ms-order actúa como orquestador pasivo, emitiendo eventos que desencadenan acciones en ms-payment
- **Idempotencia**: Propiedad que garantiza que procesar el mismo evento o realizar la misma operación múltiples veces produce el mismo resultado que hacerlo una sola vez
- **Timeout**: Tiempo máximo de espera de 30 segundos para cada llamada a una pasarela de pago antes de considerarla fallida

## Requisitos

### Requisito 1: Consumir evento OrderCreated para procesar pago

**Historia de Usuario:** Como ms-payment, quiero consumir eventos OrderCreated publicados por ms-order para procesar el pago de la orden con la pasarela seleccionada y completar el flujo de la Saga Secuencial.

#### Criterios de Aceptación

1. THE ms-payment SHALL implementar un consumer de Kafka suscrito al consumer group `payment-service-group` que procese activamente eventos del tópico `order-events`
2. WHEN ms-payment recibe un evento con eventType igual a OrderCreated, THE ms-payment SHALL extraer del payload los campos orderId, customerId, totalAmount y paymentMethod
3. WHEN ms-payment recibe un evento OrderCreated, THE ms-payment SHALL verificar en la tabla Processed_Events si el eventId ya fue procesado antes de ejecutar la lógica de negocio (idempotencia)
4. WHEN el eventId ya existe en Processed_Events, THE ms-payment SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
5. WHEN ms-payment recibe un evento con eventType desconocido, THE ms-payment SHALL ignorar el evento y registrar un log de nivel WARN (tolerancia a evolución del esquema)
6. WHEN el evento OrderCreated se procesa exitosamente, THE ms-payment SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción que la operación de negocio
7. THE ms-payment SHALL diseñar el consumer con manejo de errores mediante `onErrorResume()` para que un evento fallido no detenga el procesamiento de eventos subsiguientes

### Requisito 2: Procesar pago con pasarela externa mediante ACL

**Historia de Usuario:** Como ms-payment, quiero procesar pagos con la pasarela especificada en el evento OrderCreated para completar la transacción de pago y aislar el dominio de las particularidades de los SDKs externos.

#### Criterios de Aceptación

1. WHEN ms-payment procesa un evento OrderCreated con paymentMethod igual a stripe, THE ms-payment SHALL invocar el SDK de Stripe envuelto en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty
2. WHEN ms-payment procesa un evento OrderCreated con paymentMethod igual a wompi, THE ms-payment SHALL invocar el SDK de Wompi envuelto en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty
3. WHEN ms-payment procesa un evento OrderCreated con paymentMethod igual a mercadopago, THE ms-payment SHALL invocar el SDK de MercadoPago envuelto en `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` para no bloquear el EventLoop de Netty
4. THE ms-payment SHALL implementar el patrón Strategy + Factory para seleccionar en runtime la implementación de PaymentGateway según el Payment_Method especificado
5. WHEN la pasarela procesa el pago exitosamente, THE ms-payment SHALL crear un registro de Pago con status COMPLETED, almacenar el Transaction_ID retornado por la pasarela, e insertar un Evento_De_Dominio de tipo PaymentProcessed en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
6. WHEN la pasarela rechaza el pago, THE ms-payment SHALL crear un registro de Pago con status FAILED, almacenar el failure_reason retornado por la pasarela, e insertar un Evento_De_Dominio de tipo PaymentFailed en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
7. WHEN la pasarela retorna un Transaction_ID que ya existe en la tabla payments (violación de constraint UNIQUE), THE ms-payment SHALL tratar la operación como idempotente, retornar el Pago existente sin crear uno nuevo, y NO insertar un nuevo evento en Outbox_Events
8. THE ms-payment SHALL aplicar un timeout de 30 segundos a cada llamada a pasarela mediante el operador `timeout()` de Reactor
9. WHEN el timeout se excede, THE ms-payment SHALL crear un registro de Pago con status FAILED y failure_reason indicando timeout, e insertar un Evento_De_Dominio de tipo PaymentFailed en la tabla Outbox_Events

### Requisito 3: Implementar Circuit Breaker para resiliencia ante fallos de pasarelas

**Historia de Usuario:** Como plataforma Arka, quiero que ms-payment implemente Circuit Breaker para prevenir cascadas de fallos cuando una pasarela de pago está experimentando problemas.

#### Criterios de Aceptación

1. THE ms-payment SHALL configurar un Circuit Breaker de Resilience4j por cada pasarela de pago (stripe, wompi, mercadopago) con failure-rate-threshold de 50%
2. WHEN el 50% de las llamadas a una pasarela fallan en una ventana de 10 requests, THE Circuit_Breaker SHALL transicionar al estado OPEN y rechazar llamadas subsiguientes sin invocar la pasarela por 30 segundos
3. WHILE el Circuit_Breaker está en estado OPEN, THE ms-payment SHALL retornar inmediatamente un error indicando que la pasarela no está disponible, crear un registro de Pago con status FAILED y failure_reason indicando circuit breaker open, e insertar un Evento_De_Dominio de tipo PaymentFailed en la tabla Outbox_Events
4. WHEN el Circuit_Breaker transiciona al estado HALF_OPEN después de 30 segundos, THE ms-payment SHALL permitir 3 llamadas de prueba a la pasarela
5. WHEN las 3 llamadas de prueba son exitosas, THE Circuit_Breaker SHALL transicionar al estado CLOSED y reanudar operación normal
6. WHEN alguna de las 3 llamadas de prueba falla, THE Circuit_Breaker SHALL transicionar nuevamente al estado OPEN por otros 30 segundos
7. THE ms-payment SHALL registrar transiciones de estado del Circuit_Breaker con nivel INFO en el log para monitoreo

### Requisito 4: Implementar Retry Policy con backoff exponencial

**Historia de Usuario:** Como ms-payment, quiero reintentar llamadas fallidas a pasarelas con backoff exponencial para manejar fallos transitorios sin sobrecargar las pasarelas.

#### Criterios de Aceptación

1. THE ms-payment SHALL configurar una Retry_Policy de Resilience4j con máximo 3 reintentos y backoff exponencial (2s, 4s, 8s)
2. WHEN una llamada a pasarela falla con error transitorio (timeout, conexión rechazada, error 5xx), THE ms-payment SHALL reintentar la llamada después de 2 segundos
3. WHEN el primer reintento falla, THE ms-payment SHALL reintentar después de 4 segundos
4. WHEN el segundo reintento falla, THE ms-payment SHALL reintentar después de 8 segundos
5. WHEN el tercer reintento falla, THE ms-payment SHALL considerar la operación como fallida definitivamente, crear un registro de Pago con status FAILED, e insertar un Evento_De_Dominio de tipo PaymentFailed en la tabla Outbox_Events
6. WHEN una llamada a pasarela falla con error no transitorio (error 4xx de validación, credenciales inválidas), THE ms-payment SHALL NO reintentar y fallar inmediatamente
7. THE ms-payment SHALL registrar cada reintento con nivel WARN en el log indicando el número de intento y el tiempo de espera

### Requisito 5: Implementar Bulkhead para aislamiento de recursos por pasarela

**Historia de Usuario:** Como plataforma Arka, quiero que ms-payment implemente Bulkhead para aislar recursos por pasarela de forma que el fallo de una no afecte a las demás.

#### Criterios de Aceptación

1. THE ms-payment SHALL configurar un Bulkhead de Resilience4j por cada pasarela de pago con límite de 10 llamadas concurrentes
2. WHEN el número de llamadas concurrentes a una pasarela alcanza el límite de 10, THE ms-payment SHALL rechazar llamadas adicionales sin invocar la pasarela hasta que se liberen recursos
3. WHEN una llamada es rechazada por Bulkhead, THE ms-payment SHALL crear un registro de Pago con status FAILED y failure_reason indicando bulkhead full, e insertar un Evento_De_Dominio de tipo PaymentFailed en la tabla Outbox_Events
4. THE ms-payment SHALL registrar rechazos por Bulkhead con nivel WARN en el log para monitoreo de capacidad

### Requisito 6: Publicación de eventos mediante Transactional Outbox Pattern

**Historia de Usuario:** Como sistema distribuido, quiero garantizar la publicación confiable de eventos de dominio a Kafka para que ms-order reaccione a los resultados de pago sin pérdida de datos.

#### Criterios de Aceptación

1. THE ms-payment SHALL insertar cada Evento_De_Dominio en la tabla Outbox_Events de PostgreSQL dentro de la misma transacción R2DBC que la escritura del Pago
2. THE Evento_De_Dominio SHALL seguir el sobre estándar con los campos: eventId (UUID), eventType, timestamp, source ("ms-payment"), correlationId y payload
3. THE ms-payment SHALL publicar eventos al tópico `payment-events` de Kafka usando el orderId como partition key para garantizar orden causal por orden
4. THE Relay_Outbox SHALL consultar la tabla Outbox_Events cada 5 segundos buscando eventos con status PENDING y publicarlos a Kafka
5. WHEN un Evento_De_Dominio se publica exitosamente a Kafka, THE Relay_Outbox SHALL actualizar el campo status del evento en Outbox_Events de PENDING a PUBLISHED
6. IF el Relay_Outbox falla al enviar un evento a Kafka, THEN THE ms-payment SHALL mantener el evento con status PENDING para reintento en el siguiente ciclo del relay
7. THE ms-payment SHALL soportar dos tipos de eventos: PaymentProcessed y PaymentFailed
8. THE Evento_De_Dominio PaymentProcessed SHALL contener en su payload: orderId, transactionId, amount, paymentMethod y timestamp
9. THE Evento_De_Dominio PaymentFailed SHALL contener en su payload: orderId, reason, paymentMethod y timestamp

### Requisito 7: Consultar estado de pago por orderId

**Historia de Usuario:** Como Administrador, quiero consultar el estado de pago de una orden por su orderId para verificar si el pago fue procesado exitosamente o falló.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /payments/{orderId}, THE Controlador_REST SHALL retornar el Pago asociado al orderId incluyendo id, orderId, transactionId, paymentMethod, amount, currency, status, failureReason, createdAt y updatedAt con código HTTP 200
2. WHEN el Administrador solicita un Pago con un orderId que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
3. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN

### Requisito 8: Listar pagos con filtros

**Historia de Usuario:** Como Administrador, quiero listar pagos con filtros por estado y método de pago para consultar el historial de transacciones de forma eficiente.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /payments, THE Controlador_REST SHALL retornar una lista paginada de Pagos ordenados por fecha de creación descendente con código HTTP 200
2. WHERE el parámetro de consulta status está presente, THE Controlador_REST SHALL filtrar los Pagos por el Payment_Status especificado
3. WHERE el parámetro de consulta paymentMethod está presente, THE Controlador_REST SHALL filtrar los Pagos por el Payment_Method especificado
4. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
5. WHEN el parámetro status contiene un valor que no corresponde a un Payment_Status válido, THE Controlador_REST SHALL retornar código HTTP 400 con un mensaje indicando los estados válidos
6. WHEN el parámetro paymentMethod contiene un valor que no corresponde a un Payment_Method válido, THE Controlador_REST SHALL retornar código HTTP 400 con un mensaje indicando los métodos válidos

### Requisito 9: Reintentar pago fallido manualmente

**Historia de Usuario:** Como Administrador, quiero reintentar manualmente un pago fallido para recuperar transacciones que fallaron por errores transitorios.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud POST /payments/retry/{orderId}, THE Controlador_REST SHALL buscar el Pago asociado al orderId con status FAILED
2. WHEN existe un Pago FAILED para el orderId, THE ms-payment SHALL reintentar el procesamiento del pago con la misma pasarela y monto original
3. WHEN el reintento es exitoso, THE ms-payment SHALL actualizar el status del Pago a COMPLETED, almacenar el nuevo Transaction_ID, e insertar un Evento_De_Dominio de tipo PaymentProcessed en la tabla Outbox_Events, todo dentro de la misma transacción R2DBC
4. WHEN el reintento falla, THE ms-payment SHALL mantener el status del Pago como FAILED, actualizar el failure_reason con el nuevo error, y NO insertar un nuevo evento en Outbox_Events
5. WHEN no existe un Pago FAILED para el orderId, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
6. WHEN existe un Pago para el orderId pero su status no es FAILED, THE Controlador_REST SHALL retornar código HTTP 409 (Conflict) con un mensaje indicando que solo se pueden reintentar pagos fallidos
7. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
8. THE Controlador_REST SHALL retornar el Pago actualizado con código HTTP 200 si el reintento es exitoso

### Requisito 10: Gestión de credenciales de pasarelas mediante AWS Secrets Manager

**Historia de Usuario:** Como plataforma Arka, quiero que las credenciales de las pasarelas de pago se almacenen en AWS Secrets Manager para garantizar seguridad y rotación de secretos.

#### Criterios de Aceptación

1. THE ms-payment SHALL recuperar las credenciales de Stripe, Wompi y MercadoPago desde AWS Secrets Manager al iniciar la aplicación
2. THE ms-payment SHALL utilizar LocalStack en el perfil `local` para simular AWS Secrets Manager en desarrollo
3. WHEN ms-payment no puede recuperar las credenciales de una pasarela desde Secrets Manager, THE ms-payment SHALL fallar al iniciar la aplicación con un mensaje de error descriptivo
4. THE ms-payment SHALL cachear las credenciales en memoria después de recuperarlas para evitar llamadas repetidas a Secrets Manager en cada transacción
5. THE ms-payment SHALL configurar las credenciales mediante las propiedades `payment.gateways.stripe.api-key`, `payment.gateways.wompi.api-key` y `payment.gateways.mercadopago.api-key` en application.yaml con valores de referencia a Secrets Manager

### Requisito 11: Manejo centralizado de errores

**Historia de Usuario:** Como consumidor de la API de pagos, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Criterios de Aceptación

1. THE ms-payment SHALL implementar un GlobalExceptionHandler mediante `@ControllerAdvice` que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE ms-payment SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de dominio (DomainException), THE ms-payment SHALL retornar el código HTTP correspondiente con un ErrorResponse estandarizado
4. WHEN ocurre un error de comunicación con una pasarela de pago, THE ms-payment SHALL retornar código HTTP 503 (Service Unavailable) con un ErrorResponse indicando la indisponibilidad de la pasarela
5. WHEN ocurre un error inesperado, THE ms-payment SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
6. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)
7. WHEN un Pago no se encuentra por su orderId, THE ms-payment SHALL retornar código HTTP 404 con un ErrorResponse descriptivo

### Requisito 12: Constraint de transaction_id único a nivel de base de datos

**Historia de Usuario:** Como plataforma Arka, quiero garantizar que no se registren pagos duplicados mediante un constraint UNIQUE en transaction_id como última línea de defensa contra cobros dobles.

#### Criterios de Aceptación

1. THE tabla payments de PostgreSQL SHALL tener un constraint UNIQUE en el campo transaction_id para prevenir cobros duplicados
2. IF una operación intenta insertar un Pago con un transaction_id que ya existe, THEN PostgreSQL SHALL rechazar la operación y THE ms-payment SHALL tratar el evento como idempotente sin retornar error
3. WHEN se detecta una violación de constraint UNIQUE en transaction_id, THE ms-payment SHALL registrar un log de nivel INFO indicando que el pago ya fue procesado previamente
4. THE ms-payment SHALL validar la unicidad de transaction_id a nivel de aplicación antes de ejecutar la operación en base de datos como primera línea de defensa

### Requisito 13: Parseo y validación de respuestas de pasarelas

**Historia de Usuario:** Como ms-payment, quiero parsear y validar las respuestas de las pasarelas de pago para extraer correctamente el Transaction_ID y el estado de la transacción.

#### Criterios de Aceptación

1. THE ms-payment SHALL implementar un Parser por cada pasarela (StripeResponseParser, WompiResponseParser, MercadoPagoResponseParser) que extraiga el Transaction_ID y el estado de la respuesta
2. WHEN una pasarela retorna una respuesta exitosa, THE Parser SHALL extraer el Transaction_ID y retornar un PaymentResult con status SUCCESS
3. WHEN una pasarela retorna una respuesta de error, THE Parser SHALL extraer el código de error y el mensaje, y retornar un PaymentResult con status FAILED y el failure_reason
4. WHEN una pasarela retorna una respuesta con formato inválido, THE Parser SHALL retornar un PaymentResult con status FAILED y failure_reason indicando error de parseo
5. THE ms-payment SHALL implementar un Pretty_Printer por cada pasarela que formatee los PaymentResult de vuelta al formato de la pasarela para logging y auditoría
6. FOR ALL PaymentResult válidos, parsear la respuesta de la pasarela, formatear con Pretty_Printer y parsear nuevamente SHALL producir un PaymentResult equivalente (round-trip property)

### Requisito 14: Logging estructurado con correlationId

**Historia de Usuario:** Como equipo de operaciones, quiero que ms-payment registre logs estructurados con correlationId para trazabilidad distribuida de transacciones de pago.

#### Criterios de Aceptación

1. THE ms-payment SHALL extraer el correlationId del sobre estándar de cada evento consumido de Kafka y propagarlo en el MDC de SLF4J
2. THE ms-payment SHALL incluir el correlationId en todos los logs generados durante el procesamiento de un evento
3. THE ms-payment SHALL generar un nuevo correlationId cuando procesa una solicitud HTTP que no tiene uno asociado
4. THE ms-payment SHALL incluir en los logs de nivel INFO: inicio de procesamiento de pago, llamada a pasarela, resultado de pago, publicación de evento
5. THE ms-payment SHALL incluir en los logs de nivel WARN: reintentos, circuit breaker open, bulkhead full, eventos desconocidos
6. THE ms-payment SHALL incluir en los logs de nivel ERROR: fallos inesperados, errores de parseo, violaciones de constraints
7. THE ms-payment SHALL utilizar formato JSON estructurado en el perfil `docker` y formato legible en el perfil `local`

