# Patrones y Estándares de Código — Arka Microservicios

> Documento normativo. Define las convenciones, patrones y decisiones técnicas que **todos** los microservicios deben seguir. Leer antes de implementar cualquier componente.

**Stack:** Java 21 · Spring Boot 4.0.3 · Project Reactor · Lombok 1.18.42 · Bancolombia Scaffold 4.2.0

---

## 1. Paradigma: Reactivo por Defecto, Imperativo por Excepción

| Paradigma                              | Microservicios                                                                                      | Cuándo                                                                                                                                      |
| -------------------------------------- | --------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Reactivo (WebFlux)**                 | ms-catalog, ms-inventory, ms-order, ms-cart, ms-notifications, ms-payment, ms-shipping, ms-provider | I/O-Bound: alta concurrencia, drivers no bloqueantes (R2DBC, Reactive Mongo, WebClient). SDKs bloqueantes con `Schedulers.boundedElastic()` |
| **Imperativo (MVC + Virtual Threads)** | **ms-reporter** (única excepción)                                                                   | CPU-Bound real: generación de archivos 500MB+ en S3. `reactive=false` en `gradle.properties`                                                |

### 1.1 Reactivo + Funcional: Cómo Conviven

En servicios reactivos, la cadena reactiva (`Mono`/`Flux`) es el único mecanismo de flujo. **No se mezcla con `Optional`** para control de flujo — `Optional` bloquea semánticamente y rompe la composición reactiva.

**Regla:** Dentro de una cadena reactiva, usar los operadores de Reactor en lugar de `Optional`:

| Necesidad               | ❌ Bloqueante / Imperativo | ✅ Reactivo                           |
| ----------------------- | -------------------------- | ------------------------------------- |
| Valor puede ser null    | `Optional.ofNullable(x)`   | `Mono.justOrEmpty(x)`                 |
| Valor ausente → error   | `opt.orElseThrow(...)`     | `mono.switchIfEmpty(Mono.error(...))` |
| Valor ausente → default | `opt.orElse(default)`      | `mono.defaultIfEmpty(default)`        |
| Transformar si presente | `opt.map(fn)`              | `mono.map(fn)`                        |
| Filtrar                 | `opt.filter(pred)`         | `mono.filter(pred)`                   |

**`Optional` es válido** solo en:

- Constructores/validadores de Records del dominio (capa `model`) para parámetros opcionales
- **ms-reporter** (imperativo): código tradicional con streams bloqueantes
- Retorno de métodos utilitarios puros sin I/O que no participan en cadenas reactivas
- Reglas del Engine que operan sobre datos en memoria (ver §3.1)

```java
// ✅ Reactivo — UseCase en ms-order
public Mono<Order> createOrder(CreateOrderCommand cmd) {
    return orderRepository.save(Order.from(cmd))
        .switchIfEmpty(Mono.error(new OrderCreationException("Failed to persist order")))
        .flatMap(order -> inventoryClient.reserveStock(order)
            .map(reservation -> order.withStatus(CONFIRMED)));
}

// ❌ NUNCA en servicios reactivos
public Mono<Order> createOrder(CreateOrderCommand cmd) {
    Optional<Order> saved = orderRepository.saveBlocking(cmd); // Bloquea el EventLoop
    return Mono.justOrEmpty(saved);
}
```

### 1.2 Controladores: `@RestController` con `Mono`/`Flux`

Se usa **`@RestController` anotado** (no Router Functions). Spring WebFlux maneja la reactividad por debajo de forma idéntica. Ventajas:

- Sintaxis declarativa y familiar
- Soporte nativo de `@Valid` para validación de DTOs en entry-points
- `@ControllerAdvice` para manejo global de errores
- Documentación automática con Springdoc/OpenAPI

```java
@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final CreateProductUseCase createProductUseCase;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return createProductUseCase.execute(ProductMapper.toCommand(request))
            .map(ProductMapper::toResponse);
    }
}
```

---

## 2. Modelado de Dominio (`domain/model`)

### 2.1 Records vs Clases: Cuándo Usar Cada Uno

**Decisión: Records como estándar del dominio.** Los Records de Java 21 son inmutables, generan `equals`/`hashCode`/`toString`, y el GC maneja correctamente las copias (no hay memory leak). Son la opción por defecto.

**Usar `record`** cuando:

- El objeto representa **datos** con identidad por valor: entidades, value objects, comandos, eventos, DTOs
- Se necesita inmutabilidad (que es siempre, en un dominio bien diseñado)
- Necesita lógica de validación en el _compact constructor_
- Necesita métodos derivados (cálculos sobre sus campos)

**Usar `class` con Lombok** (`@Builder`, `@Value`) **solo** cuando:

- Se requiere **herencia** (records no pueden extender clases — es la única restricción estructural real)
- El objeto necesita ser **mutable** por compatibilidad de framework (e.g., entidades JPA con setters — con R2DBC y Reactive Mongo esto no aplica)

> **Nota Lombok + Records:** Desde Lombok 1.18.20, `@Builder` funciona en records. Desde 1.18.32, `toBuilder = true` también es compatible. Con Lombok 1.18.42 (el stack de Arka) el soporte es completo. Por tanto, la cantidad de campos **ya no es una razón** para preferir una clase sobre un record — siempre se puede anotar el record con `@Builder`.

```java
// ✅ Record — Entidad de dominio (preferido)
public record Product(
        String productId,
        String sku,
        String name,
        Money price,
        CategoryRef category,
        boolean active,
        Instant createdAt
) {
    public Product {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(sku, "sku is required");
        if (price.isNegativeOrZero()) throw new IllegalArgumentException("Price must be positive");
    }

    public Product deactivate() {
        return new Product(productId, sku, name, price, category, false, createdAt);
    }
}

// ✅ Record — Value Object
public record Money(BigDecimal amount, Currency currency) {
    public Money {
        Objects.requireNonNull(amount);
        Objects.requireNonNull(currency);
    }
    public boolean isNegativeOrZero() { return amount.compareTo(BigDecimal.ZERO) <= 0; }
    public Money add(Money other) {
        if (!currency.equals(other.currency)) throw new IllegalArgumentException("Currency mismatch");
        return new Money(amount.add(other.amount), currency);
    }
}

// ✅ Record con @Builder — muchos campos, incluyendo defaults en compact constructor
// Nota: @Builder.Default NO funciona en records. Los defaults van en el compact constructor.
@Builder(toBuilder = true)
public record ReportFilter(
        String dateFrom,
        String dateTo,
        String status,
        int page,
        int size,
        String categoryId,
        String sku,
        String customerId,
        String sortBy,
        String sortDir
) {
    public ReportFilter {
        status = status != null ? status : "ALL";
        size = size > 0 ? size : 50;
        sortDir = sortDir != null ? sortDir : "DESC";
    }
}

// ✅ Clase con Lombok — SOLO si hay herencia o mutabilidad de framework obligatoria
// (Ejemplo: integración con librería externa que requiere herencia o setters)
@Value
@Builder
public class LegacyExportRequest extends BaseExportConfig { // herencia → record no puede
    String format;
    String destination;
}
```

### 2.2 Patrón Builder para Construcción de Objetos

Lombok `@Builder` se aplica tanto en records como en clases. La elección del tipo (record vs clase) se rige por §2.1 — no por la necesidad de Builder.

**Records:** Usar `@Builder` para construcción con campos opcionales. Agregar `with*()` para **copias parciales** (mutaciones inmutables comunes en el dominio).

