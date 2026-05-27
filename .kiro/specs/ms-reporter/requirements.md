# Documento de Requisitos — ms-reporter

## Introducción

El microservicio `ms-reporter` es el dueño del dominio de Analítica y Reportes dentro de la plataforma B2B Arka. Su responsabilidad principal es implementar CQRS (Command Query Responsibility Segregation) y Event Sourcing consumiendo TODOS los eventos de los 7 tópicos Kafka del sistema, almacenar los payloads de eventos en PostgreSQL 17 con JSONB e índices GIN para consultas eficientes, construir vistas materializadas optimizadas para analítica de negocio, generar reportes pesados en formato CSV/PDF de hasta 500MB y subirlos a AWS S3, detectar patrones de stock bajo para generar alertas de reabastecimiento, y calcular métricas de negocio (KPIs) como tasa de conversión, carritos abandonados y tiempo promedio de entrega. Este servicio es el ÚNICO servicio imperativo del ecosistema Arka (Spring MVC + Virtual Threads en lugar de WebFlux) debido a su naturaleza CPU-bound para procesamiento de reportes pesados. Cubre las HU7 (Reportes de ventas semanales) y HU3 (Alertas de stock bajo) de la Fase 3.

## Glosario

- **Event_Store**: Tabla PostgreSQL `event_store` que almacena TODOS los eventos del sistema de forma inmutable con payload en JSONB, índices GIN y particionamiento por fecha
- **Read_Model**: Vista materializada optimizada para consultas analíticas construida a partir de los eventos del Event_Store (ejemplos: sales_summary, product_performance, customer_orders)
- **CQRS**: Command Query Responsibility Segregation — patrón arquitectónico que separa las operaciones de escritura (comandos) de las operaciones de lectura (consultas) usando modelos de datos diferentes
- **Event_Sourcing**: Patrón arquitectónico donde el estado del sistema se reconstruye reproduciendo la secuencia completa de eventos de dominio en lugar de almacenar solo el estado actual
- **JSONB**: Tipo de dato binario de PostgreSQL para almacenar documentos JSON con soporte para índices GIN y consultas eficientes por campos anidados
- **Índice_GIN**: Generalized Inverted Index — tipo de índice de PostgreSQL optimizado para búsquedas en tipos de datos compuestos como JSONB, arrays y texto completo
- **Reporte_Pesado**: Archivo CSV o PDF generado por ms-reporter con tamaño de hasta 500MB que contiene datos analíticos agregados y se almacena en AWS S3
- **Virtual_Threads**: Característica de Java 21 (Project Loom) que permite crear millones de threads ligeros para operaciones CPU-bound sin el overhead de threads nativos del sistema operativo
- **Administrador**: Personal interno de Arka con rol ADMIN que genera reportes, consulta métricas y configura alertas de stock
- **Tópico_Kafka**: Canal de mensajería de Kafka del cual ms-reporter consume eventos (7 tópicos: product-events, inventory-events, order-events, cart-events, payment-events, shipping-events, provider-events)
- **Consumer_Group**: Grupo de consumidores Kafka `reporter-service-group` que garantiza que cada evento se procese exactamente una vez por ms-reporter
- **Processed_Events**: Tabla PostgreSQL que almacena el eventId de cada evento consumido para garantizar idempotencia y evitar procesamiento duplicado
- **Alerta_Stock_Bajo**: Notificación generada cuando el análisis de patrones de consumo detecta que un SKU alcanzará el umbral crítico en los próximos 7 días
- **KPI**: Key Performance Indicator — métrica de negocio calculada por ms-reporter (ejemplos: tasa de conversión, valor promedio de orden, tiempo de entrega)
- **Particionamiento_Por_Fecha**: Estrategia de particionamiento de la tabla event_store donde cada partición contiene eventos de un rango de fechas específico para optimizar consultas históricas
- **Idempotencia**: Propiedad que garantiza que procesar el mismo evento múltiples veces produce el mismo resultado que procesarlo una sola vez
- **AWS_S3**: Servicio de almacenamiento de objetos de Amazon Web Services donde ms-reporter sube los reportes generados
- **Compresión_GZIP**: Algoritmo de compresión aplicado a reportes antes de subirlos a S3 para reducir tamaño y costos de almacenamiento
- **Streaming_Reportes**: Técnica de generación de reportes que escribe datos incrementalmente al archivo de salida sin cargar todo el dataset en memoria para evitar OutOfMemoryError
- **Correlación_ID**: Identificador UUID que vincula eventos relacionados de una misma operación de negocio a través de múltiples microservicios
- **Sobre_Estándar**: Formato de evento Kafka con campos: eventId, eventType, timestamp, source, correlationId y payload
- **API_Gateway**: Punto de entrada único que valida JWT, inyecta `X-User-Email` y enruta tráfico a la VPC privada
- **Controlador_REST**: Entry-point `@RestController` con retornos síncronos (no Mono/Flux) que expone los endpoints HTTP del servicio
- **JDBC**: Java Database Connectivity — API estándar de Java para acceso a bases de datos relacionales usado por ms-reporter en lugar de R2DBC reactivo

