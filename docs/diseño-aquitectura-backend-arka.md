# Arquitectura Backend Arka - Estrategia de Entrega y Diseño del Sistema

**Proyecto:** Arka - Plataforma B2B de Distribución de Accesorios para PC
**Stack Técnico:** Java 21 (WebFlux & Virtual Threads), Apache Kafka, PostgreSQL 17, MongoDB, Redis, gRPC, AWS (API Gateway, S3, SES)

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
  - `ms-inventory` (Reactivo): Dueño del stock. Utiliza _Lock Pesimista_ en PostgreSQL 17. Resuelve la **HU2** (Actualizar stock)
  - `ms-order` (Reactivo): Máquina de estados. Orquestador pasivo de la Saga. Resuelve la **HU4** (Registrar orden)
  - `ms-notifications` (Reactivo): Motor pasivo de correos integrándose a AWS SES. Resuelve la **HU6** (Notificación de estados)
- **Infraestructura Desplegada:** AWS API Gateway (Zero Trust / Entra ID), Apache Kafka, PostgreSQL 17, MongoDB y Redis
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
| **PostgreSQL 17**      | Database per Service para `ms-inventory` y `ms-order` (ACID crítico) |
| **MongoDB**            | Database per Service para `ms-catalog` y `ms-notifications`          |
| **Redis**              | Caché de catálogo para lecturas de alta frecuencia (Cache-Aside)     |

**Qué NO INCLUYE esta fase (diferido a fases posteriores):**

| Componente Diferido | HU / Razón                                              | Fase |
| ------------------- | ------------------------------------------------------- | ---- |
| `ms-cart`           | HU8 - Carritos abandonados                              | 2    |
| `ms-payment`        | Cierre financiero con pasarelas externas                | 2    |
| `ms-reporter`       | HU7 - Reportes semanales / HU3 - Reportes stock bajo    | 3    |
| `ms-shipping`       | Despachos y ACL logística (DHL, FedEx, Legacy)          | 3    |
| `ms-provider`       | Gestión automatizada de órdenes de compra a proveedores | 4    |
| Frontend            | Excluido explícitamente del alcance backend             | -    |
| Patrón BFF          | Descartado permanentemente de la arquitectura           | -    |

> **Nota sobre el pago en Fase 1:** Los clientes de Arka son almacenes (modelo B2B), por lo que se utiliza facturación diferida con términos a 30-60 días. En la Fase 1, el pago se gestiona como proceso externo (transferencia bancaria o facturación B2B). Las órdenes confirmadas por `ms-inventory` (stock reservado) transicionan automáticamente a `CONFIRMADO`. La validación automática de pago con pasarelas se incorpora en la Fase 2 con `ms-payment`.

### 💳 Fase 2: Autogestión B2B y Cierre Financiero (Saga Completa)

Con el inventario seguro, se introduce la gestión temporal de la compra y la automatización bancaria para cerrar el ciclo contable.

- **Microservicios Entregados:**
  - `ms-cart` (Reactivo): Sesiones y persistencia temporal en MongoDB usando mutaciones atómicas (`$push`/`$pull`). Resuelve la **HU8** (Carritos abandonados)
  - `ms-payment` (Reactivo / WebFlux): Capa Anti-Corrupción (ACL) para interactuar con pasarelas (Stripe, Wompi, Mercado Pago). Las llamadas bloqueantes a SDKs se envuelven en `Schedulers.boundedElastic()`. Resuelve la **HU5** (Modificar orden)
- **Evolución Arquitectónica:** La Saga Secuencial se completa: **Catálogo → Inventario → Pago**. Si `ms-payment` falla al cobrar, emite un evento de compensación por Kafka y `ms-inventory` libera el stock. Las órdenes ahora pasan por el estado `PENDIENTE_PAGO` antes de confirmarse.
- **Valor de Negocio:** Se reducen las pérdidas por abandono y se automatiza la conciliación de pagos. Las integraciones bloqueantes con pasarelas se aíslan en un scheduler dedicado sin abandonar el modelo reactivo del ecosistema

### 📈 Fase 3: Analítica Avanzada y Logística (CQRS & ACL Logística)

Se entrega la capacidad de análisis masivo para la directiva y se integra la logística de envíos con operadores externos.

- **Microservicios Entregados:**
  - `ms-reporter` (Imperativo / Virtual Threads): Data Lake de la arquitectura que consume todos los eventos de Kafka (Event Sourcing). Usa índices GIN y JSONB en PostgreSQL 17. Exporta PDF/CSV pesados (hasta 500MB) a **AWS S3**. Resuelve la **HU7** (Ventas semanales) y **HU3** (Reporte stock bajo)
  - `ms-shipping` (Reactivo / WebFlux): Capa Anti-Corrupción (ACL) para integrarse con operadores logísticos externos (DHL, FedEx) y el monolito legacy de cotización de envíos. Las llamadas bloqueantes a APIs externas se envuelven en `Schedulers.boundedElastic()`. Consume eventos de `order-events` y coordina con las APIs externas para gestionar despachos
- **Valor de Negocio:** Operaciones puede tomar decisiones estratégicas sin tumbar la base de datos de ventas (OLTP). El área logística gestiona despachos a través de una capa ACL que aísla al ecosistema de las particularidades de cada operador logístico.

### 🔄 Fase 4: Abastecimiento y Ecosistema Completo

Automatización de órdenes de compra a proveedores basada en umbrales de stock crítico, con notificación por correo electrónico y actualización manual de stock al recibir mercancía.

