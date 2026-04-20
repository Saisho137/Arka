# ms catalog

## Notas de implementación

### MongoDB Replica Set (requerido para transacciones)

`ProductUseCase` y `CategoryUseCase` usan `TransactionalGateway` para envolver el guardado del producto/categoría y su `OutboxEvent` en una **transacción atómica** (Outbox Pattern). MongoDB solo soporta transacciones multi-documento en Replica Set o Sharded Cluster.

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
