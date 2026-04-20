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

### 2.2 Generación de UUIDs: Delegación a PostgreSQL

**Decisión: Los campos `id` de tipo `UUID` en entidades de dominio deben ser nullable.** La generación del UUID se delega a PostgreSQL mediante `DEFAULT gen_random_uuid()` en la definición de la tabla.

#### Justificación

1. **Evita bugs silenciosos con `repository.save()`**: Spring Data R2DBC interpreta un `@Id` no nulo como registro existente, ejecutando `UPDATE` en lugar de `INSERT`. Si el registro no existe, el UPDATE afecta 0 filas y falla silenciosamente sin lanzar error.

2. **Reduce carga del microservicio**: La generación de UUIDs se offloadea a PostgreSQL, que es más eficiente para esta operación (usa `gen_random_uuid()` nativo).

3. **Atomicidad transaccional**: El UUID generado por la DB es parte de la misma transacción que el INSERT, garantizando consistencia.

#### Implementación

**Paso 1 — Schema SQL con `DEFAULT gen_random_uuid()`:**

```sql
CREATE TABLE stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sku VARCHAR(50) NOT NULL UNIQUE,
    quantity INTEGER NOT NULL,
    -- ...
);
```

**Paso 2 — Record de dominio con `id` nullable (sin generación en Java):**

```java
@Builder(toBuilder = true)
public record Stock(
        UUID id,  // Nullable — la DB lo genera
        String sku,
        UUID productId,
        int quantity,
        // ...
) {
    public Stock {
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(productId, "productId is required");
        // id es nullable — DB genera UUID via DEFAULT gen_random_uuid()
        if (quantity < 0) throw new IllegalArgumentException("quantity must be >= 0");
        // ...
    }
}
```

**Paso 3 — DTO de infraestructura implementa `Persistable<UUID>`:**

Spring Data R2DBC necesita saber cuándo hacer INSERT vs UPDATE. Implementar `Persistable<UUID>` con `isNew()` basado en `id == null`:

```java
@Table("stock")
@Builder(toBuilder = true)
public record StockDTO(
        @Id UUID id,
        String sku,
        @Column("product_id") UUID productId,
        int quantity,
        // ...
) {}
```

**Paso 4 — UseCase NO genera UUID al construir entidades nuevas:**

```java
// ✅ Correcto — id es null, la DB lo genera
Stock newStock = Stock.builder()
        .sku(sku)
        .productId(productId)
        .quantity(initialStock)
        .build();

return stockRepository.save(newStock); // DB genera el UUID en el INSERT

// ❌ NUNCA — generar UUID en Java
Stock newStock = Stock.builder()
        .id(UUID.randomUUID())  // ← Esto causa UPDATE en lugar de INSERT
        .sku(sku)
        .build();
```

**Paso 5 — Capturar el ID generado cuando se necesita:**

Si el UseCase necesita el UUID generado (e.g., para incluirlo en un evento), usar `.flatMap()` para capturar el resultado del `save()`:

```java
return stockReservationRepository.save(reservation)
        .flatMap(savedReservation -> {
            OutboxEvent event = buildOutboxEvent(
                    EventType.STOCK_RESERVED, sku,
                    StockReservedPayload.builder()
                            .reservationId(savedReservation.id())  // ← UUID generado por DB
                            .build());
            return outboxEventRepository.save(event)
                    .thenReturn(ReserveStockResult.builder()
                            .reservationId(savedReservation.id())
                            .build());
        });
```

#### Tablas afectadas

Este patrón aplica a **todas las tablas con `DEFAULT gen_random_uuid()`**:

- `stock`
- `stock_reservations`
- `stock_movements`
- `outbox_events`

#### Excepción: UUIDs de Fuentes Externas

**Tablas donde el UUID viene de una fuente externa** (e.g., `processed_events` recibe `event_id` de Kafka) requieren un enfoque diferente:

1. **El UUID sí se pasa desde Java** — no se genera en la BD
2. **La tabla NO tiene `DEFAULT gen_random_uuid()`** — el campo es `NOT NULL` sin default
3. **Se usa `DatabaseClient` con INSERT explícito** en lugar de `repository.save()`

**Razón:** Spring Data R2DBC interpreta un `@Id` no nulo como UPDATE. Si usamos `repository.save(dto)` con un `event_id` que viene de Kafka (siempre no nulo), Spring ejecuta UPDATE en lugar de INSERT. Como el registro no existe, el UPDATE afecta 0 filas y falla silenciosamente sin lanzar error.

**Solución:** Usar `DatabaseClient` con SQL explícito para forzar INSERT:

```java
@Override
public Mono<Void> save(UUID eventId) {
    return databaseClient.sql("INSERT INTO processed_events (event_id, processed_at) VALUES (:eventId, NOW())")
            .bind("eventId", eventId)
            .fetch()
            .rowsUpdated()
            .then();
}
```

**Schema SQL para esta excepción:**

```sql
CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,  -- Sin DEFAULT — viene de Kafka
    processed_at TIMESTAMPTZ NOT NULL
);
```

**Tablas que siguen esta excepción:**

- `processed_events` (ms-inventory, ms-order, ms-catalog) — `event_id` viene de Kafka

#### Beneficios

| Beneficio                         | Descripción                                                                |
| --------------------------------- | -------------------------------------------------------------------------- |
| Evita bugs silenciosos            | Spring Data identifica correctamente nuevas entidades                      |
| Reduce carga del microservicio    | PostgreSQL genera UUIDs más eficientemente que Java                        |
| Atomicidad transaccional          | El UUID generado es parte de la misma transacción que el INSERT            |
| Consistencia con schema           | El código Java refleja exactamente lo que hace la DB (`DEFAULT` en el DDL) |
| Simplifica lógica de construcción | No hay que recordar generar UUIDs manualmente en cada builder              |

### 2.3 Patrón Builder para Construcción de Objetos

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

### 2.4 Sealed Interfaces para Máquinas de Estado y Resultados de Decisión

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

#### 4.1 Mappers como Clases Utilitarias Separadas (No Métodos en Records)

**Decisión: Los mappers son clases `final` con `@NoArgsConstructor(access = AccessLevel.PRIVATE)` y métodos estáticos.** No se colocan como métodos del propio record DTO ni de la entidad de dominio.

**Justificación:**

1. **Dirección de dependencia** — Un mapper en entry-points conoce tanto el DTO como la entidad de dominio. Si el método `toResponse()` viviera en el record DTO, el DTO importaría la entidad de dominio, acoplando la capa de infraestructura al modelo. Si viviera en la entidad de dominio, el modelo importaría el DTO — violación directa de Clean Architecture.
2. **Responsabilidad única** — El record DTO es un contenedor de datos para serialización. El record de dominio encapsula reglas de negocio. Ninguno debería saber cómo transformarse al otro.
3. **Trazabilidad** — Un archivo `StockMapper.java` dedicado hace explícito dónde ocurre la transformación. En code reviews, los cambios de mapeo son visibles en un solo lugar.
4. **Consistencia con driven adapters** — Los driven adapters ya usan `*DTOMapper` y `*RowMapper` como clases separadas (ver §B.9). Mantener el mismo patrón en entry-points unifica el estilo del monorepo.

**Convención Lombok:** Usar `@NoArgsConstructor(access = AccessLevel.PRIVATE)` en lugar de constructor privado manual. Es el idioma Lombok para clases utilitarias y mantiene consistencia con el resto del repo donde Lombok gestiona constructores.

```java
// ✅ Mapper como clase utilitaria separada — entry-points
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StockMapper {

    public static StockResponse toResponse(Stock stock) {
        return StockResponse.builder()
                .id(stock.id())
                .sku(stock.sku())
                .quantity(stock.quantity())
                .build();
    }
}

// ❌ NUNCA — método en el DTO (acopla DTO al dominio)
public record StockResponse(UUID id, String sku, int quantity) {
    public static StockResponse from(Stock stock) { /* DTO importa Stock → violación */ }
}

// ❌ NUNCA — método en la entidad de dominio (acopla dominio al DTO)
public record Stock(UUID id, String sku, int quantity) {
    public StockResponse toResponse() { /* Dominio importa StockResponse → violación */ }
}
```

