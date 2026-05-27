# Requirements Document

## Introduction

El microservicio `ms-provider` es el dueño del Bounded Context **Proveedores B2B (Abastecimiento)** dentro de la plataforma Arka. Su responsabilidad principal es actuar como Anti-Corruption Layer (ACL) para integrar múltiples proveedores externos de accesorios de PC, automatizar la generación de órdenes de compra cuando el stock cae por debajo de umbrales definidos, y gestionar el ciclo de vida completo del proceso de reabastecimiento. Este servicio es un componente clave de la Fase 4 del proyecto. Consume eventos `StockDepleted` del tópico `inventory-events` (producidos por ms-inventory cuando el stock de un SKU cae bajo su umbral), genera órdenes de compra al proveedor asignado, las envía por email vía ms-notifications, y publica eventos `PurchaseOrderCreated` al tópico `provider-events` mediante el Transactional Outbox Pattern. El ciclo se cierra cuando el administrador recibe la mercancía y actualiza el stock manualmente vía `PUT /api/v1/inventory/{sku}/stock` en ms-inventory.

## Glossary

- **Proveedor**: Empresa externa que suministra accesorios de PC a Arka. Tiene un registro en la tabla `suppliers` con información de contacto, email, productos que suministra, lead time y condiciones comerciales
- **Orden_De_Compra**: Documento generado automáticamente que solicita reabastecimiento de uno o más SKUs a un proveedor específico. Almacenado en la tabla `purchase_orders` con estado, montos, fechas y referencia al proveedor
- **Item_Orden_Compra**: Línea de detalle dentro de una orden de compra, con SKU, cantidad solicitada, precio unitario negociado y subtotal. Almacenado en la tabla `purchase_order_items`
- **Supplier_Product**: Relación many-to-many entre proveedores y SKUs que suministran, con precio unitario negociado, lead time específico, SKU del proveedor y flag de proveedor preferido
- **Purchase_Order_Status**: Enumeración de estados de la orden de compra: PENDING, SENT, CONFIRMED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
- **StockDepleted**: Evento emitido por ms-inventory cuando el stock disponible de un SKU cae por debajo del umbral configurado (threshold). Contiene sku, currentStock, threshold y productName
- **PurchaseOrderCreated**: Evento emitido por ms-provider al tópico `provider-events` cuando se genera una nueva orden de compra. Partition key = purchaseOrderId
- **Reorder_Point**: Cantidad mínima de stock que dispara la generación automática de una orden de compra. Definido por el umbral (threshold) en ms-inventory
- **Reorder_Quantity**: Cantidad a solicitar al proveedor, calculada como (threshold × reorderMultiplier) - currentStock. El multiplicador es configurable por proveedor-producto
- **Lead_Time**: Tiempo estimado en días que tarda el proveedor en entregar la mercancía desde que recibe la orden de compra
- **ACL**: Anti-Corruption Layer — patrón arquitectónico que aísla el dominio de las particularidades de las APIs y formatos de comunicación de los proveedores externos
- **Outbox_Events**: Tabla PostgreSQL donde se insertan eventos de dominio atómicamente dentro de la misma transacción que la escritura de negocio
- **Processed_Events**: Tabla PostgreSQL que almacena el event_id de cada evento consumido para garantizar idempotencia
- **Domain_Event_Envelope**: Sobre estándar del evento Kafka con eventId, eventType, timestamp, source, correlationId y payload
- **Administrador**: Personal interno de Arka con rol ADMIN que gestiona proveedores, aprueba órdenes de compra y registra recepción de mercancía
- **Email_Notification**: Correo electrónico enviado al proveedor con el detalle de la orden de compra. Se delega a ms-notifications publicando al tópico adecuado o invocando su API

## Requirements

### Requirement 1: Consume StockDepleted event to initiate purchase order generation

**User Story:** As ms-provider, I want to consume StockDepleted events published by ms-inventory to automatically generate purchase orders when stock falls below configured thresholds.

#### Acceptance Criteria

