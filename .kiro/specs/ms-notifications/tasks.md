# Plan de Implementación: ms-notifications

## Visión General

Implementación incremental del microservicio `ms-notifications` siguiendo Clean Architecture (Bancolombia Scaffold). Se construye desde el dominio hacia afuera: modelo → motor de plantillas → estrategias → caso de uso → adaptadores driven → consumidor Kafka. No expone endpoints REST — es puramente event-driven. Java 21, Spring Boot 4.0.3, Spring WebFlux, Reactive MongoDB, AWS SES (bloqueante envuelto en `Schedulers.boundedElastic()`), Kafka consumer, jqwik para PBT.

## Tareas

- [ ] 1. Configurar estructura del proyecto y dependencias
  - [ ] 1.1 Configurar `build.gradle` con dependencias: Spring WebFlux, Reactive MongoDB Driver, Spring Kafka, AWS SES SDK, jqwik, reactor-test, Lombok, BlockHound
    - Verificar compatibilidad con Spring Boot 4.0.3 y Scaffold 4.2.0
    - Agregar `net.jqwik:jqwik:1.9.2` en testImplementation
    - _Requisitos: transversal_
  - [ ] 1.2 Configurar `application.yml` con conexión Reactive MongoDB (`notifications_db`), propiedades de Kafka (bootstrap-servers, consumer group `notification-service-group`, tópicos `order-events` e `inventory-events`), dirección de remitente SES (`notification.from-address`), email de administrador (`notification.admin-email`) y configuración de reintentos (`notification.retry.*`)
    - Configurar perfiles `default` y `local`
    - _Requisitos: 1.1, 3.6, 3.7, 7.3, 9.2_
  - [ ] 1.3 Crear script de inicialización de MongoDB con los índices de las colecciones `templates` (índice único sobre `eventType`) y `notification_history` (índice único sobre `eventId`, TTL Index de 90 días sobre `createdAt` con `expireAfterSeconds: 7776000`)
    - Ubicar en `ms-notifications/applications/app-service/src/main/resources/`
    - _Requisitos: 2.1, 8.2, 8.3_

- [ ] 2. Implementar modelo de dominio (`domain/model`)
  - [ ] 2.1 Crear los records de dominio: `NotificationTemplate` (id, eventType, subject, bodyTemplate, active, createdAt con validación en compact constructor y `@Builder(toBuilder=true)`), `NotificationHistory` (id, eventId, eventType, orderId, customerEmail, status, processedAt, createdAt), `NotificationStatus` (enum SENT, FAILED), `DomainEvent` (sobre estándar: eventId, eventType, timestamp, source, correlationId, payload como `Map<String, Object>`) y `NotificationContext` (recipientEmail, orderId, templateVariables como `Map<String, String>`)
    - Validaciones con `Objects.requireNonNull` en compact constructors
    - Defaults para `createdAt` y `processedAt` en compact constructor
    - Ubicar en `com.arka.model.template`, `com.arka.model.history`, `com.arka.model.notification`
    - _Requisitos: 2.1, 8.1_
  - [ ] 2.2 Crear las interfaces de gateway (ports): `NotificationTemplateRepository` (con `findActiveByEventType(String eventType)` retornando `Mono<NotificationTemplate>`), `NotificationHistoryRepository` (con `existsByEventId(String eventId)` retornando `Mono<Boolean>` y `save(NotificationHistory)` retornando `Mono<NotificationHistory>`), `EmailSenderPort` (con `sendEmail(String to, String subject, String htmlBody)` retornando `Mono<Void>`)
    - Ubicar en `com.arka.model.<aggregate>.gateways`
    - _Requisitos: transversal_
  - [ ] 2.3 Crear la jerarquía de excepciones de dominio: `DomainException` (abstract), `TemplateNotFoundException` (code: TEMPLATE_NOT_FOUND), `EmailSendException` (code: EMAIL_SEND_FAILED, con campo `boolean transient` para distinguir errores transitorios de permanentes)
    - _Requisitos: 2.3, 3.4, 9.5_

