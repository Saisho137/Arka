# 12 — Colección de cURLs — Arka E-Commerce B2B

> **Objetivo:** Tener TODOS los cURLs listos para lanzar durante la presentación de 40 min.
> Cada comando usa data real del sistema (PostgreSQL seed + datos creados en MongoDB).
>
> **Pre-requisito:** `docker compose up -d` desde la raíz del proyecto.

---

## Tabla de Contenido

- [1. Health Checks (Actuator)](#1-health-checks-actuator)
- [2. ms-catalog — Puerto 8084](#2-ms-catalog--puerto-8084)
  - [2.1 Categorías](#21-categorías)
  - [2.2 Productos](#22-productos)
  - [2.3 Reseñas](#23-reseñas)
  - [2.4 Swagger / OpenAPI](#24-swagger--openapi)
- [3. ms-inventory — Puerto 8082](#3-ms-inventory--puerto-8082)
  - [3.1 Consultar Stock](#31-consultar-stock)
  - [3.2 Actualizar Stock](#32-actualizar-stock)
  - [3.3 Historial de Movimientos](#33-historial-de-movimientos)
  - [3.4 Swagger / OpenAPI](#34-swagger--openapi)
- [4. ms-order — Puerto 8081](#4-ms-order--puerto-8081)
  - [4.1 Crear Orden](#41-crear-orden)
  - [4.2 Consultar Orden por ID](#42-consultar-orden-por-id)
  - [4.3 Listar Órdenes (paginado + filtro)](#43-listar-órdenes-paginado--filtro)
  - [4.4 Cambiar Estado de Orden](#44-cambiar-estado-de-orden)
  - [4.5 Cancelar Orden](#45-cancelar-orden)
- [5. gRPC Endpoints](#5-grpc-endpoints)
  - [5.1 ms-inventory — ReserveStock (puerto 9090)](#51-ms-inventory--reservestock-puerto-9090)
  - [5.2 ms-catalog — GetProductInfo (puerto 9091)](#52-ms-catalog--getproductinfo-puerto-9091)
- [6. Kafka UI](#6-kafka-ui)
- [7. Demostración de Historias de Usuario (HUs)](#7-demostración-de-historias-de-usuario-hus)
  - [HU1 — Registrar productos en el sistema](#hu1--registrar-productos-en-el-sistema)
  - [HU2 — Actualizar stock de productos](#hu2--actualizar-stock-de-productos)
  - [HU4 — Registrar una orden de compra](#hu4--registrar-una-orden-de-compra)
  - [HU6 — Notificación de cambio de estado del pedido](#hu6--notificación-de-cambio-de-estado-del-pedido)

---

## Data de Referencia (Seed)

### Usuarios de Prueba

| Email                | UUID (derivado)                        | Rol      |
| -------------------- | -------------------------------------- | -------- |
| `admin@arka.com`     | `2d66e954-4482-3e67-973c-7142c931083e` | ADMIN    |
| `customer1@arka.com` | `482eae01-3840-3d80-9a3b-17333e6b32d5` | CUSTOMER |
| `customer2@arka.com` | `3e6c5f4e-ae19-32f9-a254-ba18570e280e` | CUSTOMER |

### Órdenes Seed (db_orders)

| ID                                     | Cliente            | Estado      | Total      |
| -------------------------------------- | ------------------ | ----------- | ---------- |
| `550e8400-e29b-41d4-a716-446655440000` | customer1@arka.com | CONFIRMADO  | $1,290,000 |
| `550e8400-e29b-41d4-a716-446655440001` | customer1@arka.com | EN_DESPACHO | $450,000   |
| `550e8400-e29b-41d4-a716-446655440002` | customer2@arka.com | ENTREGADO   | $840,000   |
| `550e8400-e29b-41d4-a716-446655440003` | customer1@arka.com | CANCELADO   | $210,000   |
| `550e8400-e29b-41d4-a716-446655440004` | admin@arka.com     | CONFIRMADO  | $3,750,000 |

### Stock Seed (db_inventory)

> Sincronizados con `init_orders.sql` (productId) y `mongo-seed-catalog.js` (catálogo MongoDB).

| SKU            | Producto                  | ProductId (fijo)                       | Cantidad | Umbral |
| -------------- | ------------------------- | -------------------------------------- | -------- | ------ |
| `KB-MECH-001`  | Teclado Mecánico RGB Pro  | `f47ac10b-58cc-4372-a567-0e02b2c3d001` | 50       | 10     |
| `MS-OPT-002`   | Mouse Óptico Inalámbrico  | `f47ac10b-58cc-4372-a567-0e02b2c3d002` | 120      | 20     |
| `MNT-27-001`   | Monitor 27 pulgadas 4K    | `f47ac10b-58cc-4372-a567-0e02b2c3d003` | 15       | 3      |
| `HDS-BT-003`   | Audífonos Bluetooth NC    | `f47ac10b-58cc-4372-a567-0e02b2c3d004` | 30       | 5      |
| `USB-HB-004`   | Hub USB-C 7 puertos       | `f47ac10b-58cc-4372-a567-0e02b2c3d005` | 80       | 15     |
| `GPU-RTX-004`  | GPU NVIDIA RTX 4070 Super | `f47ac10b-58cc-4372-a567-0e02b2c3d006` | 8        | 2      |
| `RAM-DDR5-005` | Memoria RAM DDR5 32GB     | `f47ac10b-58cc-4372-a567-0e02b2c3d007` | 60       | 10     |

---

## 1. Health Checks (Actuator)

Verificar que todos los servicios estén corriendo:

```bash
# Todos de una vez
curl -s http://localhost:8081/actuator/health | jq  # ms-order
curl -s http://localhost:8082/actuator/health | jq  # ms-inventory
curl -s http://localhost:8083/actuator/health | jq  # ms-payment
curl -s http://localhost:8084/actuator/health | jq  # ms-catalog
curl -s http://localhost:8085/actuator/health | jq  # ms-notifications
curl -s http://localhost:8086/actuator/health | jq  # ms-cart
curl -s http://localhost:8087/actuator/health | jq  # ms-reporter
curl -s http://localhost:8088/actuator/health | jq  # ms-shipping
curl -s http://localhost:8089/actuator/health | jq  # ms-provider
```

**One-liner para verificar todos:**

```bash
for port in 8081 8082 8083 8084 8085 8086 8087 8088 8089; do
  echo -n "localhost:$port → "
  curl -s -o /dev/null -w "%{http_code}" http://localhost:$port/actuator/health
  echo
done
```

---

## 2. ms-catalog — Puerto 8084

### 2.1 Categorías

> Las 4 categorías están **pre-cargadas por `mongo-seed-catalog`** al arrancar el sistema.
> Sus UUIDs son fijos — úsalos directamente en los comandos de productos.

| Categoría   | UUID fijo                              |
| ----------- | -------------------------------------- |
| Periféricos | `aaaaaaaa-0000-4000-a000-000000000001` |
| Monitores   | `aaaaaaaa-0000-4000-a000-000000000002` |
| Componentes | `aaaaaaaa-0000-4000-a000-000000000003` |
| Accesorios  | `aaaaaaaa-0000-4000-a000-000000000004` |

#### Listar todas las categorías (verificar seed)

```bash
curl -s http://localhost:8084/api/v1/categories | jq
```

#### Crear una nueva categoría (demo HU1)

```bash
curl -s -X POST http://localhost:8084/api/v1/categories \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Almacenamiento",
    "description": "SSDs, discos duros y memorias flash"
  }' | jq
```

---

### 2.2 Productos

> Los 7 productos están **pre-cargados por `mongo-seed-catalog`** al arrancar el sistema.
> Sus UUIDs (`_id`) coinciden exactamente con `init_inventory.sql` y `init_orders.sql`.
> Los comandos de creación sirven para demostrar la HU1 creando **nuevos** productos.

#### Listar productos pre-cargados (verificar seed)

```bash
curl -s "http://localhost:8084/api/v1/products?page=0&size=20" | jq '.[].sku'
```

#### Crear producto nuevo — SSD NVMe (producto adicional para demo HU1)

```bash
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "SSD-NVMe-008",
    "name": "SSD NVMe 1TB PCIe Gen4",
    "description": "Unidad de estado sólido NVMe PCIe 4.0, 7000 MB/s lectura, factor M.2 2280",
    "cost": 280000,
    "price": 420000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000003",
    "initialStock": 40
  }' | jq
```

#### Crear producto — Teclado Mecánico RGB Pro (KB-MECH-001, ya existe en seed)

```bash
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "KB-MECH-001",
    "name": "Teclado Mecánico RGB Pro",
    "description": "Teclado mecánico con switches Cherry MX Red, retroiluminación RGB y cuerpo de aluminio",
    "cost": 180000,
    "price": 290000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000001",
    "initialStock": 50
  }' | jq
```

#### Crear producto — Mouse Óptico Inalámbrico (MS-OPT-002)

```bash
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "MS-OPT-002",
    "name": "Mouse Óptico Inalámbrico",
    "description": "Mouse inalámbrico ergonómico con sensor óptico de 3200 DPI, receptor USB nano y batería de 12 meses",
    "cost": 65000,
    "price": 140000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000001",
    "initialStock": 120
  }' | jq
```

#### Crear producto — Monitor 27 pulgadas 4K (MNT-27-001)

```bash
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "MNT-27-001",
    "name": "Monitor 27 pulgadas 4K",
    "description": "Monitor IPS 4K UHD 3840x2160, 60Hz, HDR400, USB-C 65W",
    "cost": 900000,
    "price": 1450000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000002",
    "initialStock": 15
  }' | jq
```

#### Crear producto — GPU NVIDIA RTX 4070 Super (GPU-RTX-004)

```bash
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "GPU-RTX-004",
    "name": "GPU NVIDIA RTX 4070 Super",
    "description": "Tarjeta gráfica RTX 4070 Super 12GB GDDR6X, ray tracing, DLSS 3",
    "cost": 2200000,
    "price": 3100000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000003",
    "initialStock": 8
  }' | jq
```

#### Crear producto — Memoria RAM DDR5 32GB (RAM-DDR5-005)

```bash
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "RAM-DDR5-005",
    "name": "Memoria RAM DDR5 32GB (2x16GB)",
    "description": "Kit de memoria DDR5 5600MHz CL36, disipador de aluminio",
    "cost": 320000,
    "price": 480000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000003",
    "initialStock": 60
  }' | jq
```

#### Listar productos (paginado)

```bash
# Página 0, tamaño 20 (defaults)
curl -s "http://localhost:8084/api/v1/products" | jq

# Página 0, tamaño 5
curl -s "http://localhost:8084/api/v1/products?page=0&size=5" | jq

# Página 1
curl -s "http://localhost:8084/api/v1/products?page=1&size=3" | jq
```

#### Obtener producto por ID

```bash
# Reemplazar <PRODUCT_ID> con el UUID retornado al crear
curl -s http://localhost:8084/api/v1/products/<PRODUCT_ID> | jq
```

#### Actualizar producto

```bash
curl -s -X PUT http://localhost:8084/api/v1/products/<PRODUCT_ID> \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Teclado Mecánico RGB Pro v2",
    "description": "Teclado mecánico con switches Cherry MX Brown, retroiluminación RGB per-key y cuerpo de aluminio CNC",
    "cost": 195000,
    "price": 320000,
    "currency": "COP",
    "categoryId": "<CATEGORY_ID_PERIFERICOS>"
  }' | jq
```

#### Desactivar producto (soft delete)

```bash
curl -s -X DELETE http://localhost:8084/api/v1/products/<PRODUCT_ID> | jq
```

---

### 2.3 Reseñas

#### Agregar reseña a un producto

```bash
curl -s -X POST http://localhost:8084/api/v1/products/<PRODUCT_ID>/reviews \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "482eae01-3840-3d80-9a3b-17333e6b32d5",
    "rating": 5,
    "comment": "Excelente teclado, las teclas son muy responsivas y la iluminación RGB se ve increíble"
  }' | jq
```

#### Agregar segunda reseña al mismo producto

```bash
curl -s -X POST http://localhost:8084/api/v1/products/<PRODUCT_ID>/reviews \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "3e6c5f4e-ae19-32f9-a254-ba18570e280e",
    "rating": 4,
    "comment": "Muy buen producto. Llegó en perfecto estado. Le doy 4 porque el cable podría ser más largo"
  }' | jq
```

#### Verificar reseñas anidadas en el producto

```bash
curl -s http://localhost:8084/api/v1/products/<PRODUCT_ID> | jq '.reviews'
```

---

### 2.4 Swagger / OpenAPI

```bash
# Abrir en navegador
open http://localhost:8084/swagger-ui.html

# JSON spec
curl -s http://localhost:8084/api-docs | jq

# YAML spec
curl -s http://localhost:8084/api-docs.yaml
```

---

## 3. ms-inventory — Puerto 8082

### 3.1 Consultar Stock

```bash
# Teclado Mecánico — stock: 50, umbral: 10
curl -s http://localhost:8082/inventory/KB-MECH-001 | jq

# Mouse Óptico Inalámbrico — stock: 120, umbral: 20
curl -s http://localhost:8082/inventory/MS-OPT-002 | jq

# Monitor 27 pulgadas 4K — stock: 15, umbral: 3
curl -s http://localhost:8082/inventory/MNT-27-001 | jq

# Audífonos Bluetooth NC — stock: 30, umbral: 5
curl -s http://localhost:8082/inventory/HDS-BT-003 | jq

# Hub USB-C — stock: 80, umbral: 15
curl -s http://localhost:8082/inventory/USB-HB-004 | jq

# GPU RTX — stock: 8, umbral: 2
curl -s http://localhost:8082/inventory/GPU-RTX-004 | jq

# RAM DDR5 — stock: 60, umbral: 10
curl -s http://localhost:8082/inventory/RAM-DDR5-005 | jq
```

### 3.2 Actualizar Stock

#### Actualizar stock del teclado a 75 unidades

```bash
curl -s -X PUT http://localhost:8082/inventory/KB-MECH-001/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 75,
    "reason": "Reabastecimiento desde proveedor TechParts Colombia"
  }' | jq
```

#### Actualizar stock del mouse óptico a 150 unidades

```bash
curl -s -X PUT http://localhost:8082/inventory/MS-OPT-002/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 150,
    "reason": "Llegada de nuevo lote importación Q2-2026"
  }' | jq
```

#### Actualizar stock del monitor a 25 unidades

```bash
curl -s -X PUT http://localhost:8082/inventory/MNT-27-001/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 25,
    "reason": "Llegada de nuevo lote importación Q2-2026"
  }' | jq
```

#### Ajustar stock de la GPU (bajo stock)

```bash
curl -s -X PUT http://localhost:8082/inventory/GPU-RTX-004/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 3,
    "reason": "Ajuste por inventario físico — 5 unidades con defecto de fábrica"
  }' | jq
```

### 3.3 Historial de Movimientos

```bash
# Historial del teclado (verás RESTOCK después del update)
curl -s "http://localhost:8082/inventory/KB-MECH-001/history?page=0&size=10" | jq

# Historial del mouse óptico
curl -s "http://localhost:8082/inventory/MS-OPT-002/history?page=0&size=10" | jq

# Historial del monitor
curl -s "http://localhost:8082/inventory/MNT-27-001/history?page=0&size=10" | jq

# Historial de la GPU (verá el ajuste de stock)
curl -s "http://localhost:8082/inventory/GPU-RTX-004/history?page=0&size=10" | jq
```

### 3.4 Swagger / OpenAPI

```bash
open http://localhost:8082/swagger-ui.html

curl -s http://localhost:8082/api-docs | jq
```

---

## 4. ms-order — Puerto 8081

### Headers requeridos

| Header         | Descripción                                      | Valores                |
| -------------- | ------------------------------------------------ | ---------------------- |
| `X-User-Email` | Email del usuario autenticado (inyectado por GW) | `admin@arka.com`, etc. |
| `X-User-Role`  | Rol del usuario (inyectado por GW)               | `ADMIN` o `CUSTOMER`   |

### 4.1 Crear Orden

> **Requisito:** ms-catalog y ms-inventory deben estar corriendo.
> ms-order consulta precio vía gRPC a ms-catalog y reserva stock vía gRPC a ms-inventory.

#### Crear orden como customer1 — 2 Teclados + 1 Monitor

```bash
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -d '{
    "customerId": "482eae01-3840-3d80-9a3b-17333e6b32d5",
    "customerEmail": "customer1@arka.com",
    "shippingAddress": "Calle 100 #15-30, Oficina 502, Bogotá, Colombia",
    "notes": "Entregar en recepción del edificio",
    "items": [
      {
        "sku": "KB-MECH-001",
        "quantity": 2
      },
      {
        "sku": "MN-UW-003",
        "quantity": 1
      }
    ]
  }' | jq
```

#### Crear orden como customer2 — 5 Módulos RAM + 3 Mouse

```bash
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer2@arka.com" \
  -d '{
    "customerId": "3e6c5f4e-ae19-32f9-a254-ba18570e280e",
    "customerEmail": "customer2@arka.com",
    "shippingAddress": "Avenida El Poblado #43A-72, Medellín, Colombia",
    "notes": "Empresa: TechSolutions SAS — NIT 900.123.456-7",
    "items": [
      {
        "sku": "RAM-DDR5-005",
        "quantity": 5
      },
      {
        "sku": "MS-WIRE-002",
        "quantity": 3
      }
    ]
  }' | jq
```

#### Crear orden que fallará por stock insuficiente (GPU solo tiene 3-8 unidades)

```bash
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -d '{
    "customerId": "482eae01-3840-3d80-9a3b-17333e6b32d5",
    "customerEmail": "customer1@arka.com",
    "shippingAddress": "Calle 72 #10-07, Bogotá, Colombia",
    "notes": "Pedido mayorista GPUs",
    "items": [
      {
        "sku": "GPU-RTX-004",
        "quantity": 50
      }
    ]
  }' | jq
```

### 4.2 Consultar Orden por ID

#### Consultar orden seed — CONFIRMADO (como ADMIN)

```bash
curl -s http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440000 \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq
```

#### Consultar orden seed — EN_DESPACHO (como customer1)

```bash
curl -s http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440001 \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

#### Consultar orden seed — ENTREGADO (como customer2)

```bash
curl -s http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440002 \
  -H "X-User-Email: customer2@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

#### Consultar orden seed — CANCELADO

```bash
curl -s http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440003 \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

#### Consultar orden recién creada

```bash
# Reemplazar <NEW_ORDER_ID> con el UUID retornado al crear la orden
curl -s http://localhost:8081/api/v1/orders/<NEW_ORDER_ID> \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

### 4.3 Listar Órdenes (paginado + filtro)

#### Como ADMIN — ver todas las órdenes

```bash
curl -s "http://localhost:8081/api/v1/orders?page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq
```

#### Como ADMIN — filtrar solo CONFIRMADO

```bash
curl -s "http://localhost:8081/api/v1/orders?status=CONFIRMADO&page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq
```

#### Como ADMIN — filtrar solo EN_DESPACHO

```bash
curl -s "http://localhost:8081/api/v1/orders?status=EN_DESPACHO&page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq
```

#### Como ADMIN — filtrar solo CANCELADO

```bash
curl -s "http://localhost:8081/api/v1/orders?status=CANCELADO&page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq
```

#### Como ADMIN — filtrar solo ENTREGADO

```bash
curl -s "http://localhost:8081/api/v1/orders?status=ENTREGADO&page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq
```

#### Como CUSTOMER — ver solo mis órdenes

```bash
curl -s "http://localhost:8081/api/v1/orders?page=0&size=20" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

#### Como CUSTOMER — filtrar mis órdenes confirmadas

```bash
curl -s "http://localhost:8081/api/v1/orders?status=CONFIRMADO&page=0&size=20" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

### 4.4 Cambiar Estado de Orden

> Solo ADMIN. Transiciones válidas: `CONFIRMADO → EN_DESPACHO` y `EN_DESPACHO → ENTREGADO`.

#### CONFIRMADO → EN_DESPACHO (orden seed 550e...0000)

```bash
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/status \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -d '{
    "newStatus": "EN_DESPACHO"
  }' | jq
```

#### EN_DESPACHO → ENTREGADO (orden seed 550e...0001)

```bash
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440001/status \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -d '{
    "newStatus": "ENTREGADO"
  }' | jq
```

#### EN_DESPACHO → ENTREGADO (la orden que acabamos de despachar: 550e...0000)

```bash
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/status \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -d '{
    "newStatus": "ENTREGADO"
  }' | jq
```

### 4.5 Cancelar Orden

> ADMIN puede cancelar cualquier orden. CUSTOMER solo las suyas.
> Solo se pueden cancelar órdenes que NO estén en estado terminal (ENTREGADO, CANCELADO).

#### Cancelar como ADMIN la orden seed CONFIRMADO (550e...0004)

```bash
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440004/cancel \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{
    "reason": "Cancelación administrativa — cliente solicitó cambio de productos"
  }' | jq
```

#### Cancelar como CUSTOMER una orden recién creada

```bash
# Reemplazar <NEW_ORDER_ID> con el UUID de una orden recién creada
curl -s -X PUT http://localhost:8081/api/v1/orders/<NEW_ORDER_ID>/cancel \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" \
  -d '{
    "reason": "Ya no necesito estos productos, encontré mejor precio"
  }' | jq
```

#### Intento de cancelar orden ya ENTREGADA (debe fallar)

```bash
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440002/cancel \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer2@arka.com" \
  -H "X-User-Role: CUSTOMER" \
  -d '{
    "reason": "Quiero devolver el producto"
  }' | jq
```

### 4.6 Swagger / OpenAPI

```bash
open http://localhost:8081/swagger-ui.html

curl -s http://localhost:8081/api-docs | jq
```

---

## 5. gRPC Endpoints

> Requiere `grpcurl` instalado: `brew install grpcurl`

### 5.1 ms-inventory — ReserveStock (puerto 9090)

#### Listar servicios disponibles

```bash
grpcurl -plaintext localhost:9090 list
```

#### Describir servicio InventoryService

```bash
grpcurl -plaintext localhost:9090 describe inventory.InventoryService
```

#### Reservar stock — teclado (exitoso)

```bash
grpcurl -plaintext -d '{
  "sku": "KB-MECH-001",
  "order_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0001",
  "quantity": 2
}' localhost:9090 inventory.InventoryService/ReserveStock
```

#### Reservar stock — mouse óptico (exitoso)

```bash
grpcurl -plaintext -d '{
  "sku": "MS-OPT-002",
  "order_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0002",
  "quantity": 5
}' localhost:9090 inventory.InventoryService/ReserveStock
```

#### Reservar stock (fallido — SKU no existe)

```bash
grpcurl -plaintext -d '{
  "sku": "NOEXISTE-999",
  "order_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0099",
  "quantity": 1
}' localhost:9090 inventory.InventoryService/ReserveStock
```

#### Reservar stock (fallido — cantidad insuficiente)

```bash
grpcurl -plaintext -d '{
  "sku": "GPU-RTX-004",
  "order_id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeee0003",
  "quantity": 999
}' localhost:9090 inventory.InventoryService/ReserveStock
```

### 5.2 ms-catalog — GetProductInfo (puerto 9091)

> Puerto **9091** en host (mapeado a 9090 dentro del contenedor).

#### Listar servicios disponibles

```bash
grpcurl -plaintext localhost:9091 list
```

#### Describir servicio CatalogService

```bash
grpcurl -plaintext localhost:9091 describe catalog.CatalogService
```

#### Obtener info del teclado

```bash
grpcurl -plaintext -d '{
  "sku": "KB-MECH-001"
}' localhost:9091 catalog.CatalogService/GetProductInfo
```

#### Obtener info del mouse óptico

```bash
grpcurl -plaintext -d '{
  "sku": "MS-OPT-002"
}' localhost:9091 catalog.CatalogService/GetProductInfo
```

#### Obtener info del monitor

```bash
grpcurl -plaintext -d '{
  "sku": "MNT-27-001"
}' localhost:9091 catalog.CatalogService/GetProductInfo
```

#### Obtener info de la GPU

```bash
grpcurl -plaintext -d '{
  "sku": "GPU-RTX-004"
}' localhost:9091 catalog.CatalogService/GetProductInfo
```

#### Obtener info de SKU inexistente (debe retornar NOT_FOUND)

```bash
grpcurl -plaintext -d '{
  "sku": "NOEXISTE-999"
}' localhost:9091 catalog.CatalogService/GetProductInfo
```

---

## 6. Kafka UI

```bash
# Abrir Kafka UI en el navegador
open http://localhost:8080
```

**Qué verificar en Kafka UI:**

- **Tópicos:** `product-events`, `inventory-events`, `order-events`, `cart-events`, `payment-events`, `shipping-events`, `provider-events`
- **Mensajes:** Ver payload JSON de eventos publicados (ej. `ProductCreated`, `StockReserved`, `OrderCreated`)
- **Consumer Groups:** `ms-inventory`, `order-service-group`, etc.
- **Partition Keys:** Verificar que los mensajes están particionados por aggregate ID

---

## 7. Demostración de Historias de Usuario (HUs)

> **Flujo de presentación sugerido (40 min):**
>
> 1. Health checks (2 min)
> 2. HU1 — Registrar productos (8 min)
> 3. HU2 — Actualizar stock (5 min)
> 4. HU4 — Registrar orden de compra (10 min)
> 5. HU6 — Cambio de estado del pedido (8 min)
> 6. gRPC + Kafka UI demo (5 min)
> 7. Preguntas (2 min)

---

### HU1 — Registrar productos en el sistema

> **Como administrador**, quiero registrar nuevos productos con sus características para que los clientes puedan comprarlos.

**Criterios de aceptación:**

- ✅ Carga de nombre, descripción, precio, stock y categoría
- ✅ Validaciones de datos requeridos
- ✅ Mensaje de confirmación tras registro exitoso

> **Contexto:** Las categorías y los 7 productos base ya están cargados por `mongo-seed-catalog`.
> Esta demo crea **productos adicionales** para demostrar el flujo completo de registro.

#### Paso 1 — Verificar categorías pre-cargadas

```bash
curl -s http://localhost:8084/api/v1/categories | jq '.[].name'
# Espera ver: "Periféricos", "Monitores", "Componentes", "Accesorios"
```

#### Paso 2 — Verificar productos pre-cargados

```bash
curl -s "http://localhost:8084/api/v1/products?page=0&size=10" | jq '.[].sku'
# Espera ver los 7 SKUs del seed
```

#### Paso 3 — Registrar nuevos productos

```bash
# Producto nuevo: SSD NVMe — Categoría Componentes (UUID fijo)
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "SSD-NVMe-008",
    "name": "SSD NVMe 1TB PCIe Gen4",
    "description": "Unidad de estado sólido NVMe PCIe 4.0, 7000 MB/s lectura, factor M.2 2280",
    "cost": 280000,
    "price": 420000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000003",
    "initialStock": 40
  }' | jq

# Producto nuevo: Headset gaming — Categoría Periféricos
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "HST-GM-009",
    "name": "Headset Gaming 7.1 Surround",
    "description": "Headset con sonido 7.1 surround virtual, micrófono retráctil y drivers de 50mm",
    "cost": 90000,
    "price": 175000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000001",
    "initialStock": 25
  }' | jq
```

#### Paso 4 — Demostrar validación (campo requerido faltante)

```bash
# Sin SKU — debe retornar 400 con mensaje de validación
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Producto sin SKU",
    "description": "Este producto no tiene SKU",
    "cost": 100000,
    "price": 150000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000001",
    "initialStock": 10
  }' | jq
```

```bash
# Precio negativo — debe retornar 400 con mensaje de validación
curl -s -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "TEST-NEG-001",
    "name": "Producto precio negativo",
    "description": "No debería crearse",
    "cost": 100000,
    "price": -50000,
    "currency": "COP",
    "categoryId": "aaaaaaaa-0000-4000-a000-000000000001",
    "initialStock": 10
  }' | jq
```

#### Paso 5 — Verificar producto registrado (confirmación)

```bash
curl -s "http://localhost:8084/api/v1/products?page=0&size=20" | jq '.[].sku'
# Ahora debe aparecer SSD-NVMe-008 y HST-GM-009 además de los 7 del seed
```

#### Paso 6 — Mostrar evento ProductCreated en Kafka UI

```
→ Ir a http://localhost:8080 → Tópico product-events → ver mensajes
→ Cada nuevo producto genera un evento ProductCreated con sku, precio, categoryId, initialStock
```

---

### HU2 — Actualizar stock de productos

> **Como administrador**, quiero actualizar la cantidad de productos en stock para evitar sobreventas.

**Criterios de aceptación:**

- ✅ Permitir modificar el stock de un producto
- ✅ No permitir valores negativos
- ✅ Historial de cambios en el stock

#### Paso 1 — Consultar stock actual

```bash
curl -s http://localhost:8082/inventory/KB-MECH-001 | jq '{sku, quantity, reservedQuantity, availableQuantity}'
```

#### Paso 2 — Actualizar stock (reabastecimiento)

```bash
curl -s -X PUT http://localhost:8082/inventory/KB-MECH-001/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 100,
    "reason": "Reabastecimiento — Orden de compra #OC-2026-042 desde TechParts Colombia"
  }' | jq
```

#### Paso 3 — Verificar nuevo stock

```bash
curl -s http://localhost:8082/inventory/KB-MECH-001 | jq '{sku, quantity, reservedQuantity, availableQuantity}'
```

#### Paso 4 — Demostrar validación (no permite valores negativos)

```bash
# Esto debe retornar error de validación
curl -s -X PUT http://localhost:8082/inventory/KB-MECH-001/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": -5,
    "reason": "Esto no debería funcionar"
  }' | jq
```

#### Paso 5 — Consultar historial de cambios

```bash
curl -s "http://localhost:8082/inventory/KB-MECH-001/history?page=0&size=20" | jq
```

> **Mostrar:** Se ven movimientos con tipo `RESTOCK`, con `previousQuantity` y `newQuantity` en cada entrada.

#### Paso 6 — Segundo ejemplo con GPU (stock bajo, cerca del umbral)

```bash
# Ver stock actual (8 unidades, umbral 2)
curl -s http://localhost:8082/inventory/GPU-RTX-004 | jq '{sku, quantity, availableQuantity}'

# Reducir stock — simular pérdida por inventario físico
curl -s -X PUT http://localhost:8082/inventory/GPU-RTX-004/stock \
  -H "Content-Type: application/json" \
  -d '{
    "quantity": 2,
    "reason": "Ajuste inventario físico — unidades dañadas en transporte"
  }' | jq

# Ver que alcanzó el umbral de agotamiento (quantity == threshold: 2 → StockDepleted)
curl -s http://localhost:8082/inventory/GPU-RTX-004 | jq '{sku, quantity, availableQuantity}'

# Ver evento StockDepleted en Kafka UI → http://localhost:8080 → inventory-events
```

---

### HU4 — Registrar una orden de compra

> **Como cliente**, quiero poder registrar una orden de compra con múltiples productos para realizar mi pedido.

**Criterios de aceptación:**

- ✅ Validación de disponibilidad de stock
- ✅ Registro de fecha y detalles del pedido
- ✅ Mensaje de confirmación con resumen del pedido

#### Paso 1 — Verificar stock disponible antes de ordenar

```bash
echo "=== Stock disponible ==="
curl -s http://localhost:8082/inventory/KB-MECH-001 | jq '{sku, availableQuantity}'
curl -s http://localhost:8082/inventory/RAM-DDR5-005 | jq '{sku, availableQuantity}'
```

#### Paso 2 — Crear orden con múltiples productos (debe tener éxito)

```bash
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -d '{
    "customerId": "482eae01-3840-3d80-9a3b-17333e6b32d5",
    "customerEmail": "customer1@arka.com",
    "shippingAddress": "Calle 100 #15-30, Oficina 502, Bogotá, Colombia",
    "notes": "Pedido B2B para renovación de equipos — Empresa ABC SAS",
    "items": [
      {
        "productId": "f47ac10b-58cc-4372-a567-0e02b2c3d001",
        "sku": "KB-MECH-001",
        "quantity": 5
      },
      {
        "productId": "f47ac10b-58cc-4372-a567-0e02b2c3d005",
        "sku": "RAM-DDR5-005",
        "quantity": 10
      }
    ]
  }' | jq
```

> **Mostrar:** Respuesta 202 con `orderId`, `status: PENDIENTE_RESERVA`, `totalAmount` calculado, `items` con precios.
> Copiar el `orderId` de la respuesta.

#### Paso 3 — Verificar que el stock se reservó

```bash
curl -s http://localhost:8082/inventory/KB-MECH-001 | jq '{sku, quantity, reservedQuantity, availableQuantity}'
curl -s http://localhost:8082/inventory/RAM-DDR5-005 | jq '{sku, quantity, reservedQuantity, availableQuantity}'
```

#### Paso 4 — Consultar detalle de la orden creada

```bash
curl -s http://localhost:8081/api/v1/orders/<NEW_ORDER_ID> \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq
```

#### Paso 5 — Demostrar validación de stock insuficiente

```bash
# Intentar ordenar 500 GPUs (solo hay ~8)
curl -s -X POST http://localhost:8081/api/v1/orders \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer2@arka.com" \
  -d '{
    "customerId": "3e6c5f4e-ae19-32f9-a254-ba18570e280e",
    "customerEmail": "customer2@arka.com",
    "shippingAddress": "Carrera 43A #1-50, Torre Sur, Medellín",
    "items": [
      {
        "productId": "f47ac10b-58cc-4372-a567-0e02b2c3d004",
        "sku": "GPU-RTX-004",
        "quantity": 500
      }
    ]
  }' | jq
```

> **Mostrar:** La orden se crea en estado PENDIENTE_RESERVA pero la saga detectará
> el fallo de stock y la cancelará automáticamente vía evento `StockReserveFailed`.

#### Paso 6 — Verificar eventos en Kafka UI

```
→ http://localhost:8080 → Tópicos: order-events, inventory-events
→ Ver OrderCreated, StockReserved / StockReserveFailed
```

---

### HU6 — Notificación de cambio de estado del pedido

> **Como cliente**, quiero recibir notificaciones sobre el estado de mi pedido para estar informado de su progreso.

**Criterios de aceptación:**

- ✅ Estados: pendiente, confirmado, en despacho, entregado
- ⚠️ Notificación por correo (ms-notifications consume eventos pero aún no envía email — pendiente Fase 2)
- ✅ Cambios de estado publicados como eventos Kafka (`OrderStatusChanged`)

#### Paso 1 — Listar órdenes con todos los estados posibles

```bash
# Ver las 5 órdenes seed con diferentes estados
curl -s "http://localhost:8081/api/v1/orders?page=0&size=20" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" | jq '.[].status'
```

#### Paso 2 — Transición CONFIRMADO → EN_DESPACHO

```bash
# Orden seed CONFIRMADO
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440000/status \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -d '{"newStatus": "EN_DESPACHO"}' | jq '{orderId, status}'
```

#### Paso 3 — Transición EN_DESPACHO → ENTREGADO

```bash
# Orden seed EN_DESPACHO
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440001/status \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -d '{"newStatus": "ENTREGADO"}' | jq '{orderId, status}'
```

#### Paso 4 — Demostrar transición inválida (ENTREGADO → EN_DESPACHO)

```bash
# Esto debe fallar — ENTREGADO es estado terminal
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440002/status \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -d '{"newStatus": "EN_DESPACHO"}' | jq
```

#### Paso 5 — Cancelar orden (otra forma de cambio de estado)

```bash
curl -s -X PUT http://localhost:8081/api/v1/orders/550e8400-e29b-41d4-a716-446655440004/cancel \
  -H "Content-Type: application/json" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN" \
  -d '{"reason": "Cliente canceló — cambio de proveedor"}' | jq '{orderId, status}'
```

#### Paso 6 — Verificar como CUSTOMER los cambios de estado

```bash
# customer1 ve sus órdenes con los nuevos estados
curl -s "http://localhost:8081/api/v1/orders?page=0&size=20" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq '.[] | {orderId, status}'
```

#### Paso 7 — Verificar eventos OrderStatusChanged en Kafka UI

```
→ http://localhost:8080 → Tópico: order-events
→ Filtrar por eventType: ORDER_STATUS_CHANGED, ORDER_CANCELLED
→ Cada evento incluye: orderId, previousStatus, newStatus, changedBy, reason
```

> **Nota para la presentación:** Aunque ms-notifications aún no envía correos (Fase 2),
> el evento `OrderStatusChanged` ya se publica a Kafka. Cuando ms-notifications implemente
> su consumer, recibirá estos eventos automáticamente y enviará el correo vía AWS SES.

---

## Apéndice A — Comandos de Infraestructura Útiles

### Docker

```bash
# Levantar todo
docker compose up -d

# Ver logs de un servicio específico
docker compose logs -f arka-ms-catalog
docker compose logs -f arka-ms-inventory
docker compose logs -f arka-ms-order

# Reiniciar un servicio
docker compose restart arka-ms-order

# Ver estado de los contenedores
docker compose ps
```

### Bases de Datos

```bash
# PostgreSQL — Conectar a db_orders
psql -h localhost -p 5432 -U arka -d db_orders

# PostgreSQL — Conectar a db_inventory
psql -h localhost -p 5433 -U arka -d db_inventory

# MongoDB — Conectar a catalog_db
mongosh "mongodb://localhost:27017/db_catalog" -u arka -p arkaSecret2025 --authenticationDatabase admin

# Redis — Verificar caché
redis-cli -h localhost -p 6379
KEYS *
```

### Kafka

```bash
# Listar tópicos
docker exec arka-kafka kafka-topics --bootstrap-server localhost:29092 --list

# Ver mensajes de un tópico
docker exec arka-kafka kafka-console-consumer \
  --bootstrap-server localhost:29092 \
  --topic order-events \
  --from-beginning \
  --max-messages 5

# Ver consumer groups
docker exec arka-kafka kafka-consumer-groups --bootstrap-server localhost:29092 --list
```

---

## Apéndice B — Resumen de HUs vs Estado de Implementación

| HU  | Descripción                           | MS Principal     | Estado          | Demo posible |
| --- | ------------------------------------- | ---------------- | --------------- | ------------ |
| HU1 | Registrar productos                   | ms-catalog       | ✅ Implementado | Sí           |
| HU2 | Actualizar stock                      | ms-inventory     | ✅ Implementado | Sí           |
| HU3 | Reportes de bajo stock                | ms-reporter      | ❌ Pendiente    | No           |
| HU4 | Registrar orden de compra             | ms-order         | ✅ Implementado | Sí           |
| HU5 | Modificar orden antes de confirmación | ms-order         | ❌ Pendiente    | No           |
| HU6 | Notificación de cambio de estado      | ms-order + Kafka | ✅ Parcial      | Sí (eventos) |
| HU7 | Reportes semanales de ventas          | ms-reporter      | ❌ Pendiente    | No           |
| HU8 | Carritos abandonados                  | ms-cart          | ❌ Pendiente    | No           |
