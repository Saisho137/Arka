# Arquitectura Backend Arka - Estrategia de Entrega y Diseño del Sistema

**Proyecto:** Arka - Plataforma B2B de Distribución de Accesorios para PC
**Stack Técnico:** Java 21 (WebFlux & Virtual Threads), Apache Kafka, PostgreSQL, MongoDB, Redis, gRPC, AWS (API Gateway, S3, SES)

---

## 1. Justificación de la Estrategia (Fases de Entrega de Valor)

La arquitectura completa del Nivel 2 (C4) contempla un ecosistema robusto de **9 microservicios** operando bajo un modelo políglota y dirigido por eventos. Dado que no es posible construirlo todo simultáneamente, el desarrollo se divide en **Fases de Entrega de Valor**.

### Principio Rector

> **"Resolver primero el problema que más duele"**

Según el contexto del negocio, los problemas críticos de Arka son:

1. **Sobreventa por concurrencia** → Se han vendido más productos de los que había en stock debido a alta concurrencia
2. **Gestión manual del inventario** → Insostenible con el volumen actual de operación
3. **Ausencia de flujo automatizado de pedidos** → Tiempos de atención altos para clientes B2B
4. **Clientes desinformados** → No conocen el estado de sus pedidos

Las HUs de alta prioridad atacan exactamente estos problemas, por lo que el MVP (Fase 1) se centra en resolverlos.

---

## 2. Fases de Entrega de Valor y Distribución Arquitectónica

### 🎯 Fase 1: MVP - Núcleo Transaccional B2B (Resolución de Sobreventas)

Esta fase entrega el sistema core para permitir la venta segura, mitigando la venta de stock inexistente mediante transacciones cortas y validaciones síncronas por gRPC.

- **Microservicios Entregados:**
  - `ms-catalog` (Reactivo): Dueño del producto y sus reseñas anidadas. Resuelve la **HU1** (Registrar productos)
  - `ms-inventory` (Reactivo): Dueño del stock. Utiliza _Lock Pesimista_ en PostgreSQL. Resuelve la **HU2** (Actualizar stock)
  - `ms-order` (Reactivo): Máquina de estados. Orquestador pasivo de la Saga. Resuelve la **HU4** (Registrar orden)
  - `ms-notifications` (Reactivo): Motor pasivo de correos integrándose a AWS SES. Resuelve la **HU6** (Notificación de estados)
- **Infraestructura Desplegada:** AWS API Gateway (Zero Trust / Entra ID), Apache Kafka, PostgreSQL, MongoDB y Redis
- **Valor de Negocio:** El cliente puede visualizar el catálogo, crear una orden y el sistema **bloquea el inventario en tiempo real** usando gRPC entre `ms-order` e `ms-inventory`, previniendo la sobreventa

**Qué INCLUYE esta fase:**

| Componente             | Justificación                                                        |
| ---------------------- | -------------------------------------------------------------------- |
| **`ms-catalog`**       | HU1 - Registrar productos (dominio: catálogo maestro)                |
| **`ms-inventory`**     | HU2 - Actualizar stock (dominio: disponibilidad física)              |
| **`ms-order`**         | HU4 - Registrar órdenes de compra                                    |
| **`ms-notifications`** | HU6 - Notificaciones de estado del pedido                            |
| **Apache Kafka**       | Broker de mensajería para comunicación asíncrona entre servicios     |
| **API Gateway**        | Punto de entrada único, Auth (JWT/Entra ID), Rate Limiting, SSL      |
| **PostgreSQL**         | Database per Service para `ms-inventory` y `ms-order` (ACID crítico) |
| **MongoDB**            | Database per Service para `ms-catalog` y `ms-notifications`          |
| **Redis**              | Caché de catálogo para lecturas de alta frecuencia (Cache-Aside)     |

**Qué NO INCLUYE esta fase (diferido a fases posteriores):**

| Componente Diferido | HU / Razón                                           | Fase |
| ------------------- | ---------------------------------------------------- | ---- |
| `ms-cart`           | HU8 - Carritos abandonados                           | 2    |
| `ms-payment`        | Cierre financiero con pasarelas externas             | 2    |
| `ms-reporter`       | HU7 - Reportes semanales / HU3 - Reportes stock bajo | 3    |
| `ms-shipping`       | Despachos y Strangler Fig Pattern                    | 3    |
| `ms-provider`       | Abastecimiento automático con proveedores            | 4    |
| Frontend            | Excluido explícitamente del alcance backend          | -    |
| Patrón BFF          | Descartado permanentemente de la arquitectura        | -    |

> **Nota sobre el pago en Fase 1:** Los clientes de Arka son almacenes (modelo B2B), por lo que se utiliza facturación diferida con términos a 30-60 días. En la Fase 1, el pago se gestiona como proceso externo (transferencia bancaria o facturación B2B). Las órdenes confirmadas por `ms-inventory` (stock reservado) transicionan automáticamente a `CONFIRMADO`. La validación automática de pago con pasarelas se incorpora en la Fase 2 con `ms-payment`.

### 💳 Fase 2: Autogestión B2B y Cierre Financiero (Saga Completa)

Con el inventario seguro, se introduce la gestión temporal de la compra y la automatización bancaria para cerrar el ciclo contable.

- **Microservicios Entregados:**
  - `ms-cart` (Reactivo): Sesiones y persistencia temporal en MongoDB usando mutaciones atómicas (`$push`/`$pull`). Resuelve la **HU8** (Carritos abandonados)
  - `ms-payment` (Imperativo / Virtual Threads): Capa Anti-Corrupción (ACL) para interactuar con pasarelas (Stripe, Wompi, Mercado Pago). Resuelve la **HU5** (Modificar orden)
- **Evolución Arquitectónica:** La Saga Secuencial se completa: **Catálogo → Inventario → Pago**. Si `ms-payment` falla al cobrar, emite un evento de compensación por Kafka y `ms-inventory` libera el stock. Las órdenes ahora pasan por el estado `PENDIENTE_PAGO` antes de confirmarse.
- **Valor de Negocio:** Se reducen las pérdidas por abandono y se automatiza la conciliación de pagos aislando las caídas bancarias gracias al aislamiento del hilo (_Virtual Threads_) en integraciones síncronas bloqueantes

### 📈 Fase 3: Analítica Avanzada y Logística (CQRS & Strangler Fig)

Se entrega la capacidad de análisis masivo para la directiva y se automatizan los envíos B2B "estrangulando" sistemas heredados.

- **Microservicios Entregados:**
  - `ms-reporter` (Imperativo / Virtual Threads): Data Lake de la arquitectura que consume todos los eventos de Kafka (Event Sourcing). Usa índices GIN y JSONB en PostgreSQL. Exporta PDF/CSV pesados (hasta 500MB) a **AWS S3**. Resuelve la **HU7** (Ventas semanales) y **HU3** (Reporte stock bajo)
  - `ms-shipping` (Imperativo): Gestión de despachos implementando el **Patrón Strangler Fig** para migrar progresivamente desde el monolito logístico
- **Valor de Negocio:** Operaciones puede tomar decisiones estratégicas sin tumbar la base de datos de ventas (OLTP). El área logística automatiza las guías de envío sin detener la operación de despachos.

### 🔄 Fase 4: Abastecimiento y Ecosistema Completo

Automatización B2B integral con los proveedores de Arka para garantizar el flujo continuo de stock.

- **Microservicios Entregados:**
  - `ms-provider` (Imperativo): Barrera de seguridad (ACL) y gestión de órdenes de compra automáticas con proveedores externos
- **Valor de Negocio:** Cuando `ms-inventory` reporta existencias críticas, el sistema negocia automáticamente presupuestos con los proveedores, cerrando el ciclo logístico y de ventas completo.

---

## 3. Historias de Usuario del Proyecto

Las Historias de Usuario se han priorizado y mapeado a las fases de entrega y a los microservicios correspondientes, asegurando un diseño guiado por el dominio (DDD).

### HU1 - Registrar productos en el sistema — `ms-catalog` (Alta Prioridad | Fase 1)

> Como administrador, quiero registrar nuevos productos con sus características para que los clientes puedan comprarlos.

**Criterios de aceptación:**

- Se debe permitir la carga de nombre, descripción, precio, stock inicial y categoría
- Validaciones de datos requeridos (campos obligatorios, precio > 0, SKU único)
- Mensaje de confirmación tras el registro exitoso
- Al registrar un producto, se publica evento `ProductCreated` vía Kafka → `ms-inventory` crea el registro de stock inicial

### HU2 - Actualizar stock de productos — `ms-inventory` (Alta Prioridad | Fase 1)

> Como administrador, quiero actualizar la cantidad de productos en stock para evitar sobreventas.

**Criterios de aceptación:**

- El sistema debe permitir modificar el stock de un producto
- No se deben permitir valores negativos (constraint `stock >= 0` a nivel de BD)
- Historial de cambios en el stock (tabla `stock_movements` con auditoría)
- Reserva temporal de stock con lock pesimista para prevenir race conditions

### HU4 - Registrar una orden de compra — `ms-order` (Alta Prioridad | Fase 1)

> Como cliente B2B, quiero poder registrar una orden de compra con múltiples productos para realizar mi pedido.

**Criterios de aceptación:**

- Se debe validar la disponibilidad del stock de forma síncrona (vía gRPC con `ms-inventory`)
- Registro de fecha y detalles del pedido
- Mensaje de confirmación con resumen del pedido
- Máquina de estados: `PENDIENTE_RESERVA` → `CONFIRMADO` → `EN_DESPACHO` → `ENTREGADO` (o `CANCELADO`). En Fase 2 se incorpora el estado `PENDIENTE_PAGO`.

### HU6 - Notificación de cambio de estado del pedido — `ms-notifications` (Alta Prioridad | Fase 1)

> Como cliente, quiero recibir notificaciones sobre el estado de mi pedido para estar informado de su progreso.

**Criterios de aceptación:**

- Notificación por correo electrónico (AWS SES)
- Estados notificados: pendiente, confirmado, en despacho, entregado
- Cada transición de estado del pedido dispara una notificación automática vía evento Kafka

### HU8 - Gestión de carritos abandonados — `ms-cart` (Media Prioridad | Fase 2)

> Como administrador, quiero visualizar los carritos abandonados para recuperar ventas potenciales.

**Criterios de aceptación:**

- El sistema debe permitir la gestión de carritos temporales con items
- Mutaciones atómicas en MongoDB (`$push` / `$pull`) para operaciones concurrentes
- Detección automática de carritos expirados mediante CronJob
- Evento `CartAbandoned` publicado a Kafka al detectar abandono
- Consulta síncrona vía gRPC a `ms-catalog` para garantizar precio actualizado en checkout

### HU5 - Modificar orden de compra — `ms-order` (Baja Prioridad | Fase 2)

> Como cliente, quiero modificar mi pedido antes de que sea confirmado para ajustar cantidades o productos.

**Criterios de aceptación:**