#### 4.2 Patrón Controller → Handler → UseCase

Los controladores REST (`@RestController`) deben ser **thin** — solo anotaciones HTTP, validación (`@Valid`), extracción de parámetros y delegación a un `Handler`. El `Handler` es un `@Component` que orquesta la llamada al UseCase, el mapeo de respuesta y el wrapping en `ResponseEntity`.

**Justificación:**

1. **Separación de concerns** — El controller se ocupa del contrato HTTP (rutas, validación, OpenAPI). El handler se ocupa de la lógica de orquestación (llamar UseCase, mapear, wrappear).
2. **Testabilidad** — El handler se testea como un POJO con mocks del UseCase, sin necesidad de `WebTestClient` ni contexto Spring.
3. **Consistencia de `ResponseEntity`** — El handler siempre retorna `ResponseEntity<T>`, garantizando un contrato uniforme en todos los endpoints.

```java
// Controller — thin, solo HTTP concerns
@RestController
@RequestMapping("/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory")
public class StockController {

    private final StockHandler stockHandler;

    @GetMapping("/{sku}")
    @Operation(summary = "Get stock availability for a SKU")
    public Mono<ResponseEntity<StockResponse>> getStock(@PathVariable String sku) {
        return stockHandler.getStock(sku);
    }
}

// Handler — orquestación, mapeo, ResponseEntity
@Component
@RequiredArgsConstructor
public class StockHandler {

    private final StockUseCase stockUseCase;

    public Mono<ResponseEntity<StockResponse>> getStock(String sku) {
        return stockUseCase.getBySku(sku)
                .map(StockMapper::toResponse)
                .map(ResponseEntity::ok);
    }

    // Colecciones: retornar Flux directamente (sin ResponseEntity)
    public Flux<StockMovementResponse> getHistory(String sku, int page, int size) {
        return stockUseCase.getHistory(sku, page, size)
                .map(StockMovementMapper::toResponse);
    }
}
```

**Regla para colecciones (`Flux`):** Los endpoints que retornan colecciones deben devolver `Flux<T>` directamente, **nunca** `Mono<ResponseEntity<List<T>>>`. Razones:

1. `Flux<T>` habilita **streaming reactivo** — Spring WebFlux serializa cada elemento a medida que llega, usando chunked transfer encoding. El servidor no acumula toda la colección en memoria.
2. `.collectList()` **rompe el streaming** — acumula todos los elementos en una `List<T>` en memoria antes de enviar la respuesta. Esto anula la ventaja reactiva y aumenta la latencia del primer byte.
3. El HTTP status 200 es implícito para `Flux<T>` — no se necesita `ResponseEntity` para indicarlo.
4. Los errores se manejan igualmente por `@ControllerAdvice` — si el `Flux` emite un `onError`, el `GlobalExceptionHandler` lo captura.

```java
// ✅ Streaming reactivo — elementos se envían a medida que llegan
public Flux<StockMovementResponse> getHistory(String sku, int page, int size) {
    return stockUseCase.getHistory(sku, page, size)
            .map(StockMovementMapper::toResponse);
}

// ❌ NUNCA — collectList() acumula todo en memoria, rompe streaming
public Mono<ResponseEntity<List<StockMovementResponse>>> getHistory(String sku, int page, int size) {
    return stockUseCase.getHistory(sku, page, size)
            .map(StockMovementMapper::toResponse)
            .collectList()
            .map(ResponseEntity::ok);
}
```

**Resumen del patrón por tipo de retorno:**

| Tipo de respuesta | Controller retorna        | Handler retorna           | `ResponseEntity`   |
| ----------------- | ------------------------- | ------------------------- | ------------------ |
| Elemento único    | `Mono<ResponseEntity<T>>` | `Mono<ResponseEntity<T>>` | Sí                 |
| Colección         | `Flux<T>`                 | `Flux<T>`                 | No (200 implícito) |

```java
// infrastructure/entry-points — mapper del API REST
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductMapper {

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

### 7.3 Logging en Capa de Dominio

**Problema:** El Scaffold Bancolombia no permite agregar dependencias en los módulos `domain/usecase` — no se puede importar `org.slf4j.Logger` directamente.

**Solución:** Crear un gateway de logging en `domain/model/commons/gateways` y su implementación en el módulo `infrastructure/helpers`.

```java
// domain/model — port de logging
public interface LoggerGateway {
    void info(String message, Object... args);
    void warn(String message, Object... args);
    void error(String message, Throwable ex);
}

// infrastructure/helpers — implementación
@Component
public class Slf4jLoggerAdapter implements LoggerGateway {
    private static final Logger log = LoggerFactory.getLogger(Slf4jLoggerAdapter.class);
    
