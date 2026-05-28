# ms-provider

Microservicio dueño del Bounded Context **Gestión de Proveedores y Órdenes de Compra** dentro de la plataforma B2B Arka. Actúa como **Anti-Corruption Layer (ACL)** para integrar múltiples proveedores externos y gestiona el ciclo de vida completo de órdenes de compra para reposición automática de inventario.

---

## Stack Tecnológico

| Componente    | Tecnología                                         |
| ------------- | -------------------------------------------------- |
| Lenguaje      | Java 21                                            |
| Framework     | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)  |
| Base de datos | PostgreSQL 17 — acceso reactivo con **R2DBC**      |
| Mensajería    | Apache Kafka 8 (KRaft)                             |
| Build         | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0     |
| Lombok        | 1.18.42                                            |
| API Docs      | Springdoc / OpenAPI (Swagger UI)                   |
| Calidad       | JaCoCo · PiTest · ArchUnit · BlockHound            |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Gestión del ciclo de vida de **proveedores** (CRUD + asignación de productos)
- Gestión de **órdenes de compra** (creación automática y manual, transiciones de estado)
- Generación automática de POs al recibir eventos `StockDepleted` de ms-inventory
- Máquina de estados para POs: `PENDING → SENT → CONFIRMED → RECEIVED` (con `PARTIALLY_RECEIVED` y `CANCELLED`)
- Publicación confiable de eventos de dominio a Kafka mediante **Transactional Outbox Pattern**
- Consumo idempotente de eventos de ms-inventory (tabla `processed_events`)

---

## Estructura de Módulos

```text
ms-provider/
├── applications/app-service/           # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml            # Config base
│       ├── application-local.yaml      # Perfil local (IntelliJ)
│       ├── application-docker.yaml     # Perfil Docker Compose
│       └── schema.sql                  # DDL de inicialización R2DBC
├── domain/
│   ├── model/                          # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── supplier/               # Supplier, SupplierProduct
│   │       ├── purchaseorder/          # PurchaseOrder, PurchaseOrderItem, PurchaseOrderStatus
│   │       ├── outbox/                 # OutboxEvent, EventType, OutboxStatus
│   │       ├── processedevent/         # ProcessedEvent (idempotencia)
│   │       ├── envelope/               # DomainEventEnvelope
│   │       ├── commons/               # PageResponse, DomainException y subclases
│   │       └── gateways/              # Ports: SupplierRepository, PurchaseOrderRepository, etc.
│   └── usecase/                        # Lógica de negocio (12 use cases)
│       └── com/arka/usecase/
│           ├── generatepurchaseorder/   # Auto-genera PO desde StockDepleted
│           ├── createsupplier/          # Crear proveedor (valida email único)
│           ├── getsupplier/             # Consultar proveedor con productos
│           ├── listsuppliers/           # Listar proveedores (paginado)
│           ├── updatesupplier/          # Actualizar proveedor
│           ├── deactivatesupplier/      # Desactivar proveedor (soft delete)
│           ├── assignsupplierproduct/   # Asignar/remover producto a proveedor
│           ├── listpurchaseorders/      # Listar POs (filtros + paginación)
│           ├── getpurchaseorder/        # Detalle de PO con ítems
│           ├── transitionpurchaseorder/ # Transiciones de estado (máquina de estados)
│           ├── createmanualpurchaseorder/ # Crear PO manual
│           └── outboxrelay/             # Relay de eventos pendientes
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── r2dbc-postgresql/           # Adapters R2DBC: Supplier, SupplierProduct, PurchaseOrder, Outbox, ProcessedEvent
│   │   └── kafka-producer/             # KafkaOutboxRelay (scheduler cada 5s)
│   └── entry-points/
│       ├── reactive-web/               # RouterConfig, SupplierHandler, PurchaseOrderHandler, GlobalExceptionHandler
│       └── kafka-consumer/             # KafkaEventConsumer (inventory-events → StockDepleted)
└── deployment/Dockerfile               # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/api/v1` — Puerto HTTP: `8089`

### Proveedores

