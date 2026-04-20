# PROYECTO ARKA

## DEFINICION GENERAL DEL PROYECTO

Desarrollar una arquitectura de microservicios para una plataforma e-commerce, que permita gestionar de forma eficiente las órdenes, el inventario, los productos y proveedores, garantizando escalabilidad, integración con AWS y una experiencia de usuario optimizada.

## MODULOS DEL PROYECTO

### Gestión de Orden

- **Orquestación del proceso de creación de órdenes.**
- Utiliza el patrón Saga, eventos SQS y funciones Lambda para coordinar inventario, envío y notificación.

### Carrito

- **Gestión del carrito de compras**, incluyendo detección de carritos abandonados y envío de notificaciones.

### Catálogo

- **Visualización del catálogo mediante integración con microservicios**, utilizando Spring Cloud y BFFs para adaptar la información a cada plataforma.

### Shipping

- **Integración del servicio de envíos con operadores logísticos externos** (DHL, FedEx) y monolito legacy mediante una Capa Anti-Corrupción (ACL).

### Proveedores

- **Administración de proveedores, almacenes e inventario.** Generación automatizada de órdenes de compra a proveedores cuando el stock alcanza umbrales críticos y actualización manual del stock por el administrador al recibir mercancía.

---

## PATRONES DE MICROSERVICIO

### Patrón API Gateway

```text
GATEWAY
  ↓
  ├─ SERVICIO A
  ├─ SERVICIO B
  └─ ¿?
```

### Patrón de Registro de Servicios

Sistema de descubrimiento automático de servicios.

### Patrón Event Sourcing

Almacenamiento de todos los eventos que cambian el estado del sistema.

### Patrón Anti-Corruption Layer (ACL)

Capa intermedia que aísla al ecosistema de las particularidades de servicios externos (pasarelas de pago, operadores logísticos, monolitos legacy).

### Patrón Circuit Breaker

Protección contra fallos en cascada entre servicios.

### Patrón Saga

Transacciones distribuidas coordinadas a través de eventos.

### Patrón CQS (Command Query Separation)

Separación entre escritura y lectura:

- **Escribir**: Actualizar estado
- **Leer**: Consultar estado

### Patrón Composition

Composición de respuestas desde múltiples servicios.

---

## SERVICIOS DE AWS

- **S3**: Almacenamiento de objetos
- **SQS**: Cola de mensajes
- **Lambda**: Funciones sin servidor
- **DynamoDB**: Base de datos NoSQL
- **SES**: Servicio de envío de correos
- **EventBridge**: Bus de eventos
- **DocumentDB**: Base de datos de documentos
- **SNS**: Servicio de notificaciones
- **RDS**: Base de datos relacional
- **EC2**: Instancias de cómputo

---

## ACTIVIDADES DEL PROYECTO

### Actividad 1

**Objetivo principal:**

- Recibir una orden
- Verificar si hay disponibilidad de entrega

**Estructuras:**

- EC2
- RDS (Ordenes, Inventario)
- API GATEWAY

**Estructuras Opcionales:**

- Validaciones adicionales

---

### Actividad 2

**Objetivo principal:**

- Crear una orden
- Dividir el proceso de creación en varios subprocesos

**Patrones:**

- PATRÓN SAGA

**Estructuras:**

- SNS
- SQS
- Lambdas
- API GATEWAY
- RDS (Ordenes, Inventario)

**Flujo:**

- Orden Creada → Actualizar Inventario → Creación Órdenes de Shipping → Orden Completada

---

### Actividad 3

**Objetivo principal:**

- Finalizar el proceso de compra
- Generar un servicio de envío de correos

**Patrones:**

- PATRÓN SAGA

**Estructuras:**

- S3
- SES
- API GATEWAY
- RDS (Ordenes, Inventario)

**Flujo:**

- Orden Creada → Actualizar Inventario → Creación Órdenes de Shipping → Orden Completada → Envío de correo con estado de la orden y plantillas de correo

---

### Actividad 4

**Objetivo principal:**

- Generar servicios para Agregar / Editar / Eliminar del Carrito
- Se activan los servicios del catálogo

**Estructuras:**

- Spring Cloud Services con AWS

---

### Actividad 5

**Objetivo principal:**

- Se despliega BFF para responder a la web/Móvil
- Se despliega el servicio de recomendaciones

**Estructuras:**

- BFF (Backend For Frontend)
- DocumentDB

---

### Actividad 6

**Objetivo principal:**

- Aplicar el patrón Anti-Corruption Layer (ACL) para integrar el servicio de shipping con operadores logísticos externos

**Estructuras:**

- Patrón ACL (Anti-Corruption Layer)

---

### Actividad 7

**Objetivo principal:**

- Completar los servicios restantes
- Refactorizar

**Estructuras:**

- Aplicar estrategias de Refactor

---

### Actividad 8

**Objetivo principal:**

- Diseñar y desarrollar un sistema para gestionar proveedores y órdenes de compra
- Integrarlo con tiendas de colaboradores para el intercambio de presupuestos

**Estructuras:**

- Creación de diagramas con diagrams.io

**Nota:** Se sugiere que solo sea proponer el diagrama sin implementaciones concretas.

---

### Actividad 9

**Objetivo principal:**

- Generar un servicio de envío de notificaciones
- Generar un servicio de reportes

**Estructuras:**

- Cronjob
- Event Bridge

---

## FASE 2: OPTIMIZACIÓN Y DESPLIEGUE

### Actividad 1

**Objetivo:**

- Finalizar el proyecto ARKA, optimizando tareas y calidad del código.

**Tareas Clave:**

- Jira
- Pair Programming
- Calidad de Código

---

### Actividad 2

**Actividades:**

- PAIR PROGRAMMING
- CODE REVIEW

---

### Actividad 3

**Generación de un PIPELINE EC2**

Flujo:

```
Develop → Push → Build → Deploy
```

---

### Actividad 4

**Generación de un PIPELINE Lambda**

Flujo:

```
Develop → Lint → Build → Push → Test → Deploy
```

---

### Actividad 5

**Generación de archivos de infraestructura**

---

### Actividad 6

**Observabilidad**

**Objetivos Clave:**

- Generación de Alarmas
- Creación de paneles de control
- Análisis de Logs
- Identificación de Cuellos de Botella y Resiliencia
- Alertas de Borde

---

## HITOS PARA ENTREGAS

### AWS:

- **Actividad requerida 1:** Actividades 1 – 3 (Sistema de Órdenes)
- **Actividad requerida 2:** Actividades 4 – 6 (Sistemas de Cloud)
- **Actividad requerida 3:** Actividades 7 – 9 (Completando Microservicios Avanzados)

### DevOps:

- **Actividad requerida 1:** Actividades 1 – 4 (Inicialización de DevOps)
- **Actividad requerida 2:** Actividad 5 (Infraestructura)
- **Actividad requerida 3:** Actividad 6 (Observabilidad)
