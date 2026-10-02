package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Integer> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM InventoryBatch batch WHERE batch.productId = :productId AND batch.isDeleted = false AND batch.remainingQty > :zero ORDER BY batch.arrivalDate ASC, batch.id ASC")
    List<InventoryBatch> findAvailableBatchesForUpdate(@Param("productId") Integer productId, @Param("zero") BigDecimal zero);
}