```java
// Record con @Builder + with* para copias parciales
@Builder(toBuilder = true)
public record Order(
        String orderId,
        String customerId,
        OrderStatus status,
        List<OrderItem> items,
        Instant createdAt
) {
    // Copia inmutable con estado cambiado — no necesita @Builder
    public Order withStatus(OrderStatus newStatus) {
        return new Order(orderId, customerId, newStatus, items, createdAt);
    }
}

// Uso
Order order = Order.builder()
    .orderId(UUID.randomUUID().toString())
    .customerId(cmd.customerId())
    .status(new OrderStatus.PendingReserve())
    .items(cmd.items())
    .createdAt(Instant.now())
    .build();

// Copia parcial con toBuilder
Order confirmed = order.toBuilder()
    .status(new OrderStatus.Confirmed(Instant.now()))
    .build();
```

### 2.3 Sealed Interfaces para Máquinas de Estado y Resultados de Decisión

Sealed interfaces + records modelan **dominios cerrados** con exhaustividad verificada en compile-time. Uso principal: máquinas de estado, resultados polimórficos, eventos de dominio.

```java
public sealed interface OrderStatus permits
        OrderStatus.PendingReserve,
        OrderStatus.Confirmed,
        OrderStatus.InShipment,
        OrderStatus.Delivered,
        OrderStatus.Cancelled {

    record PendingReserve() implements OrderStatus {}
    record Confirmed(Instant confirmedAt) implements OrderStatus {}
    record InShipment(String trackingId) implements OrderStatus {}
    record Delivered(Instant deliveredAt) implements OrderStatus {}
    record Cancelled(String reason, Instant cancelledAt) implements OrderStatus {}
}
```

Combinado con **switch pattern matching** (Java 21):

```java
public String describe(OrderStatus status) {
    return switch (status) {
        case OrderStatus.PendingReserve() -> "Esperando reserva de inventario";
        case OrderStatus.Confirmed(var at) -> "Confirmada el " + at;
        case OrderStatus.InShipment(var tid) -> "En despacho: " + tid;
        case OrderStatus.Delivered(var at) -> "Entregada el " + at;
        case OrderStatus.Cancelled(var reason, _) -> "Cancelada: " + reason;
    };
}
```

---

## 3. Lógica de Negocio (`domain/usecase`)

### 3.1 Interfaces Funcionales + Engine

> **TL;DR:** Funcional y Reactivo **no son incompatibles — son complementarios.** Reactor es en sí mismo programación funcional aplicada a streams asíncronos. El Engine síncrono opera sobre datos en memoria (pura CPU); el reactivo cuando una regla necesita I/O.

Para **reglas de negocio evaluables** (validaciones, scoring, decisiones), se utiliza el patrón **Engine** con interfaces funcionales. Hay dos variantes según si las reglas requieren I/O o no.

#### Variante A — Reglas Puras (sin I/O): Engine Síncrono

Las reglas solo inspeccionan campos del agregado en memoria — no tocan base de datos ni servicios externos. La evaluación es pura CPU: no bloquea el EventLoop y se integra en la cadena reactiva sin problema.

```java
// Regla funcional pura — devuelve Optional (sin I/O)
@FunctionalInterface
public interface BusinessRule<T, R> {
    Optional<R> evaluate(T input);
}

// Engine síncrono — evaluación por streams funcionales
public class RuleEngine<T, R> {
    private final List<BusinessRule<T, R>> rules;
    private final R defaultResult;

    public RuleEngine(List<BusinessRule<T, R>> rules, R defaultResult) {
        this.rules = List.copyOf(rules);
        this.defaultResult = defaultResult;
    }

    public R evaluate(T input) {
        return rules.stream()
                .map(rule -> rule.evaluate(input))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(defaultResult);
    }
}
```

**Integración en un UseCase reactivo:** el engine corre antes de la cadena reactiva (es pura CPU) o se envuelve como valor si encadena con I/O posterior.

```java
@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final RuleEngine<Order, RejectionReason> riskEngine;

    public Mono<Order> execute(CreateOrderCommand cmd) {
        Order draft = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .customerId(cmd.customerId())
                .status(new OrderStatus.PendingReserve())
                .items(cmd.items())
                .createdAt(Instant.now())
                .build();

        // 1. Engine síncrono — pura CPU, NO bloquea el EventLoop
        RejectionReason rejection = riskEngine.evaluate(draft);
        if (rejection != null) {
            return Mono.error(new OrderRejectedException(rejection.reason()));
        }

        // 2. Continúa con I/O reactivo
        return orderRepository.save(draft)
                .flatMap(saved -> inventoryClient.reserveStock(saved)
                        .map(r -> saved.withStatus(new OrderStatus.Confirmed(Instant.now()))));
    }
}
```

#### Variante B — Reglas con I/O: Engine Reactivo

Cuando una regla necesita consultar la base de datos o un servicio externo (e.g., verificar si un cliente está en lista negra en Redis, validar crédito en un servicio externo), la interfaz retorna `Mono<Optional<R>>`.

```java
// Regla funcional reactiva — cada regla puede hacer I/O
@FunctionalInterface
public interface ReactiveBusinessRule<T, R> {
    Mono<Optional<R>> evaluate(T input);
}

// Engine reactivo — evaluación secuencial para mantener orden de prioridad
public class ReactiveRuleEngine<T, R> {
    private final List<ReactiveBusinessRule<T, R>> rules;
    private final R defaultResult;

    public ReactiveRuleEngine(List<ReactiveBusinessRule<T, R>> rules, R defaultResult) {
        this.rules = List.copyOf(rules);
        this.defaultResult = defaultResult;
    }

    public Mono<R> evaluate(T input) {
        return Flux.fromIterable(rules)
                .concatMap(rule -> rule.evaluate(input)) // concatMap: secuencial, respeta orden
                .filter(Optional::isPresent)
                .map(Optional::get)
                .next()                                  // toma la primera regla que dispare
                .defaultIfEmpty(defaultResult);
    }
}
```

```java
// Reglas reactivas como lambdas en el UseCase
ReactiveBusinessRule<Order, RejectionReason> blacklistRule = order ->
        customerBlacklistPort.isBlacklisted(order.customerId())
            .map(listed -> listed
                ? Optional.of(new RejectionReason("Cliente en lista restringida"))
                : Optional.<RejectionReason>empty());

ReactiveBusinessRule<Order, RejectionReason> creditRule = order ->
        creditPort.getCreditLimit(order.customerId())
            .map(limit -> order.totalAmount().compareTo(limit) > 0
                ? Optional.of(new RejectionReason("Monto excede límite de crédito"))
                : Optional.<RejectionReason>empty());

// UseCase con Engine reactivo
public Mono<Order> execute(CreateOrderCommand cmd) {
    Order draft = Order.builder()/* ... */.build();

    return reactiveRiskEngine.evaluate(draft)
            .flatMap(rejection -> rejection != null
                    ? Mono.error(new OrderRejectedException(rejection.reason()))
                    : orderRepository.save(draft));
}
```

#### Cuándo usar cada variante

| Variante                           | Cuándo                                                                    | Operador clave                                   |
| ---------------------------------- | ------------------------------------------------------------------------- | ------------------------------------------------ |
| **Síncrona** (`Optional<R>`)       | Reglas que solo leen campos del agregado en memoria                       | `stream().flatMap(Optional::stream).findFirst()` |
| **Reactiva** (`Mono<Optional<R>>`) | Reglas que consultan BD, caché o servicios externos                       | `Flux.concatMap(...).next()`                     |
| **Mixta**                          | Primero engine síncrono (fast-fail), luego validaciones reactivas si pasa | Encadenar ambas en el UseCase                    |

### 3.2 Strategy + Factory (Sin Switches/If-Else)

Para **comportamientos intercambiables** en runtime (tipos de notificación, pasarelas de pago, operadores logísticos), aplicar Strategy + Factory con `Supplier`. Elimina cadenas de `if/else` y `switch` manuales.

