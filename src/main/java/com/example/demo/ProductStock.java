package com.example.demo;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "view_catalog_live_stock")
public class ProductStock {

    @Id
    private Integer productId;
    private String name;
    private String category;
    private String temperatureTier;
    private String unit;
    private BigDecimal pricePerUnit;
    private String imageUrl;
    private BigDecimal inStockQty;

    // Getters
    public Integer getProductId() { return productId; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getTemperatureTier() { return temperatureTier; }
    public String getUnit() { return unit; }
    public BigDecimal getPricePerUnit() { return pricePerUnit; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getInStockQty() { return inStockQty; }
}