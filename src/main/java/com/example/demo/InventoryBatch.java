package com.example.demo;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "inventory_batches")
public class InventoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Integer productId;
    private Integer productIdSnapshot;
    private String productNameSnapshot;
    private String productSkuSnapshot;
    private String batchNumber;
    private String supplierName = "Direct Meat Supplier";
    private BigDecimal initialQty;
    private BigDecimal remainingQty;
    private LocalDateTime arrivalDate;
    private LocalDate expirationDate;
    private Boolean isDeleted = false;
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;

    public LocalDateTime getArrivalDate() {
        return arrivalDate;
    }

    public void setArrivalDate(LocalDateTime arrivalDate) {
        this.arrivalDate = arrivalDate;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getProductIdSnapshot() {
        return productIdSnapshot;
    }

    public void setProductIdSnapshot(Integer productIdSnapshot) {
        this.productIdSnapshot = productIdSnapshot;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public void setProductNameSnapshot(String productNameSnapshot) {
        this.productNameSnapshot = productNameSnapshot;
    }

    public String getProductSkuSnapshot() {
        return productSkuSnapshot;
    }

    public void setProductSkuSnapshot(String productSkuSnapshot) {
        this.productSkuSnapshot = productSkuSnapshot;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public BigDecimal getInitialQty() {
        return initialQty;
    }

    public void setInitialQty(BigDecimal initialQty) {
        this.initialQty = initialQty;
    }

    public BigDecimal getRemainingQty() {
        return remainingQty;
    }

    public void setRemainingQty(BigDecimal remainingQty) {
        this.remainingQty = remainingQty;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Boolean getDeleted() {
        return isDeleted;
    }

    public void setDeleted(Boolean deleted) {
        isDeleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

}
