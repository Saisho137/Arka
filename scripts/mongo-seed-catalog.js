// ═══════════════════════════════════════════════════════════════
// Arka · db_catalog · MongoDB Seed Script
// ═══════════════════════════════════════════════════════════════
//
// Propósito: Insertar datos de prueba en db_catalog (categories + products)
// sincronizados con init_inventory.sql e init_orders.sql.
//
// Ejecución: Servicio one-shot `mongo-seed-catalog` en compose.yaml,
// que corre DESPUÉS de que `mongo-init-replica` completa exitosamente.
//
// Idempotente: usa updateOne + upsert:true + $setOnInsert → si el documento
// ya existe, no lo modifica. Seguro de re-ejecutar en cualquier momento.
//
// UUIDs:
//   - Productos usan los mismos productId que init_orders.sql (f47ac10b-...)
//   - Inventory usa los mismos productId en init_inventory.sql
//   - Esto garantiza coherencia entre las 3 bases de datos.
//
// Tipos BSON:
//   - UUID()          → BinData(4, ...) compatible con Spring Data uuidRepresentation=standard
//   - NumberDecimal() → Decimal128, compatible con BigDecimal de Spring Data MongoDB
//   - new Date()      → ISODate, compatible con Instant de Java
// ═══════════════════════════════════════════════════════════════

print("=== Arka Catalog Seed — START ===");

const now = new Date();

// ─── IDs de Categorías ────────────────────────────────────────
// UUIDs fijos para categorías (compatibles con Spring Data standard UUID)
const CAT_PERIFERICOS = UUID("aaaaaaaa-0000-4000-a000-000000000001");
const CAT_MONITORES = UUID("aaaaaaaa-0000-4000-a000-000000000002");
const CAT_COMPONENTES = UUID("aaaaaaaa-0000-4000-a000-000000000003");
const CAT_ACCESORIOS = UUID("aaaaaaaa-0000-4000-a000-000000000004");

// ─── Categorías ───────────────────────────────────────────────
const categories = [
    {
        _id: CAT_PERIFERICOS,
        name: "Periféricos",
        description: "Teclados, mouse, audífonos y accesorios de entrada",
        active: true,
        createdAt: now
    },
    {
        _id: CAT_MONITORES,
        name: "Monitores",
        description: "Pantallas y monitores de todas las resoluciones",
        active: true,
        createdAt: now
    },
    {
        _id: CAT_COMPONENTES,
        name: "Componentes",
        description: "GPUs, RAM, SSDs y componentes internos de PC",
        active: true,
        createdAt: now
    },
    {
        _id: CAT_ACCESORIOS,
        name: "Accesorios",
        description: "Hubs, adaptadores, cables y conectores",
        active: true,
        createdAt: now
    }
];

print("Inserting categories...");
let catInserted = 0;
categories.forEach(function (cat) {
    const result = db.categories.updateOne(
        { _id: cat._id },
        { $setOnInsert: cat },
        { upsert: true }
    );
    if (result.upsertedCount > 0) {
        print("  [+] Created category: " + cat.name);
        catInserted++;
    } else {
        print("  [=] Category already exists: " + cat.name);
    }
});
print("Categories: " + catInserted + " created.");

// ─── IDs de Productos ─────────────────────────────────────────
// CRÍTICO: Estos UUIDs deben coincidir EXACTAMENTE con:
//   - product_id en postgresql-scripts/init_inventory.sql
//   - product_id en postgresql-scripts/init_orders.sql (tabla order_items)
const products = [
    // ─── Periféricos ──────────────────────────────────────────
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d001"),
        sku: "KB-MECH-001",
        name: "Teclado Mecánico RGB Pro",
        description: "Teclado mecánico con switches Cherry MX Red, retroiluminación RGB per-key y cuerpo de aluminio CNC",
        cost: NumberDecimal("180000"),
        price: NumberDecimal("290000"),
        currency: "COP",
        categoryId: CAT_PERIFERICOS,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    },
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d002"),
        sku: "MS-OPT-002",
        name: "Mouse Óptico Inalámbrico",
        description: "Mouse inalámbrico ergonómico con sensor óptico de 3200 DPI, receptor USB nano y batería de 12 meses",
        cost: NumberDecimal("65000"),
        price: NumberDecimal("140000"),
        currency: "COP",
        categoryId: CAT_PERIFERICOS,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    },
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d004"),
        sku: "HDS-BT-003",
        name: "Audífonos Bluetooth NC",
        description: "Audífonos inalámbricos con cancelación activa de ruido (ANC), 35h de batería y plegables",
        cost: NumberDecimal("200000"),
        price: NumberDecimal("420000"),
        currency: "COP",
        categoryId: CAT_PERIFERICOS,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    },
    // ─── Monitores ────────────────────────────────────────────
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d003"),
        sku: "MNT-27-001",
        name: "Monitor 27 pulgadas 4K",
        description: "Monitor IPS 4K UHD 3840x2160, 60Hz, HDR400, USB-C 65W, ideal para diseño y productividad",
        cost: NumberDecimal("900000"),
        price: NumberDecimal("1450000"),
        currency: "COP",
        categoryId: CAT_MONITORES,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    },
    // ─── Componentes ──────────────────────────────────────────
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d006"),
        sku: "GPU-RTX-004",
        name: "GPU NVIDIA RTX 4070 Super",
        description: "Tarjeta gráfica RTX 4070 Super 12GB GDDR6X, ray tracing, DLSS 3, ideal para workstations B2B",
        cost: NumberDecimal("2200000"),
        price: NumberDecimal("3100000"),
        currency: "COP",
        categoryId: CAT_COMPONENTES,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    },
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d007"),
        sku: "RAM-DDR5-005",
        name: "Memoria RAM DDR5 32GB (2x16GB)",
        description: "Kit de memoria DDR5 5600MHz CL36, disipador de aluminio, compatible con Intel y AMD",
        cost: NumberDecimal("320000"),
        price: NumberDecimal("480000"),
        currency: "COP",
        categoryId: CAT_COMPONENTES,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    },
    // ─── Accesorios ───────────────────────────────────────────
    {
        _id: UUID("f47ac10b-58cc-4372-a567-0e02b2c3d005"),
        sku: "USB-HB-004",
        name: "Hub USB-C 7 puertos",
        description: "Hub USB-C con 3x USB-A 3.0, HDMI 4K, SD/microSD, USB-C PD 100W — compatible con Mac, Windows y Linux",
        cost: NumberDecimal("32000"),
        price: NumberDecimal("70000"),
        currency: "COP",
        categoryId: CAT_ACCESORIOS,
        active: true,
        reviews: [],
        createdAt: now,
        updatedAt: now
    }
];

print("Inserting products...");
let prodInserted = 0;
products.forEach(function (prod) {
    const result = db.products.updateOne(
        { _id: prod._id },
        { $setOnInsert: prod },
        { upsert: true }
    );
    if (result.upsertedCount > 0) {
        print("  [+] Created product: " + prod.sku + " — " + prod.name);
        prodInserted++;
    } else {
        print("  [=] Product already exists: " + prod.sku);
    }
});
print("Products: " + prodInserted + " created.");

// ─── Verificación ─────────────────────────────────────────────
print("");
print("=== Verification ===");
print("categories count: " + db.categories.countDocuments());
print("products count:   " + db.products.countDocuments());
print("");

const skus = db.products.find({}, { sku: 1, name: 1 }).toArray();
skus.forEach(function (p) {
    print("  " + p.sku + " → " + p.name);
});

print("=== Arka Catalog Seed — DONE ===");
