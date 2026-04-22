# Plan de Implementación: ms-payment (Mock)

## Visión General

Implementación **mínima y mock** del microservicio `ms-payment`. El objetivo no es integrar pasarelas reales (Stripe, Wompi, MercadoPago) sino cubrir exactamente el rol que este servicio cumple en la arquitectura y los flujos Kafka documentados:

1. **Consumir** eventos `OrderCreated` del tópico `order-events` (producidos por ms-order).
2. **Simular** el procesamiento del pago con `Random`: **80 % → PaymentProcessed**, **20 % → PaymentFailed**.
3. **Publicar** el evento resultante (`PaymentProcessed` o `PaymentFailed`) al tópico `payment-events` con `orderId` como partition key.

El servicio compila, pasa `./gradlew validateStructure` y opera de forma autónoma en Docker Compose sin dependencias externas.

**SIN:** base de datos, outbox, idempotencia, circuit breaker, bulkhead, retry, secrets manager, REST endpoints, SDKs de pasarelas.

---

## Regla de Implementación

Todos los módulos (Model, UseCase, Driven Adapter, Entry Point) **DEBEN generarse con las tareas Gradle del plugin Bancolombia Scaffold**. La creación manual de estructura de módulos está **PROHIBIDA**.

```bash
# Siempre desde la raíz de ms-payment/
./gradlew generateModel --name=<Name>
./gradlew generateUseCase --name=<Name>
./gradlew generateDrivenAdapter --type=<type> [--name=<name>]
./gradlew generateEntryPoint --type=<type>
./gradlew validateStructure
```

Ver `.agents/skills/scaffold-tasks/SKILL.md` para referencia completa.

**REUTILIZACIÓN OBLIGATORIA:** El patrón Kafka Consumer (`KafkaReceiver` + `KafkaConsumerConfig` + `KafkaConsumerLifecycle`) y el Kafka Producer se **copian y adaptan** de `ms-inventory`. La razón: `ReactiveKafkaConsumerTemplate` fue eliminado en spring-kafka 4.0 (Spring Boot 4.0.3); el único enfoque correcto es `KafkaReceiver` de reactor-kafka directamente (§B.12 del diseño).

---

## Estructura Final Esperada

```
ms-payment/
├── domain/
│   ├── model/           → :model   — PaymentEvent, PaymentGateway port (mock impl)
│   └── usecase/         → :usecase — MockPaymentUseCase (lógica 80/20)
├── infrastructure/
│   ├── driven-adapters/
│   │   └── kafka-producer/          — KafkaPaymentProducer (publica payment-events)
│   └── entry-points/
│       └── kafka-consumer/          — KafkaEventConsumer, Config, Lifecycle
└── applications/
    └── app-service/                 — Spring Boot main, application.yaml
```

---

## Contrato de Eventos (Referencia Crítica)

