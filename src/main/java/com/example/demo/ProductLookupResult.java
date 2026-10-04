package com.example.demo;

import java.util.List;

public record ProductLookupResult(
        Integer productId,
        String sku,
        String name,
        String category,
        String unit,
        List<String> aliases) {
}
