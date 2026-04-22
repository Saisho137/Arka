---
sidebar_position: 3
title: Microservicios
---

# Microservicios

## Mapa de Servicios

| Servicio         | Dominio                                  | BD                         | Paradigma  | Puerto | Fase |
| ---------------- | ---------------------------------------- | -------------------------- | ---------- | ------ | ---- |
| ms-catalog       | Catálogo de productos + reseñas anidadas | MongoDB + Redis            | Reactivo   | 8084   | 1    |
| ms-inventory     | Stock, reservas, lock pesimista          | PostgreSQL 17 (R2DBC)      | Reactivo   | 8082   | 1    |
| ms-order         | Pedidos, Saga orchestrator               | PostgreSQL 17 (R2DBC)      | Reactivo   | 8081   | 1    |
| ms-notifications | Alertas y correos (AWS SES)              | MongoDB                    | Reactivo   | 8085   | 1    |
| ms-cart          | Carrito de compras, abandono             | MongoDB                    | Reactivo   | 8086   | 2    |
| ms-payment       | Pagos mock (80% éxito / 20% fallo)       | Sin BD (event-driven puro) | Reactivo   | 8083   | 2    |
| ms-reporter      | Reportes, CQRS, Event Sourcing           | PostgreSQL 17 (JDBC) + S3  | Imperativo | 8087   | 3    |
| ms-shipping      | Logística ACL (DHL/FedEx/Legacy)         | PostgreSQL 17 (R2DBC)      | Reactivo   | 8088   | 3    |
| ms-provider      | Proveedores B2B ACL                      | PostgreSQL 17 (R2DBC)      | Reactivo   | 8089   | 4    |

---

## Fase 1 — MVP

### ms-catalog (HU1 — Registrar productos)

- CRUD de productos con SKU, nombre, descripción, precio, categoría
- Gestión de categorías maestras
- Reseñas como subdocumentos anidados en MongoDB
- Cache-Aside con Redis (TTL 1h, 95% cache hit, <1ms)
- Servidor gRPC (`CatalogService.GetProductInfo`) para consulta de precio y nombre por SKU desde ms-order y ms-cart
- Eventos Kafka: `ProductCreated`, `ProductUpdated`, `PriceChanged`
- Outbox Pattern adaptado a MongoDB (colección `outbox_events`)

### ms-inventory (HU2 — Actualizar stock)

- Control de stock por SKU con constraints `quantity >= 0`
- Lock pesimista (`SELECT ... FOR UPDATE`) para prevenir sobreventa
- Reservas temporales con timeout de 15 minutos
- Liberación automática de reservas expiradas (job cada 60s)
- Servidor gRPC para reserva síncrona desde ms-order
- Umbral de alerta configurable por SKU (`depletion_threshold`)
- Idempotencia en consumers Kafka (tabla `processed_events`)
- Eventos Kafka: `StockReserved`, `StockReserveFailed`, `StockReleased`, `StockUpdated`, `StockDepleted`

### ms-order (HU4 — Registrar órdenes)

- Creación de pedidos con validación síncrona de stock vía gRPC a ms-inventory
- Consulta de precio y nombre autoritativo vía gRPC a ms-catalog (fuente de verdad del precio, no el frontend)
- Máquina de estados: `PENDIENTE_RESERVA` → `CONFIRMADO` → `EN_DESPACHO` → `ENTREGADO` | `CANCELADO`
- Orquestador pasivo de Saga Secuencial
- Outbox Pattern (PostgreSQL)
- Eventos Kafka: `OrderCreated`, `OrderConfirmed`, `OrderStatusChanged`, `OrderCancelled`

#### Transiciones de Estado

