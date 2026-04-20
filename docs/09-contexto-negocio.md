# 09 — Contexto de Negocio

## Qué es Arka

Plataforma e-commerce **B2B** para distribución de accesorios de PC en Colombia/LATAM. Los clientes son almacenes que compran en grandes cantidades con facturación diferida (30-60 días).

## Problemas que Resuelve

1. **Sobreventa por concurrencia** — Se vendían más productos de los disponibles
2. **Gestión manual del inventario** — Insostenible con el volumen actual
3. **Ausencia de flujo automatizado de pedidos** — Tiempos de atención altos
4. **Clientes desinformados** — No conocían el estado de sus pedidos

## Actores

- **Cliente B2B:** Almacenes en LATAM que compran accesorios para PC al por mayor
- **Administrador:** Personal interno de Arka (catálogo, inventario, despachos, analítica)

## Sistemas Externos

| Sistema | Microservicio | Relación |
|---|---|---|
| Pasarelas de pago (Stripe, Wompi, MercadoPago) | ms-payment (ACL) | Procesamiento de cobros |
| Identity Provider (Entra ID / Cognito) | API Gateway | Autenticación JWT, Zero Trust |
| Operadores logísticos (DHL, FedEx, legacy) | ms-shipping (ACL) | Cotización y despacho |
| Proveedores externos | ms-provider (ACL) | Reabastecimiento vía email |
| AWS SES | ms-notifications | Correos transaccionales |
| AWS S3 | ms-reporter | Reportes pesados (hasta 500MB) |

## Decisiones Estratégicas

1. **BFF descartado** — API Gateway como único punto de entrada REST
2. **Reseñas en ms-catalog** — Subdocumentos en MongoDB, no microservicio separado
3. **Pago B2B offline en Fase 1** — Facturación diferida, pasarelas en Fase 2
4. **ms-reporter imperativo** — Único servicio con Virtual Threads para CPU-bound
5. **Naming: kebab-case** con prefijo `ms-` (ms-catalog, ms-inventory, etc.)

## Historias de Usuario

| HU | Descripción | Servicio | Fase |
|---|---|---|---|
| HU1 | Registrar productos con validaciones y SKU único | ms-catalog | 1 |
| HU2 | Actualizar stock con historial y lock pesimista | ms-inventory | 1 |
| HU4 | Registrar orden con validación gRPC de stock | ms-order | 1 |
| HU6 | Notificaciones por email ante cambios de estado | ms-notifications | 1 |
| HU8 | Carritos abandonados con detección automática | ms-cart | 2 |
| HU5 | Modificar orden antes de confirmación de pago | ms-order + ms-payment | 2 |
| HU7 | Reportes de ventas semanales (CSV/PDF hasta 500MB) | ms-reporter | 3 |
| HU3 | Alertas de stock bajo + órdenes automáticas a proveedores | ms-reporter + ms-provider | 3/4 |
