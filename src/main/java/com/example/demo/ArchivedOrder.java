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
@Table(name = "archived_orders")
public class ArchivedOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "archive_id")
    private Integer archiveId;

    private Integer originalOrderId;
    private String referenceId;
    private String customerName;
    private BigDecimal totalAmount;
    private String archivedReason;

    @Column(name = "archived_at", insertable = false, updatable = false)
    private LocalDateTime archivedAt;

    public Integer getArchiveId() { return archiveId; }
    public Integer getOriginalOrderId() { return originalOrderId; }
    public void setOriginalOrderId(Integer originalOrderId) { this.originalOrderId = originalOrderId; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getArchivedReason() { return archivedReason; }
    public void setArchivedReason(String archivedReason) { this.archivedReason = archivedReason; }
    public LocalDateTime getArchivedAt() { return archivedAt; }
}