- **Microservicios Entregados:**
  - `ms-provider` (Reactivo / WebFlux): Barrera de seguridad (ACL) que consume automáticamente el evento `StockDepleted` de `ms-inventory` y genera una orden de compra dirigida al proveedor correspondiente. Publica `PurchaseOrderCreated` con todos los datos necesarios para que `ms-notifications` envíe un correo personalizado al proveedor
- **Valor de Negocio:** Cuando `ms-inventory` detecta existencias críticas (evento `StockDepleted`), `ms-provider` crea automáticamente una orden de compra y `ms-notifications` envía el correo al proveedor. Al recibir la mercancía en bodega, el administrador actualiza el stock manualmente mediante el endpoint `PUT /inventory/{sku}/stock` de `ms-inventory`. El pago de la orden de compra al proveedor se gestiona fuera del sistema (efectivo contra entrega en bodega).

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

- Alertas automáticas cuando el stock alcanza umbrales críticos (evento `StockDepleted` → `ms-notifications` envía email al administrador)
- `ms-provider` consume `StockDepleted` y genera automáticamente una orden de compra → publica `PurchaseOrderCreated` → `ms-notifications` envía email al proveedor correspondiente (Fase 4)
- Cuando el proveedor entrega la mercancía, el administrador actualiza el stock manualmente mediante `PUT /inventory/{sku}/stock`
- El pago de la orden de compra al proveedor se gestiona fuera del sistema
- Notificación por correo electrónico al administrador (alerta) y al proveedor (orden de compra) vía `ms-notifications`

---

## 4. Patrones y Decisiones Arquitectónicas (Refinadas)

Para cumplir con los lineamientos del Scaffold Clean Architecture, la escalabilidad y las restricciones de un entorno B2B, el sistema implementa los siguientes fundamentos:

### A. Seguridad Zero Trust y Descarte del BFF

El patrón BFF (Backend for Frontend) queda oficialmente **descartado** de la solución. El **API Gateway** asume la protección perimetral absoluta (_Zero Trust_). Valida los tokens JWT contra Entra ID o Cognito, aplica _Tenant Restrictions_ (bloqueando dominios públicos como `@gmail.com` para resguardar el B2B) y enruta el tráfico directamente a los microservicios, los cuales operan 100% _stateless_. Inyecta la identidad en el header `X-User-Email` hacia la VPC privada.

### B. Bases de Datos Políglotas Estrictas (Database per Service)

Cada microservicio es dueño único de su almacenamiento para evitar acoplamientos y permitir el escalado independiente.

- **MongoDB (Drivers Reactivos):** Usado por `ms-catalog` para lecturas de catálogos polimórficos ultrarrápidas con reseñas como subdocumentos, `ms-cart` para mutaciones atómicas en arrays y `ms-notifications` para almacenar plantillas JSON dinámicas e historial de correos.
- **PostgreSQL 17 (R2DBC):** Usado por `ms-inventory`, `ms-order`, `ms-payment`, `ms-shipping` y `ms-provider` con el driver no bloqueante R2DBC. `ms-reporter` usa JDBC bloqueante (paradigma imperativo con Virtual Threads). Garantiza integridad transaccional ACID, permite bloqueos pesimistas para proteger el stock y soporta vistas indexadas JSONB para CQRS.

### C. Paradigma Híbrido: Reactivo por Defecto, Imperativo solo en ms-reporter

Basado en la naturaleza de cada Bounded Context, se divide el stack:

1. **I/O-Bound (Spring WebFlux):** Alta concurrencia y baja latencia. Implementado en **todos los microservicios excepto ms-reporter**: `ms-catalog`, `ms-inventory`, `ms-order`, `ms-cart`, `ms-notifications`, `ms-payment`, `ms-shipping` y `ms-provider`. Todo acceso a base de datos usa drivers no bloqueantes (R2DBC o Reactive Mongo). Para integraciones con SDKs de terceros bloqueantes (pasarelas de pago, APIs logísticas, sistemas de proveedores), se usa `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` — esto descarga la llamada bloqueante a un thread pool dedicado sin abandonar el modelo reactivo ni colapsar el Event Loop.
2. **CPU-Bound (Spring MVC + Virtual Threads):** Implementado **únicamente en `ms-reporter`**, justificado porque genera archivos PDF/CSV de hasta 500MB en AWS S3 — una operación de computación intensiva y continua donde el modelo reactivo no aporta ventajas y sí añade complejidad de backpressure. `reactive=false` en `gradle.properties`.

### D. Comunicación Síncrona (gRPC) vs Asíncrona (Kafka)

- **Comunicaciones Síncronas Críticas:** Exclusivamente implementadas mediante **gRPC** por su alta velocidad de serialización en la red privada. Ejemplos: `ms-order` llamando a `ms-inventory` para asegurar la reserva de stock instantánea antes de iniciar procesos asíncronos; y `ms-cart` consultando a `ms-catalog` el precio actualizado previo al checkout.
- **Arquitectura Orientada a Eventos (Kafka):** El flujo transaccional fluye de forma asíncrona mediante el broker. `ms-order` coordina la **Saga Secuencial** emitiendo eventos. Todos los servicios publican eventos de dominio que `ms-reporter` consume para construir el Read Model analítico (CQRS).

### E. Resiliencia: Outbox Pattern e Idempotencia

