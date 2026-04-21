# Pending Improvements — Arka

> Registro de mejoras pendientes y deuda técnica identificada.

---

## Mejoras Pendientes

### Arquitectura / Patrones

- [ ] Considerar **Eventos de Aplicación** (Spring `ApplicationEvent`) para Outbox Pattern en lugar del Scheduler con polling
- [ ] Considerar **Cursor (keyset) Pagination** vs Offset Pagination para endpoints de alto volumen

### Seguridad

- [ ] Implementar **Spring Security** en endpoints para verificar roles (`CUSTOMER`, `ADMIN`) — la autenticación la maneja el API Gateway, falta la autorización local
- [ ] Configurar **Spring Security Headers** y **CORS** por microservicio
- [ ] Implementar **Rate Limiting** con Decorator AOP en los entry-points

### Framework

- [ ] Revisar error de `save()` (INSERT vs UPDATE) por problema con `@Id` no nulo — verificar que todos los servicios sigan la convención de UUID nullable (ver `06-patrones-y-estandares.md` §2 UUIDs)

---

## Deuda Técnica

### ms-inventory

- [ ] **Falta versionamiento de API:** El `StockController` usa `@RequestMapping("/inventory")` sin prefijo `/api/v1`. Debería ser `/api/v1/inventory` para consistencia con ms-catalog y el diseño del API Gateway
- [ ] **Falta validación en Handler:** El `StockHandler` no valida parámetros de entrada (ej. `sku` no vacío, `page`/`size` dentro de rango). Solo el `@Valid` en `UpdateStockRequest` está presente
- [ ] **`EventType` usa `.name()` en vez de `value()` con PascalCase:** El enum `EventType` de ms-inventory usa `enum.name()` directamente (SCREAMING_SNAKE_CASE: `STOCK_RESERVED`) para serializar el `eventType` en el sobre Kafka. El estándar del monorepo (definido por ms-order) es usar un campo `value()` con PascalCase (`"StockReserved"`) que desacopla el nombre Java del contrato público Kafka. Alinear con el patrón de ms-order.

### ms-catalog

- [ ] **`EventType` usa `.name()` en vez de `value()` con PascalCase:** Mismo problema que ms-inventory. El enum `EventType` de ms-catalog usa `PRODUCT_CREATED` en vez de un campo `value()` que retorne `"ProductCreated"`. Alinear con el patrón de ms-order.
- [ ] **Endpoint gRPC no implementado:** ms-catalog aún no expone el servicio gRPC `CatalogService` que ms-order (precio autoritativo en creación de orden) y ms-cart (precio actual antes del checkout) consumirán. Solo tiene entry-points REST (WebFlux). Planificado como Task 15 en el spec de ms-catalog.

### ms-order

- [ ] **Endpoints REST no implementados:** El Saga orchestrator aún no tiene controllers REST (`POST /orders`, `GET /orders/{id}`, etc.). En desarrollo
- [ ] **Tests de propiedades (jqwik) pendientes del MVP:** Tareas 2.3 (máquina de estados), 2.5 (invariante `totalAmount`), 4.2 (creación exitosa persiste todos los artefactos), 4.3 (stock insuficiente aborta sin persistir), 4.5 (control de acceso 403), 4.7 (listado paginado/filtrado/ordenado), 4.10 (historial de auditoría completo), 4.11 (eventos outbox con eventType/topic/payload correctos). Dependencia: `jqwik:1.9.2` ya en `testImplementation` de `main.gradle`
- [ ] **Circuit Breaker en clientes gRPC:** Agregar `reactor-resilience4j` en `GrpcInventoryClient` y `GrpcCatalogClient`. Patrón CLOSED → OPEN (tras N errores consecutivos) → HALF_OPEN (sondeo periódico). Fallback: propagar `InventoryServiceUnavailableException` / `CatalogServiceUnavailableException`

### General

- [ ] **ms-notifications, ms-cart, ms-payment, ms-shipping, ms-provider:** Sin endpoints REST implementados (diferidos a sus fases respectivas)