| Método   | Ruta                                            | Descripción                                  | Códigos HTTP       |
| -------- | ----------------------------------------------- | -------------------------------------------- | ------------------ |
| `POST`   | `/api/v1/suppliers`                             | Crear un nuevo proveedor                     | 201, 400, 409      |
| `GET`    | `/api/v1/suppliers`                             | Listar proveedores activos (paginado)        | 200                |
| `GET`    | `/api/v1/suppliers/{supplierId}`                | Obtener proveedor con sus productos          | 200, 404           |
| `PUT`    | `/api/v1/suppliers/{supplierId}`                | Actualizar información de un proveedor       | 200, 400, 404      |
| `DELETE` | `/api/v1/suppliers/{supplierId}`                | Desactivar proveedor (soft delete)           | 204, 404           |
| `POST`   | `/api/v1/suppliers/{supplierId}/products`       | Asignar producto a proveedor                 | 201, 400           |
| `DELETE` | `/api/v1/suppliers/{supplierId}/products/{sku}` | Remover producto de proveedor                | 204                |

### Órdenes de Compra

| Método | Ruta                                     | Descripción                                    | Códigos HTTP       |
| ------ | ---------------------------------------- | ---------------------------------------------- | ------------------ |
| `GET`  | `/api/v1/purchase-orders`                | Listar POs (filtros: status, supplierId, sku)  | 200                |
| `GET`  | `/api/v1/purchase-orders/{id}`           | Detalle de PO con ítems                        | 200, 404           |
| `POST` | `/api/v1/purchase-orders`                | Crear PO manual                                | 201, 400, 404      |
| `PUT`  | `/api/v1/purchase-orders/{id}/send`      | Transición a SENT                              | 200, 404, 422      |
| `PUT`  | `/api/v1/purchase-orders/{id}/confirm`   | Transición a CONFIRMED                         | 200, 404, 422      |
| `PUT`  | `/api/v1/purchase-orders/{id}/receive`   | Transición a RECEIVED                          | 200, 404, 422      |
| `PUT`  | `/api/v1/purchase-orders/{id}/cancel`    | Transición a CANCELLED                         | 200, 404, 422      |

**Parámetros de paginación** en `GET`: `page` (default `0`), `size` (default `20`).

**Documentación interactiva:** `http://localhost:8089/swagger-ui.html`  
**Especificación OpenAPI:** `http://localhost:8089/api-docs`

### Ejemplos cURL

```bash
# Crear proveedor
curl -X POST http://localhost:8089/api/v1/suppliers \
  -H "Content-Type: application/json" \
  -d '{
    "name": "TechParts Colombia S.A.S",
    "email": "ventas@techparts.co",
    "phone": "+57 1 234 5678",
    "address": "Cra 15 #93-47 Of 501, Bogotá",
    "country": "CO"
  }'

# Listar proveedores
curl "http://localhost:8089/api/v1/suppliers?page=0&size=20"

# Consultar proveedor con sus productos
curl http://localhost:8089/api/v1/suppliers/a1b2c3d4-e5f6-7890-abcd-ef1234567890

# Asignar producto a proveedor
curl -X POST http://localhost:8089/api/v1/suppliers/a1b2c3d4-e5f6-7890-abcd-ef1234567890/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "KB-MECH-001",
    "supplierSku": "TP-KB-001",
    "unitPrice": 45000.00,
    "leadTimeDays": 5,
    "reorderMultiplier": 2.0,
    "preferred": true
  }'

# Crear orden de compra manual
curl -X POST http://localhost:8089/api/v1/purchase-orders \
  -H "Content-Type: application/json" \
  -d '{
    "supplierId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "items": [
      {"sku": "KB-MECH-001", "quantity": 50},
      {"sku": "MS-WL-002", "quantity": 30}
    ],
    "notes": "Reposición trimestral Q2 2026"
  }'

# Enviar PO al proveedor (transición PENDING → SENT)
curl -X PUT http://localhost:8089/api/v1/purchase-orders/{id}/send

# Listar POs filtradas por estado
curl "http://localhost:8089/api/v1/purchase-orders?status=PENDING&page=0&size=20"
```

---

## Eventos Kafka

**Tópico productor:** `provider-events` (partition key = purchaseOrderId)

| EventType              | Trigger                                                |
| ---------------------- | ------------------------------------------------------ |
| `PurchaseOrderCreated` | PO generada automáticamente (StockDepleted) o manual   |
| `PurchaseOrderSent`    | PO transicionada al estado SENT                        |

**Tópico consumidor:**

| Tópico             | Evento procesado | Acción                                                                 |
| ------------------ | ---------------- | ---------------------------------------------------------------------- |
| `inventory-events` | `StockDepleted`  | Genera PO automática con proveedor preferido y cantidad óptima         |