- [ ] 3. Implementar motor de plantillas y estrategias (`domain/usecase`)
  - [ ] 3.1 Implementar `TemplateEngine`: componente de dominio que sustituye cada `{{variable}}` en subject y bodyTemplate por el valor correspondiente del mapa de variables. Usar regex `\{\{(\w+)\}\}` para encontrar marcadores. Si una variable no tiene valor en el mapa, reemplazar por cadena vacía y registrar log WARN indicando la variable faltante.
    - Método `resolve(String template, Map<String, String> variables)` retorna String
    - _Requisitos: 2.4, 2.5, 2.6_

  - [ ]* 3.2 Escribir test de propiedad para round-trip del motor de plantillas
    - **Propiedad 3: Round-trip del motor de plantillas**
    - Generar plantillas con N variables `{{variable}}` y mapas de variables completos/parciales. Verificar que el resultado no contiene marcadores `{{...}}` cuando el mapa es completo, y que cada valor del mapa aparece en el resultado. Variables faltantes se reemplazan por cadena vacía.
    - **Valida: Requisitos 2.4, 2.5, 2.6**

  - [ ] 3.3 Implementar la interfaz funcional `EventStrategy` con método `extractContext(Map<String, Object> payload)` que retorna `NotificationContext`
    - Ubicar en `com.arka.usecase.strategy`
    - _Requisitos: 10.2_
  - [ ] 3.4 Implementar las 4 estrategias de Fase 1: `OrderConfirmedStrategy` (extrae orderId, customerId, customerEmail, items, totalAmount → destinatario: customerEmail), `OrderStatusChangedStrategy` (extrae orderId, previousStatus, newStatus, customerEmail → destinatario: customerEmail), `OrderCancelledStrategy` (extrae orderId, customerId, customerEmail, reason → destinatario: customerEmail), `StockDepletedStrategy` (extrae sku, productName, currentQuantity, threshold → destinatario: email admin configurable via propiedad Spring Boot)
    - Cada estrategia construye `NotificationContext` con `recipientEmail` y `templateVariables`
    - `StockDepletedStrategy` recibe el email del administrador por constructor
    - _Requisitos: 4.1, 4.2, 5.1, 5.2, 6.1, 6.2, 7.1, 7.2_

  - [ ]* 3.5 Escribir test de propiedad para extracción de campos por estrategia
    - **Propiedad 4: Estrategia extrae campos correctos por tipo de evento**
    - Generar payloads válidos por tipo de evento con valores aleatorios. Verificar que la estrategia correspondiente produce un `NotificationContext` con `recipientEmail` no vacío y `templateVariables` conteniendo todas las claves esperadas (OrderConfirmed: orderId, customerId, customerEmail, items, totalAmount; OrderStatusChanged: orderId, previousStatus, newStatus; OrderCancelled: orderId, reason; StockDepleted: sku, productName, currentQuantity, threshold).
    - **Valida: Requisitos 4.1, 5.1, 6.1, 7.1**

  - [ ] 3.6 Implementar `EventStrategyFactory`: factory con `Map<String, Supplier<EventStrategy>>` inmutable. Método `resolve(String eventType)` retorna `Optional<EventStrategy>`. Registrar las 4 estrategias de Fase 1 (OrderConfirmed, OrderStatusChanged, OrderCancelled, StockDepleted).
    - `Optional` es válido aquí porque es método utilitario puro sin I/O fuera de cadena reactiva
    - _Requisitos: 10.2, 10.3_