1. **Transactional Outbox Pattern:** Para prevenir el _Dual-Write problem_, servicios como `ms-inventory` y `ms-order` insertan su evento de dominio dentro de la misma transacción PostgreSQL 17 que altera el negocio. Un relay asíncrono lo empuja a Kafka, garantizando que nunca se pierdan eventos por caídas de red. En servicios con MongoDB (`ms-catalog`), se adapta el patrón usando una colección `outbox_events` con operaciones atómicas.
2. **Idempotencia en Consumidores:** Debido a que Kafka garantiza entrega _At-least-once_, cada microservicio implementa una tabla/colección escudo (`processed_events` o _Unique Constraints_ combinados) para hacer _fail-fast_ frente a eventos duplicados, evitando descontar inventario dos veces o ejecutar cobros dobles.

### F. Estrategia de Tópicos Kafka: Un Tópico por Bounded Context (Servicio)

El ecosistema adopta la convención de **un único tópico de Kafka por microservicio productor** (alineado a su _Bounded Context_), en lugar de crear un tópico por cada tipo de evento individual. Dentro de cada tópico, los distintos tipos de evento se discriminan mediante el campo `eventType` del sobre (_envelope_) estándar y opcionalmente por el header de Kafka `ce_type` (CloudEvents-compatible).

#### Justificación

| Criterio                                    | Tópico por Evento (❌ descartado)                      | Tópico por Servicio (✅ adoptado)                                                  |
| ------------------------------------------- | ------------------------------------------------------ | ---------------------------------------------------------------------------------- |
| **Cantidad de tópicos**                     | 13+ tópicos (crece con cada nuevo evento)              | **7 tópicos fijos** (crece solo si se agrega un nuevo microservicio)               |
| **Complejidad operacional**                 | Alta: más ACLs, más particiones, más monitoreo         | **Baja:** un tópico por equipo/servicio owner                                      |
| **Ordenamiento de eventos**                 | Sin garantía de orden entre tópicos del mismo dominio  | **Garantizado por partición** usando `aggregateId` como key (ej: `orderId`, `sku`) |
| **Consumer management**                     | Un consumer por tópico o wildcard frágil               | **Un consumer group por servicio** suscrito a los tópicos que le interesan         |
| **Evolución del esquema**                   | Nuevo evento = nuevo tópico + configuración + permisos | **Nuevo evento = nuevo `eventType`** dentro del tópico existente, sin cambio infra |
| **Filtrado en consumidores**                | Implícito (cada tópico = 1 evento)                     | Explícito: consumer filtra por `eventType` e ignora eventos irrelevantes           |
| **Consistencia causal por bounded context** | Fragmentada entre múltiples tópicos                    | **Natural:** todos los eventos de un dominio fluyen por un solo canal ordenado     |

#### Convención de Nombrado

```text
<dominio>-events
```

Donde `<dominio>` corresponde al nombre del _Bounded Context_ propietario del tópico: `product`, `inventory`, `order`, `cart`, `payment`, `shipping`, `provider`.

#### Tópicos Resultantes (7 total)

`product-events` · `inventory-events` · `order-events` · `cart-events` · `payment-events` · `shipping-events` · `provider-events`

#### Discriminación de Eventos dentro del Tópico

Cada mensaje publicado en un tópico usa el **sobre estándar** (ver sección 5.8) donde el campo `eventType` identifica el tipo de evento concreto. Los consumidores deben:

1. **Deserializar el sobre** para leer `eventType`
2. **Filtrar** los eventos que les corresponden (ej: `ms-inventory` solo procesa `ProductCreated` del tópico `product-events`, ignora `ProductUpdated` si no le compete)
3. **Ignorar eventos desconocidos** con log de warning (tolerancia a evolución)

#### Particionamiento

Cada productor usa el **ID del agregado raíz** como partition key de Kafka:

| Tópico             | Partition Key     | Garantía                                                          |
| ------------------ | ----------------- | ----------------------------------------------------------------- |
| `product-events`   | `productId`       | Todos los eventos de un producto van a la misma partición (orden) |
| `inventory-events` | `sku`             | Movimientos del mismo SKU ordenados causalmente                   |
| `order-events`     | `orderId`         | Ciclo de vida completo de una orden en orden estricto             |
| `cart-events`      | `cartId`          | Eventos del mismo carrito ordenados                               |
| `payment-events`   | `orderId`         | Eventos de pago correlacionados con la orden                      |
| `shipping-events`  | `orderId`         | Eventos de envío correlacionados con la orden                     |
| `provider-events`  | `purchaseOrderId` | Eventos de abastecimiento por orden de compra                     |

> **Nota sobre `ms-reporter`:** Este servicio consume TODOS los tópicos para construir su Read Model analítico (CQRS / Event Sourcing). Al suscribirse a los 7 tópicos, recibe el flujo completo de eventos del ecosistema sin requerir tópicos adicionales.

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
| **Estrategia de escalado**    | **Horizontal con caché agresivo** (Redis)                                                      | **Vertical con ACID riguroso** (PostgreSQL 17 con locks)           |
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
                               └─> PostgreSQL 17 con SELECT FOR UPDATE (lock)
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
**Base de Datos:** MongoDB (catalog_db) + Redis (Cache-Aside, TTL 1h)

**Responsabilidades principales:**

- 📦 CRUD de productos con atributos: SKU, nombre, descripción, precio, categoría
- 📂 Gestión de categorías maestras
- ⭐ Reseñas anidadas como subdocumentos dentro del documento de producto en MongoDB
- ✅ Validaciones: campos obligatorios, precio > 0, SKU único
- 📊 Publicación de eventos a Kafka: `ProductCreated`, `ProductUpdated`, `PriceChanged`
- 🔒 Cache-Aside con Redis: latencia <1ms para lecturas (95% cache hit), invalidación por write-through + TTL + eventos Kafka

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