    @Override public void info(String message, Object... args) { log.info(message, args); }
    @Override public void warn(String message, Object... args) { log.warn(message, args); }
    @Override public void error(String message, Throwable ex) { log.error(message, ex); }
}
```

Los UseCases inyectan `LoggerGateway` como cualquier otro port. Esto mantiene el dominio desacoplado de SLF4J y respeta las restricciones del Scaffold.

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

| Decisión                    | Resolución                                                                                                                             | Justificación                                                                                                  |
| --------------------------- | -------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| Record vs Clase en dominio  | **Record** por defecto; clase solo cuando herencia o mutabilidad de framework lo exigen                                                | Inmutabilidad nativa; `@Builder` funciona en records desde Lombok 1.18.20                                      |
| Generación de UUIDs         | **IDs nullable en dominio**, delegación a PostgreSQL con `DEFAULT gen_random_uuid()`. (ver §2.2)                                       | Evita bugs silenciosos con `repository.save()`; reduce carga del microservicio; atomicidad transaccional       |
| Optional en reactivo        | **No.** Usar `Mono.justOrEmpty`, `switchIfEmpty`                                                                                       | Optional bloquea semánticamente la cadena reactiva                                                             |
| Controladores               | **`@RestController`** con `Mono`/`Flux`                                                                                                | `@Valid`, `@ControllerAdvice`, sintaxis declarativa                                                            |
| Router Functions            | **No**                                                                                                                                 | Complejidad sin beneficio; Spring maneja reactividad igual                                                     |
| MapStruct                   | **No.** Mappers manuales con métodos estáticos                                                                                         | Trazabilidad, simplicidad, compatibilidad reactiva                                                             |
| Mapper location             | **Clases `final` separadas** con `@NoArgsConstructor(access = PRIVATE)` y métodos estáticos. Nunca en el record DTO ni en la entidad   | Dirección de dependencia correcta; DTO no importa dominio ni viceversa (ver §4.1)                              |
| Controller → Handler        | Controller thin (HTTP concerns) → Handler `@Component` (orquestación + `ResponseEntity`/`Flux`)                                        | Separación de concerns, testabilidad, `ResponseEntity` para `Mono`, `Flux` directo para colecciones (ver §4.2) |
| Builder                     | `@Builder` (Lombok) en records Y clases. Records también usan `with*()` para copias parciales                                          | Lombok 1.18.42 soporta `@Builder` completo en records; sin distinción por número de campos                     |
| Estructuras concurrentes    | **Reactor maneja.** `ConcurrentHashMap` solo para mapas mutables de infraestructura                                                    | Evitar interferir con el EventLoop                                                                             |
| switch vs Strategy          | **switch pattern matching** para dominios sealed; **Strategy+Factory** para extensiones en infraestructura                             | Compile-time safety vs runtime extensibility                                                                   |
| Manejo de errores           | **`@ControllerAdvice`** + operadores de error Reactor                                                                                  | Centralizado, reactivo, sin try/catch en publishers                                                            |
| Null checks en records      | **`Objects.requireNonNull`** en compact constructor                                                                                    | Idiomático JDK, conciso, lanza NPE (contrato estándar de Java)                                                 |
| Timestamps                  | **`Instant`** para persistencia; `LocalDateTime` solo si zona horaria es irrelevante                                                   | `Instant` = UTC absoluto, compatible con `TIMESTAMPTZ` de PostgreSQL                                           |
| Reglas en records           | **Sí.** Invariantes, campos calculados, métodos de consulta, mutaciones encapsuladas y `with*()` en el record                          | La entidad controla su propia consistencia; mutaciones lanzan `DomainException` específicas                    |
| DomainException             | **Abstract class** que extiende `RuntimeException`, no interfaz                                                                        | Interfaces no pueden extender clases; necesita `super(message)` compartido                                     |
| Enums descriptivos          | Valores autoexplicativos (e.g. `RESTOCK`, `SHRINKAGE`); evitar genéricos como `MANUAL_ADJUSTMENT`                                      | Trazabilidad sin depender de campos auxiliares como `reason`                                                   |
| Organización de UseCases    | **1 UseCase por entidad de dominio** con múltiples métodos; no 1 UseCase por operación con `execute()`                                 | Cohesión por agregado, menos clases, inyección de dependencias simplificada                                    |
| SQL ENUMs                   | **`CREATE TYPE ... AS ENUM`** sincronizado con Java; no `VARCHAR` para campos finitos. Requiere `EnumCodec` en R2DBC (ver §B.6)        | Validación en BD, mejor rendimiento, documentación implícita                                                   |
| Generación de componentes   | **Siempre usar Scaffold Bancolombia** (`generateModel`, `generateUseCase`, etc.)                                                       | Estructura consistente; modificar contenido, nunca crear carpetas manualmente                                  |
| Driven Adapters R2DBC       | **Enfoque híbrido:** `ReactiveCrudRepository` + DTOs para CRUD simple; `DatabaseClient` + RowMapper para SQL complejo (ver §B.9)       | Simplicidad para CRUD, control total para FOR UPDATE y lock optimista                                          |
| Spring Profiles             | **`local`** (default en IntelliJ) y **`docker`** (inyectado por Compose). 3 archivos YAML por micro (ver §B.10)                        | Cambio automático entre BD local y contenedores sin tocar código                                               |
| Driven Adapter Kafka        | **`reactor-kafka` directo** (`KafkaSender`), no `reactive-commons`. Módulo manual `kafka-producer` (ver §B.11)                         | Outbox Pattern requiere partition key, tópico y ack explícito que `DomainEventBus` no expone                   |
| Transacciones R2DBC         | **`TransactionalGateway`** port en dominio + `TransactionalOperator` en infraestructura. NUNCA `@Transactional` en UseCases (ver §D.1) | Desacopla dominio de Spring; Caso A (infra pura) y Caso B (gateway) según complejidad                          |
| Documentación API           | **Springdoc OpenAPI** (`springdoc-openapi-starter-webflux-ui`). Swagger UI en `/swagger-ui.html` (ver §D.2)                            | Generación automática desde `@RestController`; anotaciones `@Operation` opcionales                             |
| Constantes                  | **`static final`** con nombre descriptivo; valores configurables en YAML con `@Value` (ver §D.3)                                       | Elimina magic numbers/strings; facilita cambios sin recompilar                                                 |
| `Mono.defer` vs `Mono.just` | **`Mono.defer()`** cuando el argumento produce side-effects o depende de estado mutable (ver §D.4)                                     | Evita evaluación eager en `switchIfEmpty` y otros operadores                                                   |
| Paginación                  | **Offset (`LIMIT/OFFSET`)** para MVP; **Cursor (keyset)** para endpoints de alto volumen en fases posteriores (ver §D.5)               | Simplicidad para datasets pequeños; cursor cuando supere 10K registros frecuentes                              |
| Schedulers                  | **Intervalos externalizados** a `application.yaml` sin defaults inline; schedulers en entry-points (ver §D.6)                          | Fallo rápido si falta propiedad; configuración sin recompilar                                                  |
| Logging en dominio          | **`LoggerGateway`** port en dominio + implementación en `helpers` (ver §7.3)                                                           | Scaffold no permite deps en `usecase`; desacopla dominio de SLF4J                                              |
| Logging producción          | **JSON estructurado** en perfil `docker`; formato legible en perfil `local` (ver §D.7)                                                 | Facilita ingesta en CloudWatch/Grafana/Loki sin parsing adicional                                              |
| Flag `-parameters` en Gradle | **Obligatorio** en `main.gradle` de cada microservicio: `options.compilerArgs += ['-parameters']` en `tasks.withType(JavaCompile)` | Spring WebFlux requiere nombres de parámetros para resolver `@RequestParam`/`@PathVariable` sin `value` explícito. Sin el flag, falla con `INVALID_ARGUMENT` en runtime |
| MongoDB URI (SB 4.0)        | **`spring.mongodb.uri`** (no `spring.data.mongodb.uri`). Incluir `uuidRepresentation=standard` en el query string                     | Spring Boot 4.0 movió el namespace; MongoDB driver 5.x requiere UUID representation explícita para codificar `java.util.UUID` |

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

### B.11 Driven Adapter Kafka: `reactor-kafka` Directo (No `reactive-commons`)

Para la publicación de eventos de dominio a Kafka, se usa `reactor-kafka` (`KafkaSender`) directamente en lugar de la abstracción `reactive-commons` (`DomainEventBus`) que genera el Scaffold con `--type=asynceventbus --tech=kafka`.

#### Decisión y Justificación

El Scaffold Bancolombia genera un módulo `async-event-bus` basado en `reactive-commons` (`async-kafka-starter`). Esta librería provee `DomainEventBus`, una abstracción de alto nivel para publicar eventos. Sin embargo, **no es adecuada para el Transactional Outbox Pattern** que Arka implementa en `ms-inventory`, `ms-order` y `ms-catalog`.

| Criterio                                    | `reactive-commons` (`DomainEventBus`) | `reactor-kafka` (`KafkaSender`)        |
| ------------------------------------------- | ------------------------------------- | -------------------------------------- |
| Partition key por mensaje (SKU, orderId)    | ❌ No expuesto en la API              | ✅ `ProducerRecord(topic, key, value)` |
| Tópico específico por evento                | ⚠️ Solo vía config global             | ✅ Controlado en cada `ProducerRecord` |
| Ack explícito antes de marcar PUBLISHED     | ❌ Abstracto, no controlable          | ✅ `SenderResult` por evento           |
| Patrón Outbox (leer BD → publicar → marcar) | ❌ Diseñado para publicación directa  | ✅ Diseñado para este flujo            |
| Orden causal por agregado (partición)       | ❌ Sin garantía de partition key      | ✅ Garantizado por key = SKU           |

**Razón principal:** El Outbox Pattern requiere un relay que lee eventos PENDING de la tabla `outbox_events`, los publica a Kafka con el SKU como partition key al tópico `inventory-events`, y solo marca como PUBLISHED tras recibir el ack de Kafka. `reactive-commons` abstrae el broker y no expone el control necesario para este flujo.

#### Cuándo usar cada opción

| Escenario                                       | Herramienta                                | Razón                                          |
| ----------------------------------------------- | ------------------------------------------ | ---------------------------------------------- |
| Transactional Outbox Pattern (relay BD → Kafka) | `reactor-kafka` (`KafkaSender`)            | Requiere partition key, tópico y ack explícito |
| Publicación directa de eventos sin outbox       | `reactive-commons` (`DomainEventBus`)      | Abstracción suficiente, menos código           |
| Consumidor Kafka (entry-point)                  | Scaffold `generateEntryPoint --type=kafka` | El scaffold genera el listener correctamente   |

#### Estructura del Módulo

El módulo se crea manualmente como `kafka-producer` en `infrastructure/driven-adapters/` (el Scaffold no tiene un tipo nativo para Kafka producer con control de routing). Se registra en `settings.gradle` y se agrega como dependencia en `app-service`.

```text
infrastructure/driven-adapters/kafka-producer/
├── build.gradle
└── src/main/java/com/arka/kafka/
    ├── KafkaOutboxRelay.java       # Relay @Scheduled cada 5s
    ├── KafkaProducerConfig.java    # Bean KafkaSender<String, String>
    └── ExpiredReservationScheduler.java  # Job @Scheduled cada 60s (si aplica)
