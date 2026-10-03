package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    @GetMapping("/products")
    public List getCatalog() {
        return productStockRepository.findAll();
    }

    @PostMapping("/products/{productId}/stock-adjustments")
    @Transactional
    public ResponseEntity<?> adjustStock(@PathVariable Integer productId, @RequestBody AdjustStockRequest request) {
        if (request == null || request.getAdjustment() == null
                || request.getAdjustment().signum() == 0
                || request.getAdjustment().stripTrailingZeros().scale() > 2
                || request.getAdjustment().abs().compareTo(new BigDecimal("99999999.99")) > 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Stock adjustment must be non-zero, at most 99999999.99, and have no more than two decimal places"
            ));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }

        BigDecimal adjustment = request.getAdjustment();
        LocalDateTime now = LocalDateTime.now();
        if (adjustment.signum() > 0) {
            InventoryBatch batch = new InventoryBatch();
            batch.setProductId(productId);
            batch.setBatchNumber("ADMIN-" + UUID.randomUUID());
            batch.setSupplierName("Direct Meat Supplier");
            batch.setInitialQty(adjustment);
            batch.setRemainingQty(adjustment);
            batch.setArrivalDate(now);
            batch.setDeleted(false);
            batch.setCreatedAt(now);
            inventoryBatchRepository.save(batch);
        } else {
            List<InventoryBatch> batches = inventoryBatchRepository
                    .findAvailableBatchesForUpdate(productId, BigDecimal.ZERO);
            BigDecimal quantityToRemove = adjustment.abs();
            BigDecimal availableQuantity = batches.stream()
                    .map(InventoryBatch::getRemainingQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (availableQuantity.compareTo(quantityToRemove) < 0) {
                return ResponseEntity.status(409).body(Map.of("error", "Cannot reduce stock below zero"));
            }

            for (InventoryBatch batch : batches) {
                if (quantityToRemove.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }

                BigDecimal deduction = batch.getRemainingQty().min(quantityToRemove);
                batch.setRemainingQty(batch.getRemainingQty().subtract(deduction));
                quantityToRemove = quantityToRemove.subtract(deduction);
                inventoryBatchRepository.save(batch);
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Stock adjusted successfully",
                "productId", productId,
                "adjustment", adjustment
        ));
    }

    @PostMapping("/products")
    @Transactional
    public ResponseEntity<?> addProduct(@RequestBody AddProductRequest request) {
        String validationError = request.validate();
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        if (productRepository.existsByName(request.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A product with that name already exists"));
        }

        try {
            LocalDateTime now = LocalDateTime.now();

            Product product = new Product();
            product.setName(request.getName().trim());
            product.setCategory(request.getCategory());
            product.setTemperatureTier(request.getTemperatureTier());
            product.setUnit(request.getUnit());
            product.setPricePerUnit(request.getPricePerUnit());
            product.setImageUrl(request.getImageUrl());
            product.setReorderLevel(request.getReorderLevel());
            product.setDeleted(false);
            product.setCreatedAt(now);
            Product savedProduct = productRepository.save(product);

            InventoryBatch batch = new InventoryBatch();
            batch.setProductId(savedProduct.getId());
            batch.setBatchNumber(request.getBatchNumber().trim());
            batch.setSupplierName(request.getSupplierNameOrDefault());
            batch.setInitialQty(request.getInitialQty());
            batch.setRemainingQty(request.getInitialQty());
            batch.setArrivalDate(now);
            batch.setDeleted(false);
            batch.setCreatedAt(now);
            InventoryBatch savedBatch = inventoryBatchRepository.save(batch);

            return ResponseEntity.ok(Map.of(
                    "message", "Product and inventory batch added successfully",
                    "productId", savedProduct.getId(),
                    "batchId", savedBatch.getId()
            ));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "The product or batch data conflicts with the database rules"));
        }
    }

    @DeleteMapping("/inventory/{id}")
    public ResponseEntity archiveBatch(@PathVariable Integer id) {
        InventoryBatch batch = inventoryBatchRepository.findById(id).orElse(null);

        if (batch != null) {
            batch.setDeleted(true);
            inventoryBatchRepository.save(batch);
            return ResponseEntity.ok(Map.of("message", "Inventory batch archived successfully"));
        }

        return ResponseEntity.status(404).body(Map.of("error", "Batch not found"));
    }
}

class AdjustStockRequest {
    private BigDecimal adjustment;

    public BigDecimal getAdjustment() { return adjustment; }
    public void setAdjustment(BigDecimal adjustment) { this.adjustment = adjustment; }
}

class AddProductRequest {
    private String name;
    private String category;
    private String temperatureTier;
    private String unit;
    private BigDecimal pricePerUnit;
    private String imageUrl;
    private BigDecimal reorderLevel;
    private String batchNumber;
    private String supplierName;
    private BigDecimal initialQty;

    public String validate() {
        if (isBlank(name) || isBlank(category) || isBlank(temperatureTier) || isBlank(unit) || isBlank(batchNumber)) {
            return "Name, category, temperature tier, unit, and batch number are required";
        }
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) < 0) {
            return "Price must be zero or greater";
        }
        if (reorderLevel == null || reorderLevel.compareTo(BigDecimal.ZERO) < 0) {
            return "Reorder level must be zero or greater";
        }
        if (initialQty == null || initialQty.compareTo(BigDecimal.ZERO) <= 0) {
            return "Initial quantity must be greater than zero";
        }
        if (!List.of("Fresh Chilled", "Deep Freeze", "Processed Pack").contains(temperatureTier)) {
            return "Temperature tier is invalid";
        }
        if (!List.of("kg", "pack").contains(unit)) {
            return "Unit must be kg or pack";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTemperatureTier() { return temperatureTier; }
    public void setTemperatureTier(String temperatureTier) { this.temperatureTier = temperatureTier; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public void setPricePerUnit(BigDecimal pricePerUnit) { this.pricePerUnit = pricePerUnit; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public BigDecimal getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(BigDecimal reorderLevel) { this.reorderLevel = reorderLevel; }
    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public BigDecimal getInitialQty() { return initialQty; }
    public void setInitialQty(BigDecimal initialQty) { this.initialQty = initialQty; }

    public String getSupplierNameOrDefault() {
        return isBlank(supplierName) ? "Direct Meat Supplier" : supplierName.trim();
    }
}