## Requisitos

### Requisito 1: Consumir eventos de todos los tópicos Kafka

**Historia de Usuario:** Como ms-reporter, quiero consumir TODOS los eventos de los 7 tópicos Kafka del sistema para construir el Event Store completo y habilitar analítica de negocio.

#### Criterios de Aceptación

1. THE ms-reporter SHALL consumir eventos de los 7 tópicos Kafka: product-events, inventory-events, order-events, cart-events, payment-events, shipping-events y provider-events usando el consumer group `reporter-service-group`
2. WHEN ms-reporter recibe un evento de cualquier tópico, THE ms-reporter SHALL verificar en la tabla Processed_Events si el eventId ya fue procesado antes de ejecutar la lógica de negocio
3. WHEN el eventId ya existe en Processed_Events, THE ms-reporter SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
4. WHEN ms-reporter recibe un evento con un eventType desconocido, THE ms-reporter SHALL almacenar el evento en el Event_Store y registrar un log de nivel WARN sin fallar el procesamiento
5. WHEN un evento se procesa exitosamente, THE ms-reporter SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción JDBC que la inserción en el Event_Store
6. THE ms-reporter SHALL deserializar el Sobre_Estándar de cada evento para extraer eventId, eventType, timestamp, source, correlationId y payload
7. THE ms-reporter SHALL configurar el consumer Kafka con at-least-once delivery y commit manual de offsets después de persistir el evento en PostgreSQL

### Requisito 2: Almacenar eventos en Event Store con JSONB

**Historia de Usuario:** Como ms-reporter, quiero almacenar todos los eventos del sistema en una tabla PostgreSQL con payload en JSONB para habilitar consultas eficientes por campos anidados y reconstrucción del estado del sistema.

#### Criterios de Aceptación

1. THE ms-reporter SHALL insertar cada evento consumido en la tabla event_store de PostgreSQL con los campos: id (UUID PK), event_id (UUID unique), event_type (VARCHAR), timestamp (TIMESTAMPTZ), source (VARCHAR), correlation_id (UUID), topic (VARCHAR), partition_key (VARCHAR), payload (JSONB) y created_at (TIMESTAMPTZ)
2. THE tabla event_store SHALL tener un índice GIN en el campo payload para habilitar consultas eficientes por campos anidados del JSON
3. THE tabla event_store SHALL tener un índice B-tree compuesto en (event_type, timestamp) para optimizar consultas analíticas filtradas por tipo de evento y rango de fechas
4. THE tabla event_store SHALL tener un índice B-tree en correlation_id para rastrear eventos relacionados de una misma operación de negocio
5. THE tabla event_store SHALL estar particionada por rango de fechas (particiones mensuales) para optimizar consultas históricas y mantenimiento de datos
6. THE ms-reporter SHALL almacenar el payload del evento como JSONB sin transformaciones para preservar la estructura original del evento de dominio
7. THE campo event_id de la tabla event_store SHALL tener un constraint UNIQUE para prevenir duplicación de eventos a nivel de base de datos

