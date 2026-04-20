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

### ms-catalog

- [ ] **Endpoint gRPC no implementado:** ms-catalog aún no expone el servicio gRPC que ms-cart consumirá en Fase 2 para obtener el precio actualizado antes del checkout. Solo tiene entry-points REST (WebFlux)

### ms-order

- [ ] **Endpoints REST no implementados:** El Saga orchestrator aún no tiene controllers REST (`POST /orders`, `GET /orders/{id}`, etc.). En desarrollo

### General

- [ ] **ms-notifications, ms-cart, ms-payment, ms-shipping, ms-provider:** Sin endpoints REST implementados (diferidos a sus fases respectivas)
