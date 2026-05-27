---
sidebar_position: 14
title: Mejoras Pendientes
---

# Mejoras Pendientes — Arka

> Registro de gaps, deuda técnica y tareas de alineamiento previas a la implementación de ms-provider (Fase 4).

---

## Resumen de Gaps a Alto Nivel

| # | Servicio | Gap | Severidad | Impacto |
|---|----------|-----|-----------|---------|
| 1 | ms-inventory | `EventType` usa `toCamelCase()` runtime en vez de `value()` explícito | Baja | Funciona pero frágil: depende de naming convention del enum |
| 2 | ms-catalog | `EventType` usa `toCamelCase()` runtime en vez de `value()` explícito | Baja | Mismo problema que ms-inventory |
| 3 | ms-shipping | `EventType` usa `toCamelCase()` runtime en vez de `value()` explícito | Baja | Mismo problema |
| 4 | ms-catalog | Endpoint gRPC `CatalogService.GetProductInfo` no implementado | **Alta** | ms-cart no puede validar precios en checkout (bloquea flujo gRPC) |
| 5 | ms-order | `processShippingDispatched()` no implementado (Task 14) | Media | Envíos no transicionan orden a `EN_DESPACHO` automáticamente |
| 6 | ms-order | Circuit Breaker ausente en clientes gRPC | Media | Fallo de ms-inventory/ms-catalog cascadea a ms-order |
| 7 | ms-inventory | API sin versionamiento (`/inventory` en vez de `/api/v1/inventory`) | Baja | Inconsistencia con ms-catalog y API Gateway |
| 8 | ms-inventory | Falta validación en `StockHandler` (sku no vacío, page/size rango) | Baja | Parámetros inválidos llegan al UseCase |
| 9 | ms-payment | Implementación mock (Random 80/20) sin BD, Outbox, ni ACL real | **Alta** | No cumple spec — requiere reimplementación completa |
| 10 | ms-provider | Solo scaffold vacío — dominio no implementado | **Alta** | Fase 4 bloqueada |
| 11 | docs/04-api-endpoints.md | Desactualizado: lista ms-cart, ms-shipping, ms-reporter como "sin REST" | Baja | Documentación engañosa |
| 12 | docs/10-urls-puertos-globales.md | Swagger UI solo lista ms-catalog y ms-inventory | Baja | Faltan ms-order, ms-cart, ms-shipping, ms-reporter |
| 13 | General | Spring Security no implementado — solo headers manuales `X-User-Role` | Media | Autorización sin enforcement real |
| 14 | General | Tests property-based (jqwik) pendientes en todos los servicios | Baja | Cobertura de edge-cases insuficiente |
| 15 | ms-reporter | `reporter-events` no registrado en docs/03-kafka-eventos.md | Baja | Documentación incompleta |

---

## Tareas de Alineamiento Pre-ms-provider

> Implementar ANTES de ms-provider para garantizar consistencia del ecosistema.

### Bloque A — Consistencia EventType (alinear con patrón ms-order)

El patrón canónico es el de ms-order: enum con campo `value()` explícito que desacopla el nombre Java del contrato público Kafka. Los otros servicios usan `toCamelCase()` en runtime que **funciona correctamente** (produce PascalCase) pero es más frágil y menos explícito.

- [ ] A.1 **ms-inventory**: Migrar `EventType` enum a patrón `value()` explícito
  - Cambiar enum de `STOCK_RESERVED` a `STOCK_RESERVED("StockReserved")` con campo `private final String value` y método `value()`
  - Actualizar `KafkaOutboxRelay.toCamelCase()` → usar directamente `event.eventType().value()`
  - Eliminar método `toCamelCase()` (dead code)
  - Verificar que los consumers downstream (ms-reporter, ms-notifications) siguen recibiendo `"StockReserved"` sin cambios

- [ ] A.2 **ms-catalog**: Migrar `EventType` enum a patrón `value()` explícito
  - Mismo cambio: `PRODUCT_CREATED("ProductCreated")`, `PRODUCT_UPDATED("ProductUpdated")`, `PRICE_CHANGED("PriceChanged")`
  - Actualizar `KafkaOutboxRelay` para usar `.value()` directo
  - Eliminar `toCamelCase()`

- [ ] A.3 **ms-shipping**: Migrar `EventType` enum a patrón `value()` explícito
  - Cambiar `SHIPPING_DISPATCHED` a `SHIPPING_DISPATCHED("ShippingDispatched")`
  - Actualizar `KafkaOutboxRelay`

