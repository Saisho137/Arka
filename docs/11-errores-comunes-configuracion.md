---
sidebar_position: 11
title: Errores Comunes
---

# Errores Comunes y Configuración

Guía de referencia para configurar correctamente cada microservicio nuevo del monorepo Arka y evitar errores recurrentes.

---

## 1. PiTest — Minion exits with `UNKNOWN_ERROR`

### Síntoma

```text
PiTest minion exits abnormally with UNKNOWN_ERROR
```

### Causa

`archunit` y/o `jqwik` se declaran en el bloque `subprojects` de `main.gradle`, lo que los inyecta en **todos** los módulos. Cuando PiTest crea su JVM minion para mutar código en módulos como `:usecase` o `:model`, el classpath incluye la extensión JUnit5 de ArchUnit, que intenta escanear y cargar todas las clases, fallando en el contexto mutado.

### Solución (patrón ms-inventory)

**`main.gradle`** — las dependencias de test del bloque `subprojects` deben quedar así:

```groovy
subprojects {
    dependencies {
        implementation 'io.projectreactor:reactor-core'
        implementation 'io.projectreactor.addons:reactor-extra'

        testImplementation 'io.projectreactor.tools:blockhound-junit-platform:1.0.16.RELEASE'

        testImplementation 'io.projectreactor:reactor-test'
        testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
        testImplementation 'org.springframework.boot:spring-boot-starter-test'
        // ...lombok, platform BOM...
    }
}
```

`archunit` y `jqwik` **no van aquí**. Solo van en `applications/app-service/build.gradle`:

```groovy
// applications/app-service/build.gradle
dependencies {
    // ...
    testImplementation 'com.tngtech.archunit:archunit:1.4.1'
    testImplementation 'net.jqwik:jqwik:1.9.2'  // solo si se usan property-based tests
}
```

---

## 2. ArchUnit — Violación `Rule_2.2` (technology suffixes)

### Síntoma

```
ArchUnit Violation (Warning):
Class <com.arka.usecase.X$SomeRequest> has simple name ending with 'Request'
Rule: Domain classes should not be named with technology suffixes like "Request"
```

### Causa

Los inner records/classes dentro de UseCases llevan sufijos propios de la capa entry-point (`Request`, `Response`, `DTO`). ArchUnit escanea los paquetes `com.arka.usecase` y `com.arka.model` y los detecta.

### Solución

Renombrar los registros de comando/consulta en la capa de use case usando el vocabulario del dominio:

| Incorrecto (entry-point) | Correcto (use case / dominio)    |
| ------------------------ | -------------------------------- |
| `OrderItemRequest`       | `OrderItemCommand`               |
| `CreateOrderRequest`     | `CreateOrderCommand`             |
| `FilterRequest`          | `OrderFilter` / `QueryParams`    |
| `StatusResponse`         | `OrderWithItems` / `OrderResult` |

**Regla**: Los inner types de un UseCase se nombran como **Command**, **Query**, o con un nombre de dominio puro.

---

## 3. Parámetros REST fallan en tiempo de ejecución (`Name for argument not specified`)

### Síntoma

```
IllegalArgumentException: Name for argument of type [java.lang.String] not specified
```

Aparece cuando se usan `@PathVariable`, `@RequestParam` sin nombre explícito en un método de un controller compilado sin metadata de parámetros.

### Causa

El compilador de Java no preserva los nombres de parámetros de los métodos por defecto. Spring WebFlux los necesita para resolver `@PathVariable` y `@RequestParam` sin nombre explícito.

### Solución

Agregar el flag `-parameters` al compilador en **`main.gradle`** (bloque raíz, fuera de `subprojects`):

```groovy
tasks.withType(JavaCompile).configureEach {
    options.compilerArgs = [
            '-Amapstruct.suppressGeneratorTimestamp=true',
            '-parameters'
    ]
}
```

Este bloque ya está en todos los microservicios del monorepo. **No eliminar ni mover**.

---

## 4. Integration Test falla — `No qualifying bean of type 'XxxRepository'`

### Síntoma

```
UseCasesConfigTest.testUseCaseBeansExist() — FAILED
No qualifying bean of type 'com.arka.model.order.gateways.OrderRepository' available
```

