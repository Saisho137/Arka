# Documentación — Arka Backend

## Documentos Principales

> Ordenados por flujo de lectura recomendado — del contexto de negocio al detalle técnico.

| #   | Documento                                                        | Contenido                                                                    |
| --- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 01  | [09-contexto-negocio.md](09-contexto-negocio.md)                 | Qué es Arka, problemas, actores, HUs, decisiones estratégicas                |
| 02  | [01-arquitectura.md](01-arquitectura.md)                         | Stack, paradigma híbrido, Clean Architecture, seguridad, persistencia, fases |
| 03  | [02-microservicios.md](02-microservicios.md)                     | Detalle de cada microservicio, responsabilidades, eventos, estados           |
| 04  | [08-patrones-arquitectonicos.md](08-patrones-arquitectonicos.md) | Saga, Outbox, CQRS, Cache-Aside, ACL, Circuit Breaker                        |
| 05  | [03-kafka-eventos.md](03-kafka-eventos.md)                       | Tópicos, eventos, consumer groups, envelope, Outbox, idempotencia            |
| 06  | [07-flujos-criticos.md](07-flujos-criticos.md)                   | Flujos de creación de pedido, compensación, registro de producto             |
| 07  | [04-api-endpoints.md](04-api-endpoints.md)                       | Endpoints REST implementados y planificados                                  |
| 08  | [06-patrones-y-estandares.md](06-patrones-y-estandares.md)       | Documento normativo de convenciones de código y patrones                     |
| 09  | [05-levantar-sistema.md](05-levantar-sistema.md)                 | Docker Compose, infraestructura, profiles, troubleshooting                   |
| 10  | [10-urls-puertos-globales.md](10-urls-puertos-globales.md)       | Puertos, Swagger UIs, BDs, Redis, Kafka, health checks, conexiones           |
| 11  | [11-errores-comunes-configuracion.md](11-errores-comunes-configuracion.md) | Errores de compilación y configuración frecuentes               |
| 12  | [12-coleccion-curls.md](12-coleccion-curls.md)                   | Colección completa de cURLs para demo y presentación                         |

## Referencia

| Documento                                          | Contenido                                                       |
| -------------------------------------------------- | --------------------------------------------------------------- |
| [caso-kafka-reactivo.md](caso-kafka-reactivo.md)   | Estado de reactor-kafka en Spring Boot 4.x y decisiones tomadas |
| [pending-improvements.md](pending-improvements.md) | Mejoras pendientes y deuda técnica identificada                 |

## Otros

| Carpeta                            | Contenido                                           |
| ---------------------------------- | --------------------------------------------------- |
| [diagramas/](diagramas/)           | Diagramas C4 y de arquitectura                      |
| [tarea-original/](tarea-original/) | Documentos originales de la tarea/reto del proyecto |
