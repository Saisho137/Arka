# 04 — API Endpoints (Implementados)

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

## Servicios sin endpoints REST implementados

Los siguientes microservicios existen en el repositorio pero aún no tienen endpoints REST:

- **ms-order** (8081) — Saga orchestrator, en desarrollo
- **ms-notifications** (8085) — Consumer pasivo de Kafka
- **ms-cart** (8086) — Fase 2
- **ms-payment** (8083) — Fase 2
- **ms-reporter** (8087) — Fase 3
- **ms-shipping** (8088) — Fase 3
- **ms-provider** (8089) — Fase 4

---

## Endpoints Planificados (aún no implementados)

### ms-order

| Método | Endpoint              | Descripción                                  |
| ------ | --------------------- | -------------------------------------------- |
| `POST` | `/orders`             | Crear orden de compra                        |
| `GET`  | `/orders/{id}`        | Consultar detalle de orden                   |
| `GET`  | `/orders`             | Listar órdenes (filtros: status, customerId) |
| `PUT`  | `/orders/{id}/status` | Cambiar estado (admin: dispatch, deliver)    |
| `PUT`  | `/orders/{id}/cancel` | Cancelar orden                               |

---

## Health Checks

Todos los microservicios exponen: `GET /actuator/health`
