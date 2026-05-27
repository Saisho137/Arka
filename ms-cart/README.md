# ms-cart

Microservicio dueño del Bounded Context **Carrito de Compras** dentro de la plataforma B2B Arka. Gestiona carritos temporales de clientes, detecta abandono automáticamente y valida precios en tiempo real durante el checkout contra ms-catalog (gRPC). Publica eventos de abandono a Kafka para que ms-notifications envíe alertas.

---

## Stack Tecnológico

| Componente        | Tecnología                                          |
| ----------------- | --------------------------------------------------- |
| Lenguaje          | Java 21                                             |
| Framework         | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)   |
| Base de datos     | MongoDB (Reactive Driver) — Replica Set `rs0`       |
| Mensajería        | Apache Kafka 8 (KRaft) — reactor-kafka              |
| Comunicación sync | gRPC (Protobuf) — cliente hacia ms-catalog          |
| Build             | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0      |
| Lombok            | 1.18.42                                             |
| API Docs          | Springdoc / OpenAPI (Swagger UI)                    |
| Calidad           | JaCoCo · PiTest · SonarQube · ArchUnit · BlockHound |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Gestión CRUD completa de carritos temporales por cliente (crear, consultar, listar, eliminar)
- Agregar/remover/actualizar items con validación de existencia de producto vía gRPC → ms-catalog
- Detección automática de **carritos abandonados** (scheduler configurable, default 5 min)
- **Checkout con validación de precios** en tiempo real: compara precios guardados vs. precio actual de catálogo (gRPC)
- Publicación de eventos `CartAbandoned` a Kafka para notificaciones downstream
- Control de acceso por rol: `CUSTOMER` solo opera sobre sus propios carritos; `ADMIN` acceso total

---

## Estructura de Módulos

```text
ms-cart/
├── applications/app-service/          # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml           # Config base
│       ├── application-local.yaml     # Perfil local (IntelliJ / terminal)
│       └── application-docker.yaml    # Perfil Docker Compose
├── domain/
│   ├── model/                         # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── cart/                  # Cart, CartItem, CartStatus, CheckoutResult, CheckoutStatus, PriceChange
│   │       ├── event/                 # CartAbandonedEvent, DomainEventEnvelope
│   │       └── commons/exception/     # DomainException + 6 subclases (CartNotFound, EmptyCart, etc.)
│   └── usecase/                       # Lógica de negocio
│       └── com/arka/usecase/cart/     # CartUseCase (CRUD, checkout, detección abandono)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── mongo-repository/          # MongoCartAdapter (ReactiveMongoTemplate, atomic ops)
│   │   ├── kafka-producer/            # KafkaCartEventPublisher (reactor-kafka, CartAbandoned)
│   │   └── grpc-catalog/             # GrpcProductPriceAdapter → CatalogService/GetProductInfo
│   ├── entry-points/
│   │   └── reactive-web/             # CartController (REST), DTOs, GlobalExceptionHandler
│   └── helpers/
│       └── scheduler/                 # AbandonmentScheduler (detección periódica)
└── deployment/Dockerfile              # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/api/v1/carts` — Puerto HTTP: `8086`

| Método   | Ruta                         | Descripción                            | Roles           | Códigos HTTP       |
| -------- | ---------------------------- | -------------------------------------- | --------------- | ------------------ |
| `POST`   | `/`                          | Crear nuevo carrito                    | CUSTOMER, ADMIN | 201, 400           |
| `GET`    | `/{cartId}`                  | Obtener carrito por ID                 | CUSTOMER, ADMIN | 200, 403, 404      |
| `GET`    | `/`                          | Listar carritos del cliente            | CUSTOMER, ADMIN | 200                |
| `POST`   | `/{cartId}/items`            | Agregar item al carrito                | CUSTOMER        | 200, 400, 404, 503 |
| `PUT`    | `/{cartId}/items/{sku}`      | Actualizar cantidad de un item         | CUSTOMER        | 200, 400, 404      |
| `DELETE` | `/{cartId}/items/{sku}`      | Eliminar item del carrito              | CUSTOMER        | 200, 404           |
| `DELETE` | `/{cartId}/items`            | Vaciar carrito (eliminar todos items)  | CUSTOMER        | 200, 404           |
| `DELETE` | `/{cartId}`                  | Eliminar carrito completo              | CUSTOMER, ADMIN | 204, 403, 404      |
| `POST`   | `/{cartId}/checkout`         | Checkout con validación de precios     | CUSTOMER        | 200, 400, 404, 409, 503 |

**Parámetros de query** en `GET /`: `status` (opcional, filtra por `CartStatus`: `ACTIVE`, `ABANDONED`, `CHECKED_OUT`).

