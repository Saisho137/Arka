# ms-catalog

Microservicio dueño del Bounded Context **Catálogo de Productos** dentro de la plataforma B2B Arka. Gestiona el ciclo de vida completo de productos y categorías, expone precios actuales para otros servicios y publica eventos de dominio a Kafka mediante el patrón Outbox.

---

## Stack Tecnológico

| Componente    | Tecnología                                          |
| ------------- | --------------------------------------------------- |
| Lenguaje      | Java 21                                             |
| Framework     | Spring Boot 4.0.3 — **Spring WebFlux** (reactivo)   |
| Base de datos | MongoDB (Reactive Driver) — Replica Set `rs0`       |
| Caché         | Redis (Cache-Aside, TTL 1 hora)                     |
| Mensajería    | Apache Kafka 8 (KRaft)                              |
| Build         | Gradle 9.4 + Bancolombia Scaffold Plugin 4.2.0      |
| Lombok        | 1.18.42                                             |
| API Docs      | Springdoc / OpenAPI (Swagger UI)                    |
| Calidad       | JaCoCo · PiTest · SonarQube · ArchUnit · BlockHound |

> `reactive=true` en `gradle.properties` — todo el stack es no-bloqueante (Mono/Flux).

---

## Responsabilidades del Servicio

- Gestión del ciclo de vida de **productos** (crear, consultar, actualizar, desactivar) y **categorías**
- Soporte de **reseñas anidadas** dentro del documento de producto (MongoDB embedded documents)
- Cache-Aside sobre lecturas de producto con Redis (TTL 1 hora)
- Publicación confiable de eventos de dominio a Kafka mediante **Transactional Outbox Pattern**
- Transacciones multi-documento en MongoDB mediante **Replica Set** (`rs0`)

---

## Estructura de Módulos

```text
ms-catalog/
├── applications/app-service/           # Main Spring Boot, configuración, DI
│   └── src/main/resources/
│       ├── application.yaml            # Config base
│       ├── application-local.yaml      # Perfil local (IntelliJ)
│       └── application-docker.yaml     # Perfil Docker Compose
├── domain/
│   ├── model/                          # Entidades, VOs, puertos (interfaces gateway)
│   │   └── com/arka/model/
│   │       ├── product/                # Product, Review, ProductRepository port, ProductCachePort
│   │       ├── category/               # Category, CategoryRepository port
│   │       ├── outboxevent/            # OutboxEvent, EventType, payloads (ProductCreated/Updated/PriceChanged)
│   │       ├── idempotency/            # IdempotencyRecord, IdempotencyRepository port
│   │       └── commons/               # DomainException y subclases, TransactionalGateway port
│   └── usecase/                        # Lógica de negocio
│       └── com/arka/usecase/
│           ├── product/                # ProductUseCase (create, getById, listActive, update, deactivate, addReview)
│           ├── category/               # CategoryUseCase (create, listAll)
│           └── outboxrelay/            # OutboxRelayUseCase (fetchPendingEvents, markAsPublished)
├── infrastructure/
│   ├── driven-adapters/
│   │   ├── mongo-repository/           # Adapters Reactive Mongo: Product, Category, Outbox, Idempotency
│   │   ├── redis/                      # RedisCacheAdapter (ProductCachePort)
│   │   └── kafka-producer/             # KafkaOutboxRelay (scheduler cada 5s)
│   └── entry-points/
│       └── reactive-web/               # ProductController, CategoryController, ReviewController, GlobalExceptionHandler
└── deployment/Dockerfile               # Multi-stage: gradle:9.4-jdk21 → amazoncorretto:21-alpine
```

---

## Endpoints REST

Base path: `/api/v1` — Puerto HTTP: `8084`

### Productos

| Método   | Ruta             | Descripción                            | Códigos HTTP  |
| -------- | ---------------- | -------------------------------------- | ------------- |
| `POST`   | `/products`      | Crear un nuevo producto en el catálogo | 201, 400, 409 |
| `GET`    | `/products`      | Listar productos activos (paginado)    | 200           |
| `GET`    | `/products/{id}` | Obtener producto por ID                | 200, 404      |
| `PUT`    | `/products/{id}` | Actualizar información de un producto  | 200, 400, 404 |
| `DELETE` | `/products/{id}` | Desactivar producto (soft delete)      | 200, 404      |