### Requisito 3: Generar reporte de ventas semanales en CSV

**Historia de Usuario:** Como Administrador, quiero generar un reporte de ventas semanales en formato CSV con datos agregados por producto, categoría y cliente para analizar el desempeño del negocio.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud POST /reports/sales/weekly con fechas de inicio y fin válidas, THE Controlador_REST SHALL generar un archivo CSV con las columnas: sku, product_name, category, total_orders, total_quantity, total_revenue, average_order_value y period
2. THE ms-reporter SHALL consultar el Read_Model sales_summary para obtener los datos agregados del período solicitado en lugar de reconstruir desde el Event_Store
3. WHEN el reporte generado excede 10MB de tamaño, THE ms-reporter SHALL usar Streaming_Reportes para escribir el CSV incrementalmente sin cargar todo el dataset en memoria
4. WHEN el reporte se genera exitosamente, THE ms-reporter SHALL comprimir el archivo CSV con GZIP antes de subirlo a AWS S3
5. WHEN el reporte se sube exitosamente a S3, THE ms-reporter SHALL retornar código HTTP 202 (Accepted) con un ReportResponse que contenga: report_id, status (PROCESSING), s3_key y estimated_completion_time
6. THE ms-reporter SHALL almacenar metadatos del reporte en la tabla report_metadata con los campos: report_id (UUID PK), report_type (VARCHAR), status (ENUM: PROCESSING, COMPLETED, FAILED), s3_key (VARCHAR), file_size_bytes (BIGINT), requested_by (VARCHAR), requested_at (TIMESTAMPTZ) y completed_at (TIMESTAMPTZ)
7. WHEN el Administrador solicita un reporte con un rango de fechas que no contiene datos, THE ms-reporter SHALL retornar código HTTP 200 con un archivo CSV vacío que contenga solo los encabezados de columna
8. THE ms-reporter SHALL validar que la fecha de inicio sea anterior o igual a la fecha de fin mediante Bean Validation antes de procesar la solicitud

### Requisito 4: Generar reporte de ventas semanales en PDF

**Historia de Usuario:** Como Administrador, quiero generar un reporte de ventas semanales en formato PDF con gráficos y tablas para presentaciones ejecutivas.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud POST /reports/sales/weekly con formato PDF, THE Controlador_REST SHALL generar un archivo PDF que contenga: título del reporte, rango de fechas, tabla de ventas por producto, gráfico de barras de top 10 productos y resumen ejecutivo con KPIs
2. THE ms-reporter SHALL usar una librería de generación de PDF (Apache PDFBox o iText) para crear el documento con formato profesional
3. WHEN el reporte PDF generado excede 10MB de tamaño, THE ms-reporter SHALL usar Streaming_Reportes para escribir el PDF incrementalmente sin cargar todo el documento en memoria
4. WHEN el reporte se genera exitosamente, THE ms-reporter SHALL comprimir el archivo PDF con GZIP antes de subirlo a AWS S3
5. WHEN el reporte se sube exitosamente a S3, THE ms-reporter SHALL retornar código HTTP 202 (Accepted) con un ReportResponse que contenga: report_id, status (PROCESSING), s3_key y estimated_completion_time
6. THE ms-reporter SHALL incluir en el PDF un resumen ejecutivo con los KPIs: total_revenue, total_orders, average_order_value, top_selling_product y growth_percentage_vs_previous_week
7. THE ms-reporter SHALL validar que el tamaño del archivo PDF generado no exceda 500MB antes de subirlo a S3

### Requisito 5: Consultar estado de reporte generado