**Headers requeridos:** `X-User-Email` (email del usuario autenticado, inyectado por API Gateway), `X-User-Role` (`ADMIN` o `CUSTOMER`, default `CUSTOMER`).

**Documentación interactiva:** `http://localhost:8086/swagger-ui.html`  
**Especificación OpenAPI:** `http://localhost:8086/api-docs`

### Ejemplos cURL

```bash
# Crear un carrito
curl -s -X POST "http://localhost:8086/api/v1/carts" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" \
  -d '{"customerId": "customer1@arka.com"}' | jq .

# Listar carritos del cliente autenticado
curl -s "http://localhost:8086/api/v1/carts" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq .

# Listar solo carritos activos
curl -s "http://localhost:8086/api/v1/carts?status=ACTIVE" \
  -H "X-User-Email: customer1@arka.com" \
  -H "X-User-Role: CUSTOMER" | jq .

# Agregar item al carrito (valida existencia en catálogo vía gRPC)
curl -s -X POST "http://localhost:8086/api/v1/carts/{cartId}/items" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -d '{"sku": "KB-MECH-001", "quantity": 2}' | jq .

# Actualizar cantidad
curl -s -X PUT "http://localhost:8086/api/v1/carts/{cartId}/items/KB-MECH-001" \
  -H "Content-Type: application/json" \
  -H "X-User-Email: customer1@arka.com" \
  -d '{"quantity": 5}' | jq .

# Eliminar item
curl -s -X DELETE "http://localhost:8086/api/v1/carts/{cartId}/items/KB-MECH-001" \
  -H "X-User-Email: customer1@arka.com" | jq .

# Checkout — valida precios actuales contra catálogo (gRPC)
curl -s -X POST "http://localhost:8086/api/v1/carts/{cartId}/checkout" \
  -H "X-User-Email: customer1@arka.com" | jq .

# Respuesta READY (precios sin cambios):
# {"cartId": "...", "priceChanges": [], "totalAmount": 240.00, "status": "READY"}

# Respuesta PRICE_CHANGED (precios difieren):
# {"cartId": "...", "priceChanges": [{"sku": "KB-MECH-001", "oldPrice": 120.00, "newPrice": 130.00}], "totalAmount": 260.00, "status": "PRICE_CHANGED"}

# Eliminar carrito (ADMIN puede eliminar cualquiera)
curl -s -X DELETE "http://localhost:8086/api/v1/carts/{cartId}" \
  -H "X-User-Email: admin@arka.com" \
  -H "X-User-Role: ADMIN"
```

---

## Comunicación gRPC

Puerto destino: `9091` (ms-catalog gRPC server)

```protobuf
service CatalogService {
  rpc GetProductInfo (GetProductInfoRequest) returns (GetProductInfoResponse);
}
```

Consumido por `GrpcProductPriceAdapter` para:
- Validar existencia de producto al agregar items al carrito
- Obtener precio actual durante el checkout para detectar cambios de precio

---

## Eventos Kafka

**Tópico productor:** `cart-events` (partition key = `cartId`)

| EventType       | Trigger                                      | Payload principal                                            |
| --------------- | -------------------------------------------- | ------------------------------------------------------------ |
| `CartAbandoned` | Cart ACTIVE sin modificaciones > 30 min      | `cartId`, `customerId`, `itemCount`, `totalAmount`, `abandonedAt`, `lastModifiedAt` |

> `ms-cart` **no consume** eventos de otros servicios. Es productor exclusivo del tópico `cart-events`.

### Estructura del envelope Kafka (JSON)

```json
{
  "eventId": "990e8400-e29b-41d4-a716-446655440099",
  "eventType": "CartAbandoned",
  "timestamp": "2026-05-27T10:00:00Z",
  "source": "ms-cart",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "cartId": "550e8400-e29b-41d4-a716-446655440000",
    "customerId": "customer1@arka.com",
    "itemCount": 3,
    "totalAmount": 360.00,
    "abandonedAt": "2026-05-27T10:00:00Z",
    "lastModifiedAt": "2026-05-27T09:25:00Z"
  }
}
```

---

## Colecciones MongoDB (`db_cart`)

| Colección | Descripción                                                                                             |
| --------- | ------------------------------------------------------------------------------------------------------- |
| `carts`   | Documento principal del carrito con items embebidos. Campo `status`: `ACTIVE`, `ABANDONED`, `CHECKED_OUT`. |

**Índices creados al arrancar** (vía `MongoIndexConfig`):

| Índice                       | Campos                            | Uso                                    |
| ---------------------------- | --------------------------------- | -------------------------------------- |
| `idx_customerId_status`      | `customerId ASC`, `status ASC`    | Consulta de carritos por cliente       |
| `idx_status_lastModifiedAt`  | `status ASC`, `lastModifiedAt ASC` | Detección de carritos abandonados    |

