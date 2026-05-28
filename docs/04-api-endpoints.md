---
sidebar_position: 7
title: API Endpoints
---

# API Endpoints (Implementados)

> Solo los endpoints que **existen actualmente** en el código.

---

## ms-catalog — Puerto 8084

Base path: `/api/v1`

### Productos

| Método   | Endpoint                          | Descripción                                  |
| -------- | --------------------------------- | -------------------------------------------- |
| `POST`   | `/api/v1/products`                | Crear producto                               |
| `GET`    | `/api/v1/products?page=0&size=20` | Listar productos activos (paginado, max 100) |
| `GET`    | `/api/v1/products/{id}`           | Obtener producto por UUID                    |
| `PUT`    | `/api/v1/products/{id}`           | Actualizar producto                          |
| `DELETE` | `/api/v1/products/{id}`           | Desactivar producto (soft delete)            |

### Categorías

| Método | Endpoint             | Descripción                 |
| ------ | -------------------- | --------------------------- |
| `POST` | `/api/v1/categories` | Crear categoría             |
| `GET`  | `/api/v1/categories` | Listar todas las categorías |

### Reseñas

| Método | Endpoint                               | Descripción                  |
| ------ | -------------------------------------- | ---------------------------- |
| `POST` | `/api/v1/products/{productId}/reviews` | Agregar reseña a un producto |

**Swagger UI:** `http://localhost:8084/swagger-ui.html`

---

## ms-inventory — Puerto 8082

Base path: `/api/v1/inventory`

### Stock

| Método | Endpoint                                              | Descripción                                  |
| ------ | ----------------------------------------------------- | -------------------------------------------- |
| `PUT`  | `/api/v1/inventory/{sku}/stock`                       | Actualizar stock manualmente (admin)         |
| `GET`  | `/api/v1/inventory/{sku}`                             | Consultar disponibilidad de un SKU           |
| `GET`  | `/api/v1/inventory/{sku}/history?page=0&size=20`      | Historial de movimientos (paginado, max 100) |

### gRPC (puerto 9090)

| Servicio           | Método         | Descripción                              |
| ------------------ | -------------- | ---------------------------------------- |
| `InventoryService` | `ReserveStock` | Reserva síncrona de stock desde ms-order |

**Swagger UI:** `http://localhost:8082/swagger-ui.html`

---

---

## ms-order — Puerto 8081

Base path: `/api/v1`

### Órdenes

| Método | Endpoint                                        | Descripción                                                                       |
| ------ | ----------------------------------------------- | --------------------------------------------------------------------------------- |
| `POST` | `/api/v1/orders`                                | Crear orden de compra. Header: `X-User-Email`                                     |
| `GET`  | `/api/v1/orders/{id}`                           | Consultar detalle de orden. Headers: `X-User-Email`, `X-User-Role`                |
| `GET`  | `/api/v1/orders?status=&page=0&size=20`         | Listar órdenes paginadas (max 100). Filtro `status`: PENDIENTE_RESERVA, CONFIRMADO, EN_DESPACHO, ENTREGADO, CANCELADO. CUSTOMER ve solo sus órdenes. Headers: `X-User-Email`, `X-User-Role` |
| `PUT`  | `/api/v1/orders/{id}/status`                    | Cambiar estado (CONFIRMADO→EN_DESPACHO, EN_DESPACHO→ENTREGADO). Solo ADMIN. Header: `X-User-Email` |
| `PUT`  | `/api/v1/orders/{id}/cancel`                    | Cancelar orden. Headers: `X-User-Email`, `X-User-Role`                            |

> Los headers `X-User-Email` y `X-User-Role` son inyectados por el API Gateway tras validar el JWT.

**Swagger UI:** `http://localhost:8081/swagger-ui.html`

---

### ms-catalog — gRPC (puerto 9091 local / 9090 docker)

