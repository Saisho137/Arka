---
sidebar_position: 5
title: "Kafka: Eventos y Tópicos"
---

# Kafka: Tópicos, Eventos y Consumers

## Estrategia

**1 tópico por Bounded Context** (servicio productor). Los eventos se discriminan por `eventType` en el sobre estándar. Partition key = ID del agregado raíz para garantizar orden causal.

- 7 tópicos fijos (crece solo al agregar un microservicio nuevo)
- Nuevo evento = nuevo `eventType`, sin cambio de infraestructura
- Consumidores filtran por `eventType` e ignoran eventos desconocidos (log warning)

### Justificación: Tópico por Servicio vs Tópico por Evento

| Criterio                | Tópico por Evento (❌ descartado)               | Tópico por Servicio (✅ adoptado)                                         |
| ----------------------- | ----------------------------------------------- | ------------------------------------------------------------------------- |
| Cantidad de tópicos     | 13+ (crece con cada nuevo evento)               | **7 fijos** (crece solo con nuevo microservicio)                          |
| Complejidad operacional | Alta: más ACLs, particiones, monitoreo          | **Baja:** un tópico por equipo/servicio owner                             |
| Ordenamiento de eventos | Sin garantía entre tópicos del mismo dominio    | **Garantizado por partición** con `aggregateId` como key                  |
| Evolución del esquema   | Nuevo evento = nuevo tópico + config + permisos | **Nuevo evento = nuevo `eventType`**, sin cambio de infraestructura       |
| Consistencia causal     | Fragmentada entre múltiples tópicos             | **Natural:** todos los eventos de un dominio fluyen por un canal ordenado |

## Configuración

- **Particiones:** 3 por tópico
- **Replicación:** Factor 1 (dev), factor 3 (producción)
- **Retención:** 7 días (168 horas)
- **Creación:** Explícita (no auto-create en producción)

---

## Tópicos y Eventos

### `product-events` (Productor: ms-catalog)

| Evento           | Descripción          | Partition Key |
| ---------------- | -------------------- | ------------- |
| `ProductCreated` | Producto registrado  | `productId`   |
| `ProductUpdated` | Producto actualizado | `productId`   |
| `PriceChanged`   | Precio modificado    | `productId`   |

### `inventory-events` (Productor: ms-inventory)

| Evento               | Descripción                                | Partition Key |
| -------------------- | ------------------------------------------ | ------------- |
| `StockReserved`      | Stock reservado para una orden             | `sku`         |
| `StockReserveFailed` | Reserva fallida por stock insuficiente     | `sku`         |
| `StockReleased`      | Stock liberado (expiración/cancelación)    | `sku`         |
| `StockUpdated`       | Stock actualizado manualmente por admin    | `sku`         |
| `StockDepleted`      | Alerta de stock bajo (umbral por producto) | `sku`         |

### `order-events` (Productor: ms-order)

| Evento               | Descripción                                  | Partition Key |
| -------------------- | -------------------------------------------- | ------------- |
| `OrderCreated`       | Orden creada                                 | `orderId`     |
| `OrderConfirmed`     | Orden confirmada (stock reservado + pago OK) | `orderId`     |
| `OrderStatusChanged` | Transición de estado                         | `orderId`     |
| `OrderCancelled`     | Orden cancelada                              | `orderId`     |

### `cart-events` (Productor: ms-cart) — Fase 2

| Evento          | Descripción                       | Partition Key |
| --------------- | --------------------------------- | ------------- |
| `CartAbandoned` | Carrito detectado como abandonado | `cartId`      |

### `payment-events` (Productor: ms-payment) — Fase 2

| Evento             | Descripción                 | Partition Key |
| ------------------ | --------------------------- | ------------- |
| `PaymentProcessed` | Pago procesado exitosamente | `orderId`     |
| `PaymentFailed`    | Pago rechazado              | `orderId`     |

### `shipping-events` (Productor: ms-shipping) — Fase 3

| Evento               | Descripción                   | Partition Key |
| -------------------- | ----------------------------- | ------------- |
| `ShippingDispatched` | Envío despachado con tracking | `orderId`     |

