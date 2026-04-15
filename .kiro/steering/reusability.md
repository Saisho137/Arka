# Arka — Reutilización de Implementaciones y Versionado

## Principio de Reutilización

**REGLA CRÍTICA:** Para patrones transversales y componentes de infraestructura que ya están implementados y probados en `ms-inventory`, se DEBE reutilizar la implementación existente como referencia canónica, adaptando únicamente los aspectos específicos del dominio.

## Componentes Reutilizables de ms-inventory

### 1. Kafka Outbox Relay (`reactor-kafka`)

**Referencia:** `ms-inventory/infrastructure/driven-adapters/kafka-producer/`

**Qué reutilizar:**

- Estructura completa del módulo `kafka-producer`
- `KafkaOutboxRelay.java` — lógica de relay con `@Scheduled` externalizado
- `KafkaProducerConfig.java` — configuración de `KafkaSender<String, String>`
- Configuración de producer: `ACKS_CONFIG = "all"`, `RETRIES_CONFIG = 3`, `ENABLE_IDEMPOTENCE_CONFIG = true`
- Manejo de errores con `onErrorResume()` para mantener eventos PENDING

**Qué adaptar:**

- Nombre del tópico (de `inventory-events` a `product-events`, `order-events`, etc.)
- Nombre del UseCase inyectado (de `OutboxRelayUseCase` al equivalente del servicio)
- Partition key según el dominio (de `sku` a `productId`, `orderId`, etc.)

### 2. Kafka Consumer (Idempotencia con ProcessedEvents)

**Referencia:** `ms-inventory/infrastructure/entry-points/kafka-consumer/`

**Qué reutilizar:**

- Estructura completa del módulo `kafka-consumer`
- `KafkaConsumerConfig.java` — configuración de `KafkaReceiver<String, String>`
- Patrón de idempotencia: verificar `processedEventRepository.exists(eventId)` antes de procesar
- Inserción de `eventId` en `processed_events` dentro de la misma transacción
- Manejo de errores con `onErrorResume()` y log WARN para eventos ignorados

**Qué adaptar:**

- Nombre del tópico suscrito
- Lógica de procesamiento específica del dominio (llamada al UseCase correspondiente)
- Deserialización del payload según el tipo de evento

### 3. Configuración de Spring Profiles (local/docker)

**Referencia:** `ms-inventory/applications/app-service/src/main/resources/`

**Qué reutilizar:**

- Estructura de 3 archivos YAML: `application.yaml`, `application-local.yaml`, `application-docker.yaml`
- Patrón de activación: `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}`
- Configuración de hosts y puertos para cada perfil
- Externalización de schedulers sin defaults inline

**Qué adaptar:**

- Nombres de bases de datos específicas del servicio
- Puerto del servicio (`server.port`)
- Configuración específica de MongoDB vs PostgreSQL

### 4. Documentación API con Springdoc/OpenAPI

**Referencia:** `ms-inventory/applications/app-service/src/main/java/.../config/OpenApiConfig.java`

**Qué reutilizar:**

- Estructura del `@Bean OpenAPI` con metadata del servicio
- Configuración en `application.yaml`:
  - `springdoc.api-docs.path: /api-docs`
  - `springdoc.swagger-ui.path: /swagger-ui.html`
  - `springdoc.swagger-ui.enabled: true`
- Anotaciones en controladores: `@Tag`, `@Operation`, `@ApiResponse`

**Qué adaptar:**

- Metadata del servicio (title, description, version)
- Descripciones de endpoints específicos del dominio

### 5. Patrón Controller → Handler → UseCase

**Referencia:** `ms-inventory/infrastructure/entry-points/rest-api/`

**Qué reutilizar:**

- Separación de responsabilidades: Controller (HTTP) → Handler (orquestación) → UseCase (lógica)
- Controllers thin con solo anotaciones HTTP y delegación
- Handlers como `@Component` con métodos que retornan `Mono<ResponseEntity<T>>` o `Flux<T>`
- Uso de mappers estáticos para conversión DTO ↔ Dominio

**Qué adaptar:**

- Nombres de entidades y DTOs específicos del dominio
- Endpoints y rutas según el bounded context

### 6. GlobalExceptionHandler

**Referencia:** `ms-inventory/infrastructure/entry-points/rest-api/GlobalExceptionHandler.java`

**Qué reutilizar:**

- Estructura completa del `@ControllerAdvice`
- Manejo de `WebExchangeBindException` → 400
- Manejo de `DomainException` subclases → HTTP status según subclase
- Manejo de `Exception` genérica → 500 con log ERROR
- Estructura de `ErrorResponse(code, message)`

**Qué adaptar:**

- Subclases específicas de `DomainException` del dominio

### 7. Configuración de Índices MongoDB

**Referencia:** `ms-inventory` no usa MongoDB, pero el patrón es similar a la configuración de índices en PostgreSQL

**Qué reutilizar:**

- Patrón de `@Configuration` con `CommandLineRunner` para crear índices al inicio
- Uso de `ReactiveMongoTemplate.indexOps()` con `ensureIndex()`
- Encadenamiento de operaciones con `.then()`

**Qué adaptar:**

- Nombres de colecciones y campos según el dominio

## Versionado Unificado de Librerías

**REGLA CRÍTICA:** Todas las librerías transversales DEBEN usar exactamente las mismas versiones en TODO el monorepo para garantizar compatibilidad y facilitar mantenimiento.

### Referencia Canónica de Versiones

**Archivo:** `ms-inventory/build.gradle` y `ms-inventory/main.gradle`

### Librerías Transversales con Versionado Unificado

