# Documentación — Arka Backend

## Documentos Principales (ordenados)

| #   | Documento                                                        | Contenido                                                                    |
| --- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| 01  | [01-arquitectura.md](01-arquitectura.md)                         | Stack, paradigma híbrido, Clean Architecture, seguridad, persistencia, fases |
| 02  | [02-microservicios.md](02-microservicios.md)                     | Detalle de cada microservicio, responsabilidades, eventos, estados           |
| 03  | [03-kafka-eventos.md](03-kafka-eventos.md)                       | Tópicos, eventos, consumer groups, envelope, Outbox, idempotencia            |
| 04  | [04-api-endpoints.md](04-api-endpoints.md)                       | Endpoints REST implementados y planificados                                  |
| 05  | [05-levantar-sistema.md](05-levantar-sistema.md)                 | Docker Compose, infraestructura, profiles, troubleshooting                   |
| 06  | [06-patrones-y-estandares.md](06-patrones-y-estandares.md)       | Referencia rápida de convenciones de código                                  |
| 07  | [07-flujos-criticos.md](07-flujos-criticos.md)                   | Flujos de creación de pedido, compensación, registro de producto             |
| 08  | [08-patrones-arquitectonicos.md](08-patrones-arquitectonicos.md) | Saga, Outbox, CQRS, Cache-Aside, ACL, Circuit Breaker                        |
| 09  | [09-contexto-negocio.md](09-contexto-negocio.md)                 | Qué es Arka, problemas, actores, HUs, decisiones estratégicas                |
| 10  | [10-puertos-e-interfaces.md](10-puertos-e-interfaces.md)         | Puertos, Swagger UIs, BDs, Redis, Kafka, health checks, conexiones           |

## Referencia Detallada

| Documento                                                          | Contenido                                                          |
| ------------------------------------------------------------------ | ------------------------------------------------------------------ |
| [patrones-y-estandares-codigo.md](patrones-y-estandares-codigo.md) | Documento normativo completo con ejemplos de código (2700+ líneas) |
| [caso-kafka-reactivo.md](caso-kafka-reactivo.md)                   | Estado de reactor-kafka en Spring Boot 4.x y decisiones tomadas    |

## Otros

| Carpeta                            | Contenido                                           |
| ---------------------------------- | --------------------------------------------------- |
| [diagramas/](diagramas/)           | Diagramas C4 y de arquitectura                      |
| [tarea-original/](tarea-original/) | Documentos originales de la tarea/reto del proyecto |