### Evento consumido — `order-events` (eventType: `OrderCreated`)

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "timestamp": "2026-04-21T10:00:00Z",
  "source": "ms-order",
  "correlationId": "uuid",
  "payload": {
    "orderId": "uuid",
    "customerId": "uuid",
    "customerEmail": "customer@arka.com",
    "items": [...],
    "totalAmount": 690000.00
  }
}
```

### Evento publicado — `payment-events` (PaymentProcessed / PaymentFailed)

```json
{
  "eventId": "uuid-generado",
  "eventType": "PaymentProcessed",
  "timestamp": "2026-04-21T10:00:01Z",
  "source": "ms-payment",
  "correlationId": "orderId-como-correlacion",
  "payload": {
    "orderId": "uuid",
    "transactionId": "mock-txn-uuid",
    "status": "COMPLETED"
  }
}
```

```json
{
  "eventId": "uuid-generado",
  "eventType": "PaymentFailed",
  "timestamp": "2026-04-21T10:00:01Z",
  "source": "ms-payment",
  "correlationId": "orderId-como-correlacion",
  "payload": {
    "orderId": "uuid",
    "reason": "Mock payment rejected (simulated 20% failure rate)"
  }
}
```

> **Campos mínimos que ms-order necesita extraer** (ver `KafkaEventConsumer.java` de ms-order):
>
> - `PaymentProcessed`: `payload.orderId`
> - `PaymentFailed`: `payload.orderId`, `payload.reason`

---

## Tareas

### Tarea 1 — Configurar dependencias y application.yaml

- [ ] 1.1 Agregar dependencias en `build.gradle` (app-service) y `main.gradle`:
  - `io.projectreactor.kafka:reactor-kafka:1.3.25` (en módulo kafka-producer y kafka-consumer cuando se creen)
  - `spring-kafka` (del BOM de Spring Boot — sin versión)
  - `jackson-databind` (del BOM)
  - `net.jqwik:jqwik:1.9.2` (testImplementation en main.gradle)
  - **NO** agregar: R2DBC, MongoDB, Redis, Resilience4j, AWS SDK, gRPC
  - _Versiones de la tabla "Versionado Unificado" de `reusability.md`. Referencia: `ms-inventory/build.gradle` + `ms-inventory/main.gradle`._

- [ ] 1.2 Crear `application.yaml` (base), `application-local.yaml` y `application-docker.yaml`:
  - **Base (`application.yaml`):**
    ```yaml
    spring:
      profiles:
        active: ${SPRING_PROFILES_ACTIVE:local}
      application:
        name: ms-payment
    kafka:
      consumer:
        group-id: payment-service-group
        topics:
          order-events: order-events
      producer:
        topics:
          payment-events: payment-events
    ```
  - **Local (`application-local.yaml`):**
    ```yaml
    spring:
      kafka:
        bootstrap-servers: localhost:9092
    ```
  - **Docker (`application-docker.yaml`):**
    ```yaml
    spring:
      kafka:
        bootstrap-servers: arka-kafka:9092
    ```
  - **Copiar estructura exacta** de `ms-inventory/applications/app-service/src/main/resources/` (§B.10, reusability.md #9).
  - _Sin credenciales, sin datasource, sin puertos de BD._

---

### Tarea 2 — Implementar modelo de dominio (`domain/model`)

- [ ] 2.1 Generar módulo con Scaffold y crear los records de dominio mínimos:

  ```bash
  cd ms-payment && ./gradlew generateModel --name=Payment
  ```

  Crear manualmente en `com.arka.model.payment`:
  - **`PaymentStatus`** — enum con valores `COMPLETED`, `FAILED`
  - **`PaymentProcessedPayload`** — record con `UUID orderId`, `String transactionId`, `PaymentStatus status`
    - `@Builder(toBuilder = true)`, Lombok
  - **`PaymentFailedPayload`** — record con `UUID orderId`, `String reason`
    - `@Builder(toBuilder = true)`, Lombok

- [ ] 2.2 Crear `DomainEventEnvelope` en `com.arka.model.payment.event`:
  - **Copiar** de `ms-order/domain/model/src/main/java/com/arka/model/outboxevent/DomainEventEnvelope.java`
  - Cambiar la constante `MS_SOURCE = "ms-payment"` (único cambio)
  - Campos: `eventId`, `eventType`, `timestamp`, `source`, `correlationId`, `payload` (Object)
  - Compact constructor con `Objects.requireNonNull` y defaults para `timestamp` y `source`

- [ ] 2.3 Crear el port (interfaz Gateway) `PaymentEventPublisher` en `com.arka.model.payment.gateways`:
  - Un único método:
    ```java
    Mono<Void> publishPaymentEvent(String orderId, DomainEventEnvelope envelope);
    ```
  - Este port desacopla el usecase del Kafka concreto (Clean Architecture).

---

### Tarea 3 — Implementar caso de uso (`domain/usecase`)

- [ ] 3.1 Generar UseCase con Scaffold:

  ```bash
  cd ms-payment && ./gradlew generateUseCase --name=ProcessPayment
  ```

- [ ] 3.2 Implementar `ProcessPaymentUseCase` en `com.arka.usecase.processPayment`:
  - Dependencia inyectada: `PaymentEventPublisher` (port del modelo)
  - Método público principal:
    ```java
    public Mono<Void> process(UUID orderId, UUID correlationId)
    ```
  - **Lógica mock:**
    1. Generar resultado aleatorio: `new Random().nextDouble() < 0.80` → `PaymentProcessed`, resto → `PaymentFailed`
    2. Si `PaymentProcessed`:
       - Construir `PaymentProcessedPayload` con `orderId`, `transactionId = "mock-txn-" + UUID.randomUUID()`, `status = COMPLETED`
       - Construir `DomainEventEnvelope` con `eventType = "PaymentProcessed"`, `correlationId = orderId.toString()`
    3. Si `PaymentFailed`:
       - Construir `PaymentFailedPayload` con `orderId`, `reason = "Mock payment rejected (simulated 20% failure rate)"`
       - Construir `DomainEventEnvelope` con `eventType = "PaymentFailed"`, `correlationId = orderId.toString()`
    4. Llamar a `paymentEventPublisher.publishPaymentEvent(orderId.toString(), envelope)`
  - Log INFO: `"Processing mock payment for orderId={} → {}"`, con el eventType resultante
  - `@RequiredArgsConstructor` (Lombok)

---

### Tarea 4 — Implementar Kafka Producer (driven adapter)

- [ ] 4.1 Generar módulo con Scaffold:

  ```bash
  cd ms-payment && ./gradlew generateDrivenAdapter --type=generic --name=kafka-producer
  ```

  > El tipo `generic` crea un módulo vacío. El tipo `kafka` genera un producer de bajo nivel; para este mock usamos `generic` para tener control total con `KafkaSender` de reactor-kafka.

- [ ] 4.2 Implementar `KafkaPaymentProducer` en `com.arka.producer` (implementa `PaymentEventPublisher`):
  - **Copiar y adaptar** de `ms-inventory/infrastructure/driven-adapters/kafka-producer/` (reusability.md #2)
  - Dependencias del módulo: `io.projectreactor.kafka:reactor-kafka:1.3.25`, `com.fasterxml.jackson.core:jackson-databind`
  - Bean `KafkaSender<String, String>` configurado con `SenderOptions.create(producerProperties)`
  - Implementar `publishPaymentEvent(String orderId, DomainEventEnvelope envelope)`:
    - Serializar `envelope` a JSON con `ObjectMapper`
    - Usar `orderId` como partition key (`ProducerRecord<>(topic, orderId, json)`)
    - Topic leído de `@Value("${kafka.producer.topics.payment-events}")`
    - Retornar `Mono<Void>`
  - Log INFO: `"Published {} event for orderId={}"` con eventType y orderId

- [ ] 4.3 Agregar al `settings.gradle` el nuevo módulo generado por Scaffold:
  - Verificar que `kafka-producer` quedó incluido (Scaffold lo hace automáticamente, pero confirmar).

---

### Tarea 5 — Implementar Kafka Consumer (entry point)

- [ ] 5.1 Generar módulo con Scaffold:

  ```bash
  cd ms-payment && ./gradlew generateEntryPoint --type=kafka
  ```

- [ ] 5.2 Implementar `KafkaConsumerConfig` en `com.arka.consumer`:
  - **Copiar y adaptar** de `ms-inventory/infrastructure/entry-points/kafka-consumer/src/main/java/com/arka/consumer/KafkaConsumerConfig.java`
  - Crear un único bean `@Bean @Qualifier("orderEventsReceiver") KafkaReceiver<String, String>`:
    - Consumer group: `payment-service-group`
    - Topic: `order-events`
    - Propiedades tomadas del `application.yaml`
  - Dependencia del módulo: `io.projectreactor.kafka:reactor-kafka:1.3.25`

- [ ] 5.3 Implementar `KafkaEventConsumer` en `com.arka.consumer`:
  - **Copiar y adaptar** de `ms-inventory/infrastructure/entry-points/kafka-consumer/src/main/java/com/arka/consumer/KafkaEventConsumer.java`
  - Dependencias: `ProcessPaymentUseCase`, `KafkaReceiver<String, String>` (qualifier `orderEventsReceiver`), `ObjectMapper`
  - Método `startConsuming()`: suscribe al receiver con `receive().flatMap(...)`
  - Método `handleOrderEvent(String rawValue)`:
    1. Parsear JSON con `ObjectMapper` → `JsonNode envelope`
    2. Leer `eventType`
    3. `switch(eventType)`:
       - `"OrderCreated"` → extraer `payload.orderId` y `eventId`, invocar `processPaymentUseCase.process(orderId, eventId-as-correlationId)`
       - `default` → log WARN `"Unknown eventType '{}' on topic order-events — ignoring"` y `Mono.empty()`
    4. `onErrorResume`: log error, acknowledge, `Mono.empty()` (nunca bloquear la cola)
  - Acknowledge de offset siempre (even on error), para no frenar el consumer group
  - Log DEBUG al inicio de cada `OrderCreated` recibido

- [ ] 5.4 Implementar `KafkaConsumerLifecycle` en `com.arka.consumer`:
  - **Copiar y adaptar** de `ms-inventory/infrastructure/entry-points/kafka-consumer/src/main/java/com/arka/consumer/KafkaConsumerLifecycle.java`
  - Escuchar `ApplicationReadyEvent` con `@EventListener`
  - Llamar a `kafkaEventConsumer.startConsuming()`
  - Log INFO: `"Starting Kafka consumer for order-events — ms-payment mock"`

---

### Tarea 6 — Configurar app-service y wiring

- [ ] 6.1 Verificar que `MainApplication.java` existe y está correctamente anotado con `@SpringBootApplication`.
  - Si no existe, crearlo en `com.arka` bajo `applications/app-service/src/main/java/com/arka/`.

- [ ] 6.2 Crear bean de configuración `AppConfig` (o en `MainApplication`) que registre el `KafkaPaymentProducer` como implementación del port `PaymentEventPublisher`:
  - Esto se resuelve automáticamente si `KafkaPaymentProducer` está anotado con `@Component` e implementa `PaymentEventPublisher`. Confirmar que Spring DI conecta el port correctamente.
  - Si app-service no tiene dependencia directa del módulo `kafka-producer`, agregar `implementation project(':kafka-producer')` en `applications/app-service/build.gradle`.

- [ ] 6.3 Verificar `settings.gradle` incluye todos los módulos generados:
  ```groovy
  include ':app-service'
  include ':model'
  include ':usecase'
  include ':kafka-producer'   // driven adapter
  include ':kafka-consumer'   // entry point
  ```
  Ajustar `projectDir` para cada módulo según la ubicación real generada por Scaffold.

---

### Tarea 7 — Validación y compilación

- [ ] 7.1 Ejecutar validación de estructura:

  ```bash
  cd ms-payment && ./gradlew validateStructure
  ```

  Corregir cualquier violación de Clean Architecture reportada.

- [ ] 7.2 Compilar el proyecto completo:

  ```bash
  cd ms-payment && ./gradlew build -x test
  ```

  Resolver errores de compilación. No avanzar si hay errores.

- [ ] 7.3 Agregar ms-payment al `compose.yaml` raíz:
  - Usar el mismo patrón que `ms-catalog`, `ms-inventory` u `ms-order`
  - Puerto sugerido: `8084` (verificar `docs/10-urls-puertos-globales.md` para no colisionar)
  - Variables de entorno: `SPRING_PROFILES_ACTIVE=docker`
  - Dependencias Docker: `arka-kafka`
  - **NO** agregar dependencia de `arka-postgres` (sin BD)

- [ ] 7.4 Construir imagen Docker:

  ```bash
  cd ms-payment && ./gradlew :app-service:bootBuildImage
  ```

  Verificar que la imagen construye sin errores.

- [ ] 7.5 Smoke test end-to-end:

  ```bash
  # Levantar el ecosistema mínimo
  docker compose up -d arka-kafka ms-order ms-payment

  # Crear una orden (ms-order produce OrderCreated → ms-payment consume → publica PaymentProcessed/Failed → ms-order transiciona estado)
  curl -s -X POST http://localhost:8081/api/v1/orders \
    -H "Content-Type: application/json" \
    -H "X-User-Email: customer1@arka.com" \
    -H "X-User-Role: CUSTOMER" \
    -d '{
      "customerId": "482eae01-3840-3d80-9a3b-17333e6b32d5",
      "customerEmail": "customer1@arka.com",
      "shippingAddress": "Calle 123 #45-67, Bogotá, Colombia",
      "items": [{"productId": "f47ac10b-58cc-4372-a567-0e02b2c3d001", "sku": "KB-MECH-001", "quantity": 1, "unitPrice": 185000.00}]
    }' | python3 -m json.tool

  # Verificar en logs de ms-payment que procesó el evento
  docker compose logs ms-payment | grep "Processing mock payment"

  # Verificar en logs de ms-order que recibió PaymentProcessed o PaymentFailed
  docker compose logs ms-order | grep "PaymentProcessed\|PaymentFailed"
  ```

  - **Éxito:** la orden en ms-order transiciona a `CONFIRMADO` (80% de los casos) o permanece/cancela (20%).
  - No se requiere que el 80/20 sea exacto en una sola prueba; validar en ~10 órdenes.

---

## Resumen de lo que NO se implementa

| Componente                   | Razón de exclusión en el mock             |
| ---------------------------- | ----------------------------------------- |
| PostgreSQL / R2DBC           | No hay persistencia — todo en memoria     |
| Outbox Pattern               | Publicación directa a Kafka (sin BD)      |
| Tabla `processed_events`     | Sin idempotencia formal (mock sin riesgo) |
| Circuit Breaker / Retry      | No hay pasarela externa que falle         |
| Bulkhead                     | Sin concurrencia real a gestionar         |
| AWS Secrets Manager          | Sin credenciales de pasarelas             |
| REST endpoints (`/payments`) | Rol del ms es solo event-driven           |
| SDKs Stripe / Wompi / MP     | Reemplazados por `Random.nextDouble()`    |
| Strategy + Factory pattern   | Una sola implementación mock              |
