# 08 — Patrones Arquitectónicos

## Resumen de Patrones Implementados

| Patrón | Servicios | Propósito |
|---|---|---|
| **Saga Secuencial** | ms-order (orquestador) | Flujo transaccional distribuido: Catálogo → Inventario → Pago |
| **Transactional Outbox** | ms-inventory, ms-order, ms-catalog | Atomicidad entre escritura BD y publicación Kafka |
| **Idempotencia en Consumers** | Todos los consumers | Prevenir procesamiento duplicado (at-least-once) |
| **Database per Service** | Todos | Aislamiento de datos, escalado independiente |
| **Cache-Aside** | ms-catalog (Redis) | Lecturas <1ms, 95% cache hit, invalidación por eventos |
| **CQRS + Event Sourcing** | ms-reporter | Read model analítico separado del core transaccional |
| **Anti-Corruption Layer (ACL)** | ms-payment, ms-shipping, ms-provider | Aislar el dominio de APIs y SDKs externos |
| **Circuit Breaker + Bulkhead** | ms-payment, ms-shipping | Resiliencia ante fallos de servicios externos (Resilience4j) |
| **Zero Trust** | API Gateway | JWT + Entra ID, Tenant Restrictions, `X-User-Email` |

## Circuit Breaker (Resilience4j)

- Umbral de fallo: 50%
- Estado Open: 30 segundos
- Ventana: últimos 10 requests
- Reintentos: 3 con backoff exponencial (2s, 4s, 8s)

## Cache-Aside (Redis)

- **HIT (95%):** Redis <1ms
- **MISS (5%):** MongoDB ~10ms → guarda en Redis con TTL 1h
- **Invalidación:** Crear/actualizar producto → elimina key + evento Kafka