> **Documentación detallada:** El diseño completo de ms-catalog (esquema MongoDB, colecciones, estrategia de caché, eventos y plan de implementación) se encuentra en el spec del microservicio:
>
> - [Requisitos](../.kiro/specs/ms-catalog/requirements.md)
> - [Diseño técnico](../.kiro/specs/ms-catalog/design.md)
> - [Plan de tareas](../.kiro/specs/ms-catalog/tasks.md)

---

### 5.3 `ms-inventory` (Servicio de Inventario) — HU2

**Paradigma:** Reactivo (Java 21 + Spring WebFlux + R2DBC).
**HU cubierta:** HU2 - Actualizar stock de productos
**Base de Datos:** PostgreSQL 17 (db_inventory) con R2DBC

**Responsabilidades principales:**

- 📊 Control de stock en tiempo real por SKU con constraints `quantity >= 0` y `reserved_quantity >= 0` a nivel de BD
- 🔒 Reserva temporal de stock con lock pesimista (`SELECT ... FOR UPDATE`) y timeout de 15 minutos — resuelve el problema crítico #1 de Arka (sobreventa por concurrencia)
- 📝 Historial inmutable de movimientos de stock (`stock_movements`) con trazabilidad completa
- ⏰ Liberación automática de reservas expiradas (job periódico cada 60s)
- 🔗 Servidor gRPC para reserva síncrona de stock desde `ms-order`
- 🔔 Publicación de eventos a Kafka vía Transactional Outbox Pattern: `StockReserved`, `StockReserveFailed`, `StockReleased`, `StockUpdated`, `StockDepleted`
- 🎯 Umbral de alerta de stock bajo (`depletion_threshold`) configurable por producto — cada SKU tiene su propio umbral según volumen de ventas y disponibilidad típica
- 🆔 Idempotencia en consumidores Kafka mediante tabla `processed_events`

**Endpoints:**

| Método | Path                       | Rol requerido   | Descripción                        |
| ------ | -------------------------- | --------------- | ---------------------------------- |
| `PUT`  | `/inventory/{sku}/stock`   | ADMIN           | Actualizar stock manualmente (HU2) |
| `GET`  | `/inventory/{sku}`         | CUSTOMER, ADMIN | Consultar disponibilidad de un SKU |
| `GET`  | `/inventory/{sku}/history` | ADMIN           | Ver historial de movimientos (HU2) |

**Eventos Kafka:**

| Dirección | Evento               | Tópico             | Descripción                                     |
| --------- | -------------------- | ------------------ | ----------------------------------------------- |
| Produce   | `StockReserved`      | `inventory-events` | Stock reservado exitosamente para una orden     |
| Produce   | `StockReserveFailed` | `inventory-events` | Reserva fallida por stock insuficiente          |
| Produce   | `StockReleased`      | `inventory-events` | Stock liberado por expiración o cancelación     |
| Produce   | `StockUpdated`       | `inventory-events` | Stock actualizado manualmente por admin         |
| Produce   | `StockDepleted`      | `inventory-events` | Alerta de stock bajo (umbral por producto)      |
| Consume   | `ProductCreated`     | `product-events`   | Crea registro de stock inicial desde ms-catalog |
| Consume   | `OrderCancelled`     | `order-events`     | Libera reserva de stock de orden cancelada      |

> **Documentación detallada:** El diseño completo de ms-inventory (esquema SQL, entidades de dominio, casos de uso, flujos de reserva, propiedades de correctitud y plan de implementación) se encuentra en el spec del microservicio:
>
> - [Requisitos](../.kiro/specs/ms-inventory/requirements.md)
> - [Diseño técnico](../.kiro/specs/ms-inventory/design.md)
> - [Plan de tareas](../.kiro/specs/ms-inventory/tasks.md)
> - [Script SQL](../postgresql-scripts/init_inventory.sql)

---

### 5.4 `ms-order` (Servicio de Órdenes) — HU4

**Paradigma:** Reactivo (Java 21 + Spring WebFlux + R2DBC).
**HU cubierta:** HU4 - Registrar una orden de compra
**Base de Datos:** PostgreSQL 17 (db_order) con R2DBC

**Responsabilidades principales:**

- 📝 Creación de pedidos con múltiples productos — validación síncrona de stock vía gRPC a `ms-inventory`
- 🎯 Máquina de estados del pedido: `PENDIENTE_RESERVA` → `CONFIRMADO` → `EN_DESPACHO` → `ENTREGADO` (o `CANCELADO`). Fase 2 agrega `PENDIENTE_PAGO`
- 🔄 Orquestador pasivo de la Saga Secuencial (Catálogo → Inventario → Pago)
- 📊 Publicación de eventos a Kafka: `OrderCreated`, `OrderConfirmed`, `OrderStatusChanged`, `OrderCancelled`
- 📋 Auditoría de transiciones de estado en tabla `order_state_history`

**Endpoints:**

| Método | Path                  | Rol requerido   | Descripción                                        |
| ------ | --------------------- | --------------- | -------------------------------------------------- |
| `POST` | `/orders`             | CUSTOMER        | Crear orden de compra (HU4)                        |
| `GET`  | `/orders/{id}`        | CUSTOMER, ADMIN | Consultar detalle de una orden                     |
| `GET`  | `/orders`             | CUSTOMER, ADMIN | Listar órdenes (filtros: status, customerId)       |
| `PUT`  | `/orders/{id}/status` | ADMIN           | Cambiar estado (dispatch, deliver)                 |
| `PUT`  | `/orders/{id}/cancel` | CUSTOMER, ADMIN | Cancelar orden (solo si PENDIENTE_PAGO/CONFIRMADO) |

