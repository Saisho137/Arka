---
sidebar_position: 8
title: Estándares de Código
---

# Patrones y Estándares de Código

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

#### Implementación de Referencia — Engine Síncrono

Para reglas puras en memoria (validaciones de negocio, compliance, elegibilidad):

```java
// 1. @FunctionalInterface genérica — una por dominio
@FunctionalInterface
public interface OrderRule {
    Optional<OrderRejection> evaluate(Order order, List<OrderItem> items);
}

// 2. Sealed interface para el resultado polimórfico
public sealed interface OrderRejection permits
        OrderRejection.AmountExceeded,
        OrderRejection.RestrictedCustomer,
        OrderRejection.ProductIneligible {
    String reason();
    record AmountExceeded(String reason, BigDecimal limit) implements OrderRejection {}
    record RestrictedCustomer(String reason) implements OrderRejection {}
    record ProductIneligible(String reason, String sku) implements OrderRejection {}
}

// 3. Engine — lista de reglas con short-circuit en el primer rechazo
public class OrderRuleEngine {
    private final List<OrderRule> rules;

    public OrderRuleEngine(List<OrderRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Optional<OrderRejection> evaluate(Order order, List<OrderItem> items) {
        return rules.stream()
                .map(rule -> rule.evaluate(order, items))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();  // Short-circuit: primer rechazo detiene evaluación
    }
}
```

Uso en UseCase (inyectado como dependencia):

```java
public Mono<Order> createOrder(CreateOrderCommand cmd) {
    Order order = buildOrder(cmd);
    // Fast-fail síncrono — no bloquea EventLoop (reglas en memoria)
    Optional<OrderRejection> rejection = ruleEngine.evaluate(order, cmd.items());
    if (rejection.isPresent()) {
        return Mono.error(new OrderRejectedException(rejection.get()));
    }
    // Continuar con pipeline reactivo (gRPC, BD, outbox...)
    return proceedWithOrder(order, cmd);
}
```

#### Implementación de Referencia — Engine Reactivo

Para reglas que requieren I/O (consultar BD, servicios externos):

```java
@FunctionalInterface
public interface ReactiveOrderRule {
    Mono<Optional<OrderRejection>> evaluate(Order order, List<OrderItem> items);
}

public class ReactiveOrderRuleEngine {
    private final List<ReactiveOrderRule> rules;

    public ReactiveOrderRuleEngine(List<ReactiveOrderRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public Mono<Optional<OrderRejection>> evaluate(Order order, List<OrderItem> items) {
        return Flux.fromIterable(rules)
                .concatMap(rule -> rule.evaluate(order, items))  // Secuencial para short-circuit
                .filter(Optional::isPresent)
                .next()                                          // Short-circuit reactivo
                .defaultIfEmpty(Optional.empty());
    }
}
```

#### Patrón Mixto (fast-fail síncrono + validaciones reactivas)

```java
// En el UseCase: síncrono primero (barato), reactivo después (I/O)
Optional<OrderRejection> syncRejection = syncEngine.evaluate(order, items);
if (syncRejection.isPresent()) {
    return Mono.error(new OrderRejectedException(syncRejection.get()));
}
return reactiveEngine.evaluate(order, items)
        .flatMap(rejection -> rejection.isPresent()
                ? Mono.error(new OrderRejectedException(rejection.get()))
                : proceedWithOrder(order, items));
```

> **Cuándo usar:** Fase 2+ cuando aparezcan reglas de negocio complejas (límite de crédito B2B, restricciones por región, blacklists de compliance). En Fase 1, la validación directa en el UseCase es suficiente.

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

| Decisión             | Resolución                                                                                    |
| -------------------- | --------------------------------------------------------------------------------------------- |
| Timestamps           | `Instant` (UTC) → `TIMESTAMPTZ`. `LocalDateTime` solo si zona horaria irrelevante             |
| Enums                | Autoexplicativos (`RESTOCK`, `SHRINKAGE`), no genéricos (`MANUAL_ADJUSTMENT`)                 |
| Constantes           | `static final` + nombre descriptivo. Configurables → YAML con `@Value`                        |
| `Mono.defer()`       | Obligatorio en `switchIfEmpty` cuando el fallback produce side-effects                        |
| Paginación           | Offset (`page`/`size`) para MVP; Cursor (keyset) para alto volumen futuro                     |
| Schedulers           | Intervalos en `application.yaml` sin defaults inline — fallo al startup si falta              |
| Flag `-parameters`   | Obligatorio en `main.gradle` para resolver `@RequestParam`/`@PathVariable`                    |
| MongoDB URI (SB 4.0) | `spring.mongodb.uri` (no `spring.data.mongodb.uri`) + `uuidRepresentation=standard`           |
| Documentación API    | Springdoc OpenAPI en `/swagger-ui.html`                                                       |
| gRPC modules         | Manuales en entry-points con plugin `com.google.protobuf` + `grpc-server-spring-boot-starter` |
