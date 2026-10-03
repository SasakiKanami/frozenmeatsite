package com.example.demo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminBatchDetails(
        Integer id,
        String batchNumber,
        String supplierName,
        LocalDateTime arrivalDate,
        LocalDate expirationDate,
        BigDecimal initialQty,
        BigDecimal remainingQty,
        Boolean deleted) {
}