```

**`build.gradle`:**

```groovy
dependencies {
    implementation project(':model')
    implementation project(':usecase')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'io.projectreactor.kafka:reactor-kafka:1.3.23'
    implementation 'tools.jackson.core:jackson-databind'
}
```

#### Patrón de Implementación del Relay

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutboxRelay {

    private static final String TOPIC = "inventory-events"; // 1 tópico por bounded context

    private final OutboxRelayUseCase outboxRelayUseCase;
    private final KafkaSender<String, String> kafkaSender;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        outboxRelayUseCase.fetchPendingEvents()
                .flatMap(this::publishAndMark)
                .subscribe();
    }

    private Mono<Void> publishAndMark(OutboxEvent event) {
        return Mono.fromCallable(() -> buildEnvelopeJson(event))
                .flatMap(json -> send(event.partitionKey(), json, event))
                .then(Mono.defer(() -> outboxRelayUseCase.markAsPublished(event)))
                .doOnSuccess(v -> log.info("Published outbox event {} [{}]", event.id(), event.eventType()))
                .onErrorResume(ex -> {
                    log.warn("Failed to publish outbox event {} [{}]: {}",
                            event.id(), event.eventType(), ex.getMessage());
                    return Mono.empty(); // Mantener PENDING para reintento
                });
    }

    private Mono<Void> send(String key, String value, OutboxEvent event) {
        var record = new ProducerRecord<>(TOPIC, null, null, key, value);
        var senderRecord = SenderRecord.create(record, event.id());
        return kafkaSender.send(Mono.just(senderRecord)).next().then();
    }
}
```

**Puntos clave:**

- `partitionKey` (SKU) como key del `ProducerRecord` → garantiza orden causal por SKU en la misma partición
- `onErrorResume` mantiene el evento como PENDING para reintento en el siguiente ciclo
- `markAsPublished` solo se ejecuta tras ack exitoso de Kafka
- El sobre estándar (`DomainEventEnvelope`) se serializa como JSON con `ObjectMapper`

#### Configuración del Producer

```java
@Configuration
public class KafkaProducerConfig {

    @Bean
    public KafkaSender<String, String> kafkaSender(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        return KafkaSender.create(SenderOptions.create(props));
    }
}
```

#### Regla para Otros Microservicios

Esta decisión aplica a **todo microservicio que implemente el Transactional Outbox Pattern**: `ms-inventory`, `ms-order`, y `ms-catalog` (adaptado a MongoDB). El módulo `kafka-producer` se replica en cada uno con el tópico correspondiente (`inventory-events`, `order-events`, `product-events`).

### B.12 Consumidor Kafka: `KafkaReceiver` de `reactor-kafka` (Spring Boot 4.0.3)

#### Contexto: Discontinuación de Reactor Kafka y Eliminación de `ReactiveKafkaConsumerTemplate`