```java
// Strategy interface
public interface PaymentGateway {
    Mono<PaymentResult> processPayment(PaymentRequest request);
}

// Factory con registro de Suppliers — en domain/usecase o en infrastructure
public class PaymentGatewayFactory {
    private final Map<String, Supplier<PaymentGateway>> registry;

    public PaymentGatewayFactory(Map<String, Supplier<PaymentGateway>> registry) {
        this.registry = Map.copyOf(registry);
    }

    public PaymentGateway resolve(String gatewayType) {
        return Optional.ofNullable(registry.get(gatewayType.toUpperCase()))
            .map(Supplier::get)
            .orElseThrow(() -> new IllegalArgumentException("Unknown gateway: " + gatewayType));
    }
}
```

**Cuándo usar switch pattern matching en lugar de Strategy+Factory:**

- **Usar Strategy+Factory:** Cuando las implementaciones están en **infraestructura** (adapters), son inyectables y pueden crecer dinámicamente
- **Usar switch pattern matching:** Cuando el dominio es **cerrado** y finito (sealed interfaces), modelado en compile-time

```java
// ✅ Switch pattern matching — dominio cerrado (sealed)
return switch (status) {
    case PendingReserve() -> handlePendingReserve();
    case Confirmed(var at) -> handleConfirmed(at);
    case Cancelled(var reason, _) -> handleCancellation(reason);
    // ... exhaustivo en compile-time
};
```

---

## 4. Mapeo entre Capas

### Decisión: Mappers Manuales (Sin MapStruct)

**No se usa MapStruct.** Razones:

1. **Clean Architecture estricta** — los mapeos entre capas son explícitos y trazables. Un mapper manual deja claro qué campo va adónde
2. **Complejidad mínima** — cada microservicio tiene pocos DTOs; la carga de mapeo manual es baja y no justifica una dependencia extra
3. **Compatibilidad reactiva** — MapStruct genera código imperativo; integrarlo con Reactor requiere wrapping innecesario
4. **Acoplamiento** — MapStruct genera código en compile-time acoplado a nombres de campos; un rename causa errores silenciosos si no hay tests

**Convención:** Crear clases `*Mapper` con métodos estáticos en cada capa que necesite transformar. Usar **siempre el Builder** (`@Builder` de Lombok) al construir objetos destino — es más legible, tolerante a cambios de orden de campos y evita errores silenciosos por reordenamiento de parámetros.

```java
// infrastructure/entry-points — mapper del API REST
public final class ProductMapper {
    private ProductMapper() {}

    public static CreateProductCommand toCommand(CreateProductRequest req) {
        return CreateProductCommand.builder()
                .sku(req.sku())
                .name(req.name())
                .price(req.price())
                .categoryId(req.categoryId())
                .build();
    }

    public static ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .productId(product.productId())
                .sku(product.sku())
                .name(product.name())
                .price(product.price().amount())
                .active(product.active())
                .build();
    }
}
```

> Los records `CreateProductCommand`, `ProductResponse`, etc. deben anotarse con `@Builder` para que este patrón funcione (ver §2.1).

---

## 5. Manejo de Errores

### 5.1 `@ControllerAdvice` Global