**Historia de Usuario:** Como Administrador, quiero consultar el estado de un reporte previamente solicitado para saber si está listo para descarga o si falló durante la generación.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /reports/{reportId}, THE Controlador_REST SHALL retornar el estado del reporte consultando la tabla report_metadata con código HTTP 200
2. THE ReportResponse SHALL contener los campos: report_id, report_type, status (PROCESSING, COMPLETED, FAILED), s3_key, file_size_bytes, requested_by, requested_at, completed_at y download_url (presigned URL de S3 con expiración de 1 hora)
3. WHEN el reporte tiene status COMPLETED, THE ms-reporter SHALL generar una presigned URL de S3 válida por 1 hora para descarga directa del archivo
4. WHEN el reporte tiene status FAILED, THE ReportResponse SHALL incluir un campo error_message con la descripción del error que causó el fallo
5. WHEN el Administrador solicita un reportId que no existe, THE Controlador_REST SHALL retornar código HTTP 404 con un mensaje descriptivo
6. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN

### Requisito 6: Construir Read Model de ventas agregadas

**Historia de Usuario:** Como ms-reporter, quiero construir y mantener una vista materializada de ventas agregadas por producto para optimizar la generación de reportes sin reconstruir desde el Event Store.

#### Criterios de Aceptación

1. THE ms-reporter SHALL crear una tabla sales_summary con los campos: sku (VARCHAR), product_name (VARCHAR), category (VARCHAR), week_start_date (DATE), total_orders (INTEGER), total_quantity (INTEGER), total_revenue (NUMERIC), average_order_value (NUMERIC) y last_updated_at (TIMESTAMPTZ)
2. WHEN ms-reporter procesa un evento de tipo OrderConfirmed, THE ms-reporter SHALL actualizar o insertar (UPSERT) el registro correspondiente en sales_summary incrementando total_orders, total_quantity y total_revenue
3. WHEN ms-reporter procesa un evento de tipo OrderCancelled, THE ms-reporter SHALL actualizar el registro correspondiente en sales_summary decrementando total_orders, total_quantity y total_revenue
4. THE ms-reporter SHALL calcular el campo average_order_value como total_revenue dividido por total_orders cada vez que se actualiza un registro en sales_summary
5. THE tabla sales_summary SHALL tener un índice compuesto único en (sku, week_start_date) para optimizar consultas por producto y semana
6. THE ms-reporter SHALL actualizar el Read_Model dentro de la misma transacción JDBC que la inserción del evento en el Event_Store para garantizar consistencia eventual
7. WHEN ms-reporter procesa un evento de tipo PriceChanged, THE ms-reporter SHALL actualizar el campo product_name en sales_summary si el nombre del producto cambió en el payload

### Requisito 7: Detectar patrones de stock bajo y generar alertas

**Historia de Usuario:** Como Administrador, quiero recibir alertas automáticas cuando el análisis de patrones de consumo detecte que un producto alcanzará el umbral crítico en los próximos 7 días para poder reabastecer a tiempo.

#### Criterios de Aceptación

1. THE ms-reporter SHALL ejecutar un job periódico cada 24 horas que analice los eventos StockUpdated y OrderConfirmed de los últimos 30 días para calcular la tasa de consumo promedio diaria por SKU
2. WHEN la tasa de consumo promedio multiplicada por 7 días es mayor o igual al stock actual menos el umbral crítico, THE ms-reporter SHALL generar una Alerta_Stock_Bajo
3. THE ms-reporter SHALL almacenar las alertas en la tabla stock_alerts con los campos: alert_id (UUID PK), sku (VARCHAR), current_stock (INTEGER), threshold (INTEGER), daily_consumption_rate (NUMERIC), estimated_days_to_depletion (INTEGER), alert_status (ENUM: ACTIVE, RESOLVED), created_at (TIMESTAMPTZ) y resolved_at (TIMESTAMPTZ)
4. WHEN se genera una Alerta_Stock_Bajo, THE ms-reporter SHALL publicar un evento de tipo StockAlertGenerated al tópico `reporter-events` para que ms-notifications envíe un email al Administrador
5. WHEN ms-reporter procesa un evento de tipo StockUpdated con una cantidad que supera el umbral crítico más 7 días de consumo, THE ms-reporter SHALL actualizar el alert_status de la alerta correspondiente a RESOLVED
6. THE ms-reporter SHALL calcular la tasa de consumo promedio diaria como la suma de cantidades de eventos OrderConfirmed dividida por el número de días del período analizado
7. THE ms-reporter SHALL ignorar SKUs que no tienen suficiente historial de ventas (menos de 7 días de datos) para evitar alertas falsas