1. THE ms-provider SHALL implementar un consumer de Kafka suscrito al consumer group `provider-service-group` que procese activamente eventos del tópico `inventory-events`
2. WHEN ms-provider recibe un evento con eventType igual a `StockDepleted`, THE ms-provider SHALL extraer del payload los campos `sku`, `currentStock`, `threshold` y `productName`
3. WHEN ms-provider recibe un evento `StockDepleted`, THE ms-provider SHALL verificar en la tabla `processed_events` si el `eventId` ya fue procesado antes de ejecutar la lógica de negocio (idempotencia)
4. WHEN el eventId ya existe en `processed_events`, THE ms-provider SHALL descartar el evento sin ejecutar lógica de negocio y registrar un log de nivel DEBUG
5. WHEN ms-provider recibe un evento con eventType desconocido (ej. `StockReserved`, `StockReleased`), THE ms-provider SHALL ignorar el evento y registrar un log de nivel WARN (tolerancia a evolución del esquema)
6. WHEN el evento `StockDepleted` se procesa exitosamente, THE ms-provider SHALL insertar el `eventId` en la tabla `processed_events` dentro de la misma transacción que la operación de negocio
7. THE ms-provider SHALL diseñar el consumer con manejo de errores mediante `onErrorResume()` para que un evento fallido no detenga el procesamiento de eventos subsiguientes
8. WHEN ms-provider recibe un `StockDepleted` y ya existe una orden de compra en estado PENDING o SENT para el mismo SKU, THE ms-provider SHALL ignorar el evento (evitar órdenes duplicadas) y registrar un log INFO

### Requirement 2: Generate purchase order with preferred supplier

**User Story:** Como ms-provider, quiero generar órdenes de compra automáticamente con el proveedor preferido para el SKU afectado, calculando la cantidad óptima de reabastecimiento.

#### Acceptance Criteria

1. WHEN ms-provider procesa un evento `StockDepleted` para un SKU, THE ms-provider SHALL buscar en la tabla `supplier_products` el proveedor con flag `preferred = true` para ese SKU
2. WHEN no existe proveedor preferido para el SKU, THE ms-provider SHALL buscar cualquier proveedor activo que suministre el SKU, seleccionando el de menor lead_time
3. WHEN no existe ningún proveedor activo que suministre el SKU, THE ms-provider SHALL registrar un log ERROR con el SKU sin proveedor y NO crear orden de compra
4. THE ms-provider SHALL calcular la `reorder_quantity` como: `(threshold × reorderMultiplier) - currentStock`, donde `reorderMultiplier` se obtiene de `supplier_products` (default: 2)
5. THE ms-provider SHALL crear una `PurchaseOrder` con status `PENDING`, un `PurchaseOrderItem` con el SKU y cantidad calculada, el precio unitario del `supplier_products`, y total = quantity × unitPrice
6. THE ms-provider SHALL insertar la `PurchaseOrder` en la tabla `purchase_orders`, los items en `purchase_order_items`, y un evento `PurchaseOrderCreated` en la tabla `outbox_events`, todo dentro de la misma transacción R2DBC
7. THE ms-provider SHALL usar `purchaseOrderId` como partition key del evento en `outbox_events` para garantizar orden causal por orden de compra

### Requirement 3: Publish PurchaseOrderCreated event via Outbox Pattern

**User Story:** Como plataforma Arka, quiero que ms-provider publique eventos PurchaseOrderCreated al tópico `provider-events` de forma confiable mediante el Transactional Outbox Pattern.

#### Acceptance Criteria

1. THE ms-provider SHALL implementar un Outbox Relay que consulte la tabla `outbox_events` cada 5 segundos buscando registros con status `PENDING`
2. WHEN el Relay encuentra eventos pendientes, THE ms-provider SHALL publicarlos al tópico `provider-events` de Kafka con el partition key almacenado
3. WHEN el evento se publica exitosamente (ack de Kafka), THE ms-provider SHALL actualizar el status del registro en `outbox_events` a `PUBLISHED`
4. THE ms-provider SHALL emitir el evento con el Domain Event Envelope estándar: `{ eventId, eventType: "PurchaseOrderCreated", timestamp, source: "ms-provider", correlationId, payload }`
5. THE payload del evento `PurchaseOrderCreated` SHALL contener: `purchaseOrderId`, `supplierId`, `supplierName`, `supplierEmail`, `sku`, `productName`, `quantity`, `unitPrice`, `totalAmount`, `estimatedDeliveryDate`

### Requirement 4: Manage suppliers via REST endpoints (ADMIN only)

**User Story:** Como Administrador, quiero gestionar el catálogo de proveedores y sus productos asignados para mantener actualizada la información de reabastecimiento.

#### Acceptance Criteria

