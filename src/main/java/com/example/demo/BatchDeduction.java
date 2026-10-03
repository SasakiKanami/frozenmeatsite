package com.example.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "batch_deductions")
public class BatchDeduction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_item_id")
    private Integer orderItemId;

    @Column(name = "batch_id")
    private Integer batchId;

    @Column(name = "deducted_qty")
    private BigDecimal deductedQty;

    @Column(name = "deducted_at", insertable = false, updatable = false)
    private LocalDateTime deductedAt;

    public Integer getId() {
        return id;
    }

    public Integer getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Integer orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Integer getBatchId() {
        return batchId;
    }

    public void setBatchId(Integer batchId) {
        this.batchId = batchId;
    }

    public BigDecimal getDeductedQty() {
        return deductedQty;
    }

    public void setDeductedQty(BigDecimal deductedQty) {
        this.deductedQty = deductedQty;
    }

    public LocalDateTime getDeductedAt() {
        return deductedAt;
    }
}