### Requisito 8: Calcular KPIs de negocio

**Historia de Usuario:** Como Administrador, quiero consultar KPIs de negocio calculados en tiempo real para monitorear el desempeño de la plataforma.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /metrics/kpis con un rango de fechas, THE Controlador_REST SHALL retornar un KPIResponse con los siguientes indicadores: total_revenue, total_orders, average_order_value, conversion_rate, cart_abandonment_rate, average_delivery_time_days y top_selling_products
2. THE ms-reporter SHALL calcular total_revenue sumando el campo total_amount de todos los eventos OrderConfirmed en el rango de fechas
3. THE ms-reporter SHALL calcular total_orders contando los eventos OrderConfirmed en el rango de fechas
4. THE ms-reporter SHALL calcular average_order_value dividiendo total_revenue por total_orders
5. THE ms-reporter SHALL calcular conversion_rate dividiendo el número de eventos OrderConfirmed por el número de eventos CartAbandoned más OrderConfirmed en el rango de fechas
6. THE ms-reporter SHALL calcular cart_abandonment_rate dividiendo el número de eventos CartAbandoned por el número de eventos CartAbandoned más OrderConfirmed en el rango de fechas
7. THE ms-reporter SHALL calcular average_delivery_time_days promediando la diferencia en días entre el timestamp de eventos OrderConfirmed y ShippingDispatched para órdenes completadas
8. THE ms-reporter SHALL retornar top_selling_products como una lista de los 10 SKUs con mayor total_quantity vendida en el rango de fechas
9. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN

### Requisito 9: Reconstruir Read Models desde Event Store

**Historia de Usuario:** Como Administrador, quiero reconstruir las vistas materializadas desde el Event Store para corregir inconsistencias o agregar nuevos Read Models sin perder datos históricos.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud POST /admin/rebuild-read-models con el nombre del Read_Model a reconstruir, THE Controlador_REST SHALL truncar la tabla del Read_Model y reproducir todos los eventos relevantes del Event_Store en orden cronológico
2. THE ms-reporter SHALL validar que el nombre del Read_Model solicitado sea uno de los soportados: sales_summary, product_performance o customer_orders
3. WHEN la reconstrucción se inicia exitosamente, THE Controlador_REST SHALL retornar código HTTP 202 (Accepted) con un RebuildResponse que contenga: rebuild_id, read_model_name, status (IN_PROGRESS) y estimated_completion_time
4. THE ms-reporter SHALL procesar los eventos del Event_Store en lotes de 1000 eventos para evitar OutOfMemoryError durante la reconstrucción
5. WHEN la reconstrucción se completa exitosamente, THE ms-reporter SHALL actualizar el status del rebuild a COMPLETED en la tabla rebuild_jobs
6. WHEN la reconstrucción falla por un error, THE ms-reporter SHALL actualizar el status del rebuild a FAILED, registrar el error en la tabla rebuild_jobs y revertir los cambios parciales en el Read_Model
7. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN

### Requisito 10: Consultar eventos por correlationId

**Historia de Usuario:** Como Administrador, quiero consultar todos los eventos relacionados con una operación de negocio específica usando el correlationId para rastrear el flujo completo de una transacción distribuida.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /events/trace/{correlationId}, THE Controlador_REST SHALL retornar una lista de eventos del Event_Store ordenados por timestamp ascendente con código HTTP 200
2. THE EventTraceResponse SHALL contener los campos: correlation_id, total_events, events (lista de objetos con event_id, event_type, timestamp, source, topic y payload)
3. WHEN el correlationId solicitado no tiene eventos asociados, THE Controlador_REST SHALL retornar código HTTP 200 con una lista vacía
4. THE ms-reporter SHALL usar el índice B-tree en correlation_id de la tabla event_store para optimizar la consulta
5. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN

### Requisito 11: Manejo centralizado de errores

**Historia de Usuario:** Como consumidor de la API de reportes, quiero recibir respuestas de error consistentes y descriptivas para poder diagnosticar y resolver problemas de integración.

#### Criterios de Aceptación

1. THE ms-reporter SHALL implementar un GlobalExceptionHandler mediante `@ControllerAdvice` que traduzca excepciones de dominio a respuestas HTTP estandarizadas
2. WHEN ocurre un error de validación (Bean Validation), THE ms-reporter SHALL retornar código HTTP 400 con un ErrorResponse que contenga código de error y mensaje descriptivo
3. WHEN ocurre una excepción de dominio (DomainException), THE ms-reporter SHALL retornar el código HTTP correspondiente con un ErrorResponse estandarizado
4. WHEN ocurre un error de conexión a AWS S3, THE ms-reporter SHALL retornar código HTTP 503 (Service Unavailable) con un ErrorResponse indicando que el servicio de almacenamiento no está disponible
5. WHEN ocurre un error inesperado, THE ms-reporter SHALL retornar código HTTP 500, registrar el error con nivel ERROR en el log y retornar un mensaje genérico sin exponer detalles internos
6. THE ErrorResponse SHALL contener los campos: code (código de error) y message (descripción legible del error)

### Requisito 12: Configuración de Virtual Threads

**Historia de Usuario:** Como ms-reporter, quiero usar Virtual Threads de Java 21 para procesar reportes pesados de forma eficiente sin bloquear threads nativos del sistema operativo.

#### Criterios de Aceptación

1. THE ms-reporter SHALL configurar Spring Boot con `spring.threads.virtual.enabled=true` en application.yaml para habilitar Virtual Threads
2. THE ms-reporter SHALL usar `reactive=false` en gradle.properties para generar un proyecto Spring MVC en lugar de WebFlux
3. THE ms-reporter SHALL usar JDBC en lugar de R2DBC para acceso a PostgreSQL debido al paradigma imperativo
4. THE ms-reporter SHALL configurar el TaskExecutor de Spring con Virtual Threads para procesamiento asíncrono de reportes
5. THE ms-reporter SHALL usar `@Async` con Virtual Threads para la generación de reportes pesados sin bloquear el thread del controlador HTTP

### Requisito 13: Particionamiento de tabla event_store

**Historia de Usuario:** Como ms-reporter, quiero particionar la tabla event_store por rango de fechas para optimizar consultas históricas y facilitar el mantenimiento de datos antiguos.

#### Criterios de Aceptación

1. THE tabla event_store SHALL estar particionada por rango de fechas usando particionamiento declarativo de PostgreSQL con particiones mensuales
2. THE ms-reporter SHALL crear automáticamente nuevas particiones al inicio de cada mes mediante un job programado
3. WHEN una consulta filtra por rango de fechas, PostgreSQL SHALL usar partition pruning para escanear solo las particiones relevantes
4. THE ms-reporter SHALL mantener particiones de los últimos 24 meses en línea y archivar particiones más antiguas a AWS S3 Glacier
5. THE ms-reporter SHALL crear un índice GIN en el campo payload de cada partición para mantener el rendimiento de consultas por campos JSONB

### Requisito 14: Idempotencia en consumidores de Kafka

**Historia de Usuario:** Como ms-reporter, quiero garantizar que los eventos consumidos de Kafka se procesen exactamente una vez para evitar duplicación de datos en el Event Store y Read Models.

#### Criterios de Aceptación