**Parámetros de paginación** en `GET /products`: `page` (default `0`), `size` (default `20`).

### Categorías

| Método | Ruta          | Descripción                 | Códigos HTTP  |
| ------ | ------------- | --------------------------- | ------------- |
| `POST` | `/categories` | Crear una nueva categoría   | 201, 400, 409 |
| `GET`  | `/categories` | Listar todas las categorías | 200           |

### Reseñas

| Método | Ruta                            | Descripción                      | Códigos HTTP  |
| ------ | ------------------------------- | -------------------------------- | ------------- |
| `POST` | `/products/{productId}/reviews` | Agregar una reseña a un producto | 200, 400, 404 |

**Documentación interactiva:** `http://localhost:8084/swagger-ui.html`  
**Especificación OpenAPI:** `http://localhost:8084/api-docs`

### Ejemplos cURL

```bash
# Listar productos activos (primera página)
curl "http://localhost:8084/api/v1/products?page=0&size=20"

# Obtener producto por ID
curl http://localhost:8084/api/v1/products/550e8400-e29b-41d4-a716-446655440000

# Crear producto
curl -X POST http://localhost:8084/api/v1/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "ACC-KB-001",
    "name": "Teclado Mecánico Arka",
    "cost": 80.00,
    "price": 120.00,
    "currency": "COP",
    "categoryId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "initialStock": 100
  }'

# Agregar reseña
curl -X POST http://localhost:8084/api/v1/products/550e8400-e29b-41d4-a716-446655440000/reviews \
  -H "Content-Type: application/json" \
  -d '{"rating": 5, "comment": "Excelente producto"}'

# Listar categorías
curl http://localhost:8084/api/v1/categories
```

---

## Eventos Kafka

**Tópico productor:** `product-events` (partition key = `productId`)

| EventType        | Trigger                                           | Payload principal                                                             |
| ---------------- | ------------------------------------------------- | ----------------------------------------------------------------------------- |
| `ProductCreated` | Creación exitosa de un producto                   | `productId`, `sku`, `name`, `price`, `currency`, `categoryId`, `initialStock` |
| `ProductUpdated` | Actualización o desactivación de un producto      | `productId`, `sku`, `name`, `price`, `currency`, `categoryId`, `active`       |
| `PriceChanged`   | Cambio de precio en un producto (junto a Updated) | `productId`, `sku`, `oldPrice`, `newPrice`, `currency`                        |

> `ms-catalog` **no consume** eventos de otros servicios. Es productor exclusivo del tópico `product-events`.

### Estructura del envelope Kafka (JSON)

```json
{
  "eventId": "990e8400-e29b-41d4-a716-446655440099",
  "eventType": "ProductCreated",
  "timestamp": "2026-04-19T10:00:00Z",
  "source": "ms-catalog",
  "correlationId": "550e8400-e29b-41d4-a716-446655440000",
  "payload": {
    "productId": "550e8400-e29b-41d4-a716-446655440000",
    "sku": "ACC-KB-001",
    "name": "Teclado Mecánico Arka",
    "cost": 80.0,
    "price": 120.0,
    "currency": "COP",
    "categoryId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "initialStock": 100
  }
}
```

**Envío manual vía Kafka UI:** `http://localhost:8080` → Topics → `product-events` → Produce Message

---

## Colecciones MongoDB (`db_catalog`)

| Colección             | Descripción                                                                                          |
| --------------------- | ---------------------------------------------------------------------------------------------------- |
| `products`            | Documento principal del producto con reseñas embebidas. Campo `active` para soft delete.             |
| `categories`          | Categorías del catálogo. Nombre único.                                                               |
| `outbox_events`       | Eventos de dominio pendientes de publicar a Kafka (Outbox Pattern). Estados: `PENDING`, `PUBLISHED`. |
| `idempotency_records` | Registros para garantizar idempotencia en operaciones críticas.                                      |