1. THE ms-provider SHALL exponer `POST /api/v1/suppliers` para registrar un nuevo proveedor con campos: name, email, phone, address, country, active (default: true). Requiere header `X-User-Role: ADMIN`
2. THE ms-provider SHALL exponer `GET /api/v1/suppliers` para listar todos los proveedores activos con paginación (page, size). Requiere header `X-User-Role: ADMIN`
3. THE ms-provider SHALL exponer `GET /api/v1/suppliers/{supplierId}` para consultar un proveedor por ID con sus productos asignados. Requiere header `X-User-Role: ADMIN`
4. THE ms-provider SHALL exponer `PUT /api/v1/suppliers/{supplierId}` para actualizar información del proveedor. Requiere header `X-User-Role: ADMIN`
5. THE ms-provider SHALL exponer `DELETE /api/v1/suppliers/{supplierId}` para desactivar un proveedor (soft-delete: active = false). Requiere header `X-User-Role: ADMIN`
6. THE ms-provider SHALL exponer `POST /api/v1/suppliers/{supplierId}/products` para asignar un SKU al proveedor con: sku, supplierSku, unitPrice, leadTimeDays, reorderMultiplier, preferred. Requiere header `X-User-Role: ADMIN`
7. THE ms-provider SHALL exponer `DELETE /api/v1/suppliers/{supplierId}/products/{sku}` para desasignar un SKU del proveedor. Requiere header `X-User-Role: ADMIN`
8. WHEN se intenta registrar un proveedor con email duplicado, THE ms-provider SHALL retornar 409 Conflict
9. WHEN se intenta asignar un SKU ya asignado como preferred a un proveedor diferente, THE ms-provider SHALL quitar el flag preferred del anterior y asignarlo al nuevo

### Requirement 5: Manage purchase orders via REST endpoints (ADMIN only)

**User Story:** Como Administrador, quiero consultar y gestionar las órdenes de compra generadas para controlar el proceso de reabastecimiento.

#### Acceptance Criteria

1. THE ms-provider SHALL exponer `GET /api/v1/purchase-orders` para listar órdenes de compra con filtros: status, supplierId, sku, dateFrom, dateTo. Paginación con page y size. Requiere `X-User-Role: ADMIN`
2. THE ms-provider SHALL exponer `GET /api/v1/purchase-orders/{purchaseOrderId}` para consultar detalle de una orden con sus items. Requiere `X-User-Role: ADMIN`
3. THE ms-provider SHALL exponer `PUT /api/v1/purchase-orders/{purchaseOrderId}/send` para marcar una orden como SENT (enviada al proveedor). Requiere `X-User-Role: ADMIN`. Solo válido desde estado PENDING
4. THE ms-provider SHALL exponer `PUT /api/v1/purchase-orders/{purchaseOrderId}/confirm` para marcar una orden como CONFIRMED (proveedor confirmó recepción). Requiere `X-User-Role: ADMIN`. Solo válido desde estado SENT
5. THE ms-provider SHALL exponer `PUT /api/v1/purchase-orders/{purchaseOrderId}/receive` para marcar como RECEIVED (mercancía recibida). Requiere `X-User-Role: ADMIN`. Solo válido desde estados CONFIRMED o PARTIALLY_RECEIVED
6. THE ms-provider SHALL exponer `PUT /api/v1/purchase-orders/{purchaseOrderId}/cancel` para cancelar una orden. Requiere `X-User-Role: ADMIN`. Solo válido desde estados PENDING o SENT
7. WHEN se intenta una transición de estado inválida, THE ms-provider SHALL retornar 422 Unprocessable Entity con mensaje explicativo
8. THE ms-provider SHALL exponer `POST /api/v1/purchase-orders` para crear órdenes de compra manuales (el admin puede forzar un reabastecimiento sin esperar StockDepleted). Requiere `X-User-Role: ADMIN`. Payload: supplierId, items: [{sku, quantity}]

### Requirement 6: Notify supplier via ms-notifications

**User Story:** Como plataforma Arka, quiero que ms-provider solicite el envío de un email al proveedor con el detalle de la orden de compra para que este inicie el proceso de despacho.

#### Acceptance Criteria

1. WHEN una PurchaseOrder transiciona a estado SENT, THE ms-provider SHALL publicar un evento al tópico `provider-events` con eventType `PurchaseOrderSent` que ms-notifications consumirá para enviar el email al proveedor
2. THE payload del evento `PurchaseOrderSent` SHALL contener: purchaseOrderId, supplierName, supplierEmail, items (sku, productName, quantity, unitPrice), totalAmount, notes
3. ms-notifications (consumer group `notification-service-group`) SHALL consumir `PurchaseOrderSent` del tópico `provider-events` y enviar un email al `supplierEmail` con el detalle formateado de la orden
4. WHEN el email se envía exitosamente, THE ms-notifications SHALL registrar un log INFO con el purchaseOrderId y supplierEmail