> **Documentación detallada:** El diseño completo de ms-order (esquema SQL, máquina de estados, flujo de Saga, eventos y plan de implementación) se encuentra en el spec del microservicio:
>
> - [Requisitos](../.kiro/specs/ms-order/requirements.md)
> - [Diseño técnico](../.kiro/specs/ms-order/design.md)
> - [Plan de tareas](../.kiro/specs/ms-order/tasks.md)

---

### 5.5 `ms-notifications` (Servicio de Notificaciones) — HU6

**Paradigma:** Reactivo (Java 21 + Spring WebFlux).
**HU cubierta:** HU6 - Notificación de cambio de estado del pedido
**Base de Datos:** MongoDB (notifications_db)

**Responsabilidades principales:**

- 📧 Envío de emails transaccionales mediante AWS SES para cada cambio de estado del pedido
- 🔔 Consumidor "Catch-All" pasivo: escucha múltiples tópicos de Kafka, mapea datos a plantillas y dispara correos
- 🔄 Reintentos con backoff exponencial ante fallos de AWS SES
- 🆔 Idempotencia garantizada mediante unique index en `eventId` (MongoDB)

**Eventos consumidos (Fase 1):**

| Evento consumido     | Tópico             | Acción                                 |
| -------------------- | ------------------ | -------------------------------------- |
| `OrderConfirmed`     | `order-events`     | Email de confirmación al cliente       |
| `OrderStatusChanged` | `order-events`     | Email con nuevo estado                 |
| `OrderCancelled`     | `order-events`     | Email de cancelación con motivo        |
| `StockDepleted`      | `inventory-events` | Email de alerta de stock bajo al admin |

**Eventos adicionales (fases posteriores):** `ShippingDispatched` (Fase 3), `PurchaseOrderCreated` (Fase 4), `CartAbandoned` (Fase 2)

> **Documentación detallada:** El diseño completo de ms-notifications (esquema MongoDB, plantillas, flujo de idempotencia y plan de implementación) se encuentra en el spec del microservicio:
>
> - [Requisitos](../.kiro/specs/ms-notifications/requirements.md)
> - [Diseño técnico](../.kiro/specs/ms-notifications/design.md)
> - [Plan de tareas](../.kiro/specs/ms-notifications/tasks.md)

---

### 5.6 `ms-cart` (Gestión de Carritos) — HU8 (Fase 2)

**Paradigma:** Reactivo (WebFlux + Reactive Mongo Driver).

**Responsabilidades:**

- 🛒 Gestión de carritos temporales con items
- **Base de Datos:** **MongoDB**. Aprovecha las mutaciones atómicas (`$push` / `$pull`) para agregar o quitar items de los arreglos del carrito en operaciones concurrentes
- **Comunicación:** Consulta en tiempo real (Síncrono vía **gRPC**) a `ms-catalog` para garantizar que el precio de checkout sea exacto
- Implementa un motor (CronJob) que detecta carritos expirados y publica el evento `CartAbandoned` a Kafka

---

### 5.7 ms-reporter — Único Servicio Imperativo (Virtual Threads — Fase 3)

- **`ms-reporter` (Fase 3):** Imperativo (Spring MVC + Virtual Threads). CQRS y Event Sourcing en PostgreSQL 17 (usando `JSONB` y GIN Index). Realiza agregaciones pesadas (CPU-bound) exportando excels/PDFs de hasta 500MB hacia **AWS S3** como objetos inmutables.

### 5.7.1 ms-payment, ms-shipping y ms-provider — Reactivos con ACL (Fases 2, 3 y 4)

- **`ms-payment` (Fase 2):** Reactivo (WebFlux). Actúa como Capa Anti-Corrupción (ACL). Usa PostgreSQL 17 con R2DBC e idempotencia rigurosa (_Unique Constraints_ combinados para evitar cobros dobles). Las llamadas bloqueantes a SDKs de pasarelas (Stripe, Wompi, Mercado Pago) se aíslan con `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())`, sin abandonar el modelo reactivo del ecosistema. Implementa **Circuit Breaker & Bulkhead** con _Resilience4j_.
- **`ms-shipping` (Fase 3):** Reactivo (WebFlux) con PostgreSQL 17 (R2DBC). Capa Anti-Corrupción (ACL) que se integra con APIs de operadores logísticos (FedEx, DHL) y el monolito legacy de envíos. Las llamadas bloqueantes a APIs externas se aíslan con `Schedulers.boundedElastic()`. Consume `OrderStatusChanged` (EN*DESPACHO) desde `order-events` para coordinar el despacho con el operador logístico correspondiente y publica `ShippingDispatched` con datos de tracking. Implementa **Circuit Breaker** con \_Resilience4j*.
- **`ms-provider` (Fase 4):** Reactivo (WebFlux) con PostgreSQL 17 (R2DBC). Barrera ACL que consume automáticamente el evento `StockDepleted` de `inventory-events` y genera una orden de compra al proveedor correspondiente. Publica `PurchaseOrderCreated` a Kafka con todos los detalles necesarios para que `ms-notifications` envíe un correo personalizado al proveedor. El proceso de recepción de mercancía **no está automatizado**: cuando los productos llegan a bodega, el administrador actualiza el stock manualmente vía `PUT /inventory/{sku}/stock`. El pago al proveedor se gestiona fuera del sistema.