- [ ] A.4 **ms-payment**: Migrar de String literal a enum `EventType` con `value()`
  - Crear enum `EventType` con `PAYMENT_PROCESSED("PaymentProcessed")`, `PAYMENT_FAILED("PaymentFailed")`
  - Refactorizar `DomainEventEnvelope` para aceptar `EventType` en vez de String
  - Actualizar publisher para usar `.value()`
  - **NOTA**: ms-payment está pendiente de reimplementación completa (Gap #9). Hacer este cambio solo si se decide alinear el mock actual antes de la reimplementación.

### Bloque B — Completar Saga Secuencial en ms-order

- [ ] B.1 **Implementar `processShippingDispatched()`** en `OrderUseCase`
  - Consumir evento `ShippingDispatched` del tópico `shipping-events`
  - Transicionar orden de `CONFIRMADO` → `EN_DESPACHO`
  - Registrar en `order_state_history`
  - Publicar `OrderStatusChanged` vía Outbox
  - Validar idempotencia (processed_events)
  - Corresponde a Task 14 del spec de ms-order

- [ ] B.2 **Agregar Circuit Breaker en `GrpcInventoryClient`**
  - `reactor-resilience4j` ya como dependencia
  - CircuitBreaker: failureRateThreshold=50%, waitDurationInOpenState=30s, permittedNumberOfCallsInHalfOpenState=3
  - Fallback: propagar `InventoryServiceUnavailableException` (ya existe en dominio)

- [ ] B.3 **Agregar Circuit Breaker en `GrpcCatalogClient`**
  - Misma configuración que B.2
  - Fallback: propagar `CatalogServiceUnavailableException`

### Bloque C — Implementar gRPC en ms-catalog

- [ ] C.1 **Implementar servidor gRPC `CatalogService.GetProductInfo`**
  - Definir `.proto` con request `GetProductInfoRequest { string sku }` y response `GetProductInfoResponse { string sku, string name, double price, bool active }`
  - Generar entry-point gRPC con Scaffold: `./gradlew generateEntryPoint --type=grpc`
  - Implementar `GrpcCatalogService` delegando a un UseCase existente
  - Puerto gRPC: 9091 (local) / 9090 (docker) — ya configurado en compose.yaml
  - Corresponde a Task 15 del spec de ms-catalog
  - **BLOQUEA**: ms-cart checkout (validación de precios gRPC)

### Bloque D — Mejoras ms-inventory

- [ ] D.1 **Agregar versionamiento de API**: cambiar `@RequestMapping("/inventory")` a `@RequestMapping("/api/v1/inventory")`
  - Actualizar tests y documentación
  - Actualizar ms-order si referencia endpoints directos (no aplica — usa gRPC)

- [ ] D.2 **Agregar validación en `StockHandler`**: validar `sku` no vacío, `page >= 0`, `size` entre 1 y 100
  - Usar `@Validated` o validación manual con excepciones de dominio

### Bloque E — Actualizar Documentación Global

- [ ] E.1 **Actualizar `docs/04-api-endpoints.md`**: agregar endpoints REST de ms-cart, ms-shipping, ms-reporter, ms-order (completo)
  - Eliminar la sección "Servicios sin endpoints REST implementados" que ya no aplica para ms-cart, ms-shipping y ms-reporter

- [ ] E.2 **Actualizar `docs/10-urls-puertos-globales.md`**: agregar Swagger UI de ms-order (8081), ms-cart (8086), ms-shipping (8088), ms-reporter (8087)

- [ ] E.3 **Actualizar `docs/03-kafka-eventos.md`**: agregar tópico `reporter-events` con evento `StockAlertGenerated` (partition key = SKU, productor: ms-reporter)

- [ ] E.4 **Actualizar `docs/07-flujos-criticos.md`**: reflejar que `processPaymentProcessed` ya está implementado en ms-order y que el flujo Fase 2 funciona con ms-payment mock

### Bloque F — Reimplementar ms-payment (ACL completo)

> **Estado actual**: ms-payment es un mock con `Random.nextDouble()` (80% éxito / 20% fallo) sin BD, sin Outbox, sin idempotencia, sin REST endpoints. El spec completo ya existe en `.kiro/specs/ms-payment/`.

- [ ] F.1 Reimplementar ms-payment según spec: PostgreSQL (R2DBC), Outbox Pattern, ACL con Strategy+Factory para Stripe/Wompi/MercadoPago, Circuit Breaker, idempotencia, REST admin endpoints
  - El spec completo está en `.kiro/specs/ms-payment/tasks.md`
  - **IMPORTANTE**: mantener retrocompatibilidad — ms-order ya consume `PaymentProcessed`/`PaymentFailed` del tópico `payment-events`

---

## Deuda Técnica Menor (No Bloquea)

### Arquitectura / Patrones

- [ ] Considerar **Eventos de Aplicación** (Spring `ApplicationEvent`) para Outbox Pattern en lugar del Scheduler con polling
- [ ] Considerar **Cursor (keyset) Pagination** vs Offset Pagination para endpoints de alto volumen

### Seguridad

- [ ] Implementar **Spring Security** con filtro que valide `X-User-Role` header — la autenticación la maneja el API Gateway, falta enforcement local de roles
- [ ] Implementar **Rate Limiting** con Decorator AOP en los entry-points

### Tests

- [ ] Tests property-based (jqwik) pendientes en ms-order, ms-inventory, ms-reporter (specs ya definen las propiedades)
- [ ] Tests de integración con Testcontainers para adaptadores JDBC/R2DBC

---

## Prioridad de Ejecución Sugerida

1. **Bloque A** (EventType consistency) — rápido, bajo riesgo, habilita ms-provider
2. **Bloque C** (gRPC ms-catalog) — desbloquea ms-cart checkout real
3. **Bloque B** (Saga ms-order) — completa flujo end-to-end
4. **Bloque E** (docs) — sincroniza documentación con realidad
5. **Bloque D** (ms-inventory) — mejoras menores
6. **Bloque F** (ms-payment) — reimplementación completa (mayor esfuerzo)
7. **ms-provider** (Fase 4) — implementar tras Bloque A mínimo
