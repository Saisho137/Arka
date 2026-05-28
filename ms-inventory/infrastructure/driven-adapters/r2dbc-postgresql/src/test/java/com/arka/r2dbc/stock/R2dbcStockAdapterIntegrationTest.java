package com.arka.r2dbc.stock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
class R2dbcStockAdapterIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("db_inventory_test")
            .withUsername("test")
            .withPassword("test")
            .withInitScript("schema.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/db_inventory_test");
        registry.add("spring.r2dbc.username", () -> "test");
        registry.add("spring.r2dbc.password", () -> "test");
    }

    @Autowired
    private SpringDataStockRepository springDataRepository;

    @Autowired
    private DatabaseClient databaseClient;

    private R2dbcStockAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new R2dbcStockAdapter(springDataRepository, databaseClient);
    }

    @Test
    void shouldSaveAndFindBySku() {
        var stock = com.arka.model.stock.Stock.builder()
                .sku("TEST-SKU-001")
                .productId(UUID.randomUUID())
                .quantity(100)
                .reservedQuantity(10)
                .depletionThreshold(5)
                .build();

        adapter.save(stock)
                .as(StepVerifier::create)
                .assertNext(saved -> {
                    assertThat(saved.id()).isNotNull();
                    assertThat(saved.sku()).isEqualTo("TEST-SKU-001");
                    assertThat(saved.quantity()).isEqualTo(100);
                    assertThat(saved.reservedQuantity()).isEqualTo(10);
                    assertThat(saved.availableQuantity()).isEqualTo(90);
                })
                .verifyComplete();

        adapter.findBySku("TEST-SKU-001")
                .as(StepVerifier::create)
                .assertNext(found -> {
                    assertThat(found.sku()).isEqualTo("TEST-SKU-001");
                    assertThat(found.availableQuantity()).isEqualTo(90);
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateReservedQuantity() {
        var stock = com.arka.model.stock.Stock.builder()
                .sku("TEST-SKU-002")
                .productId(UUID.randomUUID())
                .quantity(200)
                .reservedQuantity(0)
                .depletionThreshold(10)
                .build();

        adapter.save(stock).block();

        adapter.updateReservedQuantity("TEST-SKU-002", 50)
                .as(StepVerifier::create)
                .assertNext(updated -> {
                    assertThat(updated.reservedQuantity()).isEqualTo(50);
                    assertThat(updated.availableQuantity()).isEqualTo(150);
                })
                .verifyComplete();
    }

    @Test
    void shouldUpdateQuantityWithOptimisticLocking() {
        var stock = com.arka.model.stock.Stock.builder()
                .sku("TEST-SKU-003")
                .productId(UUID.randomUUID())
                .quantity(300)
                .reservedQuantity(0)
                .depletionThreshold(10)
                .build();

        var saved = adapter.save(stock).block();

        adapter.updateQuantity("TEST-SKU-003", 500, saved.version())
                .as(StepVerifier::create)
                .assertNext(updated -> {
                    assertThat(updated.quantity()).isEqualTo(500);
                })
                .verifyComplete();

        // Wrong version should return empty
        adapter.updateQuantity("TEST-SKU-003", 999, saved.version())
                .as(StepVerifier::create)
                .verifyComplete();
    }

    @Test
    void shouldReturnEmptyForNonExistentSku() {
        adapter.findBySku("NON-EXISTENT")
                .as(StepVerifier::create)
                .verifyComplete();
    }
}