| Desde                       | Hacia          | Trigger                        |
| --------------------------- | -------------- | ------------------------------ |
| PENDIENTE_RESERVA           | CONFIRMADO     | gRPC exitoso (Fase 1)          |
| PENDIENTE_RESERVA           | CANCELADO      | Stock insuficiente (fail-fast) |
| CONFIRMADO                  | EN_DESPACHO    | Admin marca despacho           |
| CONFIRMADO                  | CANCELADO      | Admin/cliente cancela          |
| EN_DESPACHO                 | ENTREGADO      | Admin marca entrega            |
| _Fase 2:_ PENDIENTE_RESERVA | PENDIENTE_PAGO | gRPC exitoso + ms-payment      |
| _Fase 2:_ PENDIENTE_PAGO    | CONFIRMADO     | PaymentProcessed               |
| _Fase 2:_ PENDIENTE_PAGO    | CANCELADO      | PaymentFailed                  |

> Estados terminales: `ENTREGADO` y `CANCELADO` (no permiten transiciones).

### ms-notifications (HU6 — Notificaciones)

- Correos transaccionales vía AWS SES
- Consumer "Catch-All": escucha múltiples tópicos, mapea a plantillas, dispara correos
- Reintentos con backoff exponencial
- Idempotencia por unique index en `eventId` (MongoDB)
- Consume: `OrderConfirmed`, `OrderStatusChanged`, `OrderCancelled`, `StockDepleted`

---

## Fase 2

### ms-cart (HU8 — Carritos abandonados)

- Carritos temporales con items en MongoDB
- Mutaciones atómicas (`$push`/`$pull`)
- CronJob detecta carritos expirados → evento `CartAbandoned`
- gRPC a ms-catalog para precio actualizado en checkout

### ms-payment (HU5 — Pagos)

> **Implementación mock.** No integra pasarelas reales. Simula el procesamiento de pago con `Random` (80 % éxito, 20 % fallo) sin base de datos ni persistencia.

- Consume `OrderCreated` del tópico `order-events`
- Genera `PaymentProcessed` (80 %) o `PaymentFailed` (20 %) con `Random.nextDouble()`
- Publica el resultado a `payment-events` con `orderId` como partition key
- **Sin:** R2DBC, Outbox, idempotencia, Circuit Breaker, Secrets Manager, REST endpoints, SDKs de pasarelas
- Patron Kafka Consumer: `KafkaReceiver` (reactor-kafka directo — `ReactiveKafkaConsumerTemplate` eliminado en spring-kafka 4.0)
- Eventos Kafka: `PaymentProcessed`, `PaymentFailed`

---

## Fase 3

### ms-reporter (HU7, HU3 — Reportes)

- **Único servicio imperativo** (MVC + Virtual Threads)
- CQRS + Event Sourcing: consume TODOS los eventos de Kafka
- Almacena payloads en JSONB con índices GIN
- Exporta CSV/PDF hasta 500MB a AWS S3
- PostgreSQL 17 con JDBC

### ms-shipping (Logística)

- ACL para operadores logísticos (DHL, FedEx) y monolito legacy
- SDKs bloqueantes con `Schedulers.boundedElastic()`
- Consume `OrderStatusChanged` (EN_DESPACHO)
- Publica `ShippingDispatched` con tracking
- Circuit Breaker (Resilience4j)

---

## Fase 4

### ms-provider (Abastecimiento)

- ACL para proveedores externos
- Consume `StockDepleted` → genera orden de compra automática
- Publica `PurchaseOrderCreated` → ms-notifications envía email al proveedor
- Recepción de mercancía: admin actualiza stock manualmente vía `PUT /inventory/{sku}/stock`
- Pago al proveedor fuera del sistema

---

## ¿Por qué ms-catalog y ms-inventory están separados?

Son **Bounded Contexts distintos** con necesidades opuestas:

| Aspecto          | ms-catalog                  | ms-inventory                    |
| ---------------- | --------------------------- | ------------------------------- |
| Pregunta         | ¿QUÉ vendemos?              | ¿CUÁNTO hay disponible?         |
| Datos            | Maestros, estáticos         | Transaccionales, dinámicos      |
| Acceso           | 95% lecturas, 5% escrituras | 60% escrituras, 40% lecturas    |
| Consistencia     | Eventual (caché)            | ACID estricto (lock pesimista)  |
| Escalado         | Horizontal + Redis          | Vertical + PostgreSQL locks     |
| Problema crítico | Búsqueda eficiente          | **Sobreventa por concurrencia** |