### Causa

El test de integración de `UseCasesConfig` levanta el contexto de Spring con los use cases declarados, pero la capa de infraestructura (driven adapters / repositorios) aún no está implementada.

### Estado esperado

Es **normal** en las fases iniciales del desarrollo (Tareas 1–5). Los repositorios concretos se proveen en la Tarea 6 (infraestructura R2DBC/MongoDB). El test pasará cuando se registren los beans de infraestructura correspondientes.

### Nota de diseño

El `UseCasesConfig` de ms-order utiliza `@ComponentScan` con `FilterType.REGEX` para escanear automáticamente todos los UseCases:

```java
@Configuration
@ComponentScan(basePackages = "com.arka.usecase",
        includeFilters = {
                @ComponentScan.Filter(type = FilterType.REGEX, pattern = "^.+UseCase$")
        },
        useDefaultFilters = false)
public class UseCasesConfig {
}
```

El patrón de ms-inventory declara los beans explícitamente (más verboso pero más predecible en tests):

```java
@Configuration
public class UseCasesConfig {
    @Bean
    public StockUseCase stockUseCase(StockRepository stockRepository, ...) {
        return new StockUseCase(stockRepository, ...);
    }
}
```

---

## 5. Dockerfile — Multi-stage build (patrón ms-inventory)

El Dockerfile de ms-inventory es el estándar de referencia. Diferencias clave vs. una implementación naive:

```dockerfile
# ── Stage 1: Build ──
FROM gradle:9.4-jdk21 AS builder
WORKDIR /myapp

# Copiar wrapper y configuración PRIMERO (mejor cache de capas Docker)
COPY gradle/ gradle/
COPY gradlew gradlew
COPY *.gradle ./
COPY gradle.properties gradle.properties
COPY lombok.config lombok.config

# Copiar código fuente DESPUÉS (invalida cache solo cuando cambia el código)
COPY applications applications
COPY domain domain
COPY infrastructure infrastructure

RUN gradle build -x test --no-daemon \
    --no-configuration-cache \
    --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx768m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC"

# ── Stage 2: Run ──
FROM amazoncorretto:21-alpine
WORKDIR /myapprun
COPY --from=builder /myapp/applications/app-service/build/libs/*.jar ms-<name>.jar

RUN apk update && apk add --no-cache curl \
    && addgroup -S appgroup && adduser -S appuser -G appgroup

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -Djava.security.egd=file:/dev/./urandom"

USER appuser
ENTRYPOINT ["/bin/sh", "-c", "java $JAVA_OPTS -jar ms-<name>.jar"]
```

**Puntos críticos:**

- `--no-configuration-cache` en el build de Docker: evita conflictos con el caché serializado del host.
- `--max-workers=2` + `-Xmx768m`: limita el uso de recursos en CI/CD con contenedores pequeños.
- `-XX:+UseSerialGC` en build: el GC serial es más eficiente en procesos de corta duración.
- `USER appuser`: nunca ejecutar como root en producción.
- `XX:MaxRAMPercentage=70`: deja margen para el SO dentro del contenedor.

**Error habitual en ms-order** (Dockerfile sin optimización):

```dockerfile
# ❌ No optimiza cache — cualquier cambio de código reinvalida las capas de Gradle
COPY applications applications
COPY domain domain
COPY infrastructure infrastructure
COPY *.gradle .          # debería ir ANTES del código fuente
COPY lombok.* .
COPY gradlew.* .
COPY gradle.* .

RUN gradle build -x test --no-daemon  # ← falta --no-configuration-cache y límites de memoria
```

---

## 6. Config files estándar de `app-service` (cambian poco entre microservicios)

Los siguientes archivos de `applications/app-service/src/main/java/com/arka/config/` son prácticamente idénticos en todos los microservicios reactivos. **Copiar de ms-inventory y ajustar solo el nombre**:

### `BeansConfig.java`

Registra la implementación de `JsonSerializer` (port del dominio → Jackson):

```java
@Configuration
public class BeansConfig {
    @Bean
    public JsonSerializer jsonSerializer(ObjectMapper objectMapper) {
        return new JacksonJsonSerializer(objectMapper);
    }
}
```