| Librería                                             | Versión          | Uso                                     | Ubicación en ms-inventory                           |
| ---------------------------------------------------- | ---------------- | --------------------------------------- | --------------------------------------------------- |
| `io.projectreactor.kafka:reactor-kafka`              | `1.3.25`         | Kafka producer/consumer reactivo        | kafka-producer, kafka-consumer                      |
| `org.springdoc:springdoc-openapi-starter-webflux-ui` | `3.0.2`          | Documentación OpenAPI/Swagger (WebFlux) | app-service, reactive-web                           |
| `tools.jackson.core:jackson-databind`                | (Spring BOM)     | Serialización JSON                      | kafka-producer, kafka-consumer, app-service         |
| `net.jqwik:jqwik`                                    | `1.9.2`          | Property-based testing                  | (No usado aún en ms-inventory, versión recomendada) |
| `io.projectreactor:reactor-test`                     | (Spring BOM)     | Testing de publishers reactivos         | main.gradle (testImplementation global)             |
| `org.mockito:mockito-core`                           | (Spring BOM)     | Mocking en tests unitarios              | spring-boot-starter-test                            |
| `org.springframework.boot:spring-boot-starter-*`     | `4.0.3`          | Starters de Spring Boot                 | build.gradle (springBootVersion)                    |
| `org.springframework.kafka:spring-kafka`             | (Spring BOM)     | Integración Spring con Kafka            | kafka-producer, kafka-consumer                      |
| `org.projectlombok:lombok`                           | `1.18.42`        | Reducción de boilerplate                | build.gradle (lombokVersion)                        |
| `io.projectreactor:reactor-core`                     | (Spring BOM)     | Core de Project Reactor                 | main.gradle (implementation global)                 |
| `io.projectreactor.addons:reactor-extra`             | (Spring BOM)     | Utilidades adicionales de Reactor       | main.gradle (implementation global)                 |
| `io.projectreactor.tools:blockhound-junit-platform`  | `1.0.16.RELEASE` | Detección de bloqueos en tests          | main.gradle (testImplementation global)             |
| `org.postgresql:r2dbc-postgresql`                    | (Spring BOM)     | Driver R2DBC para PostgreSQL            | r2dbc-postgresql                                    |
| `com.tngtech.archunit:archunit`                      | `1.4.1`          | Validación de arquitectura              | app-service (testImplementation)                    |

### Cómo Verificar Versiones

Antes de agregar una dependencia transversal en un nuevo microservicio:

1. Consultar `ms-inventory/build.gradle` y `ms-inventory/main.gradle`
2. Copiar la declaración de dependencia exacta (incluyendo versión si está explícita)
3. Si la versión viene del BOM de Spring, no especificar versión explícita

### Ejemplo de Reutilización Correcta

**❌ INCORRECTO (versión diferente):**

```groovy
// En ms-catalog/infrastructure/driven-adapters/kafka-producer/build.gradle
implementation 'io.projectreactor.kafka:reactor-kafka:1.3.23' // Versión INCORRECTA
```

**✅ CORRECTO (misma versión que ms-inventory):**

```groovy
// En ms-catalog/infrastructure/driven-adapters/kafka-producer/build.gradle
implementation 'io.projectreactor.kafka:reactor-kafka:1.3.25' // Versión CORRECTA
```

**❌ INCORRECTO (versión diferente de Springdoc):**

```groovy
// En ms-catalog/applications/app-service/build.gradle
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.7.0' // Versión INCORRECTA
```

**✅ CORRECTO (misma versión que ms-inventory):**

```groovy
// En ms-catalog/applications/app-service/build.gradle
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2' // Versión CORRECTA (compatible con Spring Boot 4)
```

## Proceso de Implementación con Reutilización

### Paso 1: Identificar el Componente a Implementar

Ejemplo: "Necesito implementar el Kafka Outbox Relay en ms-catalog"

### Paso 2: Localizar la Referencia en ms-inventory

```bash
# Buscar el componente en ms-inventory
ls ms-inventory/infrastructure/driven-adapters/kafka-producer/
```

### Paso 3: Copiar la Estructura

```bash
# Crear la misma estructura en el nuevo servicio
mkdir -p ms-catalog/infrastructure/driven-adapters/kafka-producer/src/main/java/com/arka/kafka/
```

### Paso 4: Adaptar el Código

1. Copiar los archivos de referencia
2. Cambiar nombres de paquetes si es necesario
3. Adaptar aspectos específicos del dominio:
   - Nombres de tópicos
   - Partition keys
   - Nombres de UseCases
   - Payloads de eventos

### Paso 5: Verificar Versiones de Dependencias

1. Copiar el `build.gradle` del módulo de referencia
2. Verificar que las versiones coincidan con `ms-inventory`
3. Ajustar solo las dependencias de dominio (`:model`, `:usecase`)

## Beneficios de la Reutilización

1. **Consistencia**: Todos los servicios usan los mismos patrones y configuraciones
2. **Calidad**: Se reutilizan implementaciones ya probadas y validadas
3. **Velocidad**: Menos tiempo en implementación y debugging
4. **Mantenibilidad**: Cambios transversales se propagan fácilmente
5. **Compatibilidad**: Versiones unificadas evitan conflictos de dependencias

## Excepciones a la Reutilización

Solo se permite desviarse de la implementación de referencia en los siguientes casos:

1. **Diferencias de tecnología**: MongoDB vs PostgreSQL, R2DBC vs Reactive Mongo
2. **Requisitos específicos del dominio**: Lógica de negocio única del bounded context
3. **Optimizaciones justificadas**: Con aprobación explícita y documentación del motivo

En todos los demás casos, **SIEMPRE reutilizar la implementación de ms-inventory**.