---

### 5.8 Apache Kafka (Message Broker)

**Tipo:** Plataforma de Event Streaming
**Justificación:** Kafka es el **nervio central** que habilita la comunicación asíncrona entre microservicios, la Saga Pattern y el desacoplamiento temporal. Sin Kafka, cada servicio tendría que llamar síncronamente a los otros, generando acoplamiento fuerte y fallos en cascada.

**Estrategia de tópicos:** Un tópico por _Bounded Context_ (microservicio productor). El tipo de evento se discrimina por el campo `eventType` del sobre estándar (ver sección 4.F para justificación completa y estrategia de particionamiento).

**Tópicos del ecosistema (7 total):**

| Tópico             | Productor(es)  | Consumidor(es)                                                                 | Eventos (discriminados por `eventType`)                                                 |
| ------------------ | -------------- | ------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------- |
| `product-events`   | `ms-catalog`   | `ms-inventory`, `ms-reporter`                                                  | `ProductCreated`, `ProductUpdated`, `PriceChanged`                                      |
| `order-events`     | `ms-order`     | `ms-inventory`, `ms-notifications`, `ms-payment`, `ms-shipping`, `ms-reporter` | `OrderCreated`, `OrderConfirmed`, `OrderStatusChanged`, `OrderCancelled`                |
| `inventory-events` | `ms-inventory` | `ms-notifications`, `ms-provider`, `ms-reporter`                               | `StockReserved`, `StockReserveFailed`, `StockReleased`, `StockDepleted`, `StockUpdated` |
| `cart-events`      | `ms-cart`      | `ms-notifications`, `ms-reporter`                                              | `CartAbandoned` (Fase 2)                                                                |
| `payment-events`   | `ms-payment`   | `ms-order`, `ms-notifications`, `ms-reporter`                                  | `PaymentProcessed`, `PaymentFailed` (Fase 2)                                            |
| `shipping-events`  | `ms-shipping`  | `ms-order`, `ms-notifications`, `ms-reporter`                                  | `ShippingDispatched` (Fase 3)                                                           |
| `provider-events`  | `ms-provider`  | `ms-notifications`, `ms-reporter`                                              | `PurchaseOrderCreated` (Fase 4)                                                         |

**Tópicos activos en Fase 1 (MVP):** `product-events`, `order-events`, `inventory-events`

**Características de configuración:**

- **Particiones:** 3 por tópico (paralelismo básico)
- **Replicación:** Factor 1 en desarrollo, factor 3 en producción
- **Retención:** 7 días (168 horas)
- **Creación de tópicos:** Explícita (no auto-create)
- **Partition Key:** ID del agregado raíz (`productId`, `sku`, `orderId`, etc.) para garantizar orden causal

**Consumer Groups (MVP):**

| Consumer Group               | Servicio           | Tópicos suscritos                  | Filtro por `eventType`                                                       |
| ---------------------------- | ------------------ | ---------------------------------- | ---------------------------------------------------------------------------- |
| `inventory-service-group`    | `ms-inventory`     | `product-events`, `order-events`   | `ProductCreated` · `OrderCancelled`                                          |
| `notification-service-group` | `ms-notifications` | `order-events`, `inventory-events` | `OrderConfirmed` · `OrderStatusChanged` · `OrderCancelled` · `StockDepleted` |

**Consumer Groups (Ecosistema completo):**

| Consumer Group               | Servicio           | Tópicos suscritos                                                                                                           |
| ---------------------------- | ------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| `inventory-service-group`    | `ms-inventory`     | `product-events`, `order-events`                                                                                            |
| `order-service-group`        | `ms-order`         | `payment-events`, `shipping-events`                                                                                         |
| `notification-service-group` | `ms-notifications` | `order-events`, `inventory-events`, `cart-events`, `shipping-events`, `provider-events`                                     |
| `payment-service-group`      | `ms-payment`       | `order-events`                                                                                                              |
| `shipping-service-group`     | `ms-shipping`      | `order-events`                                                                                                              |
| `provider-service-group`     | `ms-provider`      | `inventory-events`                                                                                                          |
| `reporter-service-group`     | `ms-reporter`      | `product-events`, `order-events`, `inventory-events`, `cart-events`, `payment-events`, `shipping-events`, `provider-events` |

**Sobre estándar de eventos (_Event Envelope_):**

Todos los eventos publicados en cualquier tópico siguen un formato unificado. El campo `eventType` permite a los consumidores discriminar qué eventos procesar y cuáles ignorar:

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

**Reglas de consumo:**

1. El consumidor deserializa el sobre y lee `eventType`
2. Si el `eventType` es relevante → procesa el `payload`
3. Si el `eventType` es desconocido → **ignora con log warning** (tolerancia a evolución del esquema)
4. Nunca fallar por un `eventType` no reconocido — esto permite agregar nuevos eventos sin romper consumidores existentes

> **Extensibilidad:** Agregar un nuevo tipo de evento a un servicio existente solo requiere publicar un nuevo `eventType` al tópico del servicio. Los consumidores existentes que no les compete lo ignoran automáticamente. Solo al agregar un microservicio completamente nuevo se crea un tópico nuevo.

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
    │    (MISMA transacción PostgreSQL 17)
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

Implementado en **`ms-catalog`** (MongoDB), **`ms-inventory`** y **`ms-order`** (PostgreSQL 17).

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

**Alternativa avanzada (diferida):** Debezium CDC (Change Data Capture) que lee el Write-Ahead Log de PostgreSQL 17 para publicar eventos en tiempo real sin polling.

