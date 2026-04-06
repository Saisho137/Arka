---
name: scaffold-tasks
description: "Genera componentes de Clean Architecture (Model, UseCase, Driven Adapter, Entry Point, Helper) usando las tareas Gradle del plugin Bancolombia Scaffold. Usar cuando el usuario pida agregar/crear cualquiera de estos componentes en un microservicio."
---

# Scaffold Tasks — Bancolombia Clean Architecture Plugin

## Cuándo Usar

Cuando el usuario pida crear/agregar: modelo, caso de uso, driven adapter, entry point o helper en cualquier microservicio `ms-*`.

## Pre-requisitos

1. Identificar el microservicio destino (`ms-order`, `ms-catalog`, etc.)
2. Ejecutar SIEMPRE desde la raíz del microservicio: `cd ms-<name>`
3. El plugin `co.com.bancolombia.cleanArchitecture` v4.2.0 ya está en `build.gradle`

## Tareas Disponibles

### Generate Model (`gm`)

Crea entidad + interfaz Gateway en `domain/model/`.

```bash
./gradlew generateModel --name=<ModelName>
# Alias: ./gradlew gm --name=<ModelName>
```

**Genera:**

- `domain/model/src/main/java/[package]/model/<name>/<Name>.java` — Entidad
- `domain/model/src/main/java/[package]/model/<name>/gateways/<Name>Repository.java` — Port interface

### Generate Use Case (`guc`)

Crea clase UseCase en `domain/usecase/`.

```bash
./gradlew generateUseCase --name=<UseCaseName>
# Alias: ./gradlew guc --name=<UseCaseName>
```

**Genera:**

- `domain/usecase/src/main/java/[package]/usecase/<name>/<Name>UseCase.java`

### Generate Driven Adapter (`gda`)

Crea módulo en `infrastructure/driven-adapters/`.

```bash
./gradlew generateDrivenAdapter --type=<type>
# Alias: ./gradlew gda --type=<type>
```

**Tipos principales:**

| Tipo            | Descripción                      | Parámetros extra                        |
| --------------- | -------------------------------- | --------------------------------------- |
| `r2dbc`         | R2DBC PostgreSQL Client          | —                                       |
| `restconsumer`  | REST Client Consumer             | `--url=<URL>`                           |
| `kafka`         | Kafka Producer (async event bus) | `--tech=kafka`                          |
| `asynceventbus` | Async Event Bus                  | `--tech=kafka`                          |
| `secrets`       | Secrets Manager Bancolombia      | `--secrets-backend=aws_secrets_manager` |
| `redis`         | Redis                            | `--mode=template\|repository`           |
| `mongodb`       | MongoDB Repository               | `--secret=true\|false`                  |
| `jpa`           | JPA Repository                   | `--secret=true\|false`                  |
| `dynamodb`      | DynamoDB Adapter                 | —                                       |
| `s3`            | AWS S3                           | —                                       |
| `sqs`           | SQS message sender               | —                                       |
| `generic`       | Empty adapter                    | `--name=<Name>` (requerido)             |

### Generate Entry Point (`gep`)

Crea módulo en `infrastructure/entry-points/`.

```bash
./gradlew generateEntryPoint --type=<type>
# Alias: ./gradlew gep --type=<type>
```

**Tipos principales:**

| Tipo      | Descripción                         | Parámetros extra                                                            |
| --------- | ----------------------------------- | --------------------------------------------------------------------------- |
| `webflux` | API REST reactiva (WebFlux)         | `--router=true\|false`, `--authorization=true\|false`                       |
| `kafka`   | Kafka Consumer                      | —                                                                           |
| `graphql` | API GraphQL                         | `--pathgql=<path>`                                                          |
| `restmvc` | API REST (Spring MVC)               | `--server=tomcat\|jetty`                                                    |
| `sqs`     | SQS Listener                        | —                                                                           |
| `rsocket` | RSocket Controller                  | —                                                                           |
| `mcp`     | MCP Server (Model Context Protocol) | `--name=<Name>`, `--enable-tools`, `--enable-resources`, `--enable-prompts` |
| `generic` | Empty entry point                   | `--name=<Name>` (requerido)                                                 |

### Generate Helper (`gh`)

Crea módulo utilitario en `infrastructure/helpers/`.

```bash
./gradlew generateHelper --name=<HelperName>
# Alias: ./gradlew gh --name=<HelperName>
```

### Validate Structure

Valida que la estructura de capas sea correcta.

```bash
./gradlew validateStructure
```

## Flujo de Trabajo

1. **Navegar** al microservicio: `cd ms-<name>`
2. **Ejecutar** la tarea Gradle correspondiente
3. **Verificar** que `settings.gradle` fue actualizado automáticamente con el nuevo módulo
4. **Validar** estructura: `./gradlew validateStructure`
5. **Implementar** la lógica de negocio en los archivos generados

## Reglas Críticas

- Siempre usar tipos **reactivos** (`Mono<T>`, `Flux<T>`) — nunca bloquear
- Para BD siempre preferir `r2dbc` sobre `jpa` (proyecto reactivo)
- Los nombres deben ser **PascalCase** para modelos/use cases
- Después de generar, verificar que `settings.gradle` incluya el nuevo módulo
- Paquete base: `com.arka.<ms-name>` (ej: `com.arka.order`)

## Referencia

Documentación completa: https://bancolombia.github.io/scaffold-clean-architecture/docs/category/tasks
