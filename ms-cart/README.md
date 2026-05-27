# ms-cart — Carrito de Compras

Microservicio reactivo de gestión de carritos de compra para la plataforma B2B Arka. Gestiona carritos temporales de clientes, detecta abandono automáticamente y valida precios en tiempo real durante el checkout contra ms-catalog.

## Stack

- Java 21, Spring Boot 4.0.3 (WebFlux)
- MongoDB (Reactive Drivers)
- Apache Kafka (reactor-kafka) — evento `CartAbandoned`
- WebClient → ms-catalog (precio actual)
- Puerto: **8084**

## Ejecutar localmente

```bash
./gradlew bootRun
```

Requiere MongoDB en `localhost:27017` y ms-catalog en `localhost:8081`.

## Tests

```bash
./gradlew test
```

## Docker

```bash
./gradlew build -x test
docker build -t ms-cart:latest -f deployment/Dockerfile .
```

## Endpoints principales

| Método | Ruta                             | Descripción                |
|--------|----------------------------------|----------------------------|
| POST   | `/api/v1/carts`                  | Crear carrito              |
| GET    | `/api/v1/carts/{cartId}`         | Obtener carrito            |
| GET    | `/api/v1/carts`                  | Listar carritos de cliente |
| POST   | `/api/v1/carts/{cartId}/items`   | Agregar item               |
| PUT    | `/api/v1/carts/{cartId}/items/{sku}` | Actualizar cantidad    |
| DELETE | `/api/v1/carts/{cartId}/items/{sku}` | Eliminar item          |
| DELETE | `/api/v1/carts/{cartId}/items`   | Vaciar carrito             |
| DELETE | `/api/v1/carts/{cartId}`         | Eliminar carrito           |
| POST   | `/api/v1/carts/{cartId}/checkout`| Checkout con validación    |

## Variables de entorno

| Variable                         | Default                           |
|----------------------------------|-----------------------------------|
| `MS_CART_PORT`                   | 8084                              |
| `MONGODB_URI`                    | mongodb://localhost:27017/cart_db  |
| `KAFKA_BOOTSTRAP_SERVERS`        | localhost:9092                     |
| `CATALOG_SERVICE_URL`            | http://localhost:8081              |
| `CART_ABANDONMENT_CHECK_INTERVAL`| 300000 (5 min)                    |

## Arquitectura

Clean Architecture (Bancolombia Scaffold 4.2.0):

```
domain/model/     → Entidades, Value Objects, Gateways
domain/usecase/   → CartUseCase (CRUD, checkout, detección abandono)
infrastructure/
  driven-adapters/
    mongo-repository/  → MongoCartAdapter (ReactiveMongoTemplate)
    kafka-producer/    → KafkaCartEventPublisher (reactor-kafka)
    catalog-client/    → WebClientProductPriceAdapter
  entry-points/
    reactive-web/      → CartController, DTOs, GlobalExceptionHandler
  helpers/
    scheduler/         → AbandonmentScheduler
applications/app-service/ → Spring Boot main, DI
```