- [ ] 4. Implementar caso de uso principal (`domain/usecase`)
  - [ ] 4.1 Implementar `ProcessNotificationUseCase`: orquestar el flujo completo — (1) verificar idempotencia consultando `notification_history` por eventId, (2) si existe, log DEBUG "Evento duplicado descartado: {eventId}" y retornar vacío, (3) resolver estrategia via `EventStrategyFactory`, (4) extraer `NotificationContext` del payload, (5) buscar plantilla activa por eventType, (6) si no existe plantilla, log ERROR y guardar historial FAILED, (7) resolver variables en subject y bodyTemplate con `TemplateEngine`, (8) enviar email via `EmailSenderPort`, (9) guardar historial SENT. Aplicar retry con backoff exponencial (3 reintentos: 1s, 2s, 4s) solo para errores transitorios. Errores permanentes registran FAILED sin reintentar. Capturar `DuplicateKeyException` de MongoDB al guardar historial como evento duplicado sin propagar error.
    - Usar operadores reactivos: `flatMap`, `switchIfEmpty`, `retryWhen(Retry.backoff(...))`, `onErrorResume`
    - _Requisitos: 1.3, 1.4, 1.5, 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 8.1, 8.4, 9.1, 9.2, 9.3, 9.4, 9.5_

  - [ ]* 4.2 Escribir test de propiedad para idempotencia
    - **Propiedad 1: Idempotencia — eventos duplicados no generan envío**
    - Generar eventos de dominio válidos con eventId que ya existe en `notification_history` (mock `existsByEventId` retorna true). Verificar que no se invoca `EmailSenderPort.sendEmail()`, no se crea nuevo registro en historial, y el flujo completa sin error.
    - **Valida: Requisitos 1.3, 1.4, 1.5, 8.4**

  - [ ]* 4.3 Escribir test de propiedad para email al destinatario correcto
    - **Propiedad 6: Email enviado al destinatario correcto**
    - Generar eventos de tipo OrderConfirmed, OrderStatusChanged y OrderCancelled con customerEmail aleatorio, y eventos StockDepleted. Verificar que para los primeros 3 tipos el email se envía al customerEmail del payload, y para StockDepleted se envía al email del administrador configurado.
    - **Valida: Requisitos 3.2, 4.3, 5.3, 6.3, 7.3**

  - [ ]* 4.4 Escribir test de propiedad para historial completo y correcto
    - **Propiedad 7: Historial de notificación completo y correcto**
    - Generar eventos procesados exitosamente y con fallo. Verificar que cada historial tiene todos los campos no nulos: eventId (igual al del evento), eventType, status (SENT si exitoso, FAILED si fallo), processedAt y createdAt.
    - **Valida: Requisitos 3.3, 3.5, 8.1**

  - [ ]* 4.5 Escribir test de propiedad para reintentos con backoff exponencial
    - **Propiedad 8: Reintentos con backoff exponencial para errores transitorios**
    - Simular fallos transitorios del `EmailSenderPort` (mock lanza `EmailSendException` con `transient=true`). Verificar que se reintenta hasta 3 veces. Si tiene éxito en algún reintento, historial con SENT. Si falla tras 3 reintentos, historial con FAILED.
    - **Valida: Requisitos 3.4, 9.1, 9.2, 9.3, 9.4**

  - [ ]* 4.6 Escribir test de propiedad para errores permanentes sin reintentos
    - **Propiedad 9: Errores permanentes no generan reintentos**
    - Simular errores permanentes del `EmailSenderPort` (mock lanza `EmailSendException` con `transient=false`). Verificar que `sendEmail` se invoca exactamente 1 vez (sin reintentos) y el historial se registra con status FAILED.
    - **Valida: Requisitos 9.5**

  - [ ]* 4.7 Escribir test de propiedad para búsqueda de plantilla activa
    - **Propiedad 10: Búsqueda de plantilla activa por eventType**
    - Generar eventTypes relevantes con y sin plantilla activa (mock `findActiveByEventType` retorna `Mono.empty()` o plantilla). Verificar que sin plantilla activa el historial se registra con FAILED sin intentar enviar correo. Con plantilla activa, se procede al envío.
    - **Valida: Requisitos 2.2, 2.3**

- [ ] 5. Checkpoint — Verificar compilación y tests del dominio y casos de uso
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

