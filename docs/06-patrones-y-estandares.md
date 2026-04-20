# 06 — Patrones y Estándares de Código (Referencia Rápida)

> Resumen ejecutivo del documento normativo. Define QUÉ se usa y POR QUÉ, sin ejemplos de implementación.

---

## 1. Paradigma Reactivo

- **Reactivo por defecto** (`Mono`/`Flux`), imperativo solo en ms-reporter
- **No mezclar `Optional` con cadenas reactivas** — usar operadores de Reactor (`switchIfEmpty`, `defaultIfEmpty`, `Mono.justOrEmpty`)
- `Optional` solo válido en: compact constructors de records, ms-reporter, métodos utilitarios puros, reglas del Engine en memoria
- Controladores con `@RestController` + `Mono`/`Flux` (no Router Functions)

## 2. Modelado de Dominio

### Records vs Clases
- **Records como estándar** — inmutables, `equals`/`hashCode` gratis, `@Builder` compatible desde Lombok 1.18.42
- **Clases con Lombok** solo cuando hay herencia o mutabilidad obligatoria de framework
- `@Builder.Default` NO funciona en records → defaults van en el compact constructor

### UUIDs
- **IDs nullable en dominio** — PostgreSQL genera con `DEFAULT gen_random_uuid()`
- Evita bugs silenciosos de `repository.save()` (INSERT vs UPDATE)
- Excepción: `processed_events` (UUID viene de Kafka) → usar `DatabaseClient` con INSERT explícito

### Validación
- `Objects.requireNonNull()` en compact constructors (idiomático JDK)
- Mutaciones encapsuladas en la entidad (métodos `with*()`, `increaseBy()`, etc.) — nunca manipular con `toBuilder()` desde fuera
- Excepciones de dominio: `DomainException` (abstract class extends RuntimeException) con `getHttpStatus()` y `getCode()`

### Sealed Interfaces
- Máquinas de estado y resultados polimórficos como sealed interfaces + records
- Exhaustividad verificada en compile-time con switch pattern matching (Java 21)

## 3. Lógica de Negocio (UseCases)

### Engine de Reglas
- **Síncrono** (`Optional<R>`): reglas puras en memoria, no bloquean EventLoop
- **Reactivo** (`Mono<Optional<R>>`): reglas que requieren I/O (BD, servicios externos)
- **Mixto**: fast-fail síncrono primero, luego validaciones reactivas

### Strategy + Factory
- Comportamientos intercambiables en runtime (pasarelas, operadores logísticos)
- `switch pattern matching` para dominios sealed; Strategy+Factory para extensiones en infraestructura

### Organización
- **1 UseCase por entidad de dominio** con múltiples métodos descriptivos
- No 1 UseCase por operación con `execute()` — cohesión por agregado

## 4. Mapeo entre Capas

- **Mappers manuales** (no MapStruct) — trazabilidad, compatibilidad reactiva, sin acoplamiento
- Clases `final` con `@NoArgsConstructor(access = PRIVATE)` y métodos estáticos
- Nunca poner mappers en el record DTO ni en la entidad de dominio (violación de dependencias)
- Siempre usar `@Builder` al construir objetos destino

### Controller → Handler → UseCase
- Controller thin: solo HTTP concerns (`@Valid`, rutas, OpenAPI)
- Handler `@Component`: orquestación, mapeo, ResponseEntity
- `Mono<ResponseEntity<T>>` para elementos únicos; `Flux<T>` directo para colecciones (streaming reactivo, nunca `collectList()`)

## 5. Manejo de Errores

- `@ControllerAdvice` global traduce excepciones de dominio a HTTP
- Operadores de error de Reactor (`switchIfEmpty`, `onErrorResume`, `retryWhen`) — nunca `try/catch` en publishers

## 6. Concurrencia

- Reactor maneja la concurrencia — no usar `synchronized` ni `Lock` en beans reactivos
- `ConcurrentHashMap` solo para mapas mutables de infraestructura post-startup

## 7. Logging

- SLF4J obligatorio, nunca `System.out.println`
- `LoggerGateway` port en dominio + implementación en helpers (Scaffold no permite deps en usecase)
- JSON estructurado en perfil `docker`; formato legible en perfil `local`
- `correlationId` en MDC para trazabilidad distribuida

## 8. Testing

- JUnit 5 + Mockito para UseCases (sin Spring context)
- `StepVerifier` (reactor-test) para verificar publishers
- `BlockHound` para detectar llamadas bloqueantes en WebFlux

## 9. Driven Adapters R2DBC

- **Enfoque híbrido**: `ReactiveCrudRepository` para CRUD simple + `DatabaseClient` para SQL complejo (`FOR UPDATE`, lock optimista)
- DTOs `@Table` en infraestructura, nunca en dominio
- RowMapper con `Readable` para `DatabaseClient`

## 10. Kafka

- **Producer**: `reactor-kafka` 1.3.25 (`KafkaSender`) — no `reactive-commons` (Outbox requiere partition key y ack explícito)
- **Consumer**: `reactor-kafka` 1.3.25 (`KafkaReceiver`) — `ReactiveKafkaConsumerTemplate` eliminado en spring-kafka 4.0
- Módulos manuales: `kafka-producer` en driven-adapters, `kafka-consumer` en entry-points

## 11. Transacciones R2DBC

- **Nunca** `@Transactional` en UseCases ni entry-points
- **Caso A** (infra pura): transacción manejada internamente en el Driven Adapter
- **Caso B** (lógica de negocio entre escrituras): `TransactionalGateway` port en dominio + `TransactionalOperator` en infraestructura

## 12. Spring Profiles

- `local` (default IDE): `localhost` + puertos mapeados del .env
- `docker` (Compose): hostname del contenedor + puerto interno 5432
- 3 archivos YAML: `application.yaml`, `application-local.yaml`, `application-docker.yaml`

## 13. PostgreSQL ENUMs

- `CREATE TYPE ... AS ENUM` sincronizado con Java (case-sensitive)
- Requiere `EnumCodec` en configuración R2DBC + `WritingConverter` con `EnumWriteSupport` para Spring Data

## 14. Otras Decisiones

| Decisión | Resolución |
|---|---|
| Timestamps | `Instant` (UTC) → `TIMESTAMPTZ`. `LocalDateTime` solo si zona horaria irrelevante |
| Enums | Autoexplicativos (`RESTOCK`, `SHRINKAGE`), no genéricos (`MANUAL_ADJUSTMENT`) |
| Constantes | `static final` + nombre descriptivo. Configurables → YAML con `@Value` |
| `Mono.defer()` | Obligatorio en `switchIfEmpty` cuando el fallback produce side-effects |
| Paginación | Offset (`page`/`size`) para MVP; Cursor (keyset) para alto volumen futuro |
| Schedulers | Intervalos en `application.yaml` sin defaults inline — fallo al startup si falta |
| Flag `-parameters` | Obligatorio en `main.gradle` para resolver `@RequestParam`/`@PathVariable` |
| MongoDB URI (SB 4.0) | `spring.mongodb.uri` (no `spring.data.mongodb.uri`) + `uuidRepresentation=standard` |
| Documentación API | Springdoc OpenAPI en `/swagger-ui.html` |
| gRPC modules | Manuales en entry-points con plugin `com.google.protobuf` + `grpc-server-spring-boot-starter` |