### 8.3 Idempotencia en Consumers

Cada consumer de Kafka implementa tracking de eventos procesados para prevenir procesamiento duplicado (Kafka garantiza _at-least-once delivery_):

- Antes de procesar un evento: verificar si `eventId` ya existe en tracking store
- Si existe: ignorar (log warning)
- Si no existe: procesar evento + guardar `eventId` en tracking store

**Almacenamiento del tracking:**

- **PostgreSQL 17 services (`ms-inventory`, `ms-order`):** Tabla `processed_events` (eventId PK)
- **MongoDB services (`ms-notifications`):** Colección con unique index en `eventId`

### 8.4 Database per Service

Cada microservicio tiene su propia base de datos **aislada**. Ningún servicio accede directamente a la BD de otro:

| Servicio           | Base de Datos          | Motor           | Comunicación con otros servicios                                       |
| ------------------ | ---------------------- | --------------- | ---------------------------------------------------------------------- |
| `ms-catalog`       | `catalog_db`           | MongoDB + Redis | Kafka (eventos) + Redis (caché) + gRPC Server (Fase 2)                 |
| `ms-inventory`     | `inventory_db`         | PostgreSQL 17   | Kafka (eventos) + gRPC Server (reserva stock)                          |
| `ms-order`         | `order_db`             | PostgreSQL 17   | Kafka (eventos) + gRPC Client (→ ms-inventory)                         |
| `ms-notifications` | `notifications_db`     | MongoDB         | Kafka (consumer) + AWS SES (email)                                     |
| `ms-cart`          | `cart_db`              | MongoDB         | Kafka (eventos) + gRPC Client (→ ms-catalog)                           |
| `ms-payment`       | `payment_db`           | PostgreSQL 17   | Kafka (consumer/producer) + Pasarelas externas                         |
| `ms-reporter`      | `reporter_db` + AWS S3 | PostgreSQL 17   | Kafka (consumer de TODOS los eventos)                                  |
| `ms-shipping`      | `shipping_db`          | PostgreSQL 17   | Kafka (consumer de `order-events`) + API Logística externa (ACL)       |
| `ms-provider`      | `provider_db`          | PostgreSQL 17   | Kafka (consumer de `inventory-events` + producer de `provider-events`) |

### 8.5 Cache-Aside Pattern (Redis)

Implementado en **`ms-catalog`** para optimizar lecturas del catálogo de productos.

- **Cache HIT (95%):** Retorna desde Redis (<1ms latency)
- **Cache MISS (5%):** Query a MongoDB (~10ms), guarda en Redis con TTL 1 hora
- **Invalidación:** Al crear/actualizar producto → elimina key de Redis + publica evento a Kafka
- **Throughput:** 10,000+ req/s (vs ~5,000 req/s solo MongoDB)

### 8.6 CQRS & Event Sourcing (Fase 3)

Implementado en `ms-reporter`. Separa el modelo de lectura analítico del transaccional. Consume todos los eventos de Kafka, los guarda como inmutables (Event Sourcing en `JSONB`) y genera vistas preparadas (OLAP) para reportes de 500MB hacia S3 sin afectar las bases transaccionales.

### 8.7 Anti-Corruption Layer — ACL Logística (Fase 3)

Implementado en `ms-shipping`. Actúa como capa intermedia (ACL) que aísla al ecosistema de las particularidades de cada operador logístico externo (DHL, FedEx) y del monolito legacy de envíos, de forma análoga a como `ms-payment` aísla las pasarelas bancarias. Consume `OrderStatusChanged` (EN_DESPACHO) desde `order-events`, coordina con la API logística correspondiente y publica `ShippingDispatched` con los datos de tracking.

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

| Microservicio          | Motor de Persistencia      | Driver   | Justificación Arquitectónica                                                                                                 |
| ---------------------- | -------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------------------- |
| **`ms-catalog`**       | **MongoDB + Redis**        | Reactivo | Velocidad ultrarrápida (Cache-Aside) en catálogos. Documentos polimórficos para anidar reseñas (Reviews) como subdocumentos. |
| **`ms-inventory`**     | **PostgreSQL 17**          | R2DBC    | Soporte ACID absoluto. Obligatorio para candados pesimistas (`SELECT FOR UPDATE`) al proteger el inventario.                 |
| **`ms-order`**         | **PostgreSQL 17**          | R2DBC    | Atomicidad requerida para gestionar estados de la Saga y la tabla Outbox.                                                    |
| **`ms-cart`**          | **MongoDB**                | Reactivo | Permite mutaciones atómicas (`$push` / `$pull`) en los arrays de carritos sin JOINs pesados.                                 |
| **`ms-payment`**       | **PostgreSQL 17**          | JDBC     | Rigurosidad financiera (PCI-DSS compliance) y _Unique Constraints_ para idempotencia anti-dobles cobros.                     |
| **`ms-shipping`**      | **PostgreSQL 17**          | JDBC     | Relacional clásico para persistencia de guías y control de webhooks logísticos.                                              |
| **`ms-provider`**      | **PostgreSQL 17**          | JDBC     | Relacional para registro de órdenes de compra a proveedores.                                                                 |
| **`ms-notifications`** | **MongoDB**                | Reactivo | Esquema flexible para plantillas JSON dinámicas. TTL Index nativo para limpieza automática de historial.                     |
| **`ms-reporter`**      | **PostgreSQL 17 + AWS S3** | JDBC     | Tipos `JSONB` e índices `GIN` para Event Sourcing analítico (CQRS). Reportes masivos inmutables en S3.                       |