- Solo modificable en estado `PENDIENTE_PAGO` (previo a confirmación de pago)
- Se debe validar disponibilidad del nuevo stock solicitado vía gRPC
- Notificación al cliente tras modificación exitosa

### HU7 - Reportes de ventas semanales — `ms-reporter` (Media Prioridad | Fase 3)

> Como administrador, quiero generar reportes analíticos masivos para tomar decisiones estratégicas.

**Criterios de aceptación:**

- Generación de reportes CSV/PDF de hasta 500MB
- Ejecución asíncrona para no afectar el core transaccional (CQRS)
- Exportación a AWS S3 como objetos inmutables
- Consumo de todos los eventos del ecosistema (Event Sourcing) mediante Apache Kafka

### HU3 - Reporte de abastecimiento — `ms-reporter` / `ms-provider` (Baja Prioridad | Fase 3/4)

> Como administrador, quiero recibir alertas de stock bajo para gestionar el reabastecimiento oportuno.

**Criterios de aceptación:**

- Alertas automáticas cuando el stock alcanza umbrales críticos
- Integración con `ms-provider` para órdenes de compra automáticas (Fase 4)
- Notificación por correo electrónico al administrador vía `ms-notifications`

---

## 4. Patrones y Decisiones Arquitectónicas (Refinadas)

Para cumplir con los lineamientos del Scaffold Clean Architecture, la escalabilidad y las restricciones de un entorno B2B, el sistema implementa los siguientes fundamentos:

### A. Seguridad Zero Trust y Descarte del BFF

El patrón BFF (Backend for Frontend) queda oficialmente **descartado** de la solución. El **API Gateway** asume la protección perimetral absoluta (_Zero Trust_). Valida los tokens JWT contra Entra ID o Cognito, aplica _Tenant Restrictions_ (bloqueando dominios públicos como `@gmail.com` para resguardar el B2B) y enruta el tráfico directamente a los microservicios, los cuales operan 100% _stateless_. Inyecta la identidad en el header `X-User-Email` hacia la VPC privada.

### B. Bases de Datos Políglotas Estrictas (Database per Service)

Cada microservicio es dueño único de su almacenamiento para evitar acoplamientos y permitir el escalado independiente.

- **MongoDB (Drivers Reactivos):** Usado por `ms-catalog` para lecturas de catálogos polimórficos ultrarrápidas con reseñas como subdocumentos, `ms-cart` para mutaciones atómicas en arrays y `ms-notifications` para almacenar plantillas JSON dinámicas e historial de correos.
- **PostgreSQL (R2DBC / JDBC):** Usado por `ms-inventory`, `ms-order`, `ms-payment`, `ms-shipping`, `ms-provider` y `ms-reporter`. Garantiza integridad transaccional ACID, permite bloqueos pesimistas para proteger el stock y soporta vistas indexadas JSONB para CQRS.

### C. Paradigma Híbrido: Reactivo vs Imperativo (Loom)

Basado en la naturaleza de cada Bounded Context, se divide el stack:

1. **I/O-Bound (Spring WebFlux):** Alta concurrencia y baja latencia. Implementado en `ms-catalog`, `ms-inventory`, `ms-order`, `ms-cart` y `ms-notifications`. Todo acceso a base de datos usa drivers no bloqueantes (R2DBC o Reactive Mongo).
2. **CPU-Bound / External SDKs (Spring MVC + Virtual Threads):** Implementado en `ms-reporter` (para evitar colapsar el _Event Loop_ generando archivos pesados en AWS S3), `ms-payment` (por el uso de SDKs de pasarelas síncronas), `ms-shipping` y `ms-provider` (por conexiones legacy bloqueantes).

### D. Comunicación Síncrona (gRPC) vs Asíncrona (Kafka)

- **Comunicaciones Síncronas Críticas:** Exclusivamente implementadas mediante **gRPC** por su alta velocidad de serialización en la red privada. Ejemplos: `ms-order` llamando a `ms-inventory` para asegurar la reserva de stock instantánea antes de iniciar procesos asíncronos; y `ms-cart` consultando a `ms-catalog` el precio actualizado previo al checkout.
- **Arquitectura Orientada a Eventos (Kafka):** El flujo transaccional fluye de forma asíncrona mediante el broker. `ms-order` coordina la **Saga Secuencial** emitiendo eventos. Todos los servicios publican eventos de dominio que `ms-reporter` consume para construir el Read Model analítico (CQRS).

### E. Resiliencia: Outbox Pattern e Idempotencia

1. **Transactional Outbox Pattern:** Para prevenir el _Dual-Write problem_, servicios como `ms-inventory` y `ms-order` insertan su evento de dominio dentro de la misma transacción PostgreSQL que altera el negocio. Un relay asíncrono lo empuja a Kafka, garantizando que nunca se pierdan eventos por caídas de red. En servicios con MongoDB (`ms-catalog`), se adapta el patrón usando una colección `outbox_events` con operaciones atómicas.
2. **Idempotencia en Consumidores:** Debido a que Kafka garantiza entrega _At-least-once_, cada microservicio implementa una tabla/colección escudo (`processed_events` o _Unique Constraints_ combinados) para hacer _fail-fast_ frente a eventos duplicados, evitando descontar inventario dos veces o ejecutar cobros dobles.

---

## 5. Responsabilidades de cada Contenedor

La arquitectura elimina el patrón BFF e implementa un modelo _Zero Trust_. A continuación, se detalla la estructura interna de los componentes del ecosistema completo, organizados por fases de entrega.

### 5.0 Decisión Arquitectónica: ¿Por qué `ms-catalog` e `ms-inventory` son servicios separados?

Esta es una pregunta fundamental que merece una justificación sólida basada en **Domain-Driven Design (DDD)** y las características únicas del negocio de Arka.

#### Bounded Contexts Diferentes

Según Eric Evans (DDD) y la arquitectura hexagonal, cada microservicio debe representar un **bounded context** — un límite lógico del dominio con su propio lenguaje ubicuo y responsabilidades.