En mayo 2025, el equipo de Spring anunció la **discontinuación del proyecto Reactor Kafka** ([blog oficial](https://spring.io/blog/2025/05/20/reactor-kafka-discontinued)):

- `reactor-kafka` 1.3 es la **última versión minor**. Solo recibirá fixes críticos hasta fin de soporte OSS.
- `ReactiveKafkaConsumerTemplate` y `ReactiveKafkaProducerTemplate` de `spring-kafka` fueron **eliminados en spring-kafka 4.0 GA** (noviembre 2025, incluido en Spring Boot 4.0.3).
- El paquete `org.springframework.kafka.core.reactive` **no existe en spring-kafka 4.0.3**.

#### Decisión para Arka (Spring Boot 4.0.3)

Dado que `ReactiveKafkaConsumerTemplate` fue eliminado, la única opción reactiva disponible hoy es usar `KafkaReceiver` de `reactor-kafka:1.3.23` directamente. Esta es la misma librería que usa el `KafkaSender` del productor (ver §B.11).

| Opción                                    | Estado en Spring Boot 4.0.3 | Decisión Arka                                    |
| ----------------------------------------- | --------------------------- | ------------------------------------------------ |
| `ReactiveKafkaConsumerTemplate`           | ❌ Eliminado                | No usar — no existe en spring-kafka 4.0          |
| `KafkaReceiver` (reactor-kafka 1.3.23)    | ✅ Disponible               | **Usar** — única opción reactiva disponible      |
| `@KafkaListener` (spring-kafka, blocking) | ✅ Disponible               | No usar en servicios reactivos — bloquea threads |

#### Estructura del Módulo Consumer

El módulo se crea manualmente como `kafka-consumer` en `infrastructure/entry-points/`. Se registra en `settings.gradle` y se agrega como dependencia en `app-service`.

```text
infrastructure/entry-points/kafka-consumer/
├── build.gradle
└── src/main/java/com/arka/consumer/
    ├── KafkaConsumerConfig.java    # Beans KafkaReceiver<String, String> por tópico
    ├── KafkaEventConsumer.java     # Lógica de routing por eventType
    └── KafkaConsumerLifecycle.java # Inicia consumo en ApplicationReadyEvent
```

**`build.gradle`:**

```groovy
dependencies {
    implementation project(':model')
    implementation project(':usecase')
    implementation 'org.springframework:spring-context'
    implementation 'org.springframework.boot:spring-boot-starter'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'io.projectreactor.kafka:reactor-kafka:1.3.23'
    implementation 'tools.jackson.core:jackson-databind'
}
```

#### Patrón de Implementación del Consumer

```java
@Configuration
public class KafkaConsumerConfig {

    private final String bootstrapServers;
    private final String groupId;

    public KafkaConsumerConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id:ms-inventory}") String groupId) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
    }

    @Bean
    public KafkaReceiver<String, String> productEventsReceiver() {
        return KafkaReceiver.create(receiverOptions("product-events", groupId + "-product"));
    }

    @Bean
    public KafkaReceiver<String, String> orderEventsReceiver() {
        return KafkaReceiver.create(receiverOptions("order-events", groupId + "-order"));
    }

    private ReceiverOptions<String, String> receiverOptions(String topic, String consumerGroupId) {
        Map<String, Object> props = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false
        );
        return ReceiverOptions.<String, String>create(props)
                .subscription(List.of(topic));
    }
}
```

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventConsumer {

    private final KafkaReceiver<String, String> productEventsReceiver;
    private final KafkaReceiver<String, String> orderEventsReceiver;
    // ... use cases y objectMapper

    public void startConsuming() {
        productEventsReceiver.receive()
                .flatMap(msg -> handleProductEvent(msg.value())
                        .doOnSuccess(v -> msg.receiverOffset().acknowledge())
                        .onErrorResume(ex -> {
                            log.error("Unrecoverable error offset={}: {}", msg.receiverOffset().offset(), ex.getMessage());
                            msg.receiverOffset().acknowledge();
                            return Mono.empty();
                        }))
                .subscribe();
        // idem para orderEventsReceiver
    }
}
```

**Puntos clave:**

- `msg.receiverOffset().acknowledge()` confirma el offset manualmente tras procesamiento exitoso
- `onErrorResume` en el nivel del mensaje garantiza que un error no detenga el stream completo
- El routing por `eventType` se hace dentro del `flatMap` deserializando el sobre estándar
- Retry con backoff exponencial (`Retry.backoff`) para errores transitorios antes de propagar el error
- `KafkaConsumerLifecycle` inicia el consumo en `ApplicationReadyEvent` para garantizar que todos los beans estén listos

#### Nota sobre el Futuro

Cuando Spring publique una alternativa oficial a `ReactiveKafkaConsumerTemplate` compatible con Spring Boot 4.x, esta sección debe actualizarse. Por ahora, `reactor-kafka:1.3.23` es la única opción reactiva viable y seguirá recibiendo fixes de seguridad hasta fin de soporte OSS.

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

## Apéndice D: Estándares Adicionales — Transacciones, Paginación, Schedulers y Más

### D.1 Transacciones Reactivas R2DBC: `TransactionalGateway` y el Outbox Pattern

En servicios reactivos con R2DBC, Spring provee `R2dbcTransactionManager` que implementa `ReactiveTransactionManager`. La transaccionalidad se gestiona mediante `TransactionalOperator`, pero **nunca directamente en el dominio** — el dominio no debe importar dependencias de Spring.

**Principio:** El dominio (UseCases) define QUÉ operaciones son atómicas armando un pipeline reactivo. La infraestructura define CÓMO se ejecuta esa atomicidad (R2DBC, MongoDB, etc.).

**Regla crítica para el Outbox Pattern:** Toda operación que modifique datos de negocio Y escriba un evento en `outbox_events` **debe ejecutarse dentro de la misma transacción**. Sin transacción, cada `save()` es independiente — si la escritura del outbox falla después de guardar el stock, se pierde el evento (Dual-Write Problem).

#### Reglas de ubicación

1. **NUNCA** `@Transactional` en UseCases — acopla el dominio a Spring
2. **NUNCA** `@Transactional` en Entry Points — transacciones largas e innecesarias
3. La transaccionalidad se resuelve en infraestructura mediante dos patrones:

#### Caso A — Infraestructura pura (sin lógica de negocio intermedia)

Cuando el UseCase delega a un solo port sin lógica entre operaciones de BD, la transaccionalidad se maneja directamente en el Driven Adapter. El UseCase no necesita saber que hay una transacción.

Ejemplos: `OutboxRelayUseCase.markAsPublished()`, `OutboxRelayUseCase.fetchPendingEvents()`, consultas simples como `getBySku()`.

```java
// UseCase — no sabe de transacciones
public Mono<Void> markAsPublished(OutboxEvent event) {
    return outboxEventRepository.markAsPublished(event.id());
}

// Driven Adapter — la transacción es un detalle de implementación
@Repository
public class R2dbcOutboxAdapter implements OutboxEventRepository {
    @Override
    public Mono<Void> markAsPublished(UUID id) {
        // Una sola operación SQL = auto-commit, no necesita @Transactional explícito
        return client.sql("UPDATE outbox_events SET status = 'PUBLISHED' WHERE id = :id")
                .bind("id", id).fetch().rowsUpdated().then();
    }
}
```

#### Caso B — Lógica de negocio entre operaciones de BD (`TransactionalGateway`)

Cuando el UseCase tiene lógica de dominio intercalada entre múltiples escrituras a BD (validaciones, decisiones, cálculos), se usa el patrón Decorator/Wrapper con un port `TransactionalGateway`.

El UseCase arma todo el pipeline reactivo con sus reglas de negocio y lo pasa al gateway. En infraestructura, un adapter implementa el gateway usando `TransactionalOperator` de Spring.

**Interfaz en el dominio (`domain/model`):**

```java
// com.arka.model.commons.gateways.TransactionalGateway
public interface TransactionalGateway {
    <T> Mono<T> executeInTransaction(Mono<T> pipeline);
}
```

**Implementación en infraestructura (`driven-adapters`):**

```java
// com.arka.r2dbc.transaction.R2dbcTransactionalAdapter
@Component
@RequiredArgsConstructor
public class R2dbcTransactionalAdapter implements TransactionalGateway {

    private final TransactionalOperator transactionalOperator;

    @Override
    public <T> Mono<T> executeInTransaction(Mono<T> pipeline) {
        return transactionalOperator.transactional(pipeline);
    }
}
```

**Uso en el UseCase:**

```java
@RequiredArgsConstructor
public class StockUseCase {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TransactionalGateway transactionalGateway;

    // Caso B: lógica de dominio entre operaciones → TransactionalGateway
    public Mono<Stock> updateStock(String sku, int newQuantity, String reason) {
        Mono<Stock> pipeline = stockRepository.findBySku(sku)
                .switchIfEmpty(Mono.error(new StockNotFoundException(sku)))
                .flatMap(stock -> {
                    Stock updated = stock.setQuantity(newQuantity); // validación de dominio
                    return stockRepository.updateQuantity(sku, newQuantity, stock.version())
                            .switchIfEmpty(Mono.error(new OptimisticLockException(sku)))
                            .flatMap(saved -> {
                                StockMovement movement = /* decisión de dominio: RESTOCK o SHRINKAGE */;
                                OutboxEvent event = /* ... */;
                                return stockMovementRepository.save(movement)
                                        .then(outboxEventRepository.save(event))
                                        .thenReturn(saved);
                            });
                });

        return transactionalGateway.executeInTransaction(pipeline);
    }

    // Caso A: lectura simple → sin TransactionalGateway
    public Mono<Stock> getBySku(String sku) {
        return stockRepository.findBySku(sku)
                .switchIfEmpty(Mono.error(new StockNotFoundException(sku)));
    }
}
```

#### Clasificación de flujos

| Flujo                                               | Caso | Justificación                                                         |
| --------------------------------------------------- | ---- | --------------------------------------------------------------------- |
| `StockUseCase.updateStock()`                        | B    | Validación optimista + decisión RESTOCK/SHRINKAGE + evaluación umbral |
| `StockUseCase.reserveStock()`                       | B    | Lock pesimista + idempotencia + decisión reserve/fail + umbral        |
| `StockUseCase.processProductCreated()`              | B    | Idempotencia + creación stock + movimiento + processed_event          |
| `StockUseCase.getBySku()` / `getHistory()`          | A    | Lectura simple, un solo port                                          |
| `StockReservationUseCase.processOrderCancelled()`   | B    | Idempotencia + liberación + movimiento + outbox + processed_event     |
| `StockReservationUseCase.expireSingleReservation()` | B    | Cada reserva en su propia transacción (aislamiento de fallos)         |
| `OutboxRelayUseCase.markAsPublished()`              | A    | Delegación pura a un port                                             |
| `OutboxRelayUseCase.fetchPendingEvents()`           | A    | Lectura pura                                                          |

#### Rollback

El rollback es automático cuando un `onError` se propaga dentro del pipeline envuelto por `TransactionalOperator`. No se necesita configuración adicional:

```java
// Si outboxEventRepository.save() falla, el UPDATE de stock se revierte automáticamente
Mono<Stock> pipeline = stockRepository.updateQuantity(sku, newQuantity, version)
        .then(outboxEventRepository.save(event))  // Si esto falla → rollback de todo
        .thenReturn(saved);

return transactionalGateway.executeInTransaction(pipeline);
```

#### Configuración requerida

Spring Boot auto-configura `R2dbcTransactionManager`. Se necesita registrar `TransactionalOperator` como bean:

```java
@Configuration
public class R2dbcTransactionConfig {

    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager txManager) {
        return TransactionalOperator.create(txManager);
    }
}
```

#### Testing

En tests unitarios (sin Spring context), el `TransactionalGateway` se reemplaza por un passthrough que ejecuta el pipeline sin transacción real:

```java
private static final TransactionalGateway PASSTHROUGH_TX = new TransactionalGateway() {
    @Override
    public <T> Mono<T> executeInTransaction(Mono<T> pipeline) {
        return pipeline;
    }
};

@BeforeEach
void setUp() {
    useCase = new StockUseCase(stockRepository, movementRepository,
            outboxRepository, jsonSerializer, PASSTHROUGH_TX);
}
```

---

### D.2 Documentación de API con Springdoc/OpenAPI

Cada microservicio debe exponer documentación interactiva de su API REST mediante [Springdoc OpenAPI](https://springdoc.org/). Springdoc escanea los controladores `@RestController` y genera automáticamente la especificación OpenAPI 3.x.

#### Dependencia

Agregar en el `build.gradle` del módulo `entry-points` (webflux):

```groovy
// Para servicios reactivos (WebFlux)
implementation 'org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.8'

// Para ms-reporter (MVC imperativo)
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8'
```

> **Nota:** Verificar la última versión estable de `springdoc-openapi` compatible con Spring Boot 4.0.3 antes de implementar. La versión 2.8.x es la última conocida al momento de escribir este documento.

#### Configuración en `application.yaml`

```yaml
springdoc:
  api-docs:
    path: /api-docs # JSON spec en /api-docs
  swagger-ui:
    path: /swagger-ui.html # UI interactiva
    enabled: true # Habilitar en local/docker, deshabilitar en producción
  show-actuator: false # No exponer endpoints de Actuator en la doc
```

#### Metadata del servicio

Crear una clase `OpenApiConfig` en `applications/app-service`:

```java
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI inventoryOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ms-inventory API")
                        .description("Gestión de stock, reservas y movimientos de inventario")
                        .version("1.0.0")
                        .contact(new Contact().name("Equipo Arka").email("dev@arka.com")));
    }
}
```

#### Anotaciones en controladores

Usar anotaciones de OpenAPI para enriquecer la documentación generada automáticamente:

```java
@RestController
@RequestMapping("/inventory")
@Tag(name = "Inventory", description = "Operaciones de stock e inventario")
@RequiredArgsConstructor
public class StockController {

