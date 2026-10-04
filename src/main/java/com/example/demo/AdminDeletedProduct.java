package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminDeletedProduct(
        Integer productId,
        String sku,
        String name,
        String category,
        String unit,
        BigDecimal pricePerUnit,
        LocalDateTime deletedAt) {
}