| Aspecto                       | `ms-catalog` (📦)                                                                              | `ms-inventory` (📊)                                                |
| ----------------------------- | ---------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| **Bounded Context**           | Catálogo Maestro de Productos                                                                  | Disponibilidad Física y Reservas                                   |
| **Pregunta de negocio**       | ¿QUÉ vendemos?                                                                                 | ¿CUÁNTO hay disponible?                                            |
| **Responsabilidad principal** | Información descriptiva de productos (nombre, precio, categoría, atributos técnicos, imágenes) | Cantidad disponible, reservas temporales, historial de movimientos |
| **Naturaleza de los datos**   | **Datos maestros** — relativamente estáticos                                                   | **Datos transaccionales** — altamente dinámicos                    |
| **Frecuencia de cambios**     | Baja (producto se registra una vez, actualiza ocasionalmente)                                  | **Muy alta** (cada venta/reserva/abastecimiento modifica stock)    |
| **Patrón de acceso**          | 95% lecturas, 5% escrituras                                                                    | 60% escrituras, 40% lecturas                                       |
| **Problema crítico**          | Búsqueda y filtrado eficiente                                                                  | **Sobreventa por concurrencia** (problema #1 de Arka)              |
| **Mecanismo de consistencia** | Eventual (cambio de precio no afecta órdenes en curso)                                         | **ACID estricto** (lock pesimista para evitar race conditions)     |
| **Estrategia de escalado**    | **Horizontal con caché agresivo** (Redis)                                                      | **Vertical con ACID riguroso** (PostgreSQL con locks)              |
| **Equipo propietario**        | Producto / Marketing                                                                           | Operaciones / Logística                                            |
| **Ciclo de vida**             | Producto puede existir sin stock (pre-orden)                                                   | Stock puede existir sin producto visible (descontinuado)           |
| **Eventos de dominio**        | `ProductCreated`, `ProductUpdated`, `PriceChanged`                                             | `StockReserved`, `StockReleased`, `StockUpdated`                   |
| **Integraciones externas**    | Futuro: proveedores de información de productos, APIs de fabricantes                           | Futuro: WMS (Warehouse Management System), proveedores             |

#### Justificación desde los Problemas de Arka

Arka ha tenido **incidentes críticos de sobreventa** donde se vendieron más productos de los que había en stock debido a alta concurrencia. Este problema requiere:

1. **Lock pesimista (`SELECT ... FOR UPDATE`)** en la tabla de stock — bloquea el row mientras se verifica y reserva
2. **Transacciones ACID estrictas** — no se puede tolerar consistencia eventual
3. **Reservas temporales con timeout** — libera stock si el pago no se completa en 15 minutos

Si **`ms-catalog` e `ms-inventory` estuvieran juntos**, implicaría:

- ❌ Las **lecturas del catálogo** (alta frecuencia, bajo costo) competirían por conexiones de BD con las **transacciones de stock** (lock pesimista, alta criticidad)
- ❌ Un **cambio en cómo se presenta el catálogo** (ej: agregar filtros dinámicos) requeriría desplegar el mismo servicio que maneja locking crítico de stock — riesgo de regresión
- ❌ No se podría **cachear agresivamente** el catálogo (porque el servicio también maneja escrituras transaccionales de stock)
- ❌ Los **equipos de producto y operaciones** tendrían que coordinarse para CADA cambio, incluso si no afecta al otro dominio

#### Patrón Cache-Aside con Redis

Con servicios separados, se habilita el siguiente flujo optimizado:

```text
Cliente consulta catálogo:
  └─> API GW ─> ms-catalog ─> Redis (HIT 95% del tiempo, <1ms)
                                 └─> MongoDB (MISS 5%, ~10ms)

Cliente crea orden:
  └─> API GW ─> ms-order ─> gRPC: ms-inventory
                               └─> PostgreSQL con SELECT FOR UPDATE (lock)
                                    └─> Reserva stock atómicamente
```

**Beneficios tangibles:**

- ✅ **Latencia del catálogo:** <1ms (desde Redis) vs ~10-50ms (consulta directa a BD)
- ✅ **Throughput de reservas:** No degradado por lecturas del catálogo
- ✅ **Disponibilidad independiente:** Si `ms-catalog` cae, aún se pueden procesar órdenes (`ms-inventory` sigue funcionando)
- ✅ **Equipos autónomos:** Marketing puede iterar en el catálogo sin afectar crítico de stock

#### ¿Cuándo UNIR `ms-catalog` e `ms-inventory`?

Si Arka fuera un **negocio más simple** con:

- Volumen bajo (<100 pedidos/día)
- Sin problema de sobreventa (suficiente stock siempre)
- Un solo equipo gestionando todo

Entonces, sí podrían estar juntos en un **Product Service** único. Pero dado el contexto de Arka (alto volumen de transacciones, expansión LATAM, modelo B2B con alta concurrencia, problema crítico de sobreventa), la **separación es justificada incluso en el MVP**.

---

### 5.1 API Gateway (AWS) — Perímetro Zero Trust

**Tipo:** Infraestructura como Servicio

**Responsabilidades:**

- **Punto de entrada único.** Ya no existen BFFs.
- **Seguridad:** Valida tokens JWT delegando a Microsoft Entra ID o Cognito. Bloquea dominios públicos (ej. `@gmail.com`) mediante _Tenant Restrictions_. Inyecta la identidad en el header `X-User-Email` hacia la VPC privada.
- **Resiliencia:** Aplica _Rate Limiting_ (100 req/s por IP) y _SSL Termination_.
- **Enrutamiento por path** directamente a los microservicios (sin BFF)
- **Logging centralizado** — CloudWatch Logs

**Rutas del API Gateway:**

| Método   | Path                              | Servicio destino | Descripción                               |
| -------- | --------------------------------- | ---------------- | ----------------------------------------- |
| `POST`   | `/api/v1/products`                | `ms-catalog`     | Registrar producto (HU1)                  |
| `GET`    | `/api/v1/products`                | `ms-catalog`     | Listar productos (paginado)               |
| `GET`    | `/api/v1/products/{id}`           | `ms-catalog`     | Consultar producto por ID                 |
| `PUT`    | `/api/v1/products/{id}`           | `ms-catalog`     | Actualizar producto                       |
| `DELETE` | `/api/v1/products/{id}`           | `ms-catalog`     | Desactivar producto (soft delete)         |
| `POST`   | `/api/v1/categories`              | `ms-catalog`     | Crear categoría                           |
| `GET`    | `/api/v1/categories`              | `ms-catalog`     | Listar categorías                         |
| `PUT`    | `/api/v1/inventory/{sku}/stock`   | `ms-inventory`   | Actualizar stock (HU2)                    |
| `GET`    | `/api/v1/inventory/{sku}`         | `ms-inventory`   | Consultar stock                           |
| `GET`    | `/api/v1/inventory/{sku}/history` | `ms-inventory`   | Historial de stock (HU2)                  |
| `POST`   | `/api/v1/orders`                  | `ms-order`       | Crear orden (HU4)                         |
| `GET`    | `/api/v1/orders/{id}`             | `ms-order`       | Consultar orden                           |
| `GET`    | `/api/v1/orders`                  | `ms-order`       | Listar órdenes por cliente                |
| `PUT`    | `/api/v1/orders/{id}/status`      | `ms-order`       | Cambiar estado (Admin: dispatch, deliver) |
| `PUT`    | `/api/v1/orders/{id}/cancel`      | `ms-order`       | Cancelar orden                            |

---

### 5.2 `ms-catalog` (Servicio de Catálogo) — HU1

**Paradigma:** Reactivo (Java 21 + Spring WebFlux). 100% I/O-Bound.
**HU cubierta:** HU1 - Registrar productos en el sistema

**Responsabilidades:**

- 📦 **CRUD de productos** con atributos: SKU, nombre, descripción, precio, categoría
- 📂 **Gestión de categorías** — CRUD básico de categorías maestras
- ⭐ **Reseñas anidadas** — Las reseñas de productos se almacenan como subdocumentos dentro del documento de producto (no hay microservicio independiente de Recomendaciones)
- ✅ **Validaciones de negocio:**
  - Campos obligatorios (nombre, precio, SKU, categoría)
  - Precio > 0
  - SKU único en el sistema
- 📊 **Publicación de eventos a Kafka:**
  - `ProductCreated` → Consumido por `ms-inventory` para crear registro de stock inicial
  - `ProductUpdated` → Informar cambios a otros servicios
- 🔒 **Caché:** Implementa el patrón _Cache-Aside_ con Redis. Garantiza latencias <1ms para lecturas masivas.

**Base de Datos:** MongoDB (catalog_db) + Redis (Cache)

```text
catalog_db (MongoDB)
│
├── Collection: products
│   {
│     _id: ObjectId,
│     sku: "GPU-RTX4090"              (unique index),
│     name: "NVIDIA RTX 4090",
│     description: "GPU de alto rendimiento para gaming y IA",
│     price: Decimal128(1599.99),
│     category: {
│       id: "uuid-cat-001",
│       name: "GPUs"
│     },
│     active: true,
│     reviews: [
│       {
│         userId: "uuid-user-001",
│         rating: 5,
│         comment: "Excelente producto para workstations",
│         createdAt: ISODate("2026-01-15")
│       }
│     ],
│     createdAt: ISODate("2026-01-01"),
│     updatedAt: ISODate("2026-01-01")
│   }
│   Indexes:
│     - { sku: 1 }, unique: true
│     - { "category.id": 1 }
│     - { active: 1, "category.id": 1 }
│
├── Collection: categories
│   {
│     _id: ObjectId,
│     name: "GPUs"                    (unique index),
│     description: "Tarjetas gráficas de alto rendimiento",
│     createdAt: ISODate("2026-01-01")
│   }
│
└── Collection: outbox_events
    {
      _id: ObjectId,
      eventType: "ProductCreated",
      topic: "product-events",
      payload: { ... },
      status: "PENDING" | "PUBLISHED",
      createdAt: ISODate("2026-01-01")
    }
```

**Endpoints:**

| Método   | Path             | Rol requerido   | Descripción                       |
| -------- | ---------------- | --------------- | --------------------------------- |
| `POST`   | `/products`      | ADMIN           | Registrar producto (HU1)          |
| `GET`    | `/products`      | CUSTOMER, ADMIN | Listar productos (paginado)       |
| `GET`    | `/products/{id}` | CUSTOMER, ADMIN | Consultar producto por ID         |
| `PUT`    | `/products/{id}` | ADMIN           | Actualizar producto               |
| `DELETE` | `/products/{id}` | ADMIN           | Desactivar producto (soft delete) |
| `POST`   | `/categories`    | ADMIN           | Crear categoría                   |
| `GET`    | `/categories`    | CUSTOMER, ADMIN | Listar categorías                 |

**Evento publicado a Kafka — `ProductCreated`:**

```json
{
  "eventId": "uuid-event-001",
  "eventType": "ProductCreated",
  "timestamp": "2026-02-21T10:00:00Z",
  "source": "ms-catalog",
  "correlationId": "uuid-correlation",
  "payload": {
    "productId": "uuid-prod-001",
    "sku": "GPU-RTX4090",
    "name": "NVIDIA RTX 4090",
    "price": 1599.99,
    "initialStock": 50,
    "categoryId": "uuid-cat-001"
  }
}
```

**Integración con Redis — Patrón Cache-Aside:**

```text
┌─────────────────────────────────────────────────────────┐
│           CATÁLOGO: PATRÓN CACHE-ASIDE                  │
│                                                         │
│  Cliente solicita GET /products                         │
│          │                                              │
│          ▼                                              │
│  ┌──────────────────┐                                   │
│  │   ms-catalog     │                                   │
│  └────────┬─────────┘                                   │
│           │                                              │
│       1. Check Redis                                    │
│           │                                              │
│           ▼                                              │
│    ┌──────────────┐      HIT (95%)                      │
│    │    Redis     │ ───────────▶ Return <1ms            │
│    │ (ElastiCache)│                                     │
│    └──────┬───────┘                                     │
│           │                                              │
│       MISS (5%)                                         │
│           │                                              │
│           ▼                                              │
│  2. Query MongoDB                                       │
│    ┌────────────────┐                                   │
│    │  catalog_db    │  Read ~10ms                       │
│    │  (MongoDB)     │                                   │
│    └────────┬───────┘                                   │
│           │                                              │
│       3. Store in Redis (TTL: 1h)                       │
│           │                                              │
│           ▼                                              │
│    Return to client                                     │
└─────────────────────────────────────────────────────────┘

Invalidación de caché:
  ProductCreated/Updated → Invalida key en Redis → Próxima lectura rebuilt from MongoDB
```

**Justificación de Redis en el catálogo:**

| Criterio                    | MongoDB (solo)                 | MongoDB + Redis                                     |
| --------------------------- | ------------------------------ | --------------------------------------------------- |
| **Latencia de lectura**     | ~10-20ms (query + network)     | **<1ms** (in-memory cache) ✅                       |
| **Throughput de lecturas**  | ~5,000 req/s                   | **10,000+ req/s** (Redis escala horizontalmente) ✅ |
| **Carga en MongoDB**        | 100% de lecturas golpean la BD | **5% de lecturas** (solo cache misses) ✅           |
| **Complejidad operacional** | Baja                           | Media (gestión de cache + invalidación)             |

**Estrategia de invalidación:**

- **Write-through:** Al crear/actualizar producto → escribe en MongoDB + invalida cache en Redis
- **TTL:** 1 hora en Redis (auto-expiry para datos eventualmente consistentes)
- **Eventos Kafka:** `ProductUpdated` → consumer invalida key específica en Redis

**Lo que se DIFIERE para fases posteriores:**

- Búsqueda con filtros dinámicos (marca, atributos técnicos) con Redis Search
- Gestión de precios multi-moneda (COP, USD, PEN, CLP)
- Imágenes de productos (S3 + CloudFront CDN)

---

### 5.3 `ms-inventory` (Servicio de Inventario) — HU2

**Paradigma:** Reactivo (Java 21 + Spring WebFlux + R2DBC).
**HU cubierta:** HU2 - Actualizar stock de productos

**Responsabilidades:**

- 📊 **Control de stock en tiempo real** por SKU con constraint `stock >= 0` a nivel de BD
- 🔒 **Reserva temporal de stock** con timeout de 15 minutos — Usa `SELECT ... FOR UPDATE` (lock pesimista) para prevenir race conditions de concurrencia (resuelve el problema crítico de sobreventa)
- 📝 **Historial de cambios en stock** — Tabla `stock_movements` con trazabilidad completa (quién, cuándo, cuánto, por qué)
- ⏰ **Liberación de reservas expiradas** — Job periódico que libera stock de reservas con más de 15 minutos
- 🔗 **Servidor gRPC** — Expone servicio gRPC para que `ms-order` reserve stock de forma síncrona
- 🔔 **Publicación de eventos a Kafka:**
  - `StockReserved` → `ms-order` transiciona orden a `CONFIRMADO`
  - `StockReserveFailed` → `ms-order` transiciona orden a `CANCELADO`
  - `StockReleased` → Cuando se libera reserva por timeout o cancelación
  - `StockUpdated` → Cuando admin actualiza stock manualmente
  - `StockDepleted` → Alerta de stock bajo al alcanzar umbrales críticos (consumido por `ms-notifications` y `ms-reporter`)
- 🆔 **Idempotencia:** Implementa validación estricta para ignorar eventos duplicados de Kafka, evitando doble descuento.

**Base de Datos:** PostgreSQL (inventory_db)

```text
inventory_db (PostgreSQL)
├── stock
│   ├── id (UUID, PK)
│   ├── sku (VARCHAR, NOT NULL, UNIQUE)
│   ├── product_id (UUID, NOT NULL)
│   ├── quantity (INTEGER, NOT NULL, CHECK >= 0)  ← Constraint crítico
│   ├── reserved_quantity (INTEGER, DEFAULT 0)
│   ├── available_quantity (GENERATED: quantity - reserved_quantity)
│   ├── updated_at (TIMESTAMP)
│   └── version (BIGINT)  ← Optimistic locking adicional
│
├── stock_reservations
│   ├── id (UUID, PK)
│   ├── sku (VARCHAR, NOT NULL)
│   ├── order_id (UUID, NOT NULL, UNIQUE per sku)
│   ├── quantity (INTEGER, NOT NULL)
│   ├── status (ENUM: PENDING, CONFIRMED, EXPIRED, RELEASED)
│   ├── created_at (TIMESTAMP)
│   └── expires_at (TIMESTAMP, DEFAULT NOW() + 15min)
│
├── stock_movements  ← Historial inmutable (stock_history)
│   ├── id (UUID, PK)
│   ├── sku (VARCHAR, NOT NULL)
│   ├── movement_type (ENUM: MANUAL_ADJUSTMENT, ORDER_RESERVE,
│   │                        ORDER_CONFIRM, RESERVATION_RELEASE,
│   │                        PRODUCT_CREATION)
│   ├── quantity_change (INTEGER, NOT NULL)  ← Positivo o negativo
│   ├── previous_quantity (INTEGER)
│   ├── new_quantity (INTEGER)
│   ├── reference_id (UUID)  ← orderId o userId según contexto
│   ├── reason (TEXT)
│   └── created_at (TIMESTAMP)
│
└── outbox_events  ← Transactional Outbox
    ├── id (UUID, PK)
    ├── event_type (VARCHAR)
    ├── topic (VARCHAR)
    ├── payload (JSONB)
    ├── status (ENUM: PENDING, PUBLISHED)
    └── created_at (TIMESTAMP)
```

**Consumer de Eventos Kafka:**

| Evento consumido | Tópico           | Acción                                                     |
| ---------------- | ---------------- | ---------------------------------------------------------- |
| `ProductCreated` | `product-events` | Crea registro en tabla `stock` con quantity = initialStock |
| `OrderCancelled` | `order-events`   | Libera reserva de stock, restaura quantity                 |

**Endpoints:**

| Método | Path                       | Rol requerido   | Descripción                        |
| ------ | -------------------------- | --------------- | ---------------------------------- |
| `PUT`  | `/inventory/{sku}/stock`   | ADMIN           | Actualizar stock manualmente (HU2) |
| `GET`  | `/inventory/{sku}`         | CUSTOMER, ADMIN | Consultar disponibilidad de un SKU |
| `GET`  | `/inventory/{sku}/history` | ADMIN           | Ver historial de movimientos (HU2) |

#### Detalle Crítico: Prevención de Sobreventa (gRPC + Lock Pesimista)

Este es el servicio que resuelve el **problema #1 de Arka** (sobreventa por concurrencia). Al recibir una solicitud síncrona (vía gRPC desde `ms-order`), abre una transacción SQL ultracorta, ejecuta el lock pesimista, descuenta el stock, inserta el evento de dominio en la tabla Outbox y cierra la conexión en milisegundos:

```sql
── Solicitud gRPC de ms-order llega ──

BEGIN TRANSACTION;
  SELECT * FROM stock WHERE sku = 'GPU-RTX4090' FOR UPDATE;
  -- Lock adquirido: ningún otro thread puede modificar este row

  IF available_quantity >= requested_quantity THEN
    -- Decrementar available
    UPDATE stock SET reserved_quantity = reserved_quantity + :qty WHERE sku = :sku;

    -- Crear reserva con expiración
    INSERT INTO stock_reservations (sku, order_id, quantity, status, expires_at)
    VALUES (:sku, :orderId, :qty, 'PENDING', NOW() + INTERVAL '15 minutes');

    -- Registrar movimiento
    INSERT INTO stock_movements (sku, movement_type, quantity_change, ...)
    VALUES (:sku, 'ORDER_RESERVE', -:qty, ...);

    -- Guardar evento en outbox (MISMA transacción)
    INSERT INTO outbox_events (event_type, topic, payload, status)
    VALUES ('StockReserved', 'inventory-events', :json, 'PENDING');
  ELSE
    -- Stock insuficiente: rechaza la llamada gRPC inmediatamente
    INSERT INTO outbox_events (event_type, topic, payload, status)
    VALUES ('StockReserveFailed', 'inventory-events', :json, 'PENDING');
  END IF;
COMMIT;
-- Lock liberado en milisegundos
```

**Liberación de reservas expiradas:**

Job periódico que ejecuta cada 60 segundos:

1. Busca reservas con `expires_at < NOW()` y `status = PENDING`
2. Para cada reserva expirada:
   - Marca como `EXPIRED`
   - Restaura stock (`quantity += reserved_qty`, `reserved_quantity -= reserved_qty`)
   - Registra movimiento en `stock_movements` (tipo: `RESERVATION_RELEASE`)
   - Publica evento `StockReleased` a Kafka (vía outbox)

---

### 5.4 `ms-order` (Servicio de Órdenes) — HU4

**Paradigma:** Reactivo (Java 21 + Spring WebFlux + R2DBC).
**HU cubierta:** HU4 - Registrar una orden de compra

**Responsabilidades:**

- 📝 **Creación de pedidos** con múltiples productos — Validación síncrona de stock vía gRPC
- 🎯 **Máquina de estados** del pedido. Actúa como el orquestador pasivo de la **Saga Secuencial**
- 🔄 **Flujo Interno:** Recibe la petición REST del Gateway. Llama a `ms-inventory` síncronamente por **gRPC**. Si hay stock, guarda la orden y el evento en la tabla Outbox en la misma transacción.
- 📊 **Publicación de eventos a Kafka:**
  - `OrderCreated` → Inicia flujo asíncrono
  - `OrderConfirmed` → `ms-notifications` envía confirmación
  - `OrderStatusChanged` → `ms-notifications` envía actualización
  - `OrderCancelled` → `ms-inventory` libera stock + `ms-notifications` notifica

**Estados del pedido (Fase 1 - MVP):**

| Estado              | Descripción                                                   |
| ------------------- | ------------------------------------------------------------- |
| `PENDIENTE_RESERVA` | Estado efímero en memoria mientras se verifica stock vía gRPC |
| `CONFIRMADO`        | Stock reservado. Pago B2B offline (facturación 30-60 días)    |
| `EN_DESPACHO`       | Admin marca como despachado                                   |
| `ENTREGADO`         | Admin confirma recepción por almacén B2B                      |
| `CANCELADO`         | Fallo (stock insuficiente) o cancelación manual               |

**Estados adicionales (Fase 2 - con `ms-payment`):**

| Estado           | Descripción                                               |
| ---------------- | --------------------------------------------------------- |
| `PENDIENTE_PAGO` | Stock bloqueado. Orden aguarda validación de `ms-payment` |

> En Fase 2, el flujo incorpora `PENDIENTE_PAGO` entre la reserva de stock y la confirmación: `PENDIENTE_RESERVA` → `PENDIENTE_PAGO` → `CONFIRMADO`.

**Base de Datos:** PostgreSQL (order_db)

```text
order_db (PostgreSQL)
├── orders
│   ├── id (UUID, PK)
│   ├── customer_id (UUID, NOT NULL)
│   ├── status (ENUM: PENDIENTE_RESERVA, PENDIENTE_PAGO, CONFIRMADO,
│   │                  EN_DESPACHO, ENTREGADO, CANCELADO)
│   ├── total_amount (DECIMAL(12,2))
│   ├── customer_email (VARCHAR, NOT NULL)  ← Para notificaciones
│   ├── shipping_address (TEXT)
│   ├── notes (TEXT)
│   ├── created_at (TIMESTAMP)
│   └── updated_at (TIMESTAMP)
│
├── order_items
│   ├── id (UUID, PK)
│   ├── order_id (UUID, FK → orders.id)
│   ├── product_id (UUID, NOT NULL)
│   ├── sku (VARCHAR, NOT NULL)
│   ├── product_name (VARCHAR)  ← Snapshot al momento de compra
│   ├── quantity (INTEGER, NOT NULL, CHECK > 0)
│   ├── unit_price (DECIMAL(12,2), NOT NULL)
│   └── subtotal (DECIMAL(12,2), GENERATED: quantity * unit_price)
│
├── order_state_history  ← Auditoría de estados
│   ├── id (UUID, PK)
│   ├── order_id (UUID, FK → orders.id)
│   ├── previous_status (VARCHAR)
│   ├── new_status (VARCHAR, NOT NULL)
│   ├── changed_by (UUID)  ← userId del que hizo el cambio
│   ├── reason (TEXT)
│   └── created_at (TIMESTAMP)
│
└── outbox_events  ← Transactional Outbox
    ├── id (UUID, PK)
    ├── event_type (VARCHAR)
    ├── topic (VARCHAR)
    ├── payload (JSONB)
    ├── status (ENUM: PENDING, PUBLISHED)
    └── created_at (TIMESTAMP)
```

**Consumer de Eventos Kafka:**

| Evento consumido   | Tópico           | Acción                                                     |
| ------------------ | ---------------- | ---------------------------------------------------------- |
| `PaymentProcessed` | `payment-events` | Transiciona orden a `CONFIRMADO` (Fase 2)                  |
| `PaymentFailed`    | `payment-events` | Transiciona a `CANCELADO`, publica `ReleaseStock` (Fase 2) |

**Endpoints:**

| Método | Path                  | Rol requerido   | Descripción                                        |
| ------ | --------------------- | --------------- | -------------------------------------------------- |
| `POST` | `/orders`             | CUSTOMER        | Crear orden de compra (HU4)                        |
| `GET`  | `/orders/{id}`        | CUSTOMER, ADMIN | Consultar detalle de una orden                     |
| `GET`  | `/orders`             | CUSTOMER, ADMIN | Listar órdenes (filtros: status, customerId)       |
| `PUT`  | `/orders/{id}/status` | ADMIN           | Cambiar estado (dispatch, deliver)                 |
| `PUT`  | `/orders/{id}/cancel` | CUSTOMER, ADMIN | Cancelar orden (solo si PENDIENTE_PAGO/CONFIRMADO) |

**Request para crear orden (HU4):**

```json
{
  "customerId": "uuid-customer-001",
  "customerEmail": "almacen-bogota@email.com",
  "shippingAddress": "Cra 7 #32-16, Bogotá, Colombia",
  "items": [
    {
      "productId": "uuid-prod-001",
      "sku": "GPU-RTX4090",
      "quantity": 5
    },
    {
      "productId": "uuid-prod-002",
      "sku": "RAM-DDR5-32GB",
      "quantity": 20
    }
  ],
  "notes": "Entregar en horario laboral"
}
```

**Response (202 Accepted):**

```json
{
  "orderId": "uuid-order-001",
  "status": "CONFIRMADO",
  "message": "Orden registrada. Stock reservado exitosamente.",
  "items": [
    { "sku": "GPU-RTX4090", "quantity": 5, "unitPrice": 1599.99 },
    { "sku": "RAM-DDR5-32GB", "quantity": 20, "unitPrice": 89.99 }
  ],
  "totalAmount": 9799.75,
  "createdAt": "2026-02-21T10:00:00Z"
}
```

> **Nota:** Se responde `202 Accepted` porque la validación síncrona de stock por gRPC es inmediata, pero los procesos asíncronos posteriores (notificaciones, eventos) aún están en cola. En Fase 2 con `ms-payment`, la respuesta será `PENDIENTE_PAGO` y el cliente consultará el estado con `GET /orders/{id}`.

---

### 5.5 `ms-notifications` (Servicio de Notificaciones) — HU6

**Paradigma:** Reactivo (Java 21 + Spring WebFlux).
**HU cubierta:** HU6 - Notificación de cambio de estado del pedido

**Responsabilidades:**

- 📧 **Envío de emails transaccionales** mediante AWS SES para cada cambio de estado del pedido
- 🔔 **Consumidor "Catch-All" pasivo.** Escucha tópicos de Kafka (`OrderConfirmed`, `ShippingDispatched`, etc.), mapea los datos a sus plantillas y dispara correos vía **AWS SES**
- 🔄 **Estrategia de reintentos** — Implementa backoff exponencial ante fallos del servicio de email
- 🆔 **Idempotencia garantizada** — Para evitar spam al cliente por reintentos de red, registra el `eventId` procesado en su base de datos MongoDB

**Eventos cubiertos:**

| Evento consumido     | Tópico            | Acción                                    |
| -------------------- | ----------------- | ----------------------------------------- |
| `OrderConfirmed`     | `order-events`    | Email de confirmación al cliente          |
| `OrderStatusChanged` | `order-events`    | Email con nuevo estado (dispatch/deliver) |
| `OrderCancelled`     | `order-events`    | Email de cancelación con motivo           |
| `ShippingDispatched` | `shipping-events` | Email de despacho (Fase 3)                |
| `CartAbandoned`      | `cart-events`     | Email recordatorio de carrito (Fase 2)    |

**Base de Datos:** MongoDB (notifications_db)

```text
notifications_db (MongoDB)
│
├── Collection: templates
│   {
│     _id: ObjectId,
│     eventType: "OrderConfirmed",
│     subject: "Tu pedido #{{orderId}} ha sido confirmado",
│     bodyTemplate: "<html>...",
│     active: true,
│     createdAt: ISODate("2026-01-01")
│   }
│
└── Collection: notification_history
    {
      _id: ObjectId,
      eventId: "uuid-event-001"       (unique index — idempotency),
      eventType: "OrderConfirmed",
      orderId: "uuid-order-001",
      customerEmail: "almacen@empresa.com",
      status: "SENT" | "FAILED",
      processedAt: ISODate("2026-01-15"),
      createdAt: ISODate("2026-01-15")
    }
    Indexes:
      - { eventId: 1 }, unique: true     // Garantía de idempotencia
      - { createdAt: 1 }, expireAfterSeconds: 7776000  // TTL: 90 días
```

**Flujo de idempotencia:**

```text
1. Evento llega de Kafka (OrderConfirmed)
   ↓
2. MongoDB findOne({ eventId: "uuid-event-001" })
   ↓
3a. Documento existe → LOG "Duplicate event, skip" → FIN
3b. Documento NO existe → Continuar
   ↓
4. Mapear datos a plantilla y enviar email vía AWS SES
   ↓
5. MongoDB insertOne({ eventId, eventType, status: "SENT", ... })
   ↓
6. Commit offset de Kafka
```

**Justificación de MongoDB para `ms-notifications`:**

- **Esquema flexible** para plantillas JSON dinámicas que varían por tipo de evento
- **TTL Index nativo** para limpieza automática del historial (90 días) sin necesidad de jobs
- **Unique Index** en `eventId` garantiza idempotencia a nivel de BD
- MongoDB es ya parte del stack (`ms-catalog` lo usa), por lo que no agrega complejidad operacional adicional

**Lo que se DIFIERE para fases posteriores:**

- Notificaciones SMS / Push notifications
- Plantillas de email avanzadas con localización
- Recordatorios de carrito abandonado (requiere `ms-cart` - Fase 2)
- Dead Letter Queue para eventos fallidos tras reintentos

---

### 5.6 `ms-cart` (Gestión de Carritos) — HU8 (Fase 2)

**Paradigma:** Reactivo (WebFlux + Reactive Mongo Driver).

**Responsabilidades:**

- 🛒 Gestión de carritos temporales con items
- **Base de Datos:** **MongoDB**. Aprovecha las mutaciones atómicas (`$push` / `$pull`) para agregar o quitar items de los arreglos del carrito en operaciones concurrentes
- **Comunicación:** Consulta en tiempo real (Síncrono vía **gRPC**) a `ms-catalog` para garantizar que el precio de checkout sea exacto
- Implementa un motor (CronJob) que detecta carritos expirados y publica el evento `CartAbandoned` a Kafka

---

### 5.7 Microservicios Imperativos (Virtual Threads — Fases 2, 3 y 4)

- **`ms-payment` (Fase 2):** Imperativo (Spring MVC + Virtual Threads). Actúa como Capa Anti-Corrupción (ACL). Usa PostgreSQL con idempotencia rigurosa (_Unique Constraints_ combinados para evitar cobros dobles). El uso de SDKs bloqueantes de pasarelas (Stripe, Wompi, Mercado Pago) exige aislar las peticiones en Virtual Threads para no estrangular la red. Implementa **Circuit Breaker & Bulkhead** con _Resilience4j_.
- **`ms-reporter` (Fase 3):** Imperativo (Spring MVC + Virtual Threads). CQRS y Event Sourcing en PostgreSQL (usando `JSONB` y GIN Index). Realiza agregaciones pesadas (CPU-bound) exportando excels/PDFs de hasta 500MB hacia **AWS S3** como objetos inmutables.
- **`ms-shipping` (Fase 3):** Imperativo con PostgreSQL. Se integra con APIs Logísticas Legacy (FedEx, DHL) aplicando el _Strangler Fig Pattern_ para migrar progresivamente desde el monolito. Implementa **Circuit Breaker** con _Resilience4j_.
- **`ms-provider` (Fase 4):** Imperativo con PostgreSQL. Barrera ACL para recibir webhooks de proveedores de forma segura. Gestiona órdenes de compra automáticas cuando `ms-inventory` reporta existencias críticas.

---

### 5.8 Apache Kafka (Message Broker)

**Tipo:** Plataforma de Event Streaming
**Justificación:** Kafka es el **nervio central** que habilita la comunicación asíncrona entre microservicios, la Saga Pattern y el desacoplamiento temporal. Sin Kafka, cada servicio tendría que llamar síncronamente a los otros, generando acoplamiento fuerte y fallos en cascada.

**Tópicos del ecosistema:**

| Tópico             | Productor(es)  | Consumidor(es)                                                  | Eventos Principales                                                      |
| ------------------ | -------------- | --------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `product-events`   | `ms-catalog`   | `ms-inventory`                                                  | `ProductCreated`, `ProductUpdated`                                       |
| `order-events`     | `ms-order`     | `ms-inventory`, `ms-notifications`, `ms-payment`, `ms-reporter` | `OrderCreated`, `OrderConfirmed`, `OrderStatusChanged`, `OrderCancelled` |
| `inventory-events` | `ms-inventory` | `ms-order`, `ms-notifications`, `ms-reporter`                   | `StockReserved`, `StockReleased`, `StockDepleted`, `StockUpdated`        |
| `cart-events`      | `ms-cart`      | `ms-notifications`, `ms-reporter`                               | `CartAbandoned` (Fase 2)                                                 |
| `payment-events`   | `ms-payment`   | `ms-order`, `ms-notifications`, `ms-reporter`                   | `PaymentProcessed`, `PaymentFailed` (Fase 2)                             |
| `shipping-events`  | `ms-shipping`  | `ms-order`, `ms-notifications`, `ms-reporter`                   | `ShippingDispatched` (Fase 3)                                            |

**Tópicos activos en Fase 1 (MVP):** `product-events`, `order-events`, `inventory-events`

**Características de configuración:**

- **Particiones:** 3 por tópico (paralelismo básico)
- **Replicación:** Factor 1 en desarrollo, factor 3 en producción
- **Retención:** 7 días (168 horas)
- **Creación de tópicos:** Explícita (no auto-create)

**Consumer Groups (MVP):**

| Consumer Group               | Servicio           | Tópicos suscritos                  |
| ---------------------------- | ------------------ | ---------------------------------- |
| `inventory-service-group`    | `ms-inventory`     | `product-events`, `order-events`   |
| `order-service-group`        | `ms-order`         | `inventory-events`                 |
| `notification-service-group` | `ms-notifications` | `order-events`, `inventory-events` |

**Formato estándar de eventos (todos los servicios):**

```json
{
  "eventId": "uuid",
  "eventType": "OrderCreated",
  "timestamp": "2026-02-21T10:00:00Z",
  "source": "ms-order",
  "correlationId": "uuid-correlation",
  "payload": { ... }
}
```

> **Extensibilidad:** Cuando se agreguen `ms-cart`, `ms-payment`, `ms-shipping`, etc. en fases posteriores, solo se necesita crear nuevos tópicos y consumer groups. Los servicios existentes no se modifican.

---

## 6. Flujos Críticos del Ecosistema

La arquitectura garantiza la consistencia de los datos combinando llamadas síncronas de altísima velocidad (gRPC) para validaciones críticas y procesos asíncronos (Kafka) para la propagación de eventos.

### 6.1 Creación de Pedido B2B — Happy Path (Fase 1 - MVP)

```text
[Cliente B2B]
    │
    │ 1. POST /api/v1/orders
    │    { customerId, items: [{sku, qty}...], shippingAddress }
    ▼
[API Gateway]
    │ Valida JWT (Entra ID / Cognito)
    │ Bloquea dominios no-B2B (@gmail.com)
    │ Inyecta X-User-Email
    │ Enruta a ms-order
    ▼
[ms-order]
    │ 2. Valida request (campos requeridos, cantidades > 0)
    │
    │ 3. ═══ LLAMADA SÍNCRONA gRPC ═══
    │    Llama a ms-inventory para CADA item:
    │    ReserveStockRequest { sku, quantity, orderId }
    │
    ▼
[ms-inventory]  ← gRPC Server
    │ 4. Para CADA item del pedido:
    │
    │  BEGIN TRANSACTION;
    │   SELECT * FROM stock
    │   WHERE sku = :sku
    │   FOR UPDATE;  ← Lock Pesimista
    │
    │   IF available >= qty:
    │     Reserva stock
    │     Crea stock_reservation (expires: +15min)
    │     Registra movimiento en stock_movements
    │     Guarda StockReserved en outbox
    │  COMMIT;
    │
    │ 5. Responde gRPC: ReserveStockResponse { success: true }
    │
    ▼
[ms-order]  ← Continúa tras gRPC exitoso
    │ 6. Guarda orden con estado CONFIRMADO
    │    (Fase 1: pago B2B offline, facturación 30-60 días)
    │    Guarda evento OrderConfirmed en outbox_events
    │    (MISMA transacción PostgreSQL)
    │
    │ 7. Responde al cliente: 202 Accepted
    │    { orderId, status: "CONFIRMADO" }
    │
    ▼
[Outbox Relay — ms-order]
    │ 8. Polling periódico (cada 5s): Lee outbox_events PENDING
    │    Publica a Kafka: topic="order-events", event=OrderConfirmed
    │    Marca evento como PUBLISHED
    ▼
[Apache Kafka — topic: order-events]
    │
    ▼
[ms-notifications]
    │ 9. Consume OrderConfirmed
    │    Verifica idempotencia (MongoDB: eventId único)
    │    Mapea a plantilla de confirmación
    │    Dispara email vía AWS SES:
    │    "Tu pedido #uuid-001 ha sido confirmado"
    ▼
[AWS SES → Bandeja del cliente B2B]
```

### 6.2 Creación de Pedido B2B — Happy Path (Fase 2+ con `ms-payment`)

```text
[ms-order]
    │ gRPC a ms-inventory → Stock reservado
    │ Guarda orden como PENDIENTE_PAGO
    │ Publica OrderCreated a Kafka (vía Outbox)
    ▼
[Apache Kafka — topic: order-events]
    │
    ▼
[ms-payment]  ← Virtual Threads (aísla latencia bancaria)
    │ Consume OrderCreated
    │ Procesa cobro con pasarela (Stripe/Wompi/MercadoPago)
    │ ACL traduce respuesta bancaria a evento de dominio
    │ Publica PaymentProcessed a Kafka
    ▼
[ms-order]
    │ Consume PaymentProcessed
    │ Actualiza estado: PENDIENTE_PAGO → CONFIRMADO
    │ Publica OrderConfirmed
    ▼
[ms-notifications] → Email de confirmación al cliente
```

### 6.3 Flujo de Compensación: Stock Insuficiente (Fail-Fast)

```text
[ms-order]
    │ gRPC a ms-inventory
    ▼
[ms-inventory]
    │ SELECT * FROM stock WHERE sku='GPU-RTX4090' FOR UPDATE;
    │ available_quantity = 3, requested = 10
    │ 3 < 10 → STOCK INSUFICIENTE
    │
    │ Rechaza la llamada gRPC inmediatamente
    │ gRPC Response: { success: false, available: 3, requested: 10 }
    ▼
[ms-order]
    │ Hace fail-fast: devuelve error 409 Conflict al cliente
    │ Response: "Stock insuficiente para GPU-RTX4090
    │            (disponible: 3, solicitado: 10)"
    │
    │ NO se ensucian eventos en Kafka
    │ NO se persiste la orden fallida
    ▼
[Cliente recibe error inmediato — sin latencia de Saga]
```

### 6.4 Flujo de Compensación: Fallo de Pago (Fase 2)

```text
[ms-payment]
    │ Pasarela rechaza tarjeta / timeout bancario
    │ Publica PaymentFailed a Kafka
    ▼
[ms-order]
    │ Consume PaymentFailed
    │ Cambia orden: PENDIENTE_PAGO → CANCELADO
    │ Publica comando de compensación ReleaseStock
    ▼
[ms-inventory]
    │ Consume ReleaseStock
    │ Devuelve unidades al stock físico
    │ Registra movimiento RESERVATION_RELEASE
    │
    │ 0% de mercancía despachada sin pago confirmado
    ▼
[ms-notifications] → Email: "Tu pedido fue cancelado por fallo en pago"
```

### 6.5 Flujo: Actualización de Estado por Admin (Despacho)

```text
[Admin Arka]
    │
    │ PUT /api/v1/orders/uuid-001/status
    │ { "newStatus": "EN_DESPACHO" }
    ▼
[API Gateway]
    │ Valida JWT (rol ADMIN)
    ▼
[ms-order]
    │ Valida transición: CONFIRMADO → EN_DESPACHO
    │ Actualiza order.status = EN_DESPACHO
    │ Registra en order_state_history
    │ Publica: OrderStatusChanged
    │   { orderId, previousStatus: CONFIRMADO,
    │     newStatus: EN_DESPACHO, customerEmail }
    ▼
[Apache Kafka → ms-notifications]
    │ Consume OrderStatusChanged
    │ Envía email vía SES:
    │ "Tu pedido #uuid-001 ha sido despachado"
    ▼
[AWS SES → Cliente]
```

### 6.6 Flujo: Registro de Producto y Creación de Stock (HU1 + HU2)

```text
[Admin Arka]
    │
    │ POST /api/v1/products
    │ { sku: "GPU-RTX4090", name: "NVIDIA RTX 4090",
    │   price: 1599.99, categoryId: "uuid-cat-001",
    │   initialStock: 50 }
    ▼
[API Gateway]
    │ Valida JWT (rol ADMIN)
    ▼
[ms-catalog]
    │ Valida: SKU único, precio > 0, categoría existe
    │ Guarda producto en MongoDB (catalog_db)
    │ Guarda ProductCreated en colección outbox_events
    │ Responde: 201 Created { productId, sku, name, price }
    │
    │ Outbox Relay publica: ProductCreated
    │   { productId, sku, initialStock: 50 }
    ▼
[Apache Kafka — topic: product-events]
    │
    ▼
[ms-inventory]
    │ Consume ProductCreated
    │ Crea registro en tabla stock:
    │   { sku: "GPU-RTX4090", quantity: 50,
    │     reserved_quantity: 0, product_id: "uuid-prod-001" }
    │ Registra movimiento en stock_movements:
    │   { type: PRODUCT_CREATION, quantity_change: +50 }
    ▼
[Stock listo para recibir órdenes]
```

---

## 7. Transición de Estados (Máquina de Estados de la Orden)

El `ms-order` actúa como la fuente de la verdad para el ciclo de vida del pedido, gobernando los siguientes estados estrictos:

1. **PENDIENTE_RESERVA:** Estado efímero en memoria mientras se verifica el stock vía gRPC.
2. **PENDIENTE_PAGO:** (Fase 2) Stock físico bloqueado. La orden aguarda la validación asíncrona por parte de `ms-payment`.
3. **CONFIRMADO:** Pago exitoso o confirmación B2B offline. La orden ya es contabilizada en los cierres financieros de `ms-reporter`.
4. **EN_DESPACHO:** El `ms-shipping` (o admin en Fase 1) ha marcado el pedido para envío.
5. **ENTREGADO:** Confirmación final de recepción física por el almacén B2B.
6. **CANCELADO:** Estado terminal de fallo (stock insuficiente, rechazo bancario, abandono de carrito procesado, o anulación manual).

```text
                    ┌──────────────────────────────────────────────────────────────┐
                    │           MÁQUINA DE ESTADOS DE LA ORDEN                     │
                    │                                                              │
                    │   ┌───────────────────┐  Stock OK     ┌─────────────┐       │
  POST /orders ──▶  │   │ PENDIENTE_RESERVA │ ──────────▶  │ CONFIRMADO¹ │       │
                    │   │   (gRPC sync)     │              └─────────────┘       │
                    │   └───────────────────┘                    │                │
                    │       │                                    │ Admin:         │
                    │       │ Stock                              │ PUT /status    │
                    │       │ Insuficiente                       ▼                │
                    │       │ (fail-fast)              ┌─────────────┐           │
                    │       ▼                          │ EN_DESPACHO │           │
                    │   ┌───────────┐                  └─────────────┘           │
                    │   │ CANCELADO │                        │                   │
                    │   └───────────┘                        │ Admin:            │
                    │       ▲                                │ PUT /status       │
                    │       │ PaymentFailed                   ▼                   │
                    │       │ o Cancel manual          ┌───────────┐             │
                    │       │                          │ ENTREGADO │             │
                    │       │                          └───────────┘             │
                    │   ┌───────────────┐                                        │
                    │   │ PENDIENTE_PAGO│  (Fase 2: entre reserva y confirmación)│
                    │   └───────────────┘                                        │
                    └──────────────────────────────────────────────────────────────┘

¹ En Fase 1: PENDIENTE_RESERVA → CONFIRMADO (directo, pago B2B offline)
  En Fase 2: PENDIENTE_RESERVA → PENDIENTE_PAGO → CONFIRMADO (con ms-payment)
```

**Transiciones válidas:**

| Desde               | Hacia            | Trigger                                           |
| ------------------- | ---------------- | ------------------------------------------------- |
| `PENDIENTE_RESERVA` | `CONFIRMADO`     | gRPC exitoso (Fase 1) / PaymentProcessed (Fase 2) |
| `PENDIENTE_RESERVA` | `PENDIENTE_PAGO` | gRPC exitoso + hay ms-payment (Fase 2)            |
| `PENDIENTE_RESERVA` | `CANCELADO`      | Stock insuficiente (gRPC fail-fast)               |
| `PENDIENTE_PAGO`    | `CONFIRMADO`     | PaymentProcessed vía Kafka (Fase 2)               |
| `PENDIENTE_PAGO`    | `CANCELADO`      | PaymentFailed vía Kafka (Fase 2)                  |
| `CONFIRMADO`        | `EN_DESPACHO`    | Admin marca como despachado                       |
| `CONFIRMADO`        | `CANCELADO`      | Admin o cliente cancela antes de despacho         |
| `EN_DESPACHO`       | `ENTREGADO`      | Admin marca como entregado                        |

**Transiciones INVÁLIDAS (rechazadas por validación):**

- `ENTREGADO` → cualquiera (estado terminal)
- `CANCELADO` → cualquiera (estado terminal)
- `EN_DESPACHO` → `CONFIRMADO` (no se puede retroceder)

**Cada transición de estado genera:**

1. Actualización en `orders.status`
2. Registro en `order_state_history` (auditoría)
3. Evento a Kafka → `ms-notifications` envía email (HU6)

---

## 8. Patrones Arquitectónicos Implementados

El ecosistema hace uso de patrones avanzados de microservicios para garantizar resiliencia B2B:

### 8.1 Saga Secuencial (Orquestación Pasiva)

El flujo transaccional fluye de Catálogo → Inventario → Pago. `ms-order` es el dueño del estado, pero delega el trabajo asíncrono a sus pares mediante Apache Kafka. La validación síncrona de stock por gRPC elimina la latencia de la Saga en el paso más crítico (prevención de sobreventa).

**Saga en Fase 1 (MVP) — 2 pasos (gRPC + Kafka):**

| Paso | Servicio   | Acción         | Mecanismo | Compensación                    |
| ---- | ---------- | -------------- | --------- | ------------------------------- |
| 1    | `ms-order` | Reserva stock  | gRPC sync | N/A (fail-fast si no hay stock) |
| 2    | `ms-order` | Confirma orden | Local     | N/A                             |

**Saga en Fase 2 (Completa) — 3 pasos:**

| Paso | Servicio     | Acción                | Mecanismo   | Compensación                 |
| ---- | ------------ | --------------------- | ----------- | ---------------------------- |
| 1    | `ms-order`   | Reserva stock         | gRPC sync   | Fail-fast si no hay stock    |
| 2    | `ms-order`   | Guarda PENDIENTE_PAGO | Local       | N/A                          |
| 3    | `ms-payment` | Procesa pago          | Kafka async | `ReleaseStock` si pago falla |

### 8.2 Transactional Outbox Pattern

Implementado en **`ms-catalog`** (MongoDB), **`ms-inventory`** y **`ms-order`** (PostgreSQL).

Garantiza atomicidad entre la escritura en BD y la publicación de eventos a Kafka:

```text
┌─────────────────────────────────────────────┐
│            MISMA TRANSACCIÓN                │
│                                             │
│  1. INSERT INTO orders (...) VALUES (...)   │
│  2. INSERT INTO outbox_events               │
│     (event_type, topic, payload, status)    │
│     VALUES ('OrderConfirmed', 'order-events',│
│             :json, 'PENDING')               │
│                                             │
│  COMMIT;                                    │
└─────────────────────────────────────────────┘
        │
        │ Outbox Relay (polling periódico cada 5s)
        ▼
┌─────────────────────────────────────────────┐
│         OUTBOX RELAY                        │
│                                             │
│  1. SELECT * FROM outbox_events             │
│     WHERE status = 'PENDING'                │
│  2. Para cada evento:                       │
│     - kafkaProducer.send(topic, payload)    │
│     - UPDATE outbox_events                  │
│       SET status = 'PUBLISHED'              │
└─────────────────────────────────────────────┘
```

**Alternativa avanzada (diferida):** Debezium CDC (Change Data Capture) que lee el Write-Ahead Log de PostgreSQL para publicar eventos en tiempo real sin polling.

### 8.3 Idempotencia en Consumers

Cada consumer de Kafka implementa tracking de eventos procesados para prevenir procesamiento duplicado (Kafka garantiza _at-least-once delivery_):

- Antes de procesar un evento: verificar si `eventId` ya existe en tracking store
- Si existe: ignorar (log warning)
- Si no existe: procesar evento + guardar `eventId` en tracking store

**Almacenamiento del tracking:**

- **PostgreSQL services (`ms-inventory`, `ms-order`):** Tabla `processed_events` (eventId PK)
- **MongoDB services (`ms-notifications`):** Colección con unique index en `eventId`

### 8.4 Database per Service

Cada microservicio tiene su propia base de datos **aislada**. Ningún servicio accede directamente a la BD de otro:

| Servicio           | Base de Datos          | Motor           | Comunicación con otros servicios                       |
| ------------------ | ---------------------- | --------------- | ------------------------------------------------------ |
| `ms-catalog`       | `catalog_db`           | MongoDB + Redis | Kafka (eventos) + Redis (caché) + gRPC Server (Fase 2) |
| `ms-inventory`     | `inventory_db`         | PostgreSQL      | Kafka (eventos) + gRPC Server (reserva stock)          |
| `ms-order`         | `order_db`             | PostgreSQL      | Kafka (eventos) + gRPC Client (→ ms-inventory)         |
| `ms-notifications` | `notifications_db`     | MongoDB         | Kafka (consumer) + AWS SES (email)                     |
| `ms-cart`          | `cart_db`              | MongoDB         | Kafka (eventos) + gRPC Client (→ ms-catalog)           |
| `ms-payment`       | `payment_db`           | PostgreSQL      | Kafka (consumer/producer) + Pasarelas externas         |
| `ms-reporter`      | `reporter_db` + AWS S3 | PostgreSQL      | Kafka (consumer de TODOS los eventos)                  |
| `ms-shipping`      | `shipping_db`          | PostgreSQL      | Kafka + API Logística externa                          |
| `ms-provider`      | `provider_db`          | PostgreSQL      | Kafka + APIs de Proveedores (Webhooks)                 |

### 8.5 Cache-Aside Pattern (Redis)

Implementado en **`ms-catalog`** para optimizar lecturas del catálogo de productos.

- **Cache HIT (95%):** Retorna desde Redis (<1ms latency)
- **Cache MISS (5%):** Query a MongoDB (~10ms), guarda en Redis con TTL 1 hora
- **Invalidación:** Al crear/actualizar producto → elimina key de Redis + publica evento a Kafka
- **Throughput:** 10,000+ req/s (vs ~5,000 req/s solo MongoDB)

### 8.6 CQRS & Event Sourcing (Fase 3)

Implementado en `ms-reporter`. Separa el modelo de lectura analítico del transaccional. Consume todos los eventos de Kafka, los guarda como inmutables (Event Sourcing en `JSONB`) y genera vistas preparadas (OLAP) para reportes de 500MB hacia S3 sin afectar las bases transaccionales.

### 8.7 Strangler Fig Pattern (Fase 3)

Implementado en `ms-shipping`. Intercepta las llamadas de envío y, de forma progresiva, reemplaza el código del monolito legacy de cotización de envíos por la nueva lógica distribuida.

### 8.8 Circuit Breaker & Bulkhead

Usando _Resilience4j_ en `ms-payment` y `ms-shipping`. Evita fallos en cascada aislando el pool de hilos si las pasarelas de pago o las APIs logísticas presentan degradación de servicio.

**Configuración del Circuit Breaker:**

- Umbral de fallo: 50% (abre circuit si 50% de requests fallan)
- Duración en estado Open: 30 segundos
- Ventana de evaluación: últimos 10 requests
- Reintentos: 3 intentos con backoff exponencial (2s, 4s, 8s)

### 8.9 Zero Trust (Perimeter Security)

Tras la eliminación del patrón BFF, el `API Gateway` funge como muralla. Valida JWTs con _Microsoft Entra ID_, propaga la identidad en cabeceras (`X-User-Email`) y aplica _Tenant Restrictions_ para prohibir registros de correos no corporativos (`@gmail.com`), resguardando el enfoque B2B.

---

## 9. Modelo de Datos Consolidado (Políglota)

La arquitectura aplica el principio de _Database per Service_ eligiendo la herramienta ideal para la carga de trabajo:

| Microservicio          | Motor de Persistencia   | Driver   | Justificación Arquitectónica                                                                                                 |
| :--------------------- | :---------------------- | :------- | :--------------------------------------------------------------------------------------------------------------------------- |
| **`ms-catalog`**       | **MongoDB + Redis**     | Reactivo | Velocidad ultrarrápida (Cache-Aside) en catálogos. Documentos polimórficos para anidar reseñas (Reviews) como subdocumentos. |
| **`ms-inventory`**     | **PostgreSQL**          | R2DBC    | Soporte ACID absoluto. Obligatorio para candados pesimistas (`SELECT FOR UPDATE`) al proteger el inventario.                 |
| **`ms-order`**         | **PostgreSQL**          | R2DBC    | Atomicidad requerida para gestionar estados de la Saga y la tabla Outbox.                                                    |
| **`ms-cart`**          | **MongoDB**             | Reactivo | Permite mutaciones atómicas (`$push` / `$pull`) en los arrays de carritos sin JOINs pesados.                                 |
| **`ms-payment`**       | **PostgreSQL**          | JDBC     | Rigurosidad financiera (PCI-DSS compliance) y _Unique Constraints_ para idempotencia anti-dobles cobros.                     |
| **`ms-shipping`**      | **PostgreSQL**          | JDBC     | Relacional clásico para persistencia de guías y control de webhooks logísticos.                                              |
| **`ms-provider`**      | **PostgreSQL**          | JDBC     | Relacional para registro de órdenes de compra a proveedores.                                                                 |
| **`ms-notifications`** | **MongoDB**             | Reactivo | Esquema flexible para plantillas JSON dinámicas. TTL Index nativo para limpieza automática de historial.                     |
| **`ms-reporter`**      | **PostgreSQL + AWS S3** | JDBC     | Tipos `JSONB` e índices `GIN` para Event Sourcing analítico (CQRS). Reportes masivos inmutables en S3.                       |

```text
Comunicación entre BDs: SOLO vía eventos Kafka o llamadas gRPC (nunca acceso directo cruzado)
Redis: Caché de solo-lectura para ms-catalog, invalidado por eventos ProductCreated/Updated
MongoDB: Documentos flexibles para catálogo, carritos y notificaciones
PostgreSQL: ACID estricto para inventario, órdenes, pagos y reportes
```

---

## 10. Infraestructura y Stack Tecnológico

### Stack Tecnológico

| Componente                 | Tecnología                                 | Justificación                                                 |
| -------------------------- | ------------------------------------------ | ------------------------------------------------------------- |
| **Framework Backend**      | Spring Boot 3.2 (Java 21)                  | Madurez, ecosistema Spring, soporte WebFlux y Virtual Threads |
| **Paradigma Reactivo**     | Spring WebFlux + R2DBC / Reactive Mongo    | Alta concurrencia I/O-bound para servicios core               |
| **Paradigma Imperativo**   | Spring MVC + Virtual Threads (Loom)        | SDKs bloqueantes y operaciones CPU-bound                      |
| **Comunicación Síncrona**  | gRPC (Protocol Buffers)                    | Serialización ultrarrápida en red privada                     |
| **Comunicación Asíncrona** | Apache Kafka (MSK o Docker en dev)         | Event streaming, retención, consumer groups                   |
| **BD Documental**          | MongoDB                                    | Esquemas flexibles, subdocumentos, mutaciones atómicas        |
| **BD Transaccional**       | PostgreSQL 15 (RDS)                        | ACID, relaciones, constraints, lock pesimista                 |
| **Caché**                  | Redis (AWS ElastiCache)                    | Latencia <1ms para catálogo, reducción de carga en MongoDB    |
| **Almacenamiento Objetos** | AWS S3                                     | Reportes inmutables de hasta 500MB (PDF/CSV)                  |
| **API Gateway**            | AWS API Gateway                            | Managed service, JWT validation, rate limiting, SSL           |
| **Identity Provider**      | Microsoft Entra ID / AWS Cognito           | Zero Trust, Tenant Restrictions, Federated Identities         |
| **Email**                  | AWS SES                                    | Alta entregabilidad, bajo costo para LATAM                    |
| **Resiliencia**            | Resilience4j                               | Circuit Breaker, Bulkhead, Retry con backoff                  |
| **Contenedores**           | Docker + Docker Compose (dev) / ECS (prod) | Portabilidad, consistencia entre entornos                     |
| **Logging**                | SLF4J + Logback                            | Estándar Spring Boot                                          |

### Diagrama de Infraestructura AWS

```text
┌─────────────────────────────────────────────────────────┐
│ AWS Cloud                                               │
│                                                         │
│ ┌──────────────────────────────────────────────────┐    │
│ │ VPC (10.0.0.0/16)                                │    │
│ │                                                  │    │
│ │ ┌─────────────────────────────────────────────┐  │    │
│ │ │ Subnet Pública                              │  │    │
│ │ │                                             │  │    │
│ │ │ ┌──────────────────┐                        │  │    │
│ │ │ │ API Gateway      │                        │  │    │
│ │ │ │ (Zero Trust)     │                        │  │    │
│ │ │ │ JWT + Entra ID   │                        │  │    │
│ │ │ └────────┬─────────┘                        │  │    │
│ │ └──────────┼──────────────────────────────────┘  │    │
│ │            │                                     │    │
│ │ ┌──────────┼──────────────────────────────────┐  │    │
│ │ │ Subnet Privada                              │  │    │
│ │ │          ▼                                  │  │    │
│ │ │ ┌───────────────────────────────────────┐   │  │    │
│ │ │ │ ECS Cluster                           │   │  │    │
│ │ │ │                                       │   │  │    │
│ │ │ │ ┌──────────────┐ ┌──────────────┐     │   │  │    │
│ │ │ │ │ ms-catalog   │ │ ms-inventory │     │   │  │    │
│ │ │ │ │ (WebFlux)    │ │ (WebFlux)    │     │   │  │    │
│ │ │ │ └──────────────┘ └──────────────┘     │   │  │    │
│ │ │ │        gRPC ◄──────────┤              │   │  │    │
│ │ │ │ ┌──────────────┐ ┌──────────────┐     │   │  │    │
│ │ │ │ │ ms-order     │─┤ ms-notific.  │     │   │  │    │
│ │ │ │ │ (WebFlux)    │ │ (WebFlux)    │     │   │  │    │
│ │ │ │ └──────────────┘ └──────────────┘     │   │  │    │
│ │ │ │    gRPC ──▶ ms-inventory              │   │  │    │
│ │ │ └───────────────────────────────────────┘   │  │    │
│ │ │                                             │  │    │
│ │ │ ┌───────────────────────────────────────┐   │  │    │
│ │ │ │ Apache Kafka (MSK o self-hosted)      │   │  │    │
│ │ │ │ 3 brokers (o 1 en dev)               │   │  │    │
│ │ │ └───────────────────────────────────────┘   │  │    │
│ │ │                                             │  │    │
│ │ │ ┌───────────────────────────────────────┐   │  │    │
│ │ │ │ MongoDB (Atlas o DocumentDB)          │   │  │    │
│ │ │ │ catalog_db │ notifications_db         │   │  │    │
│ │ │ └───────────────────────────────────────┘   │  │    │
│ │ │                                             │  │    │
│ │ │ ┌───────────────────────────────────────┐   │  │    │
│ │ │ │ PostgreSQL (RDS Multi-AZ)             │   │  │    │
│ │ │ │ inventory_db │ order_db               │   │  │    │
│ │ │ └───────────────────────────────────────┘   │  │    │
│ │ │                                             │  │    │
│ │ │ ┌───────────────────────────────────────┐   │  │    │
│ │ │ │ Redis (ElastiCache)                   │   │  │    │
│ │ │ │ Caché para catálogo - TTL: 1 hora     │   │  │    │
│ │ │ └───────────────────────────────────────┘   │  │    │
│ │ └─────────────────────────────────────────────┘  │    │
│ └──────────────────────────────────────────────────┘    │
│                                                         │
│ ┌──────────────────┐  ┌──────────────────┐              │
│ │ AWS SES          │  │ AWS S3           │              │
│ │ (Email)          │  │ (Reportes Fase 3)│              │
│ └──────────────────┘  └──────────────────┘              │
│  Servicios regionales, fuera de VPC                     │
└─────────────────────────────────────────────────────────┘
```

---

## 11. Seguridad

| Aspecto                   | Implementación                                                              |
| ------------------------- | --------------------------------------------------------------------------- |
| **Modelo de Seguridad**   | Zero Trust — API Gateway como único punto expuesto a internet               |
| **Autenticación**         | JWT validado contra Microsoft Entra ID / AWS Cognito (Federated Identities) |
| **Tenant Restrictions**   | Bloqueo de dominios públicos (`@gmail.com`) para garantizar enfoque B2B     |
| **Autorización**          | RBAC con 2 roles: CUSTOMER (cliente B2B), ADMIN (personal interno)          |
| **HTTPS**                 | Obligatorio vía API Gateway (SSL Termination)                               |
| **Propagación Identidad** | Header `X-User-Email` inyectado por API Gateway hacia la VPC privada        |
| **BD protegida**          | PostgreSQL y MongoDB en subnet privada (no accesible desde internet)        |
| **Kafka protegido**       | MSK en subnet privada con SASL authentication                               |
| **Secrets**               | AWS Secrets Manager para credenciales de BD y configuración sensible        |
| **Rate Limiting**         | 100 req/s por IP en API Gateway                                             |
| **Validación de input**   | Bean Validation (@NotNull, @Size, @Positive) en cada servicio               |
| **Microservicios**        | 100% stateless — no almacenan sesión ni tokens localmente                   |

---

## 12. Métricas de Éxito

| Métrica                     | Objetivo        | Medición                                                                                           |
| --------------------------- | --------------- | -------------------------------------------------------------------------------------------------- |
| **Tasa de Sobreventa**      | **0% estricto** | Órdenes confirmadas con stock negativo (DEBE SER CERO). Asegurado por `SELECT FOR UPDATE` y gRPC   |
| **Protección Financiera**   | **0%**          | Mercancía despachada sin confirmación de pago (Saga Secuencial)                                    |
| **Disponibilidad**          | 99.5%           | Uptime del API Gateway                                                                             |
| **Latencia API (p95)**      | <1s             | Tiempo de respuesta en API Gateway                                                                 |
| **Latencia Catálogo (p95)** | <1ms            | Tiempo desde Redis (Cache-Aside)                                                                   |
| **Notificaciones enviadas** | >95%            | Emails enviados vs cambios de estado ocurridos                                                     |
| **Rendimiento Analítico**   | Sin impacto     | Latencia del Core Transaccional inalterada durante generación de reportes OLAP pesados (CQRS + S3) |

---

## 13. Decisiones Arquitectónicas Consolidadas

| #   | Decisión                                         | Justificación                                                                      | Trade-off                                                     |
| --- | ------------------------------------------------ | ---------------------------------------------------------------------------------- | ------------------------------------------------------------- |
| 1   | **9 microservicios** en 4 fases                  | Entrega incremental de valor. MVP con 4 servicios, resto iterativamente            | Complejidad operacional creciente por fase                    |
| 2   | **WebFlux vs Virtual Threads (híbrido)**         | No forzar 100% reactivo. WebFlux para I/O-bound, Loom para CPU-bound/SDKs legacy   | Dos paradigmas coexistiendo; requiere claridad por equipo     |
| 3   | **Eliminación permanente del BFF**               | API Gateway asume seguridad y enrutamiento. Simplifica topología de red            | Respuestas no optimizadas por plataforma (Web/Mobile)         |
| 4   | **MongoDB para `ms-catalog`**                    | Documentos polimórficos para reseñas anidadas. Cache-Aside con Redis               | Sin JOINs relacionales; consistencia eventual en catálogo     |
| 5   | **MongoDB para `ms-notifications`**              | Esquema flexible para plantillas JSON. TTL Index nativo para limpieza automática   | No es PostgreSQL (pero no requiere ACID para notificaciones)  |
| 6   | **gRPC para comunicación síncrona interna**      | Serialización ultrarrápida (Protobuf). Vital para reserva de stock en milisegundos | Mayor complejidad de contratos vs REST; requiere Proto files  |
| 7   | **Separación Catálogo e Inventario**             | Bounded Contexts distintos (DDD). Catálogo = lecturas masivas. Inventario = ACID   | Dos servicios donde uno podría bastar en negocio simple       |
| 8   | **Reseñas como subdocumentos en `ms-catalog`**   | Elimina un microservicio completo. Aprovecha modelo documental de MongoDB          | Límite de 16MB por documento MongoDB (suficiente para B2B)    |
| 9   | **Zero Trust en API Gateway**                    | Entra ID / Cognito valida tokens. Tenant Restrictions bloquea `@gmail.com`         | Dependencia de IdP externo para autenticación                 |
| 10  | **Outbox con polling** (no Debezium)             | Simplicidad, sin dependencias extra                                                | Latencia máxima adicional de 5s por ciclo de polling          |
| 11  | **Kafka como único broker** (no SQS/EventBridge) | Un solo broker simplifica la operación; suficiente para todas las fases            | Sin scheduling nativo; compensado con jobs periódicos         |
| 12  | **Saga simplificada en Fase 1** (gRPC + Kafka)   | gRPC sync para stock + Kafka async para notificaciones. Sin Payment = menos fallos | Pago B2B offline; integración con pasarelas diferida a Fase 2 |

---

## 14. Resumen Ejecutivo

El diseño y entrega evolutiva (4 Fases) de la arquitectura del **Backend de Arka** resuelve de raíz las problemáticas más punzantes para la expansión regional B2B de la compañía. Al segmentar la solución técnica:

- Se **erradica completamente la sobreventa** (el dolor #1 de la empresa) combinando transacciones ultracortas, locks pesimistas en PostgreSQL y validaciones síncronas por gRPC.
- Se **protege el estado financiero** del ecosistema utilizando Sagas Secuenciales coordinadas por Kafka, asegurando que ningún cliente sea cobrado sin que su mercancía esté físicamente separada en la bodega.
- Se dota a la directiva de **analítica profunda y automatizada** mediante el patrón CQRS y Event Sourcing en el servicio de reportes, aislando la carga pesada de inteligencia de negocios para que nunca ralentice el núcleo transaccional de ventas.

Esta arquitectura políglota, dirigida por eventos y regida bajo una estricta topología _Zero Trust_, proporciona una fundación escalable y altamente cohesiva capaz de soportar la exigente carga del mercado corporativo latinoamericano.

---

## 15. Referencias

- [Definición de Contexto de Negocio - Arka](contexto-negocio-arka-extra.md) — Fuente de verdad: acuerdos de integración, diagramas C1/C2
- [Arquitectura Backend Arka - C4 Nivel 2](arquitectura-backend-arka-c4-nivel2.md) — Documento de arquitectura completa C4
- [MVP Alternativo - Fases de Entrega](mvp-alternativo.md) — Estrategia de fases y patrones refinados
- [Backlog del Proyecto Java Backend Arka](<../assets/PDFs/Backlog%20del%20proyecto%20Java%20Backend%20Arka%20(MD).md>) — Historias de usuario y priorización
- [Proyecto Arka 1](<../assets/PDFs/Proyecto%20Arka%201%20(MD).md>) — Definición de módulos y actividades
- [Proyecto Java Backend Reto V2](<../assets/PDFs/Proyecto%20Java%20Backend%20Reto%20V2%20(MD).md>) — Descripción del reto y necesidades del negocio
- [Microservices Patterns - Chris Richardson](https://microservices.io/patterns/)
- [Saga Pattern - Microsoft Azure](https://learn.microsoft.com/en-us/azure/architecture/patterns/saga)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Resilience4j Guide](https://resilience4j.readme.io/)
- [gRPC Documentation](https://grpc.io/docs/)
