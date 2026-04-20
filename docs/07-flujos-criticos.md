# 07 — Flujos Críticos del Sistema

## 1. Creación de Pedido — Happy Path (Fase 1 MVP)

```text
Cliente B2B ──POST /orders──▶ API Gateway ──JWT──▶ ms-order
                                                      │
                                          Valida request
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
                                          Guarda orden CONFIRMADO
                                          Guarda OrderConfirmed en outbox
                                          ──▶ 202 Accepted al cliente
                                                      │
                                          Outbox Relay (5s) ──▶ Kafka: order-events
                                                                     │
                                                              ms-notifications
                                                                     │
                                                              Email vía AWS SES
```

## 2. Creación de Pedido — Happy Path (Fase 2 con ms-payment)

```text
ms-order ──gRPC──▶ ms-inventory (stock reservado)
    │
    Guarda orden PENDIENTE_PAGO
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

### Fase 1 (2 pasos)

| Paso | Servicio | Acción         | Mecanismo | Compensación             |
| ---- | -------- | -------------- | --------- | ------------------------ |
| 1    | ms-order | Reserva stock  | gRPC sync | Fail-fast (no hay stock) |
| 2    | ms-order | Confirma orden | Local     | N/A                      |

### Fase 2 (3 pasos)

| Paso | Servicio   | Acción                | Mecanismo   | Compensación          |
| ---- | ---------- | --------------------- | ----------- | --------------------- |
| 1    | ms-order   | Reserva stock         | gRPC sync   | Fail-fast             |
| 2    | ms-order   | Guarda PENDIENTE_PAGO | Local       | N/A                   |
| 3    | ms-payment | Procesa pago          | Kafka async | ReleaseStock si falla |