El consumidor es **idempotente** vía tabla `processed_events` (PK = `event_id`).

### Evento de Entrada (JSON para testing manual)

#### StockDepleted (tópico: `inventory-events`, key: SKU)

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "StockDepleted",
  "timestamp": "2026-05-27T10:00:00Z",
  "source": "ms-inventory",
  "correlationId": "corr-debug-001",
  "payload": {
    "sku": "KB-MECH-001",
    "currentStock": 3,
    "threshold": 10
  }
}
```

**Envío manual vía Kafka UI:** `http://localhost:8080` → Topics → `inventory-events` → Produce Message

---

## Esquema de Base de Datos (PostgreSQL 17 — `db_provider`)

| Tabla                  | Descripción                                                                                                    |
| ---------------------- | -------------------------------------------------------------------------------------------------------------- |
| `suppliers`            | Proveedores B2B. Email único, soft-delete con campo `active`.                                                  |
| `supplier_products`    | Relación proveedor↔SKU con precio unitario, lead time y multiplicador de reorden. `preferred` marca favorito.  |
| `purchase_orders`      | Órdenes de compra. Máquina de estados con CHECK constraint y timestamps por transición.                        |
| `purchase_order_items` | Ítems de cada PO (SKU, cantidad, precio unitario, subtotal). CHECK `quantity > 0`.                             |
| `outbox_events`        | Eventos de dominio pendientes de publicar a Kafka (Outbox Pattern). Estados: `PENDING`, `PUBLISHED`.           |
| `processed_events`     | Registro de eventos Kafka ya procesados para garantizar idempotencia.                                          |

---

## Patrones Clave

- **Anti-Corruption Layer (ACL)**: aísla la lógica de dominio de las interfaces de proveedores externos.
- **Transactional Outbox**: eventos insertados junto a la escritura de negocio. Relay asíncrono publica a Kafka cada 5s.
- **Idempotencia**: tabla `processed_events` garantiza procesamiento exactamente-una-vez de eventos Kafka.
- **Máquina de Estados**: `PurchaseOrderStatus.canTransitionTo()` valida transiciones antes de persistir.
- **Generación automática de PO**: algoritmo `(threshold × reorderMultiplier) - currentStock` para cantidad óptima.

---

## Cómo Levantar el Servicio

### Opción 1: Local (IntelliJ / terminal)

```bash
# 1. Levantar infraestructura (desde raíz del monorepo)
docker compose up -d localstack kafka kafka-ui postgres-provider

# 2. Ejecutar el servicio
cd ms-provider
./gradlew bootRun
```

**Perfil activo:** `local` (default)  
**Conexiones:** PostgreSQL `localhost:5437`, Kafka `localhost:9092`  
**Puerto servicio:** HTTP `8089`

### Opción 2: Docker Compose

```bash
# Desde raíz del monorepo
docker compose up -d localstack kafka kafka-ui
docker compose up ms-provider postgres-provider
```

**Perfil activo:** `docker` (inyectado por Compose)  
**Conexiones:** PostgreSQL `arka-db-provider:5432`, Kafka `kafka:29092`

### Variables de Entorno Relevantes

| Variable                  | Default (local)  | Descripción                        |
| ------------------------- | ---------------- | ---------------------------------- |
| `MS_PROVIDER_PORT`        | `8089`           | Puerto HTTP del servicio           |
| `R2DBC_HOST`              | `localhost`      | Host PostgreSQL                    |
| `R2DBC_PORT`              | `5437`           | Puerto PostgreSQL                  |
| `R2DBC_DB`                | `db_provider`    | Nombre de la base de datos         |
| `R2DBC_USER`              | `arka`           | Usuario R2DBC                      |
| `R2DBC_PASSWORD`          | `arkaSecret2025` | Contraseña R2DBC                   |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka                      |
| `SPRING_PROFILES_ACTIVE`  | `local`          | Perfil activo (`local` o `docker`) |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-provider/`:

```bash
./gradlew build                  # Compilar y empaquetar
./gradlew test                   # Tests unitarios (JUnit 5 + StepVerifier)
./gradlew jacocoMergedReport     # Reporte de cobertura JaCoCo (XML + HTML)
./gradlew pitest                 # Mutation testing con PiTest
./gradlew validateStructure      # Validar dependencias de capas (Clean Architecture)
```