| Servicio         | Método           | Descripción                                              |
| ---------------- | ---------------- | -------------------------------------------------------- |
| `CatalogService` | `GetProductInfo` | Precio y nombre autoritativo por SKU (ms-order, ms-cart) |

---

## ms-cart — Puerto 8086

Base path: `/api/v1/carts`

### Carritos

| Método   | Endpoint                              | Descripción                                        |
| -------- | ------------------------------------- | -------------------------------------------------- |
| `POST`   | `/api/v1/carts`                       | Crear carrito                                      |
| `GET`    | `/api/v1/carts`                       | Obtener carritos por cliente                       |
| `GET`    | `/api/v1/carts/{cartId}`              | Obtener carrito por ID                             |
| `POST`   | `/api/v1/carts/{cartId}/items`        | Agregar item al carrito                            |
| `PUT`    | `/api/v1/carts/{cartId}/items/{sku}`  | Actualizar cantidad de item                        |
| `DELETE` | `/api/v1/carts/{cartId}/items/{sku}`  | Eliminar item del carrito                          |
| `DELETE` | `/api/v1/carts/{cartId}/items`        | Limpiar todos los items del carrito                |
| `DELETE` | `/api/v1/carts/{cartId}`              | Eliminar carrito                                   |
| `POST`   | `/api/v1/carts/{cartId}/checkout`     | Checkout (valida precios con catálogo vía gRPC)    |

**Swagger UI:** `http://localhost:8086/swagger-ui.html`

---

## ms-shipping — Puerto 8088

Base path: `/api/v1/shipments`

### Envíos

| Método | Endpoint                                  | Descripción                                 |
| ------ | ----------------------------------------- | ------------------------------------------- |
| `GET`  | `/api/v1/shipments/{orderId}`             | Obtener envío por orderId                   |
| `GET`  | `/api/v1/shipments`                       | Listar envíos con filtros (solo ADMIN)      |
| `PUT`  | `/api/v1/shipments/{orderId}/status`      | Actualizar estado del envío (solo ADMIN)    |
| `POST` | `/api/v1/shipments/retry/{orderId}`       | Reintentar envío fallido (solo ADMIN)       |

### Webhooks

| Método | Endpoint                                  | Descripción                                 |
| ------ | ----------------------------------------- | ------------------------------------------- |
| `POST` | `/api/v1/webhooks/{carrier}/tracking`     | Recibir webhook de tracking de transportadora |

**Swagger UI:** `http://localhost:8088/swagger-ui.html`

---

## ms-reporter — Puerto 8087

### Reportes

| Método | Endpoint                  | Descripción                          |
| ------ | ------------------------- | ------------------------------------ |
| `POST` | `/reports/sales/weekly`   | Generar reporte semanal de ventas    |
| `GET`  | `/reports/{reportId}`     | Estado del reporte y URL de descarga |

### Administración

| Método | Endpoint                      | Descripción                       |
| ------ | ----------------------------- | --------------------------------- |
| `POST` | `/admin/rebuild-read-models`  | Reconstruir read model desde Event Store |

### Trazabilidad de Eventos

| Método | Endpoint                          | Descripción                        |
| ------ | --------------------------------- | ---------------------------------- |
| `GET`  | `/events/trace/{correlationId}`   | Rastrear eventos por correlationId |

### Métricas de Negocio

| Método | Endpoint        | Descripción                             |
| ------ | --------------- | --------------------------------------- |
| `GET`  | `/metrics/kpis` | KPIs de negocio para un rango de fechas |

**Swagger UI:** `http://localhost:8087/swagger-ui.html`

---

## Servicios sin endpoints REST implementados

- **ms-notifications** (8085) — Consumer pasivo de Kafka
- **ms-payment** (8083) — Mock actual (reimplementación pendiente, Fase 2)
- **ms-provider** (8089) — Fase 4

---

## Health Checks

Todos los microservicios exponen: `GET /actuator/health`
