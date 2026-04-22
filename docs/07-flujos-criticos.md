---
sidebar_position: 6
title: Flujos Críticos
---

# Flujos Críticos del Sistema

## 1. Creación de Pedido — Happy Path (Implementación actual — Fase 2)

> **Estado actual:** `createOrder()` persiste en `PENDIENTE_PAGO` y emite `OrderCreated`.
> ms-payment (pendiente) debe consumir ese evento y emitir `PaymentProcessed`/`PaymentFailed`.
> **Demo sin ms-payment:** simular manualmente el evento via Kafka UI (ver sección 6).

```text
Cliente B2B ──POST /orders──▶ API Gateway ──JWT──▶ ms-order
                                                      │
                                          Valida request
                                                      │
                                          ═══ gRPC ═══▼
                                                  ms-catalog
                                          GetProductInfo(sku) por cada item
                                          ← unitPrice, productName
                                                      │
                                          ═══ gRPC ═══▼
                                                  ms-inventory
                                                      │
                                          BEGIN TRANSACTION
                                          SELECT ... FOR UPDATE (lock)
                                          Reserva stock + stock_reservation
                                          Registra stock_movement
                                          Guarda StockReserved en outbox
                                          COMMIT
                                                      │
                                          gRPC Response: success ◄──
                                                      │
                                          Guarda orden PENDIENTE_PAGO
                                          (items con precio de catálogo)
                                          Guarda OrderCreated en outbox
                                          ──▶ 202 Accepted al cliente
                                                      │
                                          Outbox Relay (5s) ──▶ Kafka: order-events (OrderCreated)
                                                                     │
                                                 [ms-payment — pendiente impl.]
                                                 Simular con Kafka UI → payment-events
                                                     │
                                               ms-order consume PaymentProcessed
                                               PENDIENTE_PAGO → CONFIRMADO
                                               Publica OrderConfirmed ──▶ order-events
                                                     │
                                               ms-notifications → Email vía AWS SES
```

## 2. Creación de Pedido — Happy Path (Fase 2 con ms-payment — diseño completo)

```text
ms-order ──gRPC──▶ ms-catalog (precio y nombre por SKU)
    │
ms-order ──gRPC──▶ ms-inventory (stock reservado)
    │
    Guarda orden PENDIENTE_PAGO (items con precio de catálogo)
    Publica OrderCreated ──▶ Kafka
                                │
                          ms-payment (consume)
                          Cobra vía pasarela
                          Publica PaymentProcessed ──▶ Kafka
                                                         │
                                                   ms-order (consume)
                                                   PENDIENTE_PAGO → CONFIRMADO
                                                   Publica OrderConfirmed
                                                         │
                                                   ms-notifications → Email
```

## 3. Stock Insuficiente — Fail-Fast

```text
ms-order ──gRPC──▶ ms-inventory
                        │
                  SELECT ... FOR UPDATE
                  available=3, requested=10
                  3 < 10 → INSUFICIENTE
                        │
                  gRPC: { success: false, available: 3 }
                        │
ms-order ◄── 409 Conflict al cliente
                  "Stock insuficiente (disponible: 3, solicitado: 10)"
                  NO se persiste orden ni se publican eventos
```

## 4. Fallo de Pago — Compensación (Fase 2)

```text
ms-payment ──PaymentFailed──▶ Kafka
                                  │
                            ms-order (consume)
                            PENDIENTE_PAGO → CANCELADO
                            Publica ReleaseStock
                                  │
                            ms-inventory (consume)
                            Libera stock reservado
                            Registra movimiento RESERVATION_RELEASE
                                  │
                            ms-notifications → Email cancelación
```

## 5. Registro de Producto → Stock Inicial

```text
Admin ──POST /products──▶ ms-catalog
                              │
                        Valida (SKU único, precio > 0)
                        Guarda en MongoDB
                        Guarda ProductCreated en outbox
                        ──▶ 201 Created
                              │
                        Outbox Relay ──▶ Kafka: product-events
                                              │
                                        ms-inventory (consume)
                                        Crea registro stock (qty = initialStock)
                                        Registra movimiento PRODUCT_CREATION
```

## 6. Actualización de Estado por Admin

```text
Admin ──PUT /orders/{id}/status──▶ ms-order
                                       │
                                 Valida transición (CONFIRMADO → EN_DESPACHO)
                                 Actualiza order.status
                                 Registra en order_state_history
                                 Publica OrderStatusChanged ──▶ Kafka
                                                                  │
                                                            ms-notifications
                                                            Email: "Pedido despachado"
```

## 7. Saga Secuencial

### Fase 1 (3 pasos)

| Paso | Servicio   | Acción                              | Mecanismo | Compensación             |
| ---- | ---------- | ----------------------------------- | --------- | ------------------------ |
| 0    | ms-catalog | Consulta precio/nombre (gRPC)       | gRPC sync | Fail-fast (503)          |
| 1    | ms-order   | Reserva stock (gRPC a ms-inventory) | gRPC sync | Fail-fast (no hay stock) |
| 2    | ms-order   | Confirma orden (precio de catálogo) | Local     | N/A                      |

### Fase 2 (4 pasos)

| Paso | Servicio   | Acción                              | Mecanismo   | Compensación          |
| ---- | ---------- | ----------------------------------- | ----------- | --------------------- |
| 0    | ms-catalog | Consulta precio/nombre (gRPC)       | gRPC sync   | Fail-fast (503)       |
| 1    | ms-order   | Reserva stock (gRPC a ms-inventory) | gRPC sync   | Fail-fast             |
| 2    | ms-order   | Guarda PENDIENTE_PAGO               | Local       | N/A                   |
| 3    | ms-payment | Procesa pago                        | Kafka async | ReleaseStock si falla |
