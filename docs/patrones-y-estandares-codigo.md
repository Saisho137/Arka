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

| Decisión                   | Resolución                                                                                                 | Justificación                                                                              |
| -------------------------- | ---------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Record vs Clase en dominio | **Record** por defecto; clase solo cuando herencia o mutabilidad de framework lo exigen                    | Inmutabilidad nativa; `@Builder` funciona en records desde Lombok 1.18.20                  |
| Optional en reactivo       | **No.** Usar `Mono.justOrEmpty`, `switchIfEmpty`                                                           | Optional bloquea semánticamente la cadena reactiva                                         |
| Controladores              | **`@RestController`** con `Mono`/`Flux`                                                                    | `@Valid`, `@ControllerAdvice`, sintaxis declarativa                                        |
| Router Functions           | **No**                                                                                                     | Complejidad sin beneficio; Spring maneja reactividad igual                                 |
| MapStruct                  | **No.** Mappers manuales con métodos estáticos                                                             | Trazabilidad, simplicidad, compatibilidad reactiva                                         |
| Builder                    | `@Builder` (Lombok) en records Y clases. Records también usan `with*()` para copias parciales              | Lombok 1.18.42 soporta `@Builder` completo en records; sin distinción por número de campos |
| Estructuras concurrentes   | **Reactor maneja.** `ConcurrentHashMap` solo para mapas mutables de infraestructura                        | Evitar interferir con el EventLoop                                                         |
| switch vs Strategy         | **switch pattern matching** para dominios sealed; **Strategy+Factory** para extensiones en infraestructura | Compile-time safety vs runtime extensibility                                               |
| Manejo de errores          | **`@ControllerAdvice`** + operadores de error Reactor                                                      | Centralizado, reactivo, sin try/catch en publishers                                        |

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

## Apéndice B: Ejemplo Completo — Strategy + Factory con Supplier

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