    @Operation(summary = "Actualizar stock manualmente",
               description = "Actualiza la cantidad de stock de un SKU. Requiere rol ADMIN.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Stock actualizado"),
        @ApiResponse(responseCode = "404", description = "SKU no encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflicto de concurrencia o constraint")
    })
    @PutMapping("/{sku}/stock")
    public Mono<StockResponse> updateStock(@PathVariable String sku,
                                           @Valid @RequestBody UpdateStockRequest request) {
        // ...
    }
}
```

**Regla:** Las anotaciones de OpenAPI son opcionales para el MVP. Springdoc genera documentación funcional solo con `@RestController`, `@RequestMapping` y Bean Validation. Las anotaciones `@Operation`/`@ApiResponse` se agregan progresivamente para mejorar la calidad de la doc.

#### URLs de acceso

| Recurso      | URL                                       | Descripción          |
| ------------ | ----------------------------------------- | -------------------- |
| Swagger UI   | `http://localhost:{port}/swagger-ui.html` | Interfaz interactiva |
| OpenAPI JSON | `http://localhost:{port}/api-docs`        | Especificación JSON  |
| OpenAPI YAML | `http://localhost:{port}/api-docs.yaml`   | Especificación YAML  |

---

### D.3 Constantes: Evitar Magic Numbers y Magic Strings

Todos los valores literales que representan configuración, umbrales, nombres de tópicos, códigos de error o cualquier valor con significado de negocio deben extraerse a constantes con nombre descriptivo.

#### Reglas

1. **Constantes de dominio** (umbrales, TTLs, defaults) → `static final` en la entidad o record que las usa
2. **Constantes de infraestructura** (tópicos Kafka, nombres de tablas, códigos HTTP) → `static final` en la clase que las usa o en una clase de constantes del módulo
3. **Valores configurables** (intervalos de scheduler, batch sizes, timeouts) → Externalizar a `application.yaml` e inyectar con `@Value` o `@ConfigurationProperties`
4. **Nunca** usar literales numéricos o strings directamente en lógica de negocio

#### Ejemplos

```java
// ✅ Constante en la entidad de dominio
public record Stock(/* ... */) {
    public static final int DEFAULT_DEPLETION_THRESHOLD = 10;
}

// ✅ Constante en la entidad de dominio
public record StockReservation(/* ... */) {
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
}

// ✅ Constante de infraestructura en el adapter
public class KafkaOutboxRelay {
    private static final String TOPIC = "inventory-events";
}

// ✅ Constante de UseCase
public class OutboxRelayUseCase {
    private static final int BATCH_SIZE = 100;
}

// ❌ NUNCA — magic numbers en lógica
if (stock.availableQuantity() <= 10) { /* ... */ }  // ¿Qué es 10?
Thread.sleep(5000);                                   // ¿Por qué 5000?
return "STOCK_NOT_FOUND";                             // ¿Dónde más se usa?

// ✅ SIEMPRE — constante con nombre descriptivo
if (stock.isBelowThreshold()) { /* ... */ }           // Usa depletionThreshold interno
```

#### Valores configurables vs constantes

| Tipo de valor               | Mecanismo                | Ejemplo                                                                                            |
| --------------------------- | ------------------------ | -------------------------------------------------------------------------------------------------- |
| Umbral de negocio inmutable | `static final` en record | `DEFAULT_DEPLETION_THRESHOLD = 10`                                                                 |
| Intervalo de scheduler      | `@Value` desde YAML      | `@Scheduled(fixedDelayString = "${scheduler.outbox.interval:5000}")`                               |
| Batch size configurable     | `@Value` o constructor   | `OutboxRelayUseCase(OutboxEventRepository repo, @Value("${outbox.batch-size:100}") int batchSize)` |
| Nombre de tópico Kafka      | `@Value` desde YAML      | `@Value("${kafka.topic.inventory:inventory-events}")`                                              |

---

### D.4 `Mono.defer()` vs `Mono.just()`: Evaluación Lazy vs Eager

En cadenas reactivas, la diferencia entre `Mono.just()` y `Mono.defer()` es fundamental para evitar bugs sutiles.

#### Diferencia clave

| Operador                             | Evaluación | Cuándo se ejecuta el argumento                    |
| ------------------------------------ | ---------- | ------------------------------------------------- |
| `Mono.just(value)`                   | **Eager**  | Al momento de construir la cadena (assembly time) |
| `Mono.defer(() -> Mono.just(value))` | **Lazy**   | Al momento de suscribirse (subscription time)     |

#### Cuándo usar cada uno

**`Mono.just()`** — Cuando el valor ya está disponible y es inmutable:

```java
// ✅ Valor constante o ya calculado
return Mono.just(ReserveStockResult.builder().success(false).build());

// ✅ Valor que no cambia entre suscripciones
return Mono.just("inventory-events");
```

**`Mono.defer()`** — Cuando el valor debe calcularse en cada suscripción o depende de estado mutable:

```java
// ✅ Dentro de switchIfEmpty — evita evaluación eager del fallback
return stockRepository.findBySku(sku)
        .switchIfEmpty(Mono.defer(() -> createDefaultStock(sku)));
// Sin defer, createDefaultStock() se ejecutaría SIEMPRE, incluso si findBySku retorna valor

// ✅ Cuando el Mono depende de un valor que puede cambiar
return Mono.defer(() -> outboxRelayUseCase.markAsPublished(event));
// Garantiza que markAsPublished se ejecuta solo cuando se suscribe, no al construir la cadena

// ✅ Para envolver lógica que produce side-effects
return Mono.defer(() -> {
    log.info("Processing event {}", eventId);
    return processEvent(eventId);
});
```

**`Mono.fromCallable()`** — Cuando se necesita envolver una operación síncrona que puede lanzar excepción:

```java
// ✅ Operación síncrona que puede fallar
return Mono.fromCallable(() -> objectMapper.writeValueAsString(envelope));
// Si writeValueAsString lanza excepción, se propaga como onError (no como excepción no capturada)
```

#### Error común: `switchIfEmpty` sin `defer`