```text
Comunicación entre BDs: SOLO vía eventos Kafka o llamadas gRPC (nunca acceso directo cruzado)
Redis: Caché de solo-lectura para ms-catalog, invalidado por eventos ProductCreated/Updated
MongoDB: Documentos flexibles para catálogo, carritos y notificaciones
PostgreSQL 17: ACID estricto para inventario, órdenes, pagos y reportes
```

---

## 10. Infraestructura y Stack Tecnológico

### Stack Tecnológico

| Componente                 | Tecnología                                 | Justificación                                                 |
| -------------------------- | ------------------------------------------ | ------------------------------------------------------------- |
| **Framework Backend**      | Spring Boot 4.0.3 (Java 21)                | Madurez, ecosistema Spring, soporte WebFlux y Virtual Threads |
| **Paradigma Reactivo**     | Spring WebFlux + R2DBC / Reactive Mongo    | Alta concurrencia I/O-bound para servicios core               |
| **Paradigma Imperativo**   | Spring MVC + Virtual Threads (Loom)        | SDKs bloqueantes y operaciones CPU-bound                      |
| **Comunicación Síncrona**  | gRPC (Protocol Buffers)                    | Serialización ultrarrápida en red privada                     |
| **Comunicación Asíncrona** | Apache Kafka (MSK o Docker en dev)         | Event streaming, retención, consumer groups                   |
| **BD Documental**          | MongoDB                                    | Esquemas flexibles, subdocumentos, mutaciones atómicas        |
| **BD Transaccional**       | PostgreSQL 17 (RDS)                        | ACID, relaciones, constraints, lock pesimista                 |
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
│ │ │ │ PostgreSQL 17 (RDS Multi-AZ)          │   │  │    │
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
| **BD protegida**          | PostgreSQL 17 y MongoDB en subnet privada (no accesible desde internet)     |
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

| #   | Decisión                                         | Justificación                                                                                                                                                                 | Trade-off                                                          |
| --- | ------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------ |
| 1   | **9 microservicios** en 4 fases                  | Entrega incremental de valor. MVP con 4 servicios, resto iterativamente                                                                                                       | Complejidad operacional creciente por fase                         |
| 2   | **WebFlux vs Virtual Threads (híbrido)**         | No forzar 100% reactivo. WebFlux para I/O-bound, Loom para CPU-bound/SDKs legacy                                                                                              | Dos paradigmas coexistiendo; requiere claridad por equipo          |
| 3   | **Eliminación permanente del BFF**               | API Gateway asume seguridad y enrutamiento. Simplifica topología de red                                                                                                       | Respuestas no optimizadas por plataforma (Web/Mobile)              |
| 4   | **MongoDB para `ms-catalog`**                    | Documentos polimórficos para reseñas anidadas. Cache-Aside con Redis                                                                                                          | Sin JOINs relacionales; consistencia eventual en catálogo          |
| 5   | **MongoDB para `ms-notifications`**              | Esquema flexible para plantillas JSON. TTL Index nativo para limpieza automática                                                                                              | No es PostgreSQL 17 (pero no requiere ACID para notificaciones)    |
| 6   | **gRPC para comunicación síncrona interna**      | Serialización ultrarrápida (Protobuf). Vital para reserva de stock en milisegundos                                                                                            | Mayor complejidad de contratos vs REST; requiere Proto files       |
| 7   | **Separación Catálogo e Inventario**             | Bounded Contexts distintos (DDD). Catálogo = lecturas masivas. Inventario = ACID                                                                                              | Dos servicios donde uno podría bastar en negocio simple            |
| 8   | **Reseñas como subdocumentos en `ms-catalog`**   | Elimina un microservicio completo. Aprovecha modelo documental de MongoDB                                                                                                     | Límite de 16MB por documento MongoDB (suficiente para B2B)         |
| 9   | **Zero Trust en API Gateway**                    | Entra ID / Cognito valida tokens. Tenant Restrictions bloquea `@gmail.com`                                                                                                    | Dependencia de IdP externo para autenticación                      |
| 10  | **Outbox con polling** (no Debezium)             | Simplicidad, sin dependencias extra                                                                                                                                           | Latencia máxima adicional de 5s por ciclo de polling               |
| 11  | **Kafka como único broker** (no SQS/EventBridge) | Un solo broker simplifica la operación; suficiente para todas las fases                                                                                                       | Sin scheduling nativo; compensado con jobs periódicos              |
| 12  | **Saga simplificada en Fase 1** (gRPC + Kafka)   | gRPC sync para stock + Kafka async para notificaciones. Sin Payment = menos fallos                                                                                            | Pago B2B offline; integración con pasarelas diferida a Fase 2      |
| 13  | **Un tópico Kafka por servicio** (no por evento) | 7 tópicos vs 13+. Menos ACLs, particiones y monitoreo. Orden causal garantizado por partición dentro del bounded context. Nuevos eventos = nuevo `eventType`, sin crear infra | Consumidores deben filtrar por `eventType`; tópicos más "ruidosos" |

---

## 14. Resumen Ejecutivo

El diseño y entrega evolutiva (4 Fases) de la arquitectura del **Backend de Arka** resuelve de raíz las problemáticas más punzantes para la expansión regional B2B de la compañía. Al segmentar la solución técnica:

- Se **erradica completamente la sobreventa** (el dolor #1 de la empresa) combinando transacciones ultracortas, locks pesimistas en PostgreSQL 17 y validaciones síncronas por gRPC.
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
