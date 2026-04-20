// Initializes MongoDB as a single-node replica set (rs0).
// Required for multi-document transactions used by the Outbox Pattern.
//
// IMPORTANT: This script is NOT mounted in docker-entrypoint-initdb.d because
// those scripts run on a temporary mongod WITHOUT --replSet, so rs.initiate()
// would fail. Instead, it is executed by the `mongo-init-replica` service
// AFTER the real mongod (with --replSet rs0) is healthy.
//
// Host must be "mongodb:27017" (Docker service name), NOT "localhost:27017",
// so that other containers on the Docker network can resolve the replica set member.
//
// Idempotent: try/catch handles re-runs when the volume already has an
// initialized replica set (e.g. docker compose down && docker compose up).
try {
  rs.status();
  print("Replica set already initialized — skipping rs.initiate()");
} catch (e) {
  rs.initiate({
    _id: "rs0",
    members: [{ _id: 0, host: "mongodb:27017" }],
  });
  print("Replica set rs0 initialized successfully");
}
