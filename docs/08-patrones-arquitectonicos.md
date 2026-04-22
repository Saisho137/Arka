---
sidebar_position: 4
title: Patrones Arquitectónicos
---

# Patrones Arquitectónicos

## Resumen de Patrones Implementados

| Patrón                          | Servicios                            | Propósito                                                     |
| ------------------------------- | ------------------------------------ | ------------------------------------------------------------- |
| **Saga Secuencial**             | ms-order (orquestador)               | Flujo transaccional distribuido: Catálogo → Inventario → Pago |
| **Transactional Outbox**        | ms-inventory, ms-order, ms-catalog   | Atomicidad entre escritura BD y publicación Kafka             |
| **Idempotencia en Consumers**   | Todos los consumers                  | Prevenir procesamiento duplicado (at-least-once)              |
| **Database per Service**        | Todos                                | Aislamiento de datos, escalado independiente                  |
| **Cache-Aside**                 | ms-catalog (Redis)                   | Lecturas <1ms, 95% cache hit, invalidación por eventos        |
| **CQRS + Event Sourcing**       | ms-reporter                          | Read model analítico separado del core transaccional          |
| **Anti-Corruption Layer (ACL)** | ms-payment, ms-shipping, ms-provider | Aislar el dominio de APIs y SDKs externos                     |
| **Circuit Breaker + Bulkhead**  | ms-payment, ms-shipping              | Resiliencia ante fallos de servicios externos (Resilience4j)  |
| **Zero Trust**                  | API Gateway                          | JWT + Entra ID, Tenant Restrictions, `X-User-Email`           |

## Circuit Breaker (Resilience4j)

- Umbral de fallo: 50%
- Estado Open: 30 segundos
- Ventana: últimos 10 requests
- Reintentos: 3 con backoff exponencial (2s, 4s, 8s)

## Cache-Aside (Redis)

- **HIT (95%):** Redis <1ms
- **MISS (5%):** MongoDB ~10ms → guarda en Redis con TTL 1h
- **Invalidación:** Crear/actualizar producto → elimina key + evento Kafka

---

## Decisiones Arquitectónicas Consolidadas

| #   | Decisión                                     | Justificación                                                                                    | Trade-off                                               |
| --- | -------------------------------------------- | ------------------------------------------------------------------------------------------------ | ------------------------------------------------------- |
| 1   | 9 microservicios en 4 fases                  | Entrega incremental de valor. MVP con 4 servicios                                                | Complejidad operacional creciente por fase              |
| 2   | WebFlux vs Virtual Threads (híbrido)         | WebFlux para I/O-bound, Loom para CPU-bound/SDKs legacy                                          | Dos paradigmas coexistiendo                             |
| 3   | Eliminación permanente del BFF               | API Gateway asume seguridad y enrutamiento                                                       | Respuestas no optimizadas por plataforma                |
| 4   | MongoDB para ms-catalog                      | Documentos polimórficos para reseñas anidadas + Cache-Aside con Redis                            | Sin JOINs; consistencia eventual en catálogo            |
| 5   | MongoDB para ms-notifications                | Esquema flexible para plantillas JSON + TTL Index nativo                                         | No requiere ACID para notificaciones                    |
| 6   | gRPC para comunicación síncrona interna      | Serialización ultrarrápida (Protobuf) para reserva de stock y consulta de precio en milisegundos | Mayor complejidad de contratos vs REST                  |
| 7   | Separación Catálogo e Inventario             | Bounded Contexts distintos (DDD). Catálogo = lecturas masivas. Inventario = ACID                 | Dos servicios donde uno podría bastar en negocio simple |
| 8   | Reseñas como subdocumentos en ms-catalog     | Elimina un microservicio completo. Aprovecha modelo documental de MongoDB                        | Límite de 16MB por documento MongoDB                    |
| 9   | Zero Trust en API Gateway                    | Entra ID/Cognito valida tokens. Tenant Restrictions bloquea `@gmail.com`                         | Dependencia de IdP externo                              |
| 10  | Outbox con polling (no Debezium)             | Simplicidad, sin dependencias extra                                                              | Latencia adicional máxima de 5s por ciclo de polling    |
| 11  | Kafka como único broker (no SQS/EventBridge) | Un solo broker simplifica la operación                                                           | Sin scheduling nativo; compensado con jobs periódicos   |
| 12  | Saga simplificada en Fase 1 (gRPC + Kafka)   | gRPC sync para precio (ms-catalog) + stock (ms-inventory) + Kafka async para notificaciones      | Pago B2B offline; pasarelas diferidas a Fase 2          |
| 13  | Un tópico Kafka por servicio (no por evento) | 7 tópicos vs 13+. Orden causal por partición. Nuevo evento = nuevo `eventType`                   | Consumidores deben filtrar por `eventType`              |

---

## Métricas de Éxito

| Métrica                 | Objetivo    | Medición                                                                              |
| ----------------------- | ----------- | ------------------------------------------------------------------------------------- |
| Tasa de Sobreventa      | **0%**      | Órdenes confirmadas con stock negativo. Asegurado por `SELECT FOR UPDATE` y gRPC      |
| Protección Financiera   | **0%**      | Mercancía despachada sin confirmación de pago (Saga Secuencial)                       |
| Disponibilidad          | 99.5%       | Uptime del API Gateway                                                                |
| Latencia API (p95)      | <1s         | Tiempo de respuesta en API Gateway                                                    |
| Latencia Catálogo (p95) | <1ms        | Tiempo desde Redis (Cache-Aside)                                                      |
| Notificaciones enviadas | >95%        | Emails enviados vs cambios de estado ocurridos                                        |
| Rendimiento Analítico   | Sin impacto | Latencia del Core Transaccional inalterada durante generación de reportes OLAP (CQRS) |
