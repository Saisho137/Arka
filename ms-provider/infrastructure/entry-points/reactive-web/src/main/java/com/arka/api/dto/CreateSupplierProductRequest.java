package com.arka.api.dto;

import java.math.BigDecimal;

public record CreateSupplierProductRequest(
        String sku,
        String supplierSku,
        BigDecimal unitPrice,
        int leadTimeDays,
        BigDecimal reorderMultiplier,
        boolean preferred
) {}
