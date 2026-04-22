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

Base path: `/inventory`

### Stock

| Método | Endpoint                                  | Descripción                                  |
| ------ | ----------------------------------------- | -------------------------------------------- |
| `PUT`  | `/inventory/{sku}/stock`                  | Actualizar stock manualmente (admin)         |
| `GET`  | `/inventory/{sku}`                        | Consultar disponibilidad de un SKU           |
| `GET`  | `/inventory/{sku}/history?page=0&size=20` | Historial de movimientos (paginado, max 100) |

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

## Servicios sin endpoints REST implementados

Los siguientes microservicios existen en el repositorio pero aún no tienen endpoints REST:

- **ms-notifications** (8085) — Consumer pasivo de Kafka
- **ms-cart** (8086) — Fase 2
- **ms-payment** (8083) — Fase 2
- **ms-reporter** (8087) — Fase 3
- **ms-shipping** (8088) — Fase 3
- **ms-provider** (8089) — Fase 4

---

## Health Checks

Todos los microservicios exponen: `GET /actuator/health`
