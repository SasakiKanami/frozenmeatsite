package com.example.demo;

import java.math.BigDecimal;

public record AdminProductStock(
        Integer productId,
        String sku,
        String name,
        String category,
        String temperatureTier,
        String unit,
        BigDecimal pricePerUnit,
        String imageUrl,
        BigDecimal reorderLevel,
        BigDecimal inStockQty,
        Boolean visible) {
}
