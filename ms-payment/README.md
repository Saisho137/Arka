# ms-payment

Microservicio dueño del Bounded Context **Procesamiento de Pagos** dentro de la plataforma B2B Arka. Implementa un **Anti-Corruption Layer (ACL)** con Strategy + Factory para integrar múltiples pasarelas de pago (Stripe, Wompi, MercadoPago), procesa cobros de forma idempotente y publica resultados a Kafka mediante el **Transactional Outbox Pattern**.

---

## Stack Tecnológico

| Componente    | Tecnología                                          |
| ------------- | --------------------------------------------------- |
| Lenguaje      | Java 21                                             |
| Framework     | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)   |
| Base de datos | PostgreSQL 17 — acceso reactivo con **R2DBC**       |
| Mensajería    | Apache Kafka 8 (KRaft) — reactor-kafka              |
| Resiliencia   | Resilience4j (Circuit Breaker)                      |
| Secretos      | AWS Secrets Manager (LocalStack en desarrollo)      |
| Build         | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0      |
| Lombok        | 1.18.42                                             |
| API Docs      | Springdoc / OpenAPI (Swagger UI)                    |
| Calidad       | JaCoCo · PiTest · ArchUnit · BlockHound             |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Procesamiento de pagos con **ACL** (Strategy + Factory) para pasarelas: Stripe, Wompi, MercadoPago
- Persistencia en **PostgreSQL 17** (R2DBC) — tabla `payments` con estado del pago
- Publicación confiable de eventos de dominio a Kafka mediante **Transactional Outbox Pattern**
- Consumo idempotente de eventos `OrderCreated` del tópico `order-events` (tabla `processed_events`)
- **Circuit Breaker** (Resilience4j) para llamadas a pasarelas externas
- Credenciales de pasarelas almacenadas en **AWS Secrets Manager**
- REST endpoint para consulta de estado de pago por orden

---

## Estructura de Módulos

```text
ms-payment/
├── applications/app-service/           # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml            # Config base
│       ├── application-local.yaml      # Perfil local (IntelliJ)
│       └── application-docker.yaml     # Perfil Docker Compose
├── domain/
│   ├── model/                          # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/payment/
│   │       ├── Payment                 # Entidad principal (orderId, amount, status, gateway)
│   │       ├── PaymentStatus           # PENDING, PROCESSED, FAILED
│   │       ├── PaymentGatewayResult    # Resultado de llamada a pasarela
│   │       ├── PaymentProcessedPayload # Payload del evento PaymentProcessed
│   │       ├── PaymentFailedPayload    # Payload del evento PaymentFailed
│   │       ├── event/                  # EventType, OutboxEvent, DomainEventEnvelope
│   │       └── gateways/              # Ports: PaymentRepository, PaymentGateway, OutboxEventRepository, etc.
│   └── usecase/                        # Lógica de negocio
│       └── com/arka/usecase/
│           ├── processpayment/         # ProcessPaymentUseCase (procesa pago, persiste, publica evento)
│           └── outboxrelay/            # OutboxRelayUseCase (relay eventos pendientes a Kafka)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/           # Adapters R2DBC: Payment, Outbox, ProcessedEvent repos
│   │   └── kafka-producer/             # KafkaOutboxRelay (scheduler cada 5s)
│   ├── entry-points/
│   │   ├── reactive-web/              # PaymentController (GET /api/v1/payments/orders/{orderId})
│   │   └── kafka-consumer/            # KafkaEventConsumer (order-events → OrderCreated)
│   └── helpers/                        # Utilidades compartidas
└── deployment/Dockerfile               # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/api/v1/payments` — Puerto HTTP: `8083`

| Método | Ruta                 | Descripción                    | Códigos HTTP |
| ------ | -------------------- | ------------------------------ | ------------ |
| `GET`  | `/orders/{orderId}`  | Consultar pago por ID de orden | 200, 404     |

**Swagger UI:** `http://localhost:8083/swagger-ui.html`
**OpenAPI:** `http://localhost:8083/api-docs`

---

## Eventos Kafka

**Tópico productor:** `payment-events` (partition key = `orderId`)

| EventType          | Trigger                              |
| ------------------ | ------------------------------------ |
| `PaymentProcessed` | Pago procesado exitosamente          |
| `PaymentFailed`    | Pago rechazado por pasarela          |

**Tópicos consumidores:**

| Tópico         | EventType      | Acción                                         |
| -------------- | -------------- | ---------------------------------------------- |
| `order-events` | `OrderCreated` | Procesa pago vía pasarela configurada (ACL)    |

---

## Ejecución Local

```bash
# Desde Docker Compose (con dependencias)
docker compose up --build -d ms-payment

# Verificar health
curl http://localhost:8083/actuator/health

# Consultar pago de una orden
curl http://localhost:8083/api/v1/payments/orders/{orderId}
```
