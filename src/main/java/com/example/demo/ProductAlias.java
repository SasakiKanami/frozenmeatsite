package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_aliases")
public class ProductAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer productId;
    private String alias;
    private Integer retiredProductId;
    private String retiredProductSku;
    private LocalDateTime createdAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }
    public String getAlias() { return alias; }
    public void setAlias(String alias) { this.alias = alias; }
    public Integer getRetiredProductId() { return retiredProductId; }
    public void setRetiredProductId(Integer retiredProductId) { this.retiredProductId = retiredProductId; }
    public String getRetiredProductSku() { return retiredProductSku; }
    public void setRetiredProductSku(String retiredProductSku) { this.retiredProductSku = retiredProductSku; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
