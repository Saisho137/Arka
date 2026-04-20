# 01 — Arquitectura del Sistema

## Visión General

Arka es una plataforma e-commerce B2B para distribución de accesorios de PC en Colombia/LATAM. Arquitectura de **9 microservicios** con Clean Architecture, comunicación híbrida (gRPC síncrono + Kafka asíncrono) y modelo políglota de persistencia.

## Stack Tecnológico

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 21 |
| Framework | Spring Boot 4.0.3 (WebFlux + MVC) |
| Reactivo | Project Reactor, R2DBC, Reactive Mongo |
| Mensajería | Apache Kafka 8 (KRaft, sin ZooKeeper) |
| BD Relacional | PostgreSQL 17 |
| BD Documental | MongoDB 7 |
| Caché | Redis 7 (Cache-Aside) |
| Comunicación Síncrona | gRPC (Protobuf) |
| Cloud | AWS (API Gateway, S3, SES, Secrets Manager) |
| Build | Gradle + Bancolombia Scaffold Plugin 4.2.0 |
| Anotaciones | Lombok 1.18.42 |

## Paradigma Híbrido

| Paradigma | Servicios | Justificación |
|---|---|---|
| **Reactivo (WebFlux)** | ms-catalog, ms-inventory, ms-order, ms-cart, ms-notifications, ms-payment, ms-shipping, ms-provider | I/O-Bound, drivers no bloqueantes. SDKs bloqueantes con `Schedulers.boundedElastic()` |
| **Imperativo (MVC + Virtual Threads)** | ms-reporter | CPU-Bound: archivos 500MB+ en S3. `reactive=false` en gradle.properties |

## Clean Architecture — Capas Gradle

```
applications/app-service/    → Spring Boot main, DI
domain/model/                → Entities, Value Objects, Ports/Interfaces
domain/usecase/              → Business logic, orchestration
infrastructure/
  driven-adapters/           → Repos, clients externos (R2DBC, REST, Kafka producer)
  entry-points/              → Controllers (WebFlux), Kafka consumers, gRPC servers
  helpers/                   → Utilidades compartidas
```

Paquete base: `com.arka`

## Seguridad — Zero Trust

- **API Gateway** es el único punto expuesto a internet
- JWT validado contra Microsoft Entra ID / AWS Cognito
- Tenant Restrictions bloquea dominios públicos (`@gmail.com`) para mantener enfoque B2B
- Header `X-User-Email` inyectado hacia la VPC privada
- RBAC: 2 roles — `CUSTOMER` (cliente B2B), `ADMIN` (personal interno)
- Rate Limiting: 100 req/s por IP
- Microservicios 100% stateless
- BFF descartado permanentemente

## Comunicación Interna

| Tipo | Protocolo | Uso |
|---|---|---|
| **Síncrona** | gRPC | Validaciones críticas en tiempo real (reserva de stock, precio actual) |
| **Asíncrona** | Kafka | Flujo transaccional, Saga Pattern, Event Sourcing, desacoplamiento temporal |

### Llamadas gRPC

- `ms-order` → `ms-inventory`: Reserva de stock inmediata (Fase 1 Saga)
- `ms-cart` → `ms-catalog`: Precio actualizado antes del checkout (Fase 2)

## Persistencia Políglota (Database per Service)

| Motor | Servicios | Driver | Justificación |
|---|---|---|---|
| **MongoDB + Redis** | ms-catalog | Reactivo | Cache-Aside (<1ms), documentos polimórficos con reseñas anidadas |
| **MongoDB** | ms-cart, ms-notifications | Reactivo | Mutaciones atómicas (`$push/$pull`), plantillas JSON flexibles |
| **PostgreSQL 17** | ms-inventory, ms-order | R2DBC | ACID estricto, lock pesimista, Outbox Pattern |
| **PostgreSQL 17** | ms-payment, ms-shipping, ms-provider | R2DBC | Integridad financiera, constraints, idempotencia |
| **PostgreSQL 17 + S3** | ms-reporter | JDBC | JSONB + GIN para Event Sourcing, reportes pesados en S3 |

## Fases de Entrega

| Fase | Nombre | Microservicios | Valor |
|---|---|---|---|
| **1** | MVP — Núcleo Transaccional | ms-catalog, ms-inventory, ms-order, ms-notifications | Erradica sobreventa con gRPC + lock pesimista |
| **2** | Autogestión B2B | ms-cart, ms-payment | Saga completa con pago, carritos abandonados |
| **3** | Analítica y Logística | ms-reporter, ms-shipping | CQRS/Event Sourcing, ACL logística |
| **4** | Abastecimiento | ms-provider | Órdenes de compra automáticas a proveedores |

## Diagramas

Ver [docs/diagramas/](diagramas/) para los diagramas C1, C2 y de arquitectura del sistema.
