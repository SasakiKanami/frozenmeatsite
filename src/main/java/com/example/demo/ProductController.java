package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CloudinaryImageUploadService imageUploadService;

    @PostMapping(value = "/products/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadProductImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose an image file to upload"));
        }
        if (file.getSize() > 5L * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "Image must be 5 MB or smaller"));
        }

        try {
            String imageUrl = imageUploadService.upload(file);
            return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
        } catch (InvalidProductImageException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        } catch (CloudinaryNotConfiguredException exception) {
            return ResponseEntity.status(503).body(Map.of("error", exception.getMessage()));
        } catch (CloudinaryImageUploadException exception) {
            return ResponseEntity.status(502).body(Map.of("error", exception.getMessage()));
        }
    }

    @GetMapping("/products")
    public List<ProductStock> getCatalog() {
        return productStockRepository.findAll();
    }

    @GetMapping("/admin/products")
    public List<AdminProductStock> getAdminProducts() {
        return productStockRepository.findAllForAdmin();
    }

    @GetMapping("/admin/products/{productId}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAdminProductDetails(@PathVariable Integer productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }

        List<InventoryBatch> batches = inventoryBatchRepository
                .findByProductIdOrderByArrivalDateAscIdAsc(productId);
        LocalDate today = LocalDate.now();
        BigDecimal availableStock = batches.stream()
                .filter(batch -> !Boolean.TRUE.equals(batch.getDeleted()))
                .filter(batch -> batch.getExpirationDate() == null || !batch.getExpirationDate().isBefore(today))
                .map(InventoryBatch::getRemainingQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<AdminBatchDetails> batchDetails = batches.stream()
                .map(batch -> new AdminBatchDetails(
                        batch.getId(),
                        batch.getBatchNumber(),
                        batch.getSupplierName(),
                        batch.getArrivalDate(),
                        batch.getExpirationDate(),
                        batch.getInitialQty(),
                        batch.getRemainingQty(),
                        batch.getDeleted()))
                .toList();

        return ResponseEntity.ok(new AdminProductDetails(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getTemperatureTier(),
                product.getUnit(),
                product.getPricePerUnit(),
                product.getImageUrl(),
                product.getReorderLevel(),
                product.getVisible(),
                availableStock,
                batchDetails));
    }

    @PostMapping("/admin/products/{productId}/batches")
    @Transactional
    public ResponseEntity<?> receiveBatch(
            @PathVariable Integer productId,
            @RequestBody ReceiveBatchRequest request) {
        String validationError = request == null ? "Batch details are required" : request.validate();
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }

        LocalDateTime now = LocalDateTime.now();
        InventoryBatch batch = new InventoryBatch();
        batch.setProductId(productId);
        batch.setBatchNumber(request.getBatchNumber().trim());
        batch.setSupplierName(request.getSupplierNameOrDefault());
        batch.setInitialQty(request.getQuantity());
        batch.setRemainingQty(request.getQuantity());
        batch.setArrivalDate(now);
        batch.setExpirationDate(request.getExpirationDate());
        batch.setDeleted(false);
        batch.setCreatedAt(now);
        InventoryBatch savedBatch = inventoryBatchRepository.save(batch);
        return ResponseEntity.ok(Map.of(
                "message", "New inventory batch received",
                "batchId", savedBatch.getId()));
    }

    @PutMapping("/admin/products/{productId}/batches/{batchId}/expiration")
    @Transactional
    public ResponseEntity<?> updateBatchExpiration(
            @PathVariable Integer productId,
            @PathVariable Integer batchId,
            @RequestBody UpdateBatchExpirationRequest request) {
        if (request == null || request.getExpirationDate() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "An expiry date is required"));
        }
        InventoryBatch batch = inventoryBatchRepository.findByIdAndProductId(batchId, productId).orElse(null);
        if (batch == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Batch not found for this product"));
        }
        batch.setExpirationDate(request.getExpirationDate());
        inventoryBatchRepository.save(batch);
        return ResponseEntity.ok(Map.of(
                "message", "Batch expiry date updated",
                "batchId", batchId,
                "expirationDate", request.getExpirationDate().toString()));
    }

    @PutMapping("/products/{productId}/visibility")
    @Transactional
    public ResponseEntity<?> updateProductVisibility(
            @PathVariable Integer productId,
            @RequestBody ProductVisibilityRequest request) {
        if (request == null || request.getVisible() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Visibility must be true or false"));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }

        product.setVisible(request.getVisible());
        productRepository.save(product);
        return ResponseEntity.ok(Map.of(
                "message", request.getVisible() ? "Product is now shown in the storefront" : "Product is now hidden from the storefront",
                "productId", productId,
                "visible", request.getVisible()
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
            batch.setExpirationDate(request.getExpirationDate());
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
    public ResponseEntity<Map<String, String>> archiveBatch(@PathVariable Integer id) {
        InventoryBatch batch = inventoryBatchRepository.findById(id).orElse(null);

        if (batch != null) {
            batch.setDeleted(true);
            inventoryBatchRepository.save(batch);
            return ResponseEntity.ok(Map.of("message", "Inventory batch archived successfully"));
        }

        return ResponseEntity.status(404).body(Map.of("error", "Batch not found"));
    }
}

class ProductVisibilityRequest {
    private Boolean visible;

    public Boolean getVisible() { return visible; }
    public void setVisible(Boolean visible) { this.visible = visible; }
}

class ReceiveBatchRequest {
    private String batchNumber;
    private String supplierName;
    private BigDecimal quantity;
    private LocalDate expirationDate;

    public String validate() {
        if (batchNumber == null || batchNumber.isBlank() || batchNumber.trim().length() > 50) {
            return "Batch number is required and must be 50 characters or fewer";
        }
        if (supplierName != null && supplierName.trim().length() > 100) {
            return "Supplier name must be 100 characters or fewer";
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0
                || quantity.compareTo(new BigDecimal("99999999.99")) > 0
                || quantity.stripTrailingZeros().scale() > 2) {
            return "Quantity must be greater than zero, at most 99999999.99, and have no more than two decimal places";
        }
        if (expirationDate == null) {
            return "Batch expiration date is required";
        }
        if (expirationDate.isBefore(LocalDate.now())) {
            return "Batch expiration date cannot be in the past";
        }
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String getBatchNumber() { return batchNumber; }
    public void setBatchNumber(String batchNumber) { this.batchNumber = batchNumber; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
    public String getSupplierNameOrDefault() {
        return isBlank(supplierName) ? "Direct Meat Supplier" : supplierName.trim();
    }
}

class UpdateBatchExpirationRequest {
    private LocalDate expirationDate;

    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
}

class AddProductRequest {
    private String name;
    private String category;
    private String temperatureTier;
    private String unit;
    private BigDecimal pricePerUnit;
    private String imageUrl;
    private BigDecimal reorderLevel;
    private LocalDate expirationDate;
    private String batchNumber;
    private String supplierName;
    private BigDecimal initialQty;

    public String validate() {
        if (isBlank(name) || isBlank(category) || isBlank(temperatureTier) || isBlank(unit) || isBlank(batchNumber)) {
            return "Name, category, temperature tier, unit, and batch number are required";
        }
        if (supplierName != null && supplierName.trim().length() > 100) {
            return "Supplier name must be 100 characters or fewer";
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
        if (expirationDate == null) {
            return "Batch expiration date is required";
        }
        if (expirationDate.isBefore(LocalDate.now())) {
            return "Batch expiration date cannot be in the past";
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
    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
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