Cada microservicio define un `GlobalExceptionHandler` en entry-points que traduce excepciones de dominio a respuestas HTTP estandarizadas.

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDomain(DomainException ex) {
        return Mono.just(ResponseEntity
            .status(ex.getHttpStatus())
            .body(new ErrorResponse(ex.getCode(), ex.getMessage())));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidation(ConstraintViolationException ex) {
        return Mono.just(ResponseEntity
            .badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return Mono.just(ResponseEntity
            .internalServerError()
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")));
    }
}
```

### 5.2 Errores en Cadenas Reactivas

Dentro de la cadena reactiva, usar operadores de error de Reactor — nunca `try/catch` alrededor de publishers.

```java
return orderRepository.findById(orderId)
    .switchIfEmpty(Mono.error(new OrderNotFoundException(orderId)))
    .flatMap(order -> inventoryClient.reserveStock(order)
        .onErrorResume(StockUnavailableException.class,
            e -> Mono.error(new OrderRejectedException("Stock insuficiente: " + e.getMessage()))))
    .retryWhen(Retry.backoff(3, Duration.ofMillis(500))
        .filter(TransientException.class::isInstance));
```

---

## 6. Concurrencia y Estructuras de Datos

### Regla General

En servicios reactivos, **la concurrencia es manejada por Reactor** (Schedulers, operadores). No usar `synchronized`, `Lock`, ni `ConcurrentHashMap` para control de flujo — Reactor lo abstrae.

**`ConcurrentHashMap` es válido para:**

- Registros/cachés en memoria dentro de Factories o Engines que se inicializan al startup y se leen concurrentemente
- Mapas compartidos en ms-reporter (imperativo) donde múltiples Virtual Threads acceden concurrentemente

**No usar nunca:**

- `synchronized` en beans reactivos (bloquea el EventLoop)
- `HashMap` mutable compartido entre threads sin protección

```java
// ✅ Factory con Map inmutable — no necesita ConcurrentHashMap
public class NotificationSenderFactory {
    private final Map<String, Supplier<NotificationSender>> registry;

    public NotificationSenderFactory() {
        this.registry = Map.of(  // Inmutable
            "EMAIL", EmailSender::new,
            "SMS", SmsSender::new
        );
    }
}

// ✅ ConcurrentHashMap — solo cuando el mapa muta después del startup
private final Map<String, MetricCounter> counters = new ConcurrentHashMap<>();
```

---

## 7. Logging

### 7.1 `CommandLineRunner` al Startup

Cada microservicio registra un log de confirmación al completar la inicialización.

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class MainApplication {

    private static final Logger log = LoggerFactory.getLogger(MainApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

    @Bean
    public CommandLineRunner initLog() {
        return args -> log.info("=== {} iniciado correctamente ===", "ms-catalog");
    }
}
```

### 7.2 Convenciones de Logging

- Usar **SLF4J** (`LoggerFactory`) — nunca `System.out.println`
- Loggear al **inicio y fin** de operaciones críticas (creación de orden, reserva de stock)
- Incluir siempre `correlationId` en logs de operaciones distribuidas
- Nivel `WARN` para eventos ignorados de Kafka (tolerancia a evolución)
- Nivel `ERROR` solo para fallos no recuperables

---

## 8. Testing

- **JUnit 5** + **Mockito** para unit tests de UseCases
- **StepVerifier** (`reactor-test`) para verificar publishers en servicios reactivos
- **BlockHound** para detectar llamadas bloqueantes inadvertidas en servicios WebFlux
- Test del dominio **sin Spring** — los UseCases se testean como POJOs con mocks de los ports

```java
@Test
void shouldCreateOrder_whenStockAvailable() {
    when(orderRepository.save(any())).thenReturn(Mono.just(expectedOrder));
    when(inventoryClient.reserveStock(any())).thenReturn(Mono.just(reservation));

    StepVerifier.create(createOrderUseCase.execute(command))
        .expectNextMatches(order -> order.status() instanceof OrderStatus.Confirmed)
        .verifyComplete();
}
```

---

## 9. Resumen de Decisiones

| Decisión                   | Resolución                                                                                                                       | Justificación                                                                               |
| -------------------------- | -------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| Record vs Clase en dominio | **Record** por defecto; clase solo cuando herencia o mutabilidad de framework lo exigen                                          | Inmutabilidad nativa; `@Builder` funciona en records desde Lombok 1.18.20                   |
| Optional en reactivo       | **No.** Usar `Mono.justOrEmpty`, `switchIfEmpty`                                                                                 | Optional bloquea semánticamente la cadena reactiva                                          |
| Controladores              | **`@RestController`** con `Mono`/`Flux`                                                                                          | `@Valid`, `@ControllerAdvice`, sintaxis declarativa                                         |
| Router Functions           | **No**                                                                                                                           | Complejidad sin beneficio; Spring maneja reactividad igual                                  |
| MapStruct                  | **No.** Mappers manuales con métodos estáticos                                                                                   | Trazabilidad, simplicidad, compatibilidad reactiva                                          |
| Builder                    | `@Builder` (Lombok) en records Y clases. Records también usan `with*()` para copias parciales                                    | Lombok 1.18.42 soporta `@Builder` completo en records; sin distinción por número de campos  |
| Estructuras concurrentes   | **Reactor maneja.** `ConcurrentHashMap` solo para mapas mutables de infraestructura                                              | Evitar interferir con el EventLoop                                                          |
| switch vs Strategy         | **switch pattern matching** para dominios sealed; **Strategy+Factory** para extensiones en infraestructura                       | Compile-time safety vs runtime extensibility                                                |
| Manejo de errores          | **`@ControllerAdvice`** + operadores de error Reactor                                                                            | Centralizado, reactivo, sin try/catch en publishers                                         |
| Null checks en records     | **`Objects.requireNonNull`** en compact constructor                                                                              | Idiomático JDK, conciso, lanza NPE (contrato estándar de Java)                              |
| Timestamps                 | **`Instant`** para persistencia; `LocalDateTime` solo si zona horaria es irrelevante                                             | `Instant` = UTC absoluto, compatible con `TIMESTAMPTZ` de PostgreSQL                        |
| Reglas en records          | **Sí.** Invariantes, campos calculados, métodos de consulta, mutaciones encapsuladas y `with*()` en el record                    | La entidad controla su propia consistencia; mutaciones lanzan `DomainException` específicas |
| DomainException            | **Abstract class** que extiende `RuntimeException`, no interfaz                                                                  | Interfaces no pueden extender clases; necesita `super(message)` compartido                  |
| Enums descriptivos         | Valores autoexplicativos (e.g. `RESTOCK`, `SHRINKAGE`); evitar genéricos como `MANUAL_ADJUSTMENT`                                | Trazabilidad sin depender de campos auxiliares como `reason`                                |
| Organización de UseCases   | **1 UseCase por entidad de dominio** con múltiples métodos; no 1 UseCase por operación con `execute()`                           | Cohesión por agregado, menos clases, inyección de dependencias simplificada                 |
| SQL ENUMs                  | **`CREATE TYPE ... AS ENUM`** sincronizado con Java; no `VARCHAR` para campos finitos. Requiere `EnumCodec` en R2DBC (ver §B.6)  | Validación en BD, mejor rendimiento, documentación implícita                                |
| Generación de componentes  | **Siempre usar Scaffold Bancolombia** (`generateModel`, `generateUseCase`, etc.)                                                 | Estructura consistente; modificar contenido, nunca crear carpetas manualmente               |
| Driven Adapters R2DBC      | **Enfoque híbrido:** `ReactiveCrudRepository` + DTOs para CRUD simple; `DatabaseClient` + RowMapper para SQL complejo (ver §B.9) | Simplicidad para CRUD, control total para FOR UPDATE y lock optimista                       |
| Spring Profiles            | **`local`** (default en IntelliJ) y **`docker`** (inyectado por Compose). 3 archivos YAML por micro (ver §B.10)                  | Cambio automático entre BD local y contenedores sin tocar código                            |

---

## Apéndice A: Ejemplo Completo — Sealed Interface + Engine (Síncrono + Reactivo) + UseCase

```java
// Máquina de estados cerrada como sealed interface
public sealed interface RiskDecision permits
        RiskDecision.Approved,
        RiskDecision.ReviewRequired,
        RiskDecision.Rejected {
    record Approved(String reason) implements RiskDecision {}
    record ReviewRequired(String reason, List<String> warnings) implements RiskDecision {}
    record Rejected(String reason, List<String> errors) implements RiskDecision {}
}

// ——— VARIANTE A: Reglas puras (sin I/O) — Engine Síncrono ———

@FunctionalInterface
public interface RiskRule {
    Optional<RiskDecision> evaluate(Order order);
}

public class RiskEngine {
    private final List<RiskRule> rules = List.of(
        order -> order.totalExceeds(MAX_AMOUNT)
            ? Optional.of(new RiskDecision.ReviewRequired("Monto alto", List.of("REVIEW_AMOUNT")))
            : Optional.empty(),
        order -> order.hasInvalidItems()
            ? Optional.of(new RiskDecision.Rejected("Items inválidos", List.of("INVALID_ITEMS")))
            : Optional.empty()
    );

    public RiskDecision evaluate(Order order) {
        return rules.stream()
            .map(rule -> rule.evaluate(order))
            .flatMap(Optional::stream)
            .findFirst()
            .orElse(new RiskDecision.Approved("All checks passed"));
    }
}

// ——— VARIANTE B: Reglas con I/O — Engine Reactivo ———

@FunctionalInterface
public interface ReactiveRiskRule {
    Mono<Optional<RiskDecision>> evaluate(Order order);
}

public class ReactiveRiskEngine {
    private final List<ReactiveRiskRule> rules;

    public ReactiveRiskEngine(CustomerBlacklistPort blacklistPort, CreditPort creditPort) {
        this.rules = List.of(
            order -> blacklistPort.isBlacklisted(order.customerId())
                .map(listed -> listed
                    ? Optional.of(new RiskDecision.Rejected("Blacklisted", List.of("BLACKLIST")))
                    : Optional.<RiskDecision>empty()),
            order -> creditPort.getCreditLimit(order.customerId())
                .map(limit -> order.totalAmount().compareTo(limit) > 0
                    ? Optional.of(new RiskDecision.ReviewRequired("Excede crédito", List.of("CREDIT_LIMIT")))
                    : Optional.<RiskDecision>empty())
        );
    }

    public Mono<RiskDecision> evaluate(Order order) {
        return Flux.fromIterable(rules)
            .concatMap(rule -> rule.evaluate(order))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .next()
            .defaultIfEmpty(new RiskDecision.Approved("All checks passed"));
    }
}

// ——— PATRÓN MIXTO en UseCase: fast-fail síncrono + validación reactiva profunda ———

@RequiredArgsConstructor
public class CreateOrderUseCase {
    private final OrderRepository orderRepository;
    private final RiskEngine riskEngine;             // reglas puras — en memoria
    private final ReactiveRiskEngine reactiveRisk;   // reglas con I/O

    public Mono<Order> execute(CreateOrderCommand cmd) {
        Order draft = Order.builder()
                .orderId(UUID.randomUUID().toString())
                .customerId(cmd.customerId())
                .status(new OrderStatus.PendingReserve())
                .items(cmd.items())
                .createdAt(Instant.now())
                .build();

        // 1. Fast-fail: reglas en memoria (sin I/O, no bloquea el EventLoop)
        RiskDecision syncDecision = riskEngine.evaluate(draft);
        if (syncDecision instanceof RiskDecision.Rejected r) {
            return Mono.error(new OrderRejectedException(r.reason()));
        }

        // 2. Validaciones que requieren I/O (blacklist, crédito)
        return reactiveRisk.evaluate(draft)
                .flatMap(decision -> switch (decision) {
                    case RiskDecision.Rejected rej ->
                        Mono.error(new OrderRejectedException(rej.reason()));
                    case RiskDecision.ReviewRequired rev ->
                        orderRepository.save(draft.withStatus(new OrderStatus.PendingReview()));
                    case RiskDecision.Approved app ->
                        orderRepository.save(draft);
                });
    }
}
```

## Apéndice B: Estándares Adicionales Definidos Durante Implementación

### B.1 Validación en Compact Constructors: `Objects.requireNonNull`

Para precondiciones de nullidad en records, usar `Objects.requireNonNull(field, "message")` en lugar de `if (field == null) throw new IllegalArgumentException(...)`. Es el estándar de la JDK para precondiciones de nullidad, más conciso y semánticamente correcto (lanza `NullPointerException`, que es el contrato de Java para argumentos nulos).

```java
// ✅ Idiomático — Objects.requireNonNull
public record Stock(UUID id, String sku, UUID productId, int quantity) {
    public Stock {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(productId, "productId is required");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
    }
}

// ❌ Verbose — if/throw manual para nulls
public record Stock(UUID id, String sku, UUID productId, int quantity) {
    public Stock {
        if (sku == null) throw new IllegalArgumentException("sku is required");
    }
}
```

### B.2 Timestamps: `Instant` sobre `LocalDateTime`

Usar `Instant` para todos los campos de fecha/hora que se persisten en `TIMESTAMP WITH TIME ZONE` de PostgreSQL. `LocalDateTime` no tiene zona horaria, lo que causa bugs en entornos multi-región. `Instant` representa un punto absoluto en el tiempo (UTC), que es exactamente lo que `TIMESTAMPTZ` almacena.

| Tipo            | Zona horaria | Columna SQL                 | Uso                                   |
| --------------- | ------------ | --------------------------- | ------------------------------------- |
| `Instant`       | UTC absoluto | `TIMESTAMP WITH TIME ZONE`  | Persistencia, eventos, auditoría      |
| `LocalDateTime` | Sin zona     | `TIMESTAMP` (sin time zone) | Solo si la zona es irrelevante (raro) |

### B.3 Reglas de Dominio en Records

Los records pueden y deben contener lógica de dominio que la entidad controla sin necesidad de UseCase. Esto incluye:

- **Invariantes** en el compact constructor (validaciones que siempre deben cumplirse)
- **Campos calculados** (e.g., `availableQuantity = quantity - reservedQuantity`)
- **Métodos de consulta** que responden preguntas sobre el estado de la entidad
- **Métodos de mutación encapsulada** que devuelven nuevas instancias inmutables con validaciones de dominio
- **Métodos `with*()`** para copias inmutables con mutaciones comunes

#### B.3.1 Encapsulamiento de Mutaciones en Records

Las entidades de dominio deben encapsular **todas las operaciones de mutación** como métodos que devuelven una nueva instancia inmutable vía `toBuilder()`. Esto garantiza que:

1. **Las reglas de negocio viven en la entidad**, no en el UseCase ni en capas externas
2. **Es imposible construir un estado inválido** — cada mutación valida internamente antes de producir la nueva instancia
3. **Las excepciones son de dominio**, no genéricas (`IllegalArgumentException`) — cada violación de regla lanza una `DomainException` específica con HTTP status y código de error

**Regla:** Nunca manipular campos de una entidad desde fuera usando `toBuilder()` directamente para operaciones que tienen reglas de negocio. Siempre usar el método de mutación encapsulada del record.

```java
// ❌ NUNCA — lógica de dominio fugada al UseCase
Stock updated = stock.toBuilder()
    .quantity(stock.quantity() - requested)
    .updatedAt(Instant.now())
    .build();

// ✅ SIEMPRE — la entidad protege sus invariantes
Stock updated = stock.decreaseBy(requested);
```

**Patrón de implementación:**

```java
@Builder(toBuilder = true)
public record Stock(UUID id, String sku, UUID productId, int quantity,
                    int reservedQuantity, int availableQuantity,
                    int depletionThreshold, Instant updatedAt, long version) {
    public static final int DEFAULT_DEPLETION_THRESHOLD = 10;

    public Stock {
        // Invariantes en compact constructor
        Objects.requireNonNull(sku, "sku is required");
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        if (reservedQuantity < 0) throw new IllegalArgumentException("reservedQuantity must be >= 0");
        if (reservedQuantity > quantity)
            throw new IllegalArgumentException("reservedQuantity cannot exceed quantity");
        if (depletionThreshold < 0) throw new IllegalArgumentException("depletionThreshold must be >= 0");
        // Campo calculado
        availableQuantity = quantity - reservedQuantity;
        // Defaults condicionales
        depletionThreshold = depletionThreshold > 0 ? depletionThreshold : DEFAULT_DEPLETION_THRESHOLD;
        version = version > 0 ? version : 1;
    }

    // --- Métodos de consulta ---
    public boolean canReserve(int requestedQuantity) { return availableQuantity >= requestedQuantity; }
    public boolean isBelowThreshold() { return availableQuantity <= depletionThreshold; }

    // --- Mutaciones encapsuladas con excepciones de dominio ---

    public Stock increaseBy(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        return this.toBuilder().quantity(this.quantity + amount).updatedAt(Instant.now()).build();
    }

    public Stock decreaseBy(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        if (amount > availableQuantity) throw new InsufficientStockException(sku, amount, availableQuantity);
        return this.toBuilder().quantity(this.quantity - amount).updatedAt(Instant.now()).build();
    }

    public Stock setQuantity(int newQuantity) {
        if (newQuantity < 0) throw new InvalidStockQuantityException(sku, newQuantity, "must be >= 0");
        if (newQuantity < reservedQuantity)
            throw new InvalidStockQuantityException(sku, newQuantity, reservedQuantity);
        return this.toBuilder().quantity(newQuantity).updatedAt(Instant.now()).build();
    }

    public Stock reserve(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        if (amount > availableQuantity) throw new InsufficientStockException(sku, amount, availableQuantity);
        return this.toBuilder().reservedQuantity(this.reservedQuantity + amount).updatedAt(Instant.now()).build();
    }

    public Stock releaseReservation(int amount) {
        if (amount <= 0) throw new InvalidStockQuantityException(sku, amount, "must be > 0");
        if (amount > reservedQuantity)
            throw new ExcessiveReleaseException(sku, amount, reservedQuantity);
        return this.toBuilder().reservedQuantity(this.reservedQuantity - amount).updatedAt(Instant.now()).build();
    }
}
```

**Cuándo usar cada tipo de excepción en mutaciones:**

| Violación                                           | Excepción                       | HTTP | Código                   |
| --------------------------------------------------- | ------------------------------- | ---- | ------------------------ |
| Cantidad solicitada > stock disponible              | `InsufficientStockException`    | 409  | `INSUFFICIENT_STOCK`     |
| Cantidad inválida (negativa, cero cuando no aplica) | `InvalidStockQuantityException` | 409  | `INVALID_STOCK_QUANTITY` |
| Liberación excede cantidad reservada                | `ExcessiveReleaseException`     | 409  | `EXCESSIVE_RELEASE`      |
| Nuevo quantity < reservedQuantity                   | `InvalidStockQuantityException` | 409  | `INVALID_STOCK_QUANTITY` |

> **Nota:** Este patrón aplica a cualquier entidad de dominio con reglas de negocio sobre sus campos, no solo a `Stock`. Cada microservicio debe encapsular las mutaciones de sus agregados de la misma forma.

### B.4 Excepciones de Dominio: Abstract Class (no Interface)

`DomainException` es una clase abstracta que extiende `RuntimeException`, no una interfaz. Razones:

1. Necesita extender `RuntimeException` para integrarse con `@ControllerAdvice` y el mecanismo de excepciones de Java
2. Una interfaz no puede extender una clase
3. La clase abstracta permite compartir el constructor `super(message)` y forzar la implementación de `getHttpStatus()` y `getCode()`

```java
public abstract class DomainException extends RuntimeException {
    protected DomainException(String message) { super(message); }
    public abstract int getHttpStatus();
    public abstract String getCode();
}

public class StockNotFoundException extends DomainException {
    public StockNotFoundException(String sku) { super("Stock not found for SKU: " + sku); }
    @Override public int getHttpStatus() { return 404; }
    @Override public String getCode() { return "STOCK_NOT_FOUND"; }
}
```

### B.5 Enums Descriptivos con Trazabilidad

Los enums de dominio deben ser autoexplicativos. Cada valor debe indicar exactamente qué operación representa, sin depender de campos auxiliares como `reason` para entender el movimiento.

```java
// ✅ Descriptivo — cada valor es autoexplicativo
public enum MovementType {
    RESTOCK,              // Ingreso de mercancía a bodega
    SHRINKAGE,            // Reducción por merma, daño o ajuste
    ORDER_RESERVE,        // Reserva asociada a una orden
    ORDER_CONFIRM,        // Confirmación de orden
    RESERVATION_RELEASE,  // Liberación de reserva
    PRODUCT_CREATION      // Stock inicial al crear producto
}

// ❌ Genérico — requiere campo "reason" para entender qué pasó
public enum MovementType {
    MANUAL_ADJUSTMENT  // ¿Fue restock? ¿Fue merma? No se sabe sin leer "reason"
}
```

### B.6 PostgreSQL ENUMs Sincronizados con Java

Para campos con valores finitos y conocidos, usar `CREATE TYPE ... AS ENUM` en PostgreSQL en lugar de `VARCHAR`. Ventajas:

- Validación a nivel de BD (rechaza valores inválidos)
- Mejor rendimiento (almacenamiento interno como entero)
- Documentación implícita del esquema
- Sincronización explícita con los enums de Java

Los valores del ENUM de PostgreSQL deben coincidir exactamente con los valores del enum de Java (case-sensitive).

```sql
-- Sincronizado con com.arka.model.stockmovement.MovementType
CREATE TYPE movement_type AS ENUM (
    'RESTOCK', 'SHRINKAGE', 'ORDER_RESERVE', 'ORDER_CONFIRM',
    'RESERVATION_RELEASE', 'PRODUCT_CREATION'
);

CREATE TABLE stock_movements (
    movement_type  movement_type NOT NULL,  -- ✅ ENUM, no VARCHAR
    ...
);
```

#### Configuración Obligatoria de `EnumCodec` en Driven Adapters R2DBC

El driver `r2dbc-postgresql` soporta ENUMs de PostgreSQL, pero **requiere configuración explícita** mediante `EnumCodec`. Sin esta configuración, el driver no puede codificar ni decodificar los tipos ENUM custom de PostgreSQL y lanzará `IllegalArgumentException: Cannot encode parameter of type <EnumClass>`.

**Paso 1 — Registrar cada ENUM en la configuración de conexión:**

Cada ENUM de PostgreSQL debe mapearse a su clase Java correspondiente usando `EnumCodec.builder()`. Esto se registra como `CodecRegistrar` en la configuración de la `ConnectionFactory`.

```java
// infrastructure/driven-adapters — configuración R2DBC
@Configuration
public class R2dbcConfig {

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer enumCodecCustomizer() {
        return builder -> builder.option(
            PostgresqlConnectionFactoryProvider.OPTIONS,
            Map.of("lock_timeout", "10s")
        );
    }

    @Bean
    public CodecRegistrar enumCodecRegistrar() {
        return EnumCodec.builder()
            .withEnum("movement_type", MovementType.class)
            .withEnum("reservation_status", ReservationStatus.class)
            .withEnum("outbox_status", OutboxStatus.class)
            .withEnum("event_type", EventType.class)
            .build();
    }
}
```

**Paso 2 — Registrar el `CodecRegistrar` en la `ConnectionFactory`:**

Si se usa configuración programática de la `ConnectionFactory` (en lugar de auto-config puro), registrar el codec directamente:

```java
PostgresqlConnectionConfiguration.builder()
    .host("localhost")
    .port(5433)
    .database("db_inventory")
    .username("user")
    .password("pass")
    .codecRegistrar(EnumCodec.builder()
        .withEnum("movement_type", MovementType.class)
        .withEnum("reservation_status", ReservationStatus.class)
        .withEnum("outbox_status", OutboxStatus.class)
        .withEnum("event_type", EventType.class)
        .build())
    .build();
```

**Paso 3 — Registrar `WritingConverter` para Spring Data R2DBC:**

Spring Data R2DBC necesita converters adicionales para mapear enums de Java a los tipos ENUM de PostgreSQL al usar `R2dbcEntityTemplate` o `ReactiveCrudRepository`. Crear un `@WritingConverter` por cada enum usando `EnumWriteSupport`:

```java
// Un converter por cada enum que se persiste como ENUM de PostgreSQL
@WritingConverter
public class MovementTypeWritingConverter extends EnumWriteSupport<MovementType> {}

@WritingConverter
public class ReservationStatusWritingConverter extends EnumWriteSupport<ReservationStatus> {}

@WritingConverter
public class OutboxStatusWritingConverter extends EnumWriteSupport<OutboxStatus> {}

@WritingConverter
public class EventTypeWritingConverter extends EnumWriteSupport<EventType> {}
```

Registrar los converters en la configuración de R2DBC:

```java
@Configuration
public class R2dbcCustomConversionsConfig extends AbstractR2dbcConfiguration {

    @Override
    public ConnectionFactory connectionFactory() {
        // ... configuración de ConnectionFactory
    }

    @Override
    protected List<Object> getCustomConverters() {
        return List.of(
            new MovementTypeWritingConverter(),
            new ReservationStatusWritingConverter(),
            new OutboxStatusWritingConverter(),
            new EventTypeWritingConverter()
        );
    }
}
```

**Alternativa con `DatabaseClient` (sin Spring Data):** Si el driven adapter usa `DatabaseClient` directamente con SQL manual, los `WritingConverter` no son necesarios — basta con el `EnumCodec` del Paso 1/2. El `DatabaseClient` usa los codecs del driver directamente al hacer `.bind("status", ReservationStatus.PENDING)`.

**Checklist al agregar un nuevo ENUM:**

1. Crear el `CREATE TYPE ... AS ENUM` en el script SQL de inicialización
2. Crear el `enum` Java correspondiente con valores idénticos (case-sensitive)
3. Registrar el mapeo en `EnumCodec.builder().withEnum("pg_type", JavaEnum.class)`
4. Si se usa Spring Data R2DBC: crear `@WritingConverter` con `EnumWriteSupport<JavaEnum>`
5. Verificar que lectura y escritura funcionan en tests de integración

### B.7 Scaffold Bancolombia: Siempre Usar para Generar Componentes

Todos los componentes del microservicio (modelos, use cases, driven adapters, entry points) deben generarse con el plugin Scaffold Bancolombia. Una vez generado el boilerplate, se modifica el contenido respetando la estructura de paquetes y archivos del plugin.

```bash
# Generar modelo (entidad + gateway interface)
./gradlew generateModel --name=Stock

# Generar caso de uso
./gradlew generateUseCase --name=UpdateStock

# Generar driven adapter
./gradlew generateDrivenAdapter --type=r2dbc

# Generar entry point
./gradlew generateEntryPoint --type=webflux
```

**Regla:** Nunca crear manualmente las carpetas o archivos que el scaffold genera. Modificar solo el contenido.

### B.8 Organización de UseCases: 1 UseCase por Entidad de Dominio

Los UseCases se organizan por **entidad de dominio principal** (agregado), no por operación individual. Cada UseCase agrupa todos los métodos de negocio que operan sobre esa entidad, en lugar de crear una clase separada con un solo método `execute()` por cada operación.

**Regla:** Generar con `./gradlew generateUseCase --name=<Entidad>` (e.g., `--name=Stock`). El scaffold crea el paquete y la clase. Luego agregar los métodos de negocio como métodos públicos con nombres descriptivos.

**Criterio de agrupación:** La entidad que es el sujeto principal de la operación determina a qué UseCase pertenece. Si una operación modifica `Stock` como efecto principal, va en `StockUseCase` aunque también toque `StockReservation` o `OutboxEvent`.

```java
// ✅ 1 UseCase por entidad — múltiples métodos descriptivos
@RequiredArgsConstructor
public class StockUseCase {
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OutboxEventRepository outboxEventRepository;
    // ... otros ports necesarios

    public Mono<Stock> getBySku(String sku) { /* ... */ }
    public Flux<StockMovement> getHistory(String sku, int page, int size) { /* ... */ }
    public Mono<Stock> updateStock(String sku, int newQuantity, String reason) { /* ... */ }
    public Mono<ReserveStockResult> reserveStock(String sku, UUID orderId, int quantity) { /* ... */ }
    public Mono<Void> processProductCreated(UUID eventId, String sku, UUID productId, int initialStock) { /* ... */ }
}

// ❌ NUNCA — 1 UseCase por operación con execute()
public class GetStockUseCase {
    public Mono<Stock> execute(String sku) { /* ... */ }
}
public class UpdateStockUseCase {
    public Mono<Stock> execute(String sku, int qty, String reason) { /* ... */ }
}
public class ReserveStockUseCase {
    public Mono<ReserveStockResult> execute(String sku, UUID orderId, int qty) { /* ... */ }
}
```

**Ventajas:**

- Cohesión por agregado: toda la lógica de `Stock` vive en un solo lugar
- Menos clases: 3 UseCases en lugar de 7+ para ms-inventory
- DI simplificada: los entry-points inyectan 1 UseCase en lugar de 3-4
- Los métodos privados auxiliares (e.g., `emitStockDepletedIfNeeded`) se comparten naturalmente entre operaciones del mismo agregado

**Ejemplo de mapeo entidad → UseCase:**

| Entidad principal  | UseCase                   | Métodos                                                                          |
| ------------------ | ------------------------- | -------------------------------------------------------------------------------- |
| `Stock`            | `StockUseCase`            | `getBySku`, `getHistory`, `updateStock`, `reserveStock`, `processProductCreated` |
| `StockReservation` | `StockReservationUseCase` | `expireReservations`, `processOrderCancelled`                                    |
| `OutboxEvent`      | `OutboxRelayUseCase`      | `fetchPendingEvents`, `markAsPublished`                                          |

### B.9 Driven Adapters R2DBC: Enfoque Híbrido (ReactiveCrudRepository + DatabaseClient)

Los driven adapters R2DBC usan un **enfoque híbrido** que combina `ReactiveCrudRepository` para operaciones CRUD simples con `DatabaseClient` para SQL complejo. Esto maximiza la simplicidad sin sacrificar control.

#### Decisión y Justificación

| Enfoque                                 | Cuándo usar                                                          | Ejemplo                                                                         |
| --------------------------------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| `ReactiveCrudRepository` + DTO `@Table` | CRUD simple, query methods derivados, `@Query` con SQL nativo        | `save()`, `findBySku()`, `findByStatus()`, `deleteById()`                       |
| `DatabaseClient` + RowMapper            | SQL que no se puede expresar en `@Query` o que requiere control fino | `SELECT ... FOR UPDATE`, `UPDATE ... WHERE version = ?` con retorno condicional |

**¿Por qué no solo `ReactiveCrudRepository`?**

- `@Query` soporta SQL nativo incluyendo `FOR UPDATE`, pero no permite lógica condicional sobre el resultado (e.g., retornar `Mono.empty()` si 0 rows afectadas para lock optimista)
- `@Modifying @Query` retorna `Mono<Integer>` (rows afectadas), pero no la entidad actualizada — requiere una segunda query

**¿Por qué no solo `DatabaseClient`?**

- Para operaciones simples (save, findBy...) es código innecesario — `ReactiveCrudRepository` lo resuelve en una línea
- Los query methods derivados son más legibles y menos propensos a errores de SQL

#### Estructura del Adapter Híbrido

Cada adapter inyecta tanto el `ReactiveCrudRepository` (para CRUD) como el `DatabaseClient` (para SQL complejo). El DTO `@Table` es la entidad de infraestructura que Spring Data necesita; el RowMapper se usa solo para las queries manuales con `DatabaseClient`.

```java
@Repository
@RequiredArgsConstructor
public class R2dbcStockAdapter implements StockRepository {

    private final SpringDataStockRepository repository;  // ReactiveCrudRepository
    private final DatabaseClient client;                  // Para SQL complejo

    // ✅ CRUD simple → ReactiveCrudRepository
    @Override
    public Mono<Stock> findBySku(String sku) {
        return repository.findBySku(sku)
                .map(StockDTOMapper::toDomain);
    }

    @Override
    public Mono<Stock> save(Stock stock) {
        return repository.save(StockDTOMapper.toDTO(stock))
                .map(StockDTOMapper::toDomain);
    }

    // ✅ SQL complejo → DatabaseClient
    @Override
    public Mono<Stock> findBySkuForUpdate(String sku) {
        return client.sql("SELECT * FROM stock WHERE sku = :sku FOR UPDATE")
                .bind("sku", sku)
                .map(StockRowMapper::map)
                .one();
    }

    @Override
    public Mono<Stock> updateQuantity(String sku, int newQuantity, long expectedVersion) {
        return client.sql("UPDATE stock SET quantity = :qty, version = version + 1, " +
                        "updated_at = NOW() WHERE sku = :sku AND version = :version")
                .bind("qty", newQuantity)
                .bind("sku", sku)
                .bind("version", expectedVersion)
                .fetch().rowsUpdated()
                .filter(rows -> rows > 0)
                .flatMap(rows -> findBySku(sku));
    }
}
```

#### DTO `@Table` (Entidad de Infraestructura)

El DTO vive en el paquete del driven adapter, nunca en `domain/model`. Usa `@Table` y `@Column` de Spring Data R2DBC. Los campos deben coincidir con las columnas de la tabla.

```java
// infrastructure/driven-adapters — DTO de persistencia
@Table("stock")
@Builder(toBuilder = true)
public record StockDTO(
    @Id UUID id,
    String sku,
    @Column("product_id") UUID productId,
    int quantity,
    @Column("reserved_quantity") int reservedQuantity,
    @Column("depletion_threshold") int depletionThreshold,
    @Column("updated_at") Instant updatedAt,
    long version
) {}
```

> **Nota:** `available_quantity` es `GENERATED ALWAYS AS` en PostgreSQL — no se incluye en el DTO porque no se puede insertar ni actualizar.

#### Mapper entre DTO y Dominio

Clase con métodos estáticos que convierte entre el DTO de infraestructura y la entidad de dominio. Vive junto al DTO en el paquete del driven adapter.

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StockDTOMapper {

    static Stock toDomain(StockDTO dto) {
        return Stock.builder()
                .id(dto.id())
                .sku(dto.sku())
                .productId(dto.productId())
                .quantity(dto.quantity())
                .reservedQuantity(dto.reservedQuantity())
                .depletionThreshold(dto.depletionThreshold())
                .updatedAt(dto.updatedAt())
                .version(dto.version())
                .build();
    }

    static StockDTO toDTO(Stock domain) {
        return StockDTO.builder()
                .id(domain.id())
                .sku(domain.sku())
                .productId(domain.productId())
                .quantity(domain.quantity())
                .reservedQuantity(domain.reservedQuantity())
                .depletionThreshold(domain.depletionThreshold())
                .updatedAt(domain.updatedAt())
                .version(domain.version())
                .build();
    }
}
```

#### RowMapper para DatabaseClient

Para las queries manuales con `DatabaseClient`, se usa un RowMapper con `Readable` (interfaz de R2DBC SPI, compatible con Spring Boot 4.x / Spring Framework 7.x). El RowMapper mapea directamente de la fila SQL a la entidad de dominio — no pasa por el DTO porque `DatabaseClient` no usa Spring Data mapping.

```java
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class StockRowMapper {

    static Stock map(Readable row) {
        return Stock.builder()
                .id(row.get("id", UUID.class))
                .sku(row.get("sku", String.class))
                .productId(row.get("product_id", UUID.class))
                .quantity(row.get("quantity", Integer.class))
                .reservedQuantity(row.get("reserved_quantity", Integer.class))
                .depletionThreshold(row.get("depletion_threshold", Integer.class))
                .updatedAt(row.get("updated_at", Instant.class))
                .version(row.get("version", Long.class))
                .build();
    }
}
```

**¿Qué es `Readable`?** Es la interfaz padre de `Row` en R2DBC SPI (`io.r2dbc.spi.Readable`). En Spring Boot 4.x, `DatabaseClient.map()` acepta `Function<? super Readable, T>`. El método `row.get("columna", Tipo.class)` lee el valor de la columna y lo convierte al tipo Java automáticamente (UUID↔uuid, Integer↔integer, Instant↔timestamptz).

**¿Por qué el RowMapper no usa el DTO?** Porque `DatabaseClient` no pasa por el mapping de Spring Data — trabaja directamente con filas R2DBC. Crear un DTO intermedio sería código muerto. El RowMapper ya cumple la función de aislamiento entre SQL y dominio.

#### Cuándo usar cada herramienta — Guía rápida

| Operación                                              | Herramienta                     | Razón                                             |
| ------------------------------------------------------ | ------------------------------- | ------------------------------------------------- |
| `save(entity)`                                         | `ReactiveCrudRepository.save()` | Insert/Update automático con `@Id`                |
| `findBySku(sku)`                                       | Query method derivado           | Una línea, sin SQL                                |
| `findByStatusAndExpiresAtBefore(...)`                  | Query method derivado           | Composición de condiciones                        |
| `SELECT ... FOR UPDATE`                                | `DatabaseClient` + RowMapper    | `@Query` no permite lógica condicional post-query |
| `UPDATE ... WHERE version = ?` con retorno condicional | `DatabaseClient`                | Necesita `filter(rows > 0)` para lock optimista   |
| `@Query("SELECT ... ORDER BY ... LIMIT ...")`          | `@Query` en Repository          | SQL nativo simple sin lógica post-query           |
| `@Modifying @Query("UPDATE ...")`                      | `@Query` en Repository          | UPDATE/DELETE simple que retorna rows afectadas   |

#### Archivos por Adapter

Cada adapter produce estos archivos en su paquete:

```text
com/arka/r2dbc/stock/
├── R2dbcStockAdapter.java          # Implementa StockRepository (port)
├── SpringDataStockRepository.java  # ReactiveCrudRepository<StockDTO, UUID>
├── StockDTO.java                   # DTO @Table (entidad de infraestructura)
├── StockDTOMapper.java             # Mapper DTO ↔ Dominio
└── StockRowMapper.java             # Mapper Readable → Dominio (para DatabaseClient)
```

### B.10 Spring Profiles: Configuración Local vs Docker

Cada microservicio usa Spring Profiles para cambiar automáticamente entre la BD local (desarrollo en IntelliJ) y los contenedores Docker (ecosistema completo con Compose).

#### Archivos de configuración por microservicio

```text
ms-<name>/applications/app-service/src/main/resources/
├── application.yaml          # Config compartida + defaults para local
├── application-local.yaml    # Override para desarrollo en IntelliJ (perfil por defecto)
└── application-docker.yaml   # Override para Docker Compose (hostnames de contenedores)
```

#### Cómo funciona

- `application.yaml` define `spring.profiles.active: ${SPRING_PROFILES_ACTIVE:local}` — si no se define la variable, usa `local`
- Desde IntelliJ: no se configura nada. El perfil `local` se activa automáticamente. Apunta a `localhost` con el puerto mapeado del `.env`
- Desde Docker Compose: `SPRING_PROFILES_ACTIVE=docker` se inyecta como variable de entorno en `compose.yaml`. El perfil `docker` apunta al hostname del contenedor de BD (e.g., `arka-db-inventory`) en el puerto interno 5432

#### Ejemplo — Microservicio con PostgreSQL R2DBC

```yaml
# application.yaml — config base con defaults para local
server:
  port: ${MS_INVENTORY_PORT:8082}
spring:
  application:
    name: ms-inventory
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  r2dbc:
    url: r2dbc:postgresql://${R2DBC_HOST:localhost}:${R2DBC_PORT:5433}/${R2DBC_DB:db_inventory}
    username: ${R2DBC_USER:arka}
    password: ${R2DBC_PASSWORD:arkaSecret2025}
```

```yaml
# application-local.yaml — desarrollo en IntelliJ
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5433/db_inventory
    username: arka
    password: arkaSecret2025
```

```yaml
# application-docker.yaml — Docker Compose
spring:
  r2dbc:
    url: r2dbc:postgresql://arka-db-inventory:5432/db_inventory
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
```

#### Diferencia clave entre local y docker

| Aspecto      | Perfil `local`                         | Perfil `docker`                                     |
| ------------ | -------------------------------------- | --------------------------------------------------- |
| Host de BD   | `localhost`                            | Hostname del contenedor (e.g., `arka-db-inventory`) |
| Puerto de BD | Puerto mapeado del `.env` (e.g., 5433) | Puerto interno del contenedor (siempre 5432)        |
| Credenciales | Hardcoded en el YAML                   | Variables de entorno del `.env`                     |
| Activación   | Automática (default)                   | `SPRING_PROFILES_ACTIVE=docker` en `compose.yaml`   |

#### Convención en compose.yaml

Todos los microservicios deben tener `SPRING_PROFILES_ACTIVE=docker` en su sección `environment`:

```yaml
ms-inventory:
  environment:
    - SPRING_PROFILES_ACTIVE=docker
  env_file:
    - .env
```

#### Flujo de desarrollo recomendado

1. Levantar solo la BD: `docker compose up postgres-inventory -d`
2. Correr el micro desde IntelliJ (perfil `local` automático, apunta a `localhost:5433`)
3. Para el ecosistema completo: `docker compose up -d` (perfil `docker` automático para todos)

---

## Apéndice C: Ejemplo Completo — Strategy + Factory con Supplier

```java
// Strategy interface (domain)
public interface NotificationSender {
    String getType();
    NotificationResult send(String recipient, String message);
}

// Factory con Suppliers (domain o infrastructure)
public class NotificationSenderFactory {
    private final Map<String, Supplier<NotificationSender>> registry;

    public NotificationSenderFactory(Map<String, Supplier<NotificationSender>> registry) {
        this.registry = Map.copyOf(registry); // Inmutable
    }

    public NotificationSender resolve(String type) {
        return Optional.ofNullable(registry.get(type.toUpperCase()))
            .map(Supplier::get)
            .orElseThrow(() -> new IllegalArgumentException("Unknown notification type: " + type));
    }
}

// Uso en UseCase
@RequiredArgsConstructor
public class SendNotificationUseCase {
    private final NotificationSenderFactory factory;

    public NotificationResult execute(String type, String recipient, String message) {
        return factory.resolve(type).send(recipient, message);
    }
}
```