---

## Patrones Clave

- **Publicación directa a Kafka** (reactor-kafka): idempotent producer (`acks=all`, `retries=3`, `enable.idempotence=true`). Apropiado para eventos informativos no-críticos como abandono.
- **gRPC Client** (`@GrpcClient("ms-catalog")`): validación de productos y precios en tiempo real contra ms-catalog. Patrón idéntico al de ms-order.
- **Atomic MongoDB Operations**: usa `$push`, `$pull`, `$inc`, positional `$` operator para modificar items sin race conditions en single-document updates.
- **Scheduled Abandonment Detection**: `@Scheduled` con intervalo configurable. Detecta carritos ACTIVE sin actividad > threshold y publica eventos.
- **Domain Exception Hierarchy**: excepciones tipadas con HTTP status y código de error; mapeadas centralmente en `GlobalExceptionHandler`.
- **Security Headers**: CSP, HSTS, X-Content-Type-Options aplicados vía `WebFilter`.

---

## Cómo Levantar el Servicio

### Opción 1: Local (IntelliJ / terminal)

```bash
# 1. Levantar infraestructura (desde raíz del monorepo)
docker compose up -d mongodb mongo-init-replica kafka kafka-ui

# 2. Levantar ms-catalog (dependencia gRPC)
cd ms-catalog && ./gradlew bootRun &

# 3. Ejecutar el servicio
cd ms-cart
./gradlew bootRun
```

**Perfil activo:** `local` (default)  
**Conexiones:** MongoDB `localhost:27017`, Kafka `localhost:9092`, ms-catalog gRPC `localhost:9091`  
**Puerto servicio:** HTTP `8086`

### Opción 2: Docker Compose

```bash
# Desde raíz del monorepo
docker compose up -d mongodb mongo-init-replica kafka kafka-ui ms-catalog
docker compose up ms-cart
```

**Perfil activo:** `docker` (inyectado por Compose)  
**Conexiones:** MongoDB `arka-mongodb:27017`, Kafka `kafka:29092`, ms-catalog gRPC `arka-ms-catalog:9091`

### Variables de Entorno Relevantes

| Variable                  | Default (local)  | Descripción                        |
| ------------------------- | ---------------- | ---------------------------------- |
| `MS_CART_PORT`            | `8086`           | Puerto HTTP del servicio           |
| `MONGO_HOST`              | `localhost`      | Host MongoDB                       |
| `MONGO_PORT`              | `27017`          | Puerto MongoDB                     |
| `MONGO_USER`              | `arka`           | Usuario MongoDB                    |
| `MONGO_PASSWORD`          | `arkaSecret2025` | Contraseña MongoDB                 |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka                      |
| `MS_CATALOG_GRPC_HOST`    | `localhost`      | Host gRPC ms-catalog               |
| `MS_CATALOG_GRPC_PORT`    | `9091`           | Puerto gRPC ms-catalog             |
| `SPRING_PROFILES_ACTIVE`  | `local`          | Perfil activo (`local` o `docker`) |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-cart/`:

```bash
./gradlew build                  # Compilar y empaquetar
./gradlew test                   # Tests unitarios (JUnit 5 + StepVerifier)
./gradlew jacocoMergedReport     # Reporte de cobertura JaCoCo (XML + HTML)
./gradlew pitest                 # Mutation testing con PiTest
./gradlew sonar                  # Análisis estático SonarQube
./gradlew validateStructure      # Validar dependencias de capas (Clean Architecture)
```

---

## Consideraciones Importantes

- **Nunca usar `Optional` en cadenas reactivas** — usar `Mono.justOrEmpty()`, `switchIfEmpty()`, `defaultIfEmpty()`.
- **Nunca `synchronized`/`Lock` en beans reactivos** — Reactor gestiona la concurrencia.
- El **AbandonmentScheduler** tiene su intervalo configurado en `application.yaml` (`cart.abandonment.check-interval`, default 300000ms) — no hardcodeado en `@Scheduled`.
- **Precio autoritativo**: durante checkout, el precio actual siempre proviene de ms-catalog (gRPC). Se compara contra el precio almacenado en el carrito para detectar cambios.
- **MongoDB Replica Set** (`rs0`): requerido para que el driver reactive funcione correctamente con las operaciones del template.
- El Dockerfile usa **multi-stage build** y corre con usuario no-root (`appuser`) sobre `amazoncorretto:21-alpine`.
- **BlockHound** está activo en tests para detectar llamadas bloqueantes en el contexto reactivo.
- **gRPC stubs**: inyectados por constructor (`@GrpcClient` en parámetro del constructor) para cumplir la regla ArchUnit de campos finales.
