# ms-shipping

Microservicio de logística y envíos para la plataforma Arka e-commerce B2B. Gestiona la generación de guías de envío con múltiples carriers (DHL, FedEx, Legacy), tracking de paquetes y webhooks de actualización de estado.

## Stack

- Java 21 + Spring Boot 4.0.3 (WebFlux — Reactivo)
- PostgreSQL 17 (R2DBC)
- Kafka (Reactor Kafka — producer/consumer)
- AWS S3 (LocalStack — almacenamiento de labels PDF)
- Resilience4j (Circuit Breaker, Retry, Bulkhead por carrier)

## Arquitectura

Clean Architecture con Bancolombia Scaffold Plugin 4.2.0:

```
applications/app-service/    → Spring Boot main, DI, health indicators
domain/model/                → Entities, Value Objects, Gateway ports
domain/usecase/              → Business logic (7 use cases)
infrastructure/
  driven-adapters/
    r2dbc-postgresql/        → Shipment, Outbox, ProcessedEvent repos
    carrier-factory/         → DHL, FedEx, Legacy adapters + Resilience4j
    s3-repository/           → Label PDF storage
    kafka-producer/          → Outbox relay → Kafka
    async-event-bus/         → Domain events
  entry-points/
    reactive-web/            → REST controllers (Shipment + Webhook)
    kafka-consumer/          → Order events consumer
  helpers/
    metrics/                 → Micrometer metrics
```

## Ejecutar localmente

```bash
# Requiere: PostgreSQL + Kafka + LocalStack (docker compose up desde raíz del monorepo)
./gradlew bootRun
```

Puerto: `8088` (configurable via `MS_SHIPPING_PORT`)

## Tests

```bash
./gradlew test                  # Unit + integration tests
./gradlew validateStructure     # Clean Architecture validation
```

## Build Docker

```bash
docker build -t ms-shipping:latest -f deployment/Dockerfile .
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/v1/shipments/{orderId}` | Any | Get shipment by order ID |
| GET | `/api/v1/shipments` | ADMIN | List shipments (paginated, filterable) |
| PUT | `/api/v1/shipments/{orderId}/status` | ADMIN | Update shipment status |
| POST | `/api/v1/shipments/retry/{orderId}` | ADMIN | Retry failed shipment |
| POST | `/api/v1/webhooks/{carrier}/tracking` | HMAC | Carrier tracking webhook |

Headers: `X-User-Role`, `X-User-Email`, `X-Webhook-Signature`

## Kafka

| Topic | Role | Events |
|-------|------|--------|
| `order-events` | Consumer | OrderConfirmed → triggers label generation |
| `shipping-events` | Producer | ShippingDispatched (via Outbox Pattern) |

## Patrones

- **ACL (Anti-Corruption Layer)**: Strategy + Factory para 3 carriers
- **Outbox Pattern**: Transaccional BD + evento atómico, relay cada 5s
- **Circuit Breaker**: 50% failure threshold, 30s open state
- **Retry**: 3 intentos con exponential backoff (2s base, x2 multiplier)
- **Bulkhead**: 10 concurrent calls por carrier
- **Idempotency**: ProcessedEvents table para evitar duplicados Kafka

## Configuración

| Variable | Default | Description |
|----------|---------|-------------|
| `MS_SHIPPING_PORT` | 8088 | Server port |
| `R2DBC_HOST` | localhost | PostgreSQL host |
| `R2DBC_PORT` | 5436 | PostgreSQL port |
| `R2DBC_DB` | db_shipping | Database name |
| `KAFKA_BOOTSTRAP_SERVERS` | localhost:9092 | Kafka brokers |
| `AWS_ENDPOINT` | http://localhost:4566 | LocalStack endpoint |
| `DHL_WEBHOOK_SECRET` | test-dhl-webhook-secret | DHL HMAC secret |
| `FEDEX_WEBHOOK_SECRET` | test-fedex-webhook-secret | FedEx HMAC secret |
| `LEGACY_WEBHOOK_SECRET` | test-legacy-webhook-secret | Legacy HMAC secret |

![Clean Architecture](https://miro.medium.com/max/1400/1*ZdlHz8B0-qu9Y-QO3AXR_w.png)

## Domain

Es el módulo más interno de la arquitectura, pertenece a la capa del dominio y encapsula la lógica y reglas del negocio mediante modelos y entidades del dominio.

## Usecases

Este módulo gradle perteneciente a la capa del dominio, implementa los casos de uso del sistema, define lógica de aplicación y reacciona a las invocaciones desde el módulo de entry points, orquestando los flujos hacia el módulo de entities.

## Infrastructure

### Helpers

En el apartado de helpers tendremos utilidades generales para los Driven Adapters y Entry Points.

Estas utilidades no están arraigadas a objetos concretos, se realiza el uso de generics para modelar comportamientos
genéricos de los diferentes objetos de persistencia que puedan existir, este tipo de implementaciones se realizan
basadas en el patrón de diseño [Unit of Work y Repository](https://medium.com/@krzychukosobudzki/repository-design-pattern-bc490b256006)

Estas clases no puede existir solas y debe heredarse su compartimiento en los **Driven Adapters**

### Driven Adapters

Los driven adapter representan implementaciones externas a nuestro sistema, como lo son conexiones a servicios rest,
soap, bases de datos, lectura de archivos planos, y en concreto cualquier origen y fuente de datos con la que debamos
interactuar.

### Entry Points

Los entry points representan los puntos de entrada de la aplicación o el inicio de los flujos de negocio.

## Application

Este módulo es el más externo de la arquitectura, es el encargado de ensamblar los distintos módulos, resolver las dependencias y crear los beans de los casos de use (UseCases) de forma automática, inyectando en éstos instancias concretas de las dependencias declaradas. Además inicia la aplicación (es el único módulo del proyecto donde encontraremos la función “public static void main(String[] args)”.

**Los beans de los casos de uso se disponibilizan automaticamente gracias a un '@ComponentScan' ubicado en esta capa.**