### `OpenApiConfig.java`

Solo cambia el título, descripción y nombre del método:

```java
@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI <msName>OpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-<name> API")
                        .description("Descripción del microservicio.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Arka Platform Team")
                                .email("platform@arka.com")));
    }
}
```

### `config/serializer/JacksonJsonSerializer.java`

No cambia entre microservicios. Implementación del port `JsonSerializer`:

```java
@RequiredArgsConstructor
public class JacksonJsonSerializer implements JsonSerializer {
    private final ObjectMapper objectMapper;

    @Override
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize payload to JSON: " + e.getMessage(), e);
        }
    }
}
```

---

## 7. `application.yaml` — Estructura base para microservicios reactivos

Basado en ms-inventory. Ajustar puertos, nombre, BD y tópicos:

```yaml
server:
  port: "${MS_<NAME>_PORT:808X}"

spring:
  application:
    name: "ms-<name>"
  profiles:
    active: "${SPRING_PROFILES_ACTIVE:local}"
  r2dbc: # omitir si el MS usa MongoDB
    url: "r2dbc:postgresql://${R2DBC_HOST:localhost}:${R2DBC_PORT:5432}/${R2DBC_DB:db_<name>}"
    username: "${R2DBC_USER:arka}"
    password: "${R2DBC_PASSWORD:arkaSecret2025}"
    pool:
      enabled: true
      initial-size: 5
      max-size: 20
  kafka:
    bootstrap-servers: "${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}"
    consumer:
      group-id: "ms-<name>"
      auto-offset-reset: earliest
      enable-auto-commit: false

springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
  show-actuator: false

management:
  endpoints:
    web:
      exposure:
        include: "health,prometheus"
  endpoint:
    health:
      probes:
        enabled: true

cors:
  allowed-origins: "http://localhost:4200,http://localhost:8080"

logging:
  level:
    com.arka: INFO
```

---

## 8. Dependencias raíz de `app-service/build.gradle` (estándar)

Basado en ms-inventory. Agregar módulos de infraestructura a medida que se crean con el scaffold:

```groovy
apply plugin: 'org.springframework.boot'

dependencies {
    implementation project(':model')
    implementation project(':usecase')

    // Infraestructura — agregar con scaffold (gda, gep)
    // implementation project(':reactive-web')
    // implementation project(':r2dbc-postgresql')
    // implementation project(':kafka-producer')
    // implementation project(':kafka-consumer')
    // implementation project(':grpc-<name>')

    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'tools.jackson.core:jackson-databind'
    implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:3.0.2'
    runtimeOnly 'org.springframework.boot:spring-boot-devtools'

    // Testing — solo en app-service, NO en main.gradle
    testImplementation 'com.tngtech.archunit:archunit:1.4.1'
    testImplementation 'net.jqwik:jqwik:1.9.2'  // solo si se usan property-based tests
    testImplementation 'tools.jackson.core:jackson-databind'
}

tasks.register('explodedJar', Copy) {
    with jar
    into layout.buildDirectory.dir("exploded")
}

jar { enabled = false }

bootJar {
    archiveFileName = "${project.getParent().getName()}.${archiveExtension.get()}"
}
```

---

## Checklist al crear un nuevo microservicio

- [ ] `main.gradle`: `archunit` y `jqwik` fuera del bloque `subprojects`
- [ ] `main.gradle`: `tasks.withType(JavaCompile)` con `-parameters` presente
- [ ] `main.gradle`: `pitest.excludedTestClasses = ['com.arka.ArchitectureTest']`
- [ ] `app-service/build.gradle`: `archunit` (y `jqwik` si aplica) solo aquí
- [ ] `Dockerfile`: copiar `gradle/`, `*.gradle`, `gradle.properties`, `lombok.config` ANTES del código fuente
- [ ] `Dockerfile`: `--no-configuration-cache --max-workers=2` en el `RUN gradle build`
- [ ] Inner types en UseCases: usar sufijos `Command` / `Query` / nombre de dominio, nunca `Request` / `Response` / `DTO`
- [ ] `BeansConfig`, `OpenApiConfig`, `JacksonJsonSerializer`: copiar de ms-inventory y ajustar nombre
