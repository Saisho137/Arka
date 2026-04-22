---
sidebar_position: 10
title: Puertos e Interfaces
---

# Puertos e Interfaces del Sistema

> Mapa completo de todos los puertos, UIs, herramientas de observabilidad y conexiones a bases de datos expuestos por el ecosistema. **No incluye endpoints REST de controllers** (ver [04-api-endpoints.md](04-api-endpoints.md)).

---

## Herramientas y UIs

| Recurso                   | URL                                         | Descripción                                                         |
| ------------------------- | ------------------------------------------- | ------------------------------------------------------------------- |
| Kafka UI                  | <http://localhost:8080>                     | Visualización de tópicos, mensajes, consumer groups y offsets       |
| LocalStack Dashboard      | <http://localhost:4566/\_localstack/health> | Estado de los servicios AWS emulados                                |
| LocalStack (endpoint AWS) | <http://localhost:4566>                     | Emulador AWS: Secrets Manager, S3, SES, API Gateway, CloudFormation |

---

## Documentación API (Swagger / OpenAPI)

Disponible en los microservicios que ya tienen entry-points REST implementados:

| Servicio     | Swagger UI                              | OpenAPI JSON                     | OpenAPI YAML                          |
| ------------ | --------------------------------------- | -------------------------------- | ------------------------------------- |
| ms-catalog   | <http://localhost:8084/swagger-ui.html> | <http://localhost:8084/api-docs> | <http://localhost:8084/api-docs.yaml> |
| ms-inventory | <http://localhost:8082/swagger-ui.html> | <http://localhost:8082/api-docs> | <http://localhost:8082/api-docs.yaml> |

---

## Health Checks (Actuator)

Todos los microservicios exponen el endpoint de Spring Actuator:

| Servicio         | Health Check                            |
| ---------------- | --------------------------------------- |
| ms-order         | <http://localhost:8081/actuator/health> |
| ms-inventory     | <http://localhost:8082/actuator/health> |
| ms-payment       | <http://localhost:8083/actuator/health> |
| ms-catalog       | <http://localhost:8084/actuator/health> |
| ms-notifications | <http://localhost:8085/actuator/health> |
| ms-cart          | <http://localhost:8086/actuator/health> |
| ms-reporter      | <http://localhost:8087/actuator/health> |
| ms-shipping      | <http://localhost:8088/actuator/health> |
| ms-provider      | <http://localhost:8089/actuator/health> |

---

## gRPC

| Servicio     | Puerto Host | Protocolo     | Descripción                                                                        |
| ------------ | ----------- | ------------- | ---------------------------------------------------------------------------------- |
| ms-inventory | **9090**    | gRPC/Protobuf | `InventoryService.ReserveStock` — reserva síncrona de stock desde ms-order         |
| ms-catalog   | **9091**    | gRPC/Protobuf | `CatalogService.GetProductInfo` — precio y nombre por SKU desde ms-order y ms-cart |

ms-inventory usa puerto `9090` tanto en local como en docker. ms-catalog usa `9091` en local (evita colisión) y `9090` en docker (cada contenedor tiene su namespace).

---

## Bases de Datos — PostgreSQL 17

Todas usan las credenciales del `.env` (`POSTGRES_USER` / `POSTGRES_PASSWORD`).

| Contenedor        | Base de Datos  | Puerto Host | Puerto Interno | Servicio propietario | Conexión local                             |
| ----------------- | -------------- | ----------- | -------------- | -------------------- | ------------------------------------------ |
| arka-db-orders    | `db_orders`    | **5432**    | 5432           | ms-order             | `postgresql://localhost:5432/db_orders`    |
| arka-db-inventory | `db_inventory` | **5433**    | 5432           | ms-inventory         | `postgresql://localhost:5433/db_inventory` |
| arka-db-payment   | `db_payment`   | **5434**    | 5432           | ms-payment           | `postgresql://localhost:5434/db_payment`   |
| arka-db-reporter  | `db_reporter`  | **5435**    | 5432           | ms-reporter          | `postgresql://localhost:5435/db_reporter`  |
| arka-db-shipping  | `db_shipping`  | **5436**    | 5432           | ms-shipping          | `postgresql://localhost:5436/db_shipping`  |
| arka-db-provider  | `db_provider`  | **5437**    | 5432           | ms-provider          | `postgresql://localhost:5437/db_provider`  |