### `provider-events` (Productor: ms-provider) — Fase 4

| Evento                 | Descripción                          | Partition Key     |
| ---------------------- | ------------------------------------ | ----------------- |
| `PurchaseOrderCreated` | Orden de compra generada a proveedor | `purchaseOrderId` |

---

## Consumer Groups

### MVP (Fase 1)

| Consumer Group               | Servicio         | Tópicos                            | Filtra por eventType                                                      |
| ---------------------------- | ---------------- | ---------------------------------- | ------------------------------------------------------------------------- |
| `inventory-service-group`    | ms-inventory     | `product-events`, `order-events`   | `ProductCreated`, `OrderCancelled`, `OrderConfirmed`                      |
| `notification-service-group` | ms-notifications | `order-events`, `inventory-events` | `OrderConfirmed`, `OrderStatusChanged`, `OrderCancelled`, `StockDepleted` |

### Ecosistema Completo

| Consumer Group               | Servicio         | Tópicos                                                                                 |
| ---------------------------- | ---------------- | --------------------------------------------------------------------------------------- |
| `inventory-service-group`    | ms-inventory     | `product-events`, `order-events`                                                        |
| `order-service-group`        | ms-order         | `payment-events`, `shipping-events`                                                     |
| `notification-service-group` | ms-notifications | `order-events`, `inventory-events`, `cart-events`, `shipping-events`, `provider-events` |
| `payment-service-group`      | ms-payment       | `order-events`                                                                          |
| `shipping-service-group`     | ms-shipping      | `order-events`                                                                          |
| `provider-service-group`     | ms-provider      | `inventory-events`                                                                      |
| `reporter-service-group`     | ms-reporter      | **TODOS** (7 tópicos)                                                                   |

---

## Event Envelope ("Sobre" Estándar)

Todos los eventos siguen este formato:

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "timestamp": "2026-02-21T10:00:00Z",
  "source": "ms-order",
  "correlationId": "uuid-correlation",
  "payload": { ... }
}
```

### Reglas de Consumo

1. Deserializar el Sobre → leer `eventType`
2. Si es relevante → procesar `payload`
3. Si es desconocido → **ignorar con log warning** (tolerancia a evolución)
4. Nunca fallar por un `eventType` no reconocido

---

## Patrones de Resiliencia en Kafka

### Transactional Outbox Pattern

Usado por: **ms-inventory**, **ms-order** (PostgreSQL), **ms-catalog** (MongoDB)

1. Escritura de negocio + evento en `outbox_events` → **misma transacción**
2. Outbox Relay (polling cada 5s) → publica a Kafka con partition key
3. Marca como `PUBLISHED` tras ack de Kafka

### Idempotencia en Consumers

Kafka garantiza at-least-once. Cada consumer tiene tracking de eventos procesados:

- **PostgreSQL services:** Tabla `processed_events` (eventId PK)
- **MongoDB services:** Unique index en `eventId`

Antes de procesar: verificar si `eventId` existe. Si existe → ignorar. Si no → procesar + guardar.

---

## Tecnología de Consumer/Producer

Debido a la discontinuación de `reactor-kafka` y la eliminación de `ReactiveKafkaConsumerTemplate` en spring-kafka 4.0:

- **Producer:** `reactor-kafka` 1.3.25 (`KafkaSender`) — control de partition key y ack explícito para Outbox
- **Consumer:** `reactor-kafka` 1.3.25 (`KafkaReceiver`) — única opción reactiva disponible en Spring Boot 4.0.3
- **No usar:** `reactive-commons` (DomainEventBus) ni `@KafkaListener` en servicios reactivos

---

## Referencia: Estado de Kafka Reactivo en Spring Boot 4.x

Para profundizar en la discontinuación de `reactor-kafka`, la resolución del `NoSuchMethodError` con Kafka 8 y las opciones arquitectónicas disponibles, ver:

→ [caso-kafka-reactivo.md](caso-kafka-reactivo.md)