```java
// ❌ BUG — createDefaultStock() se ejecuta SIEMPRE (eager)
return repository.findBySku(sku)
        .switchIfEmpty(createDefaultStock(sku));  // Se ejecuta aunque findBySku retorne valor

// ✅ CORRECTO — createDefaultStock() solo se ejecuta si findBySku retorna vacío
return repository.findBySku(sku)
        .switchIfEmpty(Mono.defer(() -> createDefaultStock(sku)));
```

> **Regla práctica:** Si el argumento de `switchIfEmpty()` es un `Mono` que produce side-effects (escritura en BD, llamada a servicio externo, log), **siempre** envolverlo en `Mono.defer()`.

---

### D.5 Paginación: Offset vs Cursor — Decisión y Estándar

#### Análisis comparativo

| Criterio                                        | Offset (`LIMIT/OFFSET`)                                                          | Cursor (Keyset)                                                            |
| ----------------------------------------------- | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| **Rendimiento con datasets grandes**            | Degrada linealmente — `OFFSET 100000` escanea 100K filas                         | Constante — usa índice para posicionarse directamente                      |
| **Consistencia ante inserciones/eliminaciones** | Inestable — puede saltar o duplicar registros si los datos cambian entre páginas | Estable — el cursor apunta a una posición fija en el índice                |
| **Complejidad de implementación**               | Baja — `page` y `size` como query params                                         | Media — requiere codificar/decodificar cursor, manejar dirección           |
| **Compatibilidad con UI**                       | Natural — "Página 1, 2, 3..."                                                    | Requiere "Siguiente/Anterior" (no permite saltar a página N)               |
| **Soporte en Spring Data R2DBC**                | Nativo — `OFFSET` y `LIMIT` en SQL                                               | Manual — `WHERE created_at < :cursor ORDER BY created_at DESC LIMIT :size` |

#### Decisión para Arka

**Offset para el MVP (Fase 1).** Cursor para endpoints de alto volumen en fases posteriores.

**Justificación:**

1. Los datasets de Fase 1 son pequeños (cientos a miles de registros por SKU en `stock_movements`, decenas de órdenes por cliente)
2. Los endpoints paginados son administrativos (historial de stock, listado de órdenes) — no son de alta frecuencia
3. La complejidad adicional de cursor pagination no se justifica para el volumen actual
4. Spring Data R2DBC no tiene soporte nativo para cursor pagination — requiere SQL manual con `DatabaseClient`

**Cuándo migrar a cursor:**

- Cuando un endpoint supere **10,000 registros** frecuentemente consultados
- Cuando se detecte degradación de latencia en páginas profundas (página > 100)
- `ms-reporter` (Fase 3) debería usar cursor desde el inicio por el volumen de eventos

#### Estándar de implementación — Offset (MVP)

**Query params:** `page` (0-indexed, default 0) y `size` (default 20, max 100)

```java
// Entry-point — controlador
@GetMapping("/{sku}/history")
public Flux<StockMovementResponse> getHistory(
        @PathVariable String sku,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    int safeSize = Math.min(size, 100); // Cap máximo
    return stockUseCase.getHistory(sku, page, safeSize)
            .map(StockMovementMapper::toResponse);
}

// Driven adapter — SQL
@Override
public Flux<StockMovement> findBySkuOrderByCreatedAtDesc(String sku, int page, int size) {
    return client.sql("SELECT * FROM stock_movements WHERE sku = :sku " +
                      "ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
            .bind("sku", sku)
            .bind("limit", size)
            .bind("offset", page * size)
            .map(StockMovementRowMapper::map)
            .all();
}
```

#### Estándar de implementación — Cursor (futuro)

Cuando se implemente cursor pagination, usar el campo `created_at` (o `id` si es UUID v7 con orden temporal) como cursor:

```java
// Query params: cursor (opaco, base64), size
@GetMapping("/{sku}/history")
public Mono<CursorPage<StockMovementResponse>> getHistory(
        @PathVariable String sku,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int size) {
    // Decodificar cursor → Instant
    // SQL: WHERE sku = :sku AND created_at < :cursor ORDER BY created_at DESC LIMIT :size+1
    // Si retorna size+1 registros → hay más páginas, el último es el nextCursor
}

// Response wrapper
public record CursorPage<T>(List<T> items, String nextCursor, boolean hasMore) {}
```

---

### D.6 Schedulers: Estándar de Implementación y Externalización

Los schedulers (`@Scheduled`) en Arka se usan para dos patrones: el Outbox Relay (publicación de eventos a Kafka) y la expiración de reservas. Deben seguir un estándar uniforme.

#### Ubicación en Clean Architecture

Los schedulers son **entry-points** — son puntos de entrada al sistema que disparan lógica de negocio. Viven en `infrastructure/entry-points/` o en `infrastructure/driven-adapters/` si están acoplados a infraestructura específica (como el Kafka producer).

| Scheduler                     | Ubicación                                        | Justificación                                     |
| ----------------------------- | ------------------------------------------------ | ------------------------------------------------- |
| `ExpiredReservationScheduler` | `infrastructure/entry-points/scheduler/`         | Dispara lógica de dominio pura (expirar reservas) |
| `KafkaOutboxRelay`            | `infrastructure/driven-adapters/kafka-producer/` | Acoplado a Kafka (publica eventos)                |

#### Externalización de intervalos a `application.yaml`

**Regla:** Nunca hardcodear intervalos de scheduler. La expresión (intervalo o cron) vive exclusivamente en `application.yaml`; el Java solo referencia la propiedad. Sin defaults inline en la anotación — si la propiedad no existe en YAML, Spring falla al startup, lo cual es preferible a ejecutar con un valor silenciosamente incorrecto.

```java
// ✅ Referencia pura a YAML — sin default inline
@Scheduled(fixedDelayString = "${scheduler.outbox-relay.interval}")
public void relay() { /* ... */ }

@Scheduled(fixedDelayString = "${scheduler.expired-reservations.interval}")
public void expireReservations() { /* ... */ }
```

```yaml
# application.yaml — fuente única de verdad para intervalos
scheduler:
  outbox-relay:
    interval: 5000 # ms — polling del outbox cada 5s
  expired-reservations:
    interval: 60000 # ms — verificar reservas expiradas cada 60s
```

Para schedulers con expresión cron (futuros):

```java
// ✅ Cron externalizado — la expresión vive en YAML, no en Java
@Scheduled(cron = "${scheduler.daily-report.cron}")
public void generateDailyReport() { /* ... */ }
```

```yaml
# application.yaml
scheduler:
  daily-report:
    cron: "0 0 2 * * ?" # Cada día a las 2 AM
```

#### Patrón estándar de un Scheduler

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpiredReservationScheduler {

    private final StockReservationUseCase stockReservationUseCase;

    @Scheduled(fixedDelayString = "${scheduler.expired-reservations.interval}")
    public void expireReservations() {
        log.info("Starting expired reservations check cycle");
        stockReservationUseCase.expireReservations()
                .doOnComplete(() -> log.info("Expired reservations check cycle completed"))
                .doOnError(ex -> log.error("Error during expired reservations check: {}", ex.getMessage()))
                .onErrorComplete()  // No propagar error al scheduler — se reintenta en el siguiente ciclo
                .subscribe();
    }
}
```

**Puntos clave:**

- `@Slf4j` para logging
- Log al inicio y fin de cada ciclo
- `onErrorComplete()` para que errores no detengan el scheduler
- El scheduler **delega** al UseCase — nunca contiene lógica de negocio
- `subscribe()` al final porque `@Scheduled` espera `void`, no `Mono`

#### Habilitación de Scheduling

En la clase principal de cada microservicio que use schedulers:

```java
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class MainApplication { /* ... */ }
```

---

### D.7 Logging Estructurado para Observabilidad (CloudWatch / Grafana)

El estándar de logging de §7 define SLF4J como librería obligatoria. Esta sección complementa con el formato de logs para facilitar la ingesta en sistemas de observabilidad (CloudWatch Logs, Grafana Loki, ELK).

#### Formato JSON para producción

En el perfil `docker` (producción/staging), los logs deben emitirse en formato JSON para facilitar el parsing automático:

```yaml
# application-docker.yaml
logging:
  pattern:
    console: >
      {"timestamp":"%d{yyyy-MM-dd'T'HH:mm:ss.SSSZ}","level":"%level","service":"${spring.application.name}","thread":"%thread","logger":"%logger{36}","message":"%msg","correlationId":"%X{correlationId}"}%n
