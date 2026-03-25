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
| BD Relacional         | PostgreSQL 16 (R2DBC / JDBC)                      |
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

- **Reactivo (WebFlux):** Servicios I/O-Bound con drivers no bloqueantes — ms-catalog, ms-inventory, ms-order, ms-cart, ms-notifications
- **Imperativo (MVC + Virtual Threads):** Servicios CPU-Bound o con SDKs bloqueantes — ms-reporter, ms-payment\*, ms-shipping\*, ms-provider\*

> \* Actualmente scaffolded como reactivos; diseñados para migrar a MVC + Virtual Threads cuando integren dependencias bloqueantes.

### Microservicios

| Servicio             | Dominio                                   | BD                          | Fase |
| -------------------- | ----------------------------------------- | --------------------------- | ---- |
| **ms-catalog**       | Catálogo de productos, reseñas anidadas   | MongoDB + Redis             | 1    |
| **ms-inventory**     | Stock, reservas, lock pesimista           | PostgreSQL (`db_inventory`) | 1    |
| **ms-order**         | Gestión de pedidos, orquestador Saga      | PostgreSQL (`db_orders`)    | 1    |
| **ms-notifications** | Notificaciones transaccionales (AWS SES)  | MongoDB                     | 1    |
| **ms-cart**          | Carrito de compras, detección de abandono | MongoDB                     | 2    |
| **ms-payment**       | Procesamiento de pagos (ACL pasarelas)    | PostgreSQL (`db_payment`)   | 2    |
| **ms-reporter**      | Reportes analíticos, CQRS, Event Sourcing | PostgreSQL + AWS S3         | 3    |
| **ms-shipping**      | Logística y envíos (Strangler Fig)        | PostgreSQL                  | 3    |
| **ms-provider**      | Proveedores B2B (ACL externa)             | PostgreSQL                  | 4    |

### Comunicación entre Servicios

- **gRPC (síncrono):** `ms-order → ms-inventory` (reserva de stock inmediata), `ms-cart → ms-catalog` (precio actualizado en checkout)
- **Kafka (asíncrono):** Sagas Secuenciales, eventos de dominio, Event Sourcing para ms-reporter

### Kafka Topics

```text
product-created · product-updated · order-created · order-confirmed · order-cancelled
stock-reserved · stock-released · stock-depleted · payment-processed · payment-failed
cart-abandoned · shipping-dispatched · stock-received
```

### Patrones Arquitectónicos

| Patrón                    | Dónde se aplica                                                |
| ------------------------- | -------------------------------------------------------------- |
| **Saga Secuencial**       | ms-order (orquestador) → ms-inventory → ms-payment             |
| **CQRS / Event Sourcing** | ms-reporter consume todos los eventos de Kafka                 |
| **Outbox Pattern**        | ms-order, ms-inventory (transacción BD + evento atómico)       |
| **Cache-Aside**           | ms-catalog con Redis para lecturas de alta frecuencia          |
| **Anti-Corruption Layer** | ms-payment (pasarelas de pago), ms-provider (APIs proveedores) |
| **Strangler Fig**         | ms-shipping (migración de monolito logístico legacy)           |
| **Circuit Breaker**       | Llamadas a servicios externos                                  |
| **Database per Service**  | Cada microservicio es dueño exclusivo de su almacenamiento     |
| **Zero Trust**            | API Gateway valida JWT, inyecta identidad (`X-User-Email`)     |

## Fases de Entrega de Valor

### Fase 1 — MVP: Núcleo Transaccional (HU1, HU2, HU4, HU6)

Catálogo, inventario seguro con lock pesimista, pedidos con reserva de stock por gRPC, notificaciones por correo. Resuelve la sobreventa.

### Fase 2 — Autogestión B2B (HU5, HU8)

Carrito con detección de abandono, integración de pasarelas de pago vía ACL. Saga completa con compensación.

### Fase 3 — Analítica y Logística (HU3, HU7)

Reportes masivos CSV/PDF (hasta 500MB) exportados a S3. Automatización de envíos con Strangler Fig.

### Fase 4 — Abastecimiento Automático

Reposición automática de stock con proveedores externos cuando se detectan umbrales críticos.

## Estructura del Monorepo

```text
Arka/
├── compose.yaml                 # Stack local (PostgreSQL ×3, Kafka, Kafka UI, LocalStack, Traefik)
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
├── ms-payment/                  # Microservicio de Pagos (WebFlux → MVC futuro)
├── ms-reporter/                 # Microservicio de Reportes (MVC + Virtual Threads)
├── ms-shipping/                 # Microservicio de Envíos (WebFlux → MVC futuro)
└── ms-provider/                 # Microservicio de Proveedores (WebFlux → MVC futuro)
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
│   ├── entry-points/            # Controllers (WebFlux/MVC), Kafka consumers
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

| Servicio               | Puerto    | Descripción                             |
| ---------------------- | --------- | --------------------------------------- |
| PostgreSQL (orders)    | 5432      | `db_orders`                             |
| PostgreSQL (inventory) | 5433      | `db_inventory`                          |
| PostgreSQL (payment)   | 5434      | `db_payment`                            |
| Kafka (KRaft)          | 9092      | Broker de mensajería                    |
| Kafka UI               | 8080      | Dashboard de topics y mensajes          |
| LocalStack             | 4566      | Secrets Manager + API Gateway simulados |
| Traefik                | 80 / 8090 | Load balancer + dashboard               |

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

| Sistema                            | Microservicio    | Protocolo             |
| ---------------------------------- | ---------------- | --------------------- |
| Stripe, Wompi, Mercado Pago        | ms-payment       | HTTPS (ACL)           |
| Operadores logísticos (FedEx, DHL) | ms-shipping      | HTTPS (Strangler Fig) |
| Proveedores de mercancía           | ms-provider      | HTTPS/Webhooks (ACL)  |
| AWS SES (correos transaccionales)  | ms-notifications | HTTPS                 |
| AWS S3 (reportes pesados)          | ms-reporter      | AWS SDK               |

## Documentación

- [Diseño arquitectónico completo](docs/diseño-aquitectura-backend-arka.md) — Fases, patrones, responsabilidades C4
- [Contexto de negocio](docs/contexto-negocio-arka-extra.md) — Acuerdos de integración, diagramas C1/C2
- [Backlog del proyecto](docs/Backlog%20del%20proyecto%20Java%20Backend%20Arka.md) — Historias de usuario priorizadas
- [Contexto del reto](docs/Proyecto%20Java%20Backend%20Reto%20V2.md) — Necesidades del negocio y problemas actuales
