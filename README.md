# Arka — Plataforma E-commerce B2B

Plataforma backend de distribución de accesorios para PC orientada al mercado B2B (Colombia/LATAM). Los clientes son almacenes que compran al por mayor y necesitan gestión automatizada de pedidos, inventario, despachos y reportes.

## Problema de Negocio

Arka enfrenta desafíos críticos de operación:

1. **Sobreventa por concurrencia** — ventas que superan el stock real por accesos simultáneos
2. **Gestión manual del inventario** — insostenible con el volumen actual
3. **Ausencia de flujo automatizado de pedidos** — tiempos de atención altos para clientes B2B
4. **Clientes desinformados** — sin visibilidad del estado de sus pedidos

## Stack Técnico

| Componente            | Tecnología                                        |
| --------------------- | ------------------------------------------------- |
| Lenguaje              | Java 21                                           |
| Framework             | Spring Boot 4.0.3 (WebFlux + MVC/Virtual Threads) |
| Reactivo              | Project Reactor                                   |
| Mensajería            | Apache Kafka 8 (KRaft, sin ZooKeeper)             |
| BD Relacional         | PostgreSQL 17 (R2DBC / JDBC)                      |
| BD Documental         | MongoDB (Reactive Drivers)                        |
| Caché                 | Redis (Cache-Aside)                               |
| Comunicación síncrona | gRPC                                              |
| Infraestructura cloud | AWS (API Gateway, S3, SES, Secrets Manager)       |
| Simulación local AWS  | LocalStack (CloudFormation)                       |
| Build                 | Gradle + Bancolombia Scaffold Plugin 4.2.0        |
| Load Balancer         | Traefik                                           |

## Arquitectura

9 microservicios bajo **Clean Architecture** (Scaffold Bancolombia), **Database per Service** y **Arquitectura Dirigida por Eventos** (EDA) con Kafka como eje central.

### Paradigma Híbrido

- **Reactivo (WebFlux):** Todos los microservicios excepto ms-reporter — ms-catalog, ms-inventory, ms-order, ms-cart, ms-notifications, ms-payment, ms-shipping, ms-provider. SDKs bloqueantes de terceros se envuelven con `Schedulers.boundedElastic()`
- **Imperativo (MVC + Virtual Threads):** **ms-reporter** (única excepción) — CPU-Bound real: generación de archivos PDF/CSV hasta 500MB exportados a AWS S3. `reactive=false` en gradle.properties

### Microservicios

| Servicio             | Dominio                                         | BD                             | Fase |
| -------------------- | ----------------------------------------------- | ------------------------------ | ---- |
| **ms-catalog**       | Catálogo de productos, reseñas anidadas         | MongoDB + Redis                | 1    |
| **ms-inventory**     | Stock, reservas, lock pesimista                 | PostgreSQL 17 (`db_inventory`) | 1    |
| **ms-order**         | Gestión de pedidos, orquestador Saga            | PostgreSQL 17 (`db_orders`)    | 1    |
| **ms-notifications** | Notificaciones transaccionales (AWS SES)        | MongoDB                        | 1    |
| **ms-cart**          | Carrito de compras, detección de abandono       | MongoDB                        | 2    |
| **ms-payment**       | Procesamiento de pagos (ACL pasarelas)          | PostgreSQL 17 (`db_payment`)   | 2    |
| **ms-reporter**      | Reportes analíticos, CQRS, Event Sourcing       | PostgreSQL 17 + AWS S3         | 3    |
| **ms-shipping**      | Logística y envíos (ACL logística)              | PostgreSQL 17                  | 3    |
| **ms-provider**      | Proveedores B2B (órdenes de compra automáticas) | PostgreSQL 17                  | 4    |

### Comunicación entre Servicios

- **gRPC (síncrono):** `ms-order → ms-inventory` (reserva de stock inmediata), `ms-cart → ms-catalog` (precio actualizado en checkout)
- **Kafka (asíncrono):** Sagas Secuenciales, eventos de dominio, Event Sourcing para ms-reporter

### Kafka Topics (1 tópico por servicio)

