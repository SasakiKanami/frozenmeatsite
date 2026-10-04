package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_sku_registry")
public class ProductSkuRegistry {

    @Id
    private String sku;

    private Integer productId;
    private LocalDateTime allocatedAt;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public LocalDateTime getAllocatedAt() { return allocatedAt; }
    public void setAllocatedAt(LocalDateTime allocatedAt) { this.allocatedAt = allocatedAt; }
}
