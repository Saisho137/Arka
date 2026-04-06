package com.arka.r2dbc.stock;

import com.arka.model.stock.Stock;
import io.r2dbc.spi.Readable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

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