---

## Patrones Clave

- **Transactional Outbox**: el evento se inserta en la misma transacción MongoDB que la escritura de negocio. El relay asíncrono (`KafkaOutboxRelay`) publica a Kafka cada 5s.
- **MongoDB Replica Set** (`rs0`): requerido para transacciones multi-documento. El driver Reactive Mongo lanza error si la URI no incluye `replicaSet=rs0`.
- **Cache-Aside** con Redis: lecturas de producto se sirven desde caché (TTL 1 hora). Invalidación al actualizar o desactivar.
- **TransactionalGateway**: port de dominio implementado en infra con `ReactiveMongoTransactionManager` + `TransactionalOperator`. Los UseCases no importan Spring.

---

## Notas de implementación — MongoDB Replica Set

`ProductUseCase` y `CategoryUseCase` usan `TransactionalGateway` para envolver el guardado del producto/categoría y su `OutboxEvent` en una **transacción atómica**.

**Cómo está configurado localmente (`docker compose up`):**

1. `mongodb` arranca con `--replSet rs0 --bind_ip_all`.
2. `mongo-init-replica` (one-shot) ejecuta `rs.initiate()` via `mongosh` una vez que MongoDB está healthy. Es idempotente: si el RS ya existe, no hace nada.
3. `ms-catalog` depende de `mongo-init-replica: service_completed_successfully`, garantizando que el RS esté listo antes del arranque de Spring.

**URI de conexión** — debe incluir `replicaSet=rs0`:

```text
mongodb://<user>:<pass>@<host>:<port>/db_catalog?authSource=admin&replicaSet=rs0
```

Sin `replicaSet=rs0` el driver reactive lanza:

> _"Sessions are not supported by the MongoDB cluster to which this client is connected"_

**Beans de transacción registrados en `MongoConfig`:**

- `ReactiveMongoTransactionManager` — gestiona el ciclo de vida de la transacción.
- `TransactionalOperator` — envuelve el `Mono<T>` del use case.
- `MongoTransactionalAdapter` — implementación del puerto `TransactionalGateway`.

---

## Decisiones técnicas — Por qué hay un `mongo-keyfile` y un `mongo-entrypoint.sh`

### Problema 1: MongoDB 7 requiere `--keyFile` cuando se combinan autenticación y Replica Set

MongoDB permite habilitar autenticación (`MONGO_INITDB_ROOT_*`) y Replica Set (`--replSet`) de forma independiente, pero **al usarlos juntos impone una restricción de seguridad**: los nodos del Replica Set deben autenticarse entre sí mediante un shared secret. Esto se configura con `--keyFile`.

Sin `--keyFile`, `mongod` falla al arrancar con:

```text
BadValue: security.keyFile is required when authorization is enabled with replica sets
```

La solución es generar el archivo una sola vez:

```bash
openssl rand -base64 756 > scripts/mongo-keyfile
chmod 400 scripts/mongo-keyfile
```

Y montarlo en el contenedor con permisos correctos antes de que `mongod` arranque.

### Problema 2: El `command` override en Docker Compose saltaba la creación del usuario admin

El primer intento fue usar `command: bash -c "cp keyfile && exec mongod ..."` en el servicio `mongodb` del `compose.yaml`. Esto funciona para otras imágenes, pero **la imagen oficial de MongoDB usa `docker-entrypoint.sh` como `ENTRYPOINT`**, y al sobrescribir `command` con `bash -c ...`, Docker interpreta que `bash` es un comando para pasarle al entrypoint — el cual lo ejecuta directamente sin correr el proceso de inicialización que crea el usuario root (`MONGO_INITDB_ROOT_*`).

Resultado: `mongo-init-replica` fallaba con `Authentication failed` porque el usuario admin nunca se creó.

La solución correcta es sobrescribir `entrypoint` (no `command`), con un script wrapper [`scripts/mongo-entrypoint.sh`](scripts/mongo-entrypoint.sh) que:

