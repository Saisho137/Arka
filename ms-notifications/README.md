# ms-notifications

Microservicio dueño del Bounded Context **Alertas y Notificaciones Transaccionales** dentro de la plataforma B2B Arka. Actúa como consumer "Catch-All" de múltiples tópicos Kafka, mapea eventos de dominio a plantillas de email y dispara correos vía AWS SES.

---

## Stack Tecnológico

| Componente    | Tecnología                                          |
| ------------- | --------------------------------------------------- |
| Lenguaje      | Java 21                                             |
| Framework     | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)   |
| Base de datos | MongoDB (Reactive Driver) — Replica Set `rs0`       |
| Mensajería    | Apache Kafka 8 (KRaft) — reactor-kafka              |
| Email         | AWS SES (LocalStack en desarrollo)                  |
| Build         | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0      |
| Lombok        | 1.18.42                                             |
| Calidad       | JaCoCo · PiTest · ArchUnit · BlockHound             |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Correos transaccionales vía **AWS SES** ante cambios de estado en el ecosistema
- Consumer "Catch-All": escucha múltiples tópicos Kafka y mapea eventos a plantillas
- Reintentos con **backoff exponencial** para fallos de envío
- **Idempotencia** por unique index en `eventId` (MongoDB) — previene duplicados
- Historial de notificaciones enviadas persistido en MongoDB

---

## Estructura de Módulos

```text
ms-notifications/
├── applications/app-service/           # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml            # Config base
│       ├── application-local.yaml      # Perfil local (IntelliJ)
│       └── application-docker.yaml     # Perfil Docker Compose
├── domain/
│   ├── model/                          # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/notification/
│   │       ├── NotificationHistory     # Registro de notificación enviada
│   │       ├── NotificationStatus      # SENT, FAILED, PENDING
│   │       ├── DomainEventEnvelope     # Sobre estándar de eventos
│   │       └── gateways/              # Ports: EmailGateway, NotificationRepository
│   └── usecase/                        # Lógica de negocio
│       └── com/arka/usecase/notification/  # NotificationUseCase
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── mongo-repository/           # MongoNotificationAdapter (historial + idempotencia)
│   │   └── ses-email/                  # SesEmailAdapter (AWS SES vía LocalStack)
│   ├── entry-points/
│   │   └── kafka-consumer/             # KafkaEventConsumer (multi-tópico)
│   └── helpers/                        # Utilidades compartidas
└── deployment/Dockerfile               # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

**Este servicio no expone endpoints REST.** Es un consumer pasivo de Kafka que reacciona a eventos de dominio.

**Health Check:** `GET http://localhost:8085/actuator/health`

---

## Eventos Kafka Consumidos

| Tópico             | EventType              | Acción                                     |
| ------------------ | ---------------------- | ------------------------------------------ |
| `order-events`     | `OrderConfirmed`       | Email de confirmación de pedido al cliente  |
| `order-events`     | `OrderStatusChanged`   | Email de cambio de estado al cliente        |
| `order-events`     | `OrderCancelled`       | Email de cancelación al cliente             |
| `inventory-events` | `StockDepleted`        | Alerta de stock bajo al admin              |
| `cart-events`      | `CartAbandoned`        | Recordatorio de carrito abandonado          |
| `shipping-events`  | `ShippingDispatched`   | Email de despacho con tracking al cliente   |
| `provider-events`  | `PurchaseOrderCreated` | Email de orden de compra al proveedor       |

---

## Ejecución Local

```bash
# Desde Docker Compose (con dependencias)
docker compose up --build -d ms-notifications

# Verificar health
curl http://localhost:8085/actuator/health
```