Scripts de inicialización: `postgresql-scripts/init_<servicio>.sql` (se ejecutan al crear el contenedor por primera vez).

### Conexión desde un cliente SQL

```bash
# Ejemplo con psql
psql -h localhost -p 5433 -U arka -d db_inventory

# Ejemplo con DBeaver / DataGrip
# Host: localhost | Port: 5433 | Database: db_inventory | User/Pass: del .env
```

---

## Base de Datos — MongoDB 7

Instancia compartida con replica set (`rs0`) habilitado para transacciones multi-documento.

| Contenedor   | Puerto Host | Servicios propietarios                | Bases de datos                              |
| ------------ | ----------- | ------------------------------------- | ------------------------------------------- |
| arka-mongodb | **27017**   | ms-catalog, ms-cart, ms-notifications | `catalog_db`, `cart_db`, `notifications_db` |

Credenciales: `MONGO_USER` / `MONGO_PASSWORD` del `.env`.

### Conexión

```bash
# mongosh
mongosh "mongodb://localhost:27017/catalog_db" -u <MONGO_USER> -p <MONGO_PASSWORD> --authenticationDatabase admin

# MongoDB Compass / Studio 3T
# URI: mongodb://<user>:<pass>@localhost:27017/?authSource=admin&replicaSet=rs0
```

> **Nota:** El replica set es inicializado automáticamente por el contenedor `mongo-init-replica` (one-shot). Si MongoDB arranca sin replica set, las transacciones fallarán.

---

## Caché — Redis 7

| Contenedor | Puerto Host | Servicio propietario | Uso                                 |
| ---------- | ----------- | -------------------- | ----------------------------------- |
| arka-redis | **6379**    | ms-catalog           | Cache-Aside para productos (TTL 1h) |

### Conexión Redis

```bash
# redis-cli
redis-cli -h localhost -p 6379

# Comandos útiles
KEYS *              # Ver todas las keys
GET <key>           # Ver valor de una key
TTL <key>           # Tiempo restante de vida
FLUSHALL            # Limpiar toda la caché (dev only)
```

---

## Kafka — Confluent (KRaft)

| Contenedor | Puerto Host | Puerto Interno (inter-broker) | Descripción                                |
| ---------- | ----------- | ----------------------------- | ------------------------------------------ |
| arka-kafka | **9092**    | 29092                         | Broker Kafka en modo KRaft (sin ZooKeeper) |

### Conexión desde herramientas locales

```bash
# kafka CLI
kafka-topics --bootstrap-server localhost:9092 --list
kafka-console-consumer --bootstrap-server localhost:9092 --topic product-events --from-beginning

# Desde otro contenedor en arka-network
kafka:29092
```

### Tópicos creados

`product-events` · `inventory-events` · `order-events` · `cart-events` · `payment-events` · `shipping-events` · `provider-events`

> Ver [03-kafka-eventos.md](03-kafka-eventos.md) para detalle de eventos y consumer groups.

---

## Resumen de Puertos

| Puerto | Servicio                  | Tipo                  |
| ------ | ------------------------- | --------------------- |
| 4566   | LocalStack (AWS)          | Infraestructura       |
| 5432   | PostgreSQL — db_orders    | Base de datos         |
| 5433   | PostgreSQL — db_inventory | Base de datos         |
| 5434   | PostgreSQL — db_payment   | Base de datos         |
| 5435   | PostgreSQL — db_reporter  | Base de datos         |
| 5436   | PostgreSQL — db_shipping  | Base de datos         |
| 5437   | PostgreSQL — db_provider  | Base de datos         |
| 6379   | Redis                     | Caché                 |
| 8080   | Kafka UI                  | Herramienta           |
| 8081   | ms-order                  | Microservicio         |
| 8082   | ms-inventory              | Microservicio         |
| 8083   | ms-payment                | Microservicio         |
| 8084   | ms-catalog                | Microservicio         |
| 8085   | ms-notifications          | Microservicio         |
| 8086   | ms-cart                   | Microservicio         |
| 8087   | ms-reporter               | Microservicio         |
| 8088   | ms-shipping               | Microservicio         |
| 8089   | ms-provider               | Microservicio         |
| 9090   | gRPC — ms-inventory       | Comunicación síncrona |
| 9091   | gRPC — ms-catalog (local) | Comunicación síncrona |
| 9092   | Kafka broker              | Mensajería            |
| 27017  | MongoDB                   | Base de datos         |
