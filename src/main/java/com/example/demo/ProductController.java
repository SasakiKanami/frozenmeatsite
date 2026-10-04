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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

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
    private OrderItemRepository orderItemRepository;

    @Autowired
    private ProductAliasRepository productAliasRepository;

    @Autowired
    private ProductSkuRegistryRepository productSkuRegistryRepository;

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

    @GetMapping("/admin/products/search")
    public ResponseEntity<?> searchProducts(@RequestParam String query) {
        String term = query == null ? "" : query.trim();
        if (term.length() < 2) {
            return ResponseEntity.ok(List.of());
        }
        Map<Integer, Product> productsById = new LinkedHashMap<>();
        for (Product product : productRepository.searchActiveByName(term)) {
            productsById.put(product.getId(), product);
        }
        for (Product product : productRepository.findBySkuContainingIgnoreCaseAndIsDeletedFalse(term)) {
            productsById.put(product.getId(), product);
        }
        for (ProductAlias alias : productAliasRepository.searchActiveAliases(term)) {
            if (alias.getProductId() == null || productsById.containsKey(alias.getProductId())) continue;
            productRepository.findById(alias.getProductId())
                    .filter(product -> !Boolean.TRUE.equals(product.getDeleted()))
                    .ifPresent(product -> productsById.put(product.getId(), product));
        }
        List<Integer> productIds = new ArrayList<>(productsById.keySet());
        Map<Integer, List<String>> aliasesByProduct = new LinkedHashMap<>();
        if (!productIds.isEmpty()) {
            for (ProductAlias alias : productAliasRepository.findByProductIdIn(productIds)) {
                aliasesByProduct.computeIfAbsent(alias.getProductId(), ignored -> new ArrayList<>()).add(alias.getAlias());
            }
        }
        List<ProductLookupResult> results = productsById.values().stream()
                .sorted(java.util.Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER))
                .map(product -> new ProductLookupResult(
                        product.getId(),
                        product.getSku(),
                        product.getName(),
                        product.getCategory(),
                        product.getUnit(),
                        aliasesByProduct.getOrDefault(product.getId(), List.of())))
                .toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/admin/deleted-products")
    public List<AdminDeletedProduct> getDeletedProducts() {
        return productRepository.findByIsDeletedTrueOrderByDeletedAtDesc().stream()
                .map(product -> new AdminDeletedProduct(
                        product.getId(),
                        product.getSku(),
                        product.getName(),
                        product.getCategory(),
                        product.getUnit(),
                        product.getPricePerUnit(),
                        product.getDeletedAt()))
                .toList();
    }

    @DeleteMapping("/admin/products/{productId}")
    @Transactional
    public ResponseEntity<?> moveProductToTrash(@PathVariable Integer productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }
        if (Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(409).body(Map.of("error", "Product is already in the trash"));
        }

        product.setVisible(false);
        product.setDeleted(true);
        product.setDeletedAt(LocalDateTime.now());
        productRepository.save(product);
        return ResponseEntity.ok(Map.of("message", "Product moved to trash"));
    }

    @PostMapping("/admin/products/{productId}/restore")
    @Transactional
    public ResponseEntity<?> restoreProduct(@PathVariable Integer productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || !Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Deleted product not found"));
        }

        product.setDeleted(false);
        product.setDeletedAt(null);
        product.setVisible(false);
        productRepository.save(product);
        return ResponseEntity.ok(Map.of("message", "Product restored and remains hidden from the storefront"));
    }

    @DeleteMapping("/admin/products/{productId}/permanent")
    @Transactional
    public ResponseEntity<?> permanentlyDeleteProduct(@PathVariable Integer productId) {
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }
        if (!Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(409).body(Map.of("error", "Move the product to trash before permanently deleting it"));
        }

        List<OrderItem> orderItems = orderItemRepository.findByProductId(productId);
        for (OrderItem item : orderItems) {
            item.setProductIdSnapshot(productId);
            item.setProductNameSnapshot(product.getName());
            item.setProductUnitSnapshot(product.getUnit());
            item.setProductSkuSnapshot(product.getSku());
            item.setProductId(null);
        }
        if (!orderItems.isEmpty()) {
            orderItemRepository.saveAllAndFlush(orderItems);
        }

        List<InventoryBatch> batches = inventoryBatchRepository
                .findByProductIdOrderByArrivalDateAscIdAsc(productId);
        for (InventoryBatch batch : batches) {
            batch.setProductIdSnapshot(productId);
            batch.setProductNameSnapshot(product.getName());
            batch.setProductSkuSnapshot(product.getSku());
            batch.setProductId(null);
        }
        if (!batches.isEmpty()) {
            inventoryBatchRepository.saveAllAndFlush(batches);
        }

        List<ProductAlias> aliases = productAliasRepository.findByProductId(productId);
        for (ProductAlias alias : aliases) {
            alias.setRetiredProductId(productId);
            alias.setRetiredProductSku(product.getSku());
            alias.setProductId(null);
        }
        if (!aliases.isEmpty()) {
            productAliasRepository.saveAllAndFlush(aliases);
        }

        productRepository.delete(product);
        return ResponseEntity.ok(Map.of(
                "message", "Product permanently deleted; order and batch history was retained"));
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
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getTemperatureTier(),
                product.getUnit(),
                product.getPricePerUnit(),
                product.getImageUrl(),
                product.getReorderLevel(),
                product.getVisible(),
                availableStock,
                productAliasRepository.findByProductId(productId).stream()
                        .map(ProductAlias::getAlias)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList(),
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
        batch.setProductIdSnapshot(productId);
        batch.setProductNameSnapshot(product.getName());
        batch.setProductSkuSnapshot(product.getSku());
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

    @PutMapping("/admin/products/{productId}/sku")
    @Transactional
    public ResponseEntity<?> updateProductSku(
            @PathVariable Integer productId,
            @RequestBody UpdateProductSkuRequest request) {
        if (request == null || request.getSku() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A product SKU is required"));
        }
        String validationError = validateSku(request.getSku());
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }
        String sku = normalizeSku(request.getSku());
        if (sku.equals(product.getSku())) {
            return ResponseEntity.ok(Map.of("message", "SKU unchanged", "sku", sku));
        }
        if (productSkuRegistryRepository.existsById(sku)) {
            return ResponseEntity.status(409).body(Map.of("error", "That SKU has already been used and cannot be reused"));
        }

        ProductSkuRegistry previousSku = productSkuRegistryRepository.findById(product.getSku())
                .orElseThrow(() -> new IllegalStateException("Current product SKU is missing from the registry"));
        previousSku.setProductId(null);
        productSkuRegistryRepository.save(previousSku);

        ProductSkuRegistry registryEntry = new ProductSkuRegistry();
        registryEntry.setSku(sku);
        registryEntry.setProductId(productId);
        registryEntry.setAllocatedAt(LocalDateTime.now());
        productSkuRegistryRepository.saveAndFlush(registryEntry);
        product.setSku(sku);
        productRepository.save(product);

        return ResponseEntity.ok(Map.of("message", "Product SKU updated", "sku", sku));
    }

    @PostMapping("/admin/products/{productId}/aliases")
    @Transactional
    public ResponseEntity<?> addProductAlias(
            @PathVariable Integer productId,
            @RequestBody ProductAliasRequest request) {
        String validationError = request == null ? "An alternate name is required" : request.validate();
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }
        Product product = productRepository.findById(productId).orElse(null);
        if (product == null || Boolean.TRUE.equals(product.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Product not found"));
        }

        String aliasName = request.getAlias().trim();
        if (product.getName().equalsIgnoreCase(aliasName)
                || productRepository.existsByNameIgnoreCase(aliasName)
                || productAliasRepository.existsByAliasIgnoreCase(aliasName)) {
            return ResponseEntity.status(409).body(Map.of("error", "That name is already assigned to a product or alias"));
        }
        ProductAlias alias = new ProductAlias();
        alias.setProductId(productId);
        alias.setAlias(aliasName);
        alias.setCreatedAt(LocalDateTime.now());
        productAliasRepository.save(alias);
        return ResponseEntity.ok(Map.of("message", "Alternate product name added", "alias", aliasName));
    }

    @PostMapping("/products")
    @Transactional
    public ResponseEntity<?> addProduct(@RequestBody AddProductRequest request) {
        String validationError = request.validate();
        if (validationError != null) {
            return ResponseEntity.badRequest().body(Map.of("error", validationError));
        }

        String productName = request.getName().trim();
        if (request.getSku() != null && !request.getSku().isBlank()) {
            String skuError = validateSku(request.getSku());
            if (skuError != null) {
                return ResponseEntity.badRequest().body(Map.of("error", skuError));
            }
        }
        if (productRepository.existsByNameIgnoreCase(productName)
                || productAliasRepository.existsByAliasIgnoreCase(productName)) {
            return ResponseEntity.status(409).body(Map.of("error", "That product name already belongs to a product or its alternate name. Search and select the existing item to receive stock."));
        }
        Set<String> normalizedAliases = new HashSet<>();
        for (String alias : request.getAliases()) {
            String normalizedAlias = alias.trim().toLowerCase(Locale.ROOT);
            if (normalizedAlias.equals(productName.toLowerCase(Locale.ROOT))
                    || !normalizedAliases.add(normalizedAlias)
                    || productRepository.existsByNameIgnoreCase(alias.trim())
                    || productAliasRepository.existsByAliasIgnoreCase(alias.trim())) {
                return ResponseEntity.status(409).body(Map.of("error", "An alternate name is duplicated or already belongs to another product"));
            }
        }

        try {
            LocalDateTime now = LocalDateTime.now();

            Product product = new Product();
            product.setName(productName);
            String sku = request.getSku() == null || request.getSku().isBlank()
                    ? nextAvailableSku()
                    : normalizeSku(request.getSku());
            if (productSkuRegistryRepository.existsById(sku)) {
                return ResponseEntity.status(409).body(Map.of("error", "That SKU has already been used and cannot be reused"));
            }
            product.setSku(sku);
            product.setCategory(request.getCategory());
            product.setTemperatureTier(request.getTemperatureTier());
            product.setUnit(request.getUnit());
            product.setPricePerUnit(request.getPricePerUnit());
            product.setImageUrl(request.getImageUrl());
            product.setReorderLevel(request.getReorderLevel());
            product.setDeleted(false);
            product.setCreatedAt(now);
            Product savedProduct = productRepository.save(product);

            ProductSkuRegistry skuRegistryEntry = new ProductSkuRegistry();
            skuRegistryEntry.setSku(sku);
            skuRegistryEntry.setProductId(savedProduct.getId());
            skuRegistryEntry.setAllocatedAt(now);
            productSkuRegistryRepository.saveAndFlush(skuRegistryEntry);

            InventoryBatch batch = new InventoryBatch();
            batch.setProductId(savedProduct.getId());
            batch.setProductIdSnapshot(savedProduct.getId());
            batch.setProductNameSnapshot(savedProduct.getName());
            batch.setProductSkuSnapshot(savedProduct.getSku());
            batch.setBatchNumber(request.getBatchNumber().trim());
            batch.setSupplierName(request.getSupplierNameOrDefault());
            batch.setInitialQty(request.getInitialQty());
            batch.setRemainingQty(request.getInitialQty());
            batch.setArrivalDate(now);
            batch.setExpirationDate(request.getExpirationDate());
            batch.setDeleted(false);
            batch.setCreatedAt(now);
            InventoryBatch savedBatch = inventoryBatchRepository.save(batch);
            for (String aliasName : request.getAliases()) {
                ProductAlias alias = new ProductAlias();
                alias.setProductId(savedProduct.getId());
                alias.setAlias(aliasName.trim());
                alias.setCreatedAt(now);
                productAliasRepository.save(alias);
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Product and inventory batch added successfully",
                    "productId", savedProduct.getId(),
                    "sku", savedProduct.getSku(),
                    "batchId", savedBatch.getId()
            ));
        } catch (DataIntegrityViolationException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", "The product or batch data conflicts with the database rules"));
        }
    }

    private String nextAvailableSku() {
        String sku;
        do {
            sku = String.format(Locale.ROOT, "SKU-%06d", productSkuRegistryRepository.nextSkuNumber());
        } while (productSkuRegistryRepository.existsById(sku));
        return sku;
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private String validateSku(String sku) {
        String normalized = normalizeSku(sku);
        return normalized.matches("[A-Z0-9][A-Z0-9._-]{0,39}")
                ? null
                : "SKU must contain 1 to 40 letters, numbers, periods, underscores, or hyphens";
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
    private String sku;
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
    private List<String> aliases = List.of();

    public String validate() {
        if (isBlank(name) || isBlank(category) || isBlank(temperatureTier) || isBlank(unit) || isBlank(batchNumber)) {
            return "Name, category, temperature tier, unit, and batch number are required";
        }
        if (supplierName != null && supplierName.trim().length() > 100) {
            return "Supplier name must be 100 characters or fewer";
        }
        if (sku != null && sku.trim().length() > 40) {
            return "SKU must be 40 characters or fewer";
        }
        if (aliases == null || aliases.stream().anyMatch(alias ->
                alias == null || alias.isBlank() || alias.trim().length() > 120)) {
            return "Alternate names must be non-empty and 120 characters or fewer";
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
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public List<String> getAliases() { return aliases == null ? List.of() : aliases; }
    public void setAliases(List<String> aliases) { this.aliases = aliases; }
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

class UpdateProductSkuRequest {
    private String sku;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
}

class ProductAliasRequest {
    private String alias;

    public String validate() {
        if (alias == null || alias.isBlank() || alias.trim().length() > 120) {
            return "Alternate name is required and must be 120 characters or fewer";
        }
        return null;
    }

    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
}