1. Prepara el keyFile con los permisos correctos (`chmod 400`, `chown mongodb:mongodb`).
2. Delega explícitamente al entrypoint oficial: `exec /usr/local/bin/docker-entrypoint.sh "$@"`.

Así la imagen crea el usuario admin normalmente y luego arranca `mongod` con `--keyFile`.

### Problema 3: `MongoIndexConfig` mataba la app en el primer arranque

`MongoIndexConfig` crea los índices de MongoDB al arrancar vía `CommandLineRunner`. En el primer arranque del Replica Set recién iniciado, el nodo tarda unos segundos en elegirse como primario. Si la creación de índices se ejecuta en ese instante, el driver lanza `DataAccessResourceFailureException`.

El `.block()` al final de la cadena reactiva propaga la excepción al `CommandLineRunner`. Spring Boot interpreta cualquier excepción no capturada en un `CommandLineRunner` como fallo fatal y llama a `System.exit(1)` — la JVM muere, Netty deja de escuchar en el puerto 8084 y el healthcheck falla, aunque en los logs aparezca `Started MainApplication` justo antes del crash.

La solución es agregar `.onErrorResume()` antes del `.block()` para que el fallo sea **no fatal**: se loguea un warning y la app continúa. Los índices se crearán correctamente en el siguiente reinicio (cuando MongoDB ya sea primario estable) o en ejecución normal si MongoDB está disponible.

```java
.doOnError(e -> log.error("Error creating MongoDB indexes", e))
.onErrorResume(e -> {
    log.warn("MongoDB index creation failed (non-fatal). Indexes will be created on next startup.");
    return Mono.empty();
})
.block();
```

---

## Cómo Levantar el Servicio

### Opción 1: Local (IntelliJ / terminal)

```bash
# 1. Levantar infraestructura (desde raíz del monorepo)
docker compose up -d mongodb mongo-init-replica redis kafka kafka-ui

# 2. Ejecutar el servicio
cd ms-catalog
./gradlew bootRun
```

**Perfil activo:** `local` (default)  
**Conexiones:** MongoDB `localhost:27017`, Redis `localhost:6379`, Kafka `localhost:9092`  
**Puerto servicio:** HTTP `8084`

### Opción 2: Docker Compose

```bash
# Desde raíz del monorepo
docker compose up -d mongodb mongo-init-replica redis kafka kafka-ui
docker compose up ms-catalog
```

**Perfil activo:** `docker` (inyectado por Compose)  
**Conexiones:** MongoDB `arka-mongodb:27017`, Redis `arka-redis:6379`, Kafka `kafka:29092`

### Variables de Entorno Relevantes

| Variable                  | Default (local)  | Descripción                        |
| ------------------------- | ---------------- | ---------------------------------- |
| `MS_CATALOG_PORT`         | `8084`           | Puerto HTTP del servicio           |
| `MONGO_HOST`              | `localhost`      | Host MongoDB                       |
| `MONGO_PORT`              | `27017`          | Puerto MongoDB                     |
| `MONGO_USER`              | `arka`           | Usuario MongoDB                    |
| `MONGO_PASSWORD`          | `arkaSecret2025` | Contraseña MongoDB                 |
| `REDIS_HOST`              | `localhost`      | Host Redis                         |
| `REDIS_PORT`              | `6379`           | Puerto Redis                       |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers Kafka                      |
| `SPRING_PROFILES_ACTIVE`  | `local`          | Perfil activo (`local` o `docker`) |

---

## Comandos de Build y Calidad

Ejecutar desde `ms-catalog/`:

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
- Los **driven adapters NO manejan transacciones** — la transacción la define el UseCase vía `TransactionalGateway`.
- El **Outbox Relay** tiene su intervalo configurado en `application.yaml` (`scheduler.outbox-relay.interval`, default 5000ms) — no hardcodeado en `@Scheduled`.
- El Dockerfile usa **multi-stage build** y corre con usuario no-root (`appuser`) sobre `amazoncorretto:21-alpine`.
- **BlockHound** está activo en tests para detectar llamadas bloqueantes en el contexto reactivo.