- [ ] 6. Implementar adaptadores driven (`infrastructure/driven-adapters`)
  - [ ] 6.1 Implementar `MongoTemplateAdapter` (implementa `NotificationTemplateRepository`): consulta a la colección `templates` filtrando por `eventType` y `active = true` usando Reactive MongoDB Driver. Mapeo manual de documento MongoDB a record `NotificationTemplate` con `@Builder`.
    - _Requisitos: 2.1, 2.2_
  - [ ] 6.2 Implementar `MongoHistoryAdapter` (implementa `NotificationHistoryRepository`): operaciones `existsByEventId` (consulta por eventId, retorna `Mono<Boolean>`) y `save` (inserta documento en `notification_history`). Capturar `DuplicateKeyException` en `save` y tratarla como evento duplicado sin propagar error.
    - _Requisitos: 1.3, 8.1, 8.2, 8.4_
  - [ ] 6.3 Implementar `SesEmailAdapter` (implementa `EmailSenderPort`): envolver llamada al SDK bloqueante de AWS SES con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`. Construir `SendEmailRequest` con source (from-address configurable), destination (to), subject y body HTML. Clasificar errores de SES: `SdkClientException` (timeout) → transitorio, `SesException` HTTP 429/5xx → transitorio, `MessageRejectedException` → permanente, `MailFromDomainNotVerifiedException` → permanente. Lanzar `EmailSendException` con flag `transient` según clasificación.
    - Obtener credenciales de AWS SES desde AWS Secrets Manager
    - _Requisitos: 3.1, 3.2, 3.6, 9.5_

- [ ] 7. Implementar entry-point Kafka (`infrastructure/entry-points`)
  - [ ] 7.1 Implementar `KafkaNotificationConsumer`: consumidor Kafka suscrito al consumer group `notification-service-group` en los tópicos `order-events` e `inventory-events`. Deserializar el sobre estándar (`DomainEvent`), extraer `eventType`. Si el eventType pertenece al conjunto relevante de Fase 1 (OrderConfirmed, OrderStatusChanged, OrderCancelled, StockDepleted), delegar a `ProcessNotificationUseCase.execute()`. Si el eventType es desconocido o no relevante, registrar log WARN con el eventType recibido e ignorar (tolerancia a evolución del esquema). Manejar errores de deserialización con log ERROR e ignorar evento.
    - _Requisitos: 1.1, 1.2, 1.6, 1.7_

  - [ ]* 7.2 Escribir test de propiedad para filtrado de eventos desconocidos
    - **Propiedad 2: Filtrado de eventos desconocidos**
    - Generar eventos con eventTypes aleatorios que no pertenezcan al conjunto relevante (excluyendo OrderConfirmed, OrderStatusChanged, OrderCancelled, StockDepleted). Verificar que no se invoca `ProcessNotificationUseCase`, no se crea registro en `notification_history` y no se lanza excepción.
    - **Valida: Requisitos 1.6, 1.7**

  - [ ]* 7.3 Escribir test de propiedad para email resuelto con variables completas
    - **Propiedad 5: Email resuelto contiene todas las variables requeridas**
    - Generar eventos de cada tipo relevante con plantilla activa y payload completo. Verificar que el subject y bodyTemplate resueltos contienen los valores de todas las variables especificadas para ese tipo de evento y no queda ningún marcador `{{...}}` sin resolver.
    - **Valida: Requisitos 4.4, 5.4, 6.4, 7.4**

- [ ] 8. Integración final y configuración de Spring
  - [ ] 8.1 Configurar beans de Spring en `app-service`: inyección de dependencias para `ProcessNotificationUseCase`, `TemplateEngine`, `EventStrategyFactory` (con las 4 estrategias registradas), adaptadores MongoDB (`MongoTemplateAdapter`, `MongoHistoryAdapter`), `SesEmailAdapter` y `KafkaNotificationConsumer`. Agregar `@ConfigurationPropertiesScan` y `CommandLineRunner` de log de inicio ("=== ms-notifications iniciado correctamente ===").
    - Inyectar `notification.from-address` y `notification.admin-email` desde propiedades
    - _Requisitos: transversal_

- [ ] 9. Checkpoint final — Verificar que todos los tests pasan
  - Asegurar que todos los tests pasan, preguntar al usuario si surgen dudas.

## Notas

- Las tareas marcadas con `*` son opcionales y pueden omitirse para un MVP más rápido
- Cada tarea referencia requisitos específicos para trazabilidad
- Los checkpoints aseguran validación incremental
- Los tests de propiedades validan propiedades universales de correctitud (jqwik, mínimo 100 iteraciones)
- Los tests unitarios validan ejemplos específicos y edge cases (JUnit 5 + Mockito + StepVerifier)
- Este microservicio no expone endpoints REST — el único entry-point es el consumidor Kafka