| Tópico             | Productor    | Eventos (`eventType`)                                                             |
| ------------------ | ------------ | --------------------------------------------------------------------------------- |
| `product-events`   | ms-catalog   | ProductCreated · ProductUpdated · PriceChanged                                    |
| `inventory-events` | ms-inventory | StockReserved · StockReserveFailed · StockReleased · StockDepleted · StockUpdated |
| `order-events`     | ms-order     | OrderCreated · OrderConfirmed · OrderStatusChanged · OrderCancelled               |
| `cart-events`      | ms-cart      | CartAbandoned                                                                     |
| `payment-events`   | ms-payment   | PaymentProcessed · PaymentFailed                                                  |
| `shipping-events`  | ms-shipping  | ShippingDispatched                                                                |
| `provider-events`  | ms-provider  | PurchaseOrderCreated                                                              |

Partition key = aggregate ID. Consumidores discriminan por campo `eventType` del envelope estándar.

### Patrones Arquitectónicos

| Patrón                    | Dónde se aplica                                                                    |
| ------------------------- | ---------------------------------------------------------------------------------- |
| **Saga Secuencial**       | ms-order (orquestador) → ms-inventory → ms-payment                                 |
| **CQRS / Event Sourcing** | ms-reporter consume todos los eventos de Kafka                                     |
| **Outbox Pattern**        | ms-order, ms-inventory (transacción BD + evento atómico)                           |
| **Cache-Aside**           | ms-catalog con Redis para lecturas de alta frecuencia                              |
| **Anti-Corruption Layer** | ms-payment (pasarelas de pago), ms-shipping (logística), ms-provider (proveedores) |
| **Circuit Breaker**       | Llamadas a servicios externos (ms-payment, ms-shipping)                            |
| **Database per Service**  | Cada microservicio es dueño exclusivo de su almacenamiento                         |
| **Zero Trust**            | API Gateway valida JWT, inyecta identidad (`X-User-Email`)                         |

## Fases de Entrega de Valor

### Fase 1 — MVP: Núcleo Transaccional (HU1, HU2, HU4, HU6)

Catálogo, inventario seguro con lock pesimista, pedidos con reserva de stock por gRPC, notificaciones por correo. Resuelve la sobreventa.

### Fase 2 — Autogestión B2B (HU5, HU8)

Carrito con detección de abandono, integración de pasarelas de pago vía ACL. Saga completa con compensación.

### Fase 3 — Analítica y Logística (HU3, HU7)

Reportes masivos CSV/PDF (hasta 500MB) exportados a S3. Gestión de despachos con ACL logística (DHL, FedEx, Legacy).

### Fase 4 — Abastecimiento (Orden de compra automatizada)

ms-provider consume `StockDepleted` y genera automáticamente órdenes de compra. ms-notifications envía email al proveedor. Actualización de stock manual por admin al recibir mercancía.

## Estructura del Monorepo

```text
Arka/
├── compose.yaml                 # Stack local (PostgreSQL 17 ×3, Kafka, Kafka UI, LocalStack, Traefik)
├── .env                         # Variables de entorno (puertos, credenciales)
├── localstack/                  # CloudFormation: Secrets Manager + API Gateway
│   ├── bootstrap.sh
│   └── infra.yaml
├── docs/                        # Documentación de arquitectura y backlog
├── ms-catalog/                  # Microservicio de Catálogo (WebFlux)
├── ms-inventory/                # Microservicio de Inventario (WebFlux)
├── ms-order/                    # Microservicio de Pedidos (WebFlux)
├── ms-notifications/            # Microservicio de Notificaciones (WebFlux)
├── ms-cart/                     # Microservicio de Carrito (WebFlux)
├── ms-payment/                  # Microservicio de Pagos (WebFlux — ACL pasarelas)
├── ms-reporter/                 # Microservicio de Reportes (MVC + Virtual Threads — único imperativo)
├── ms-shipping/                 # Microservicio de Envíos (WebFlux — ACL logística)
└── ms-provider/                 # Microservicio de Proveedores (WebFlux — ACL proveedores)
```

### Estructura interna de cada microservicio (Clean Architecture)

