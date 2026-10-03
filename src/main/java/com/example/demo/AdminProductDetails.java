package com.example.demo;

import java.math.BigDecimal;
import java.util.List;

public record AdminProductDetails(
        Integer productId,
        String name,
        String category,
        String temperatureTier,
        String unit,
        BigDecimal pricePerUnit,
        String imageUrl,
        BigDecimal reorderLevel,
        Boolean visible,
        BigDecimal inStockQty,
        List<AdminBatchDetails> batches) {
}