1. THE ms-reporter SHALL verificar la existencia del eventId en la tabla Processed_Events antes de procesar cualquier evento consumido de Kafka
2. WHEN el eventId ya existe en Processed_Events, THE ms-reporter SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
3. WHEN un evento se procesa exitosamente, THE ms-reporter SHALL insertar el eventId en la tabla Processed_Events dentro de la misma transacción JDBC que la inserción en el Event_Store y actualización de Read Models
4. THE tabla Processed_Events SHALL tener el campo event_id como clave primaria (UUID) para garantizar unicidad a nivel de base de datos
5. THE ms-reporter SHALL usar transacciones JDBC con nivel de aislamiento READ_COMMITTED para garantizar consistencia entre Event_Store, Read_Models y Processed_Events

### Requisito 15: Compresión y límite de tamaño de reportes

**Historia de Usuario:** Como ms-reporter, quiero comprimir reportes antes de subirlos a S3 y validar que no excedan 500MB para controlar costos de almacenamiento y transferencia.

#### Criterios de Aceptación

1. WHEN ms-reporter genera un reporte CSV o PDF, THE ms-reporter SHALL comprimir el archivo con GZIP antes de subirlo a AWS S3
2. THE ms-reporter SHALL validar que el tamaño del archivo comprimido no exceda 500MB antes de iniciar la subida a S3
3. WHEN el tamaño del archivo comprimido excede 500MB, THE ms-reporter SHALL actualizar el status del reporte a FAILED en la tabla report_metadata con error_message indicando que el reporte excede el límite de tamaño
4. THE ms-reporter SHALL calcular el ratio de compresión y almacenarlo en la tabla report_metadata para monitoreo de eficiencia
5. THE ms-reporter SHALL usar streaming para comprimir el archivo incrementalmente sin cargar todo el contenido en memoria

### Requisito 16: Consultar historial de reportes generados

**Historia de Usuario:** Como Administrador, quiero consultar el historial de reportes generados para auditar las solicitudes y reutilizar reportes previamente generados.

#### Criterios de Aceptación

1. WHEN el Administrador envía una solicitud GET /reports/history, THE Controlador_REST SHALL retornar una lista paginada de reportes ordenados por requested_at descendente con código HTTP 200
2. THE ReportHistoryResponse SHALL contener los campos: report_id, report_type, status, file_size_bytes, requested_by, requested_at, completed_at y download_url (si status es COMPLETED)
3. THE Controlador_REST SHALL soportar filtros opcionales por report_type, status y rango de fechas mediante query parameters
4. THE Controlador_REST SHALL soportar paginación mediante query parameters page y size con valores por defecto page=0 y size=20
5. THE Controlador_REST SHALL permitir el acceso únicamente a usuarios con rol ADMIN
6. WHEN un reporte en el historial tiene status COMPLETED y fue generado hace menos de 7 días, THE ms-reporter SHALL incluir una presigned URL de S3 válida por 1 hora en el campo download_url

### Requisito 17: Publicar eventos de dominio al tópico reporter-events

**Historia de Usuario:** Como ms-reporter, quiero publicar eventos de dominio al tópico reporter-events para notificar a otros microservicios sobre alertas de stock bajo y reportes completados.

#### Criterios de Aceptación

1. THE ms-reporter SHALL publicar eventos al tópico `reporter-events` de Kafka usando el report_id o alert_id como partition key
2. WHEN se genera una Alerta_Stock_Bajo, THE ms-reporter SHALL publicar un evento de tipo StockAlertGenerated con payload que contenga: alert_id, sku, current_stock, threshold, daily_consumption_rate y estimated_days_to_depletion
3. WHEN un reporte se completa exitosamente, THE ms-reporter SHALL publicar un evento de tipo ReportCompleted con payload que contenga: report_id, report_type, s3_key, file_size_bytes y requested_by
4. WHEN un reporte falla durante la generación, THE ms-reporter SHALL publicar un evento de tipo ReportFailed con payload que contenga: report_id, report_type, error_message y requested_by
5. THE ms-reporter SHALL usar el Sobre_Estándar para todos los eventos publicados con los campos: eventId, eventType, timestamp, source ("ms-reporter"), correlationId y payload
6. THE ms-reporter SHALL configurar el producer Kafka con acks=all, retries=3 e idempotence=true para garantizar entrega confiable de eventos