```text
ms-<name>/
├── applications/app-service/    # Spring Boot main, configuración, DI
├── domain/
│   ├── model/                   # Entities, Value Objects, Ports (Interfaces Gateway)
│   └── usecase/                 # Lógica de negocio, orquestación
├── infrastructure/
│   ├── driven-adapters/         # Repos, clientes externos (R2DBC, REST, Kafka producer)
│   ├── entry-points/            # Controllers (WebFlux), Kafka consumers (MVC solo en ms-reporter)
│   └── helpers/                 # Utilidades compartidas
├── deployment/Dockerfile        # amazoncorretto:21-alpine, usuario no-root
├── build.gradle                 # Plugins: Scaffold, Spring Boot, PiTest, JaCoCo, SonarQube
├── gradle.properties            # package=com.arka, reactive=true|false
└── settings.gradle              # Módulos: :app-service, :model, :usecase + adapters/entrypoints
```

## Inicio Rápido

### Levantar infraestructura local

```bash
docker compose up -d
```

Esto inicia:

| Servicio                  | Puerto    | Descripción                             |
| ------------------------- | --------- | --------------------------------------- |
| PostgreSQL 17 (orders)    | 5432      | `db_orders`                             |
| PostgreSQL 17 (inventory) | 5433      | `db_inventory`                          |
| PostgreSQL 17 (payment)   | 5434      | `db_payment`                            |
| Kafka (KRaft)             | 9092      | Broker de mensajería                    |
| Kafka UI                  | 8080      | Dashboard de topics y mensajes          |
| LocalStack                | 4566      | Secrets Manager + API Gateway simulados |
| Traefik                   | 80 / 8090 | Load balancer + dashboard               |

### Scaffold — Generar componentes

Desde la raíz de cada microservicio:

```bash
cd ms-<name>
./gradlew generateModel --name=MiModelo          # Model + Gateway interface
./gradlew generateUseCase --name=MiCasoDeUso      # UseCase class
./gradlew generateDrivenAdapter --type=r2dbc       # Driven adapter (r2dbc, kafka, restconsumer, etc.)
./gradlew generateEntryPoint --type=webflux        # Entry point (webflux, kafka, etc.)
./gradlew validateStructure                        # Validar capas de Clean Architecture
```

### Ejecutar un microservicio

```bash
cd ms-<name>
./gradlew bootRun
```

## Seguridad (Zero Trust)

- **API Gateway** como único punto de entrada público
- Autenticación delegada a **Microsoft Entra ID / AWS Cognito**
- Tokens **JWT** validados en el gateway; identidad propagada vía header `X-User-Email`
- **Tenant Restrictions**: dominios públicos (`@gmail.com`) bloqueados para garantizar enfoque B2B
- Microservicios internos 100% stateless dentro de VPC privada
- Patrón **BFF descartado** permanentemente

## Calidad de Código

- **JaCoCo**: cobertura obligatoria (XML + HTML)
- **PiTest**: mutation testing sobre `com.arka.*` (JUnit 5)
- **SonarQube**: análisis estático integrado en build
- **BlockHound**: detección de llamadas bloqueantes en tests reactivos

## Integraciones Externas

| Sistema                            | Microservicio    | Protocolo   |
| ---------------------------------- | ---------------- | ----------- |
| Stripe, Wompi, Mercado Pago        | ms-payment       | HTTPS (ACL) |
| Operadores logísticos (FedEx, DHL) | ms-shipping      | HTTPS (ACL) |
| Proveedores de mercancía           | ms-provider      | HTTPS (ACL) |
| AWS SES (correos transaccionales)  | ms-notifications | HTTPS       |
| AWS S3 (reportes pesados)          | ms-reporter      | AWS SDK     |

## Documentación

- [Diseño arquitectónico completo](docs/01-arquitectura.md) — Stack, paradigma híbrido, Clean Architecture, seguridad, persistencia, fases
- [Contexto de negocio](docs/09-contexto-negocio.md) — Qué es Arka, problemas, actores, HUs, decisiones estratégicas
- [Backlog del proyecto](docs/tarea-original/Backlog%20del%20proyecto%20Java%20Backend%20Arka.md) — Historias de usuario priorizadas
- [Contexto del reto](docs/tarea-original/Proyecto%20Java%20Backend%20Reto%20V2.md) — Necesidades del negocio y problemas actuales