```

Alternativa más limpia con Logback JSON encoder (si se agrega la dependencia `logstash-logback-encoder`):

```xml
<!-- logback-spring.xml — perfil docker -->
<springProfile name="docker">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"service":"${spring.application.name}"}</customFields>
        </encoder>
    </appender>
</springProfile>
```

#### Formato legible para desarrollo local

En el perfil `local`, mantener el formato estándar de Spring Boot (legible por humanos):

```yaml
# application-local.yaml
logging:
  level:
    com.arka: DEBUG
    org.springframework.r2dbc: DEBUG
```

#### Propagación de `correlationId`

Para trazabilidad distribuida, propagar el `correlationId` en el MDC de SLF4J:

```java
// En entry-points — extraer del header o generar uno nuevo
public Mono<StockResponse> updateStock(@PathVariable String sku,
                                       @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
    String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
    return stockUseCase.updateStock(sku, newQuantity, reason)
            .contextWrite(Context.of("correlationId", corrId));
}
```

> **Nota:** La integración completa con Micrometer Tracing / OpenTelemetry para propagación automática de trace IDs se define en la fase de despliegue a AWS, no en el código de aplicación.

---

### D.8 Token de LocalStack en Docker Compose

LocalStack requiere un `LOCALSTACK_AUTH_TOKEN` para activar funcionalidades del emulador. En desarrollo local, este token se configura como variable de entorno en el `.env` y se referencia en `compose.yaml`.

#### Configuración

1. Agregar en `.env`:

```env
# LocalStack — Auth Token (obtener en https://app.localstack.cloud/workspace/auth-token)
LOCALSTACK_AUTH_TOKEN=ls-NuTUQOso-SUTO-9934-hUVo-2908GUhI9fa5
```

2. Referenciar en `compose.yaml` dentro del servicio `localstack`:

```yaml
localstack:
  environment:
    - LOCALSTACK_AUTH_TOKEN=${LOCALSTACK_AUTH_TOKEN- }
```

> **Nota de seguridad:** El token del `.env` es para desarrollo local. En CI/CD, usar un CI Auth Token configurado como secreto del pipeline. Nunca commitear tokens reales a Git — el `.env` ya está en `.gitignore`.

Referencia: [LocalStack Auth Token docs](https://docs.localstack.cloud/aws/getting-started/auth-token/)

---

### B.12 Entry Point gRPC: Configuración del Módulo con Protobuf

Los microservicios que exponen un servidor gRPC (e.g., `ms-inventory` para reservas síncronas desde `ms-order`) usan un módulo dedicado `grpc-<nombre>` en `infrastructure/entry-points/`. El módulo se crea manualmente (el Scaffold no tiene un tipo nativo para gRPC server) y se registra en `settings.gradle`.

#### Estructura del módulo

```text
infrastructure/entry-points/grpc-inventory/
├── build.gradle
└── src/main/
    ├── java/com/arka/grpc/
    │   └── InventoryGrpcService.java   # Implementa el stub generado
    └── proto/
        └── inventory.proto             # Definición del servicio gRPC
```

#### `build.gradle` del módulo gRPC

```groovy
plugins {
    id 'com.google.protobuf' version '0.9.4'
}

dependencies {
    implementation project(':usecase')
    implementation project(':model')

    implementation 'net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE'
    implementation 'io.grpc:grpc-stub'
    implementation 'io.grpc:grpc-protobuf'
    implementation 'javax.annotation:javax.annotation-api:1.3.2'
}

protobuf {
    protoc {
        artifact = 'com.google.protobuf:protoc:3.25.3'
    }
    plugins {
        grpc {
            artifact = 'io.grpc:protoc-gen-grpc-java:1.63.0'
        }
    }
    generateProtoTasks {
        all()*.plugins {
            grpc {}
        }
    }
}

sourceSets {
    main {
        java {
            srcDirs 'build/generated/source/proto/main/grpc'
            srcDirs 'build/generated/source/proto/main/java'
        }
    }
}
```

**Puntos clave:**

- El plugin `com.google.protobuf` genera los stubs Java a partir del `.proto` en `build/generated/source/proto/`
- `grpc-server-spring-boot-starter` de `net.devh` integra el servidor gRPC con Spring Boot (auto-configura el puerto, health checks, etc.)
- `javax.annotation-api` es requerido por el código generado por `protoc-gen-grpc-java` (anotaciones `@Generated`)
- Los `srcDirs` en `sourceSets` exponen el código generado al compilador Java

#### Versiones de dependencias gRPC

| Artefacto                                  | Versión         | Rol                            |
| ------------------------------------------ | --------------- | ------------------------------ |
| `com.google.protobuf:protoc`               | `3.25.3`        | Compilador Protobuf            |
| `io.grpc:protoc-gen-grpc-java`             | `1.63.0`        | Plugin para generar stubs Java |
| `io.grpc:grpc-stub`                        | BOM de Spring   | Runtime de stubs gRPC          |
| `io.grpc:grpc-protobuf`                    | BOM de Spring   | Serialización Protobuf en gRPC |
| `net.devh:grpc-server-spring-boot-starter` | `3.1.0.RELEASE` | Integración gRPC ↔ Spring Boot |

> Las versiones de `grpc-stub` y `grpc-protobuf` las gestiona el BOM de Spring Boot — no especificar versión explícita para evitar conflictos.

#### Definición del servicio (`.proto`)

El archivo `.proto` se ubica en `src/main/proto/` del módulo. Convención de nombrado: `<dominio>.proto`.

```protobuf
syntax = "proto3";

option java_package = "com.arka.grpc.inventory";
option java_outer_classname = "InventoryProto";
option java_multiple_files = true;

service InventoryService {
    rpc ReserveStock(ReserveStockRequest) returns (ReserveStockResponse);
}

message ReserveStockRequest {
    string sku = 1;
    string order_id = 2;
    int32 quantity = 3;
}

message ReserveStockResponse {
    bool success = 1;
    string reservation_id = 2;
    int32 available_quantity = 3;
    string reason = 4;
}
```

#### Implementación del servicio gRPC

```java
@GrpcService
@RequiredArgsConstructor
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private final StockUseCase stockUseCase;

    @Override
    public void reserveStock(ReserveStockRequest request, StreamObserver<ReserveStockResponse> responseObserver) {
        stockUseCase.reserveStock(request.getSku(), UUID.fromString(request.getOrderId()), request.getQuantity())
                .map(result -> ReserveStockResponse.newBuilder()
                        .setSuccess(result.success())
                        .setReservationId(result.reservationId() != null ? result.reservationId().toString() : "")
                        .setAvailableQuantity(result.availableQuantity())
                        .setReason(result.reason() != null ? result.reason() : "")
                        .build())
                .subscribe(
                        response -> {
                            responseObserver.onNext(response);
                            responseObserver.onCompleted();
                        },
                        responseObserver::onError
                );
    }
}
```

#### Configuración del puerto gRPC en `application.yaml`

```yaml
grpc:
  server:
    port: ${GRPC_PORT:9090}
```

> **Regla:** El puerto gRPC se externaliza a YAML igual que el puerto HTTP. El valor por defecto `9090` aplica en perfil `local`; en `docker` se inyecta desde `.env`.
