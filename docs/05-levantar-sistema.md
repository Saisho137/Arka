---
sidebar_position: 9
title: Cómo Levantar el Sistema
---

# Cómo Levantar el Sistema

## Prerequisitos

- Docker y Docker Compose
- Java 21 (para desarrollo local)
- Archivo `.env` en la raíz del proyecto (ya incluido)

---

## Arquitectura de Contenedores

### Infraestructura Base

| Servicio             | Imagen                      | Puerto Host | Propósito                                |
| -------------------- | --------------------------- | ----------- | ---------------------------------------- |
| `postgres-orders`    | postgres:17-alpine          | 5432        | BD ms-order                              |
| `postgres-inventory` | postgres:17-alpine          | 5433        | BD ms-inventory                          |
| `postgres-payment`   | postgres:17-alpine          | 5434        | BD ms-payment                            |
| `postgres-reporter`  | postgres:17-alpine          | 5435        | BD ms-reporter                           |
| `postgres-shipping`  | postgres:17-alpine          | 5436        | BD ms-shipping                           |
| `postgres-provider`  | postgres:17-alpine          | 5437        | BD ms-provider                           |
| `mongodb`            | mongo:7-jammy               | 27017       | BD ms-catalog, ms-cart, ms-notifications |
| `mongo-init-replica` | mongo:7-jammy               | —           | Inicializa replica set (one-shot)        |
| `redis`              | redis:7-alpine              | 6379        | Caché ms-catalog                         |
| `kafka`              | confluentinc/cp-kafka:8.0.4 | 9092        | Message broker (KRaft)                   |
| `kafka-init`         | confluentinc/cp-kafka:8.0.4 | —           | Crea los 7 tópicos (one-shot)            |
| `kafka-ui`           | provectuslabs/kafka-ui      | 8080        | UI para ver mensajes Kafka               |
| `localstack`         | localstack/localstack       | 4566        | AWS simulado (Secrets, S3, SES)          |

### Microservicios

| Servicio           | Puerto | Dependencias                                          |
| ------------------ | ------ | ----------------------------------------------------- |
| `ms-catalog`       | 8084   | mongodb, mongo-init-replica, redis, localstack, kafka |
| `ms-inventory`     | 8082   | postgres-inventory, localstack, kafka                 |
| `ms-order`         | 8081   | postgres-orders, localstack, kafka, ms-inventory      |
| `ms-notifications` | 8085   | mongo-init-replica, localstack, kafka                 |
| `ms-cart`          | 8086   | mongo-init-replica, localstack, kafka, ms-catalog     |
| `ms-payment`       | 8083   | kafka                                                 |
| `ms-reporter`      | 8087   | postgres-reporter, localstack, kafka                  |
| `ms-shipping`      | 8088   | postgres-shipping, localstack, kafka                  |
| `ms-provider`      | 8089   | postgres-provider, localstack, kafka                  |

---

## Opción 1: Levantar TODO el sistema

```bash
docker compose up --build -d
```

Esto levanta toda la infraestructura + los 9 microservicios. El orden de dependencias se resuelve automáticamente por los `depends_on` con healthchecks.

### Verificar que todo está corriendo

```bash
docker compose ps
```

### Ver logs de un servicio

```bash
docker compose logs -f ms-catalog
```

---

## Opción 2: Solo infraestructura (para desarrollo local)

Útil cuando quieres correr un microservicio desde IntelliJ/IDE.

```bash
# Solo las BDs, Kafka, Redis y LocalStack
docker compose up -d postgres-orders postgres-inventory mongodb mongo-init-replica redis kafka kafka-init localstack
```

Luego corre el microservicio desde el IDE. El perfil `local` se activa automáticamente y apunta a `localhost` con los puertos mapeados del `.env`.

---

## Opción 3: Un microservicio específico

```bash
# Levanta el servicio + todas sus dependencias
docker compose up --build -d ms-catalog

# Rebuildir y levantar después de cambios
docker compose up --build -d ms-inventory
```

---

## Opción 4: Fase 1 MVP completa

```bash
docker compose up --build -d ms-catalog ms-inventory ms-order ms-notifications
```

Esto levanta automáticamente toda la infraestructura necesaria (PostgreSQL, MongoDB, Redis, Kafka, LocalStack).

---

## Spring Profiles

| Perfil   | Activación                                 | Host de BD              | Puerto BD             |
| -------- | ------------------------------------------ | ----------------------- | --------------------- |
| `local`  | Automático desde IDE (default)             | `localhost`             | Puerto mapeado (.env) |
| `docker` | `SPRING_PROFILES_ACTIVE=docker` en compose | Hostname del contenedor | 5432 (interno)        |

---

## URLs Útiles

| Recurso              | URL                                     |
| -------------------- | --------------------------------------- |
| Kafka UI             | <http://localhost:8080>                 |
| LocalStack           | <http://localhost:4566>                 |
| ms-catalog Swagger   | <http://localhost:8084/swagger-ui.html> |
| ms-inventory Swagger | <http://localhost:8082/swagger-ui.html> |
| ms-catalog Health    | <http://localhost:8084/actuator/health> |
| ms-inventory Health  | <http://localhost:8082/actuator/health> |

---

## Troubleshooting

### MongoDB no inicia

MongoDB requiere que el replica set se inicialice. El servicio `mongo-init-replica` lo hace automáticamente. Si falla:

```bash
docker compose restart mongo-init-replica
```

### Kafka no crea tópicos

```bash
docker compose restart kafka-init
```

### Un microservicio no arranca

Verificar que sus dependencias estén healthy:

```bash
docker compose ps
docker compose logs <servicio>
```

### Rebuildir todo desde cero

```bash
docker compose down -v  # Elimina volúmenes (datos)
docker compose up --build -d
```

> **Advertencia:** `down -v` elimina todos los datos de las bases de datos.

---

## Scripts SQL

Los scripts de inicialización de PostgreSQL están en `postgresql-scripts/`:

- `init_orders.sql` — Schema de ms-order
- `init_inventory.sql` — Schema de ms-inventory
- `init_payment.sql` — Schema de ms-payment
- `init_reporter.sql` — Schema de ms-reporter
- `init_shipping.sql` — Schema de ms-shipping
- `init_provider.sql` — Schema de ms-provider

Se ejecutan automáticamente al crear el contenedor de PostgreSQL por primera vez.

## LocalStack (AWS Local)

Configurado en `localstack/`:

- `infra.yaml` — CloudFormation para recursos AWS
- `bootstrap.sh` — Script de inicialización

Servicios emulados: Secrets Manager, API Gateway, S3, SES, CloudFormation.
