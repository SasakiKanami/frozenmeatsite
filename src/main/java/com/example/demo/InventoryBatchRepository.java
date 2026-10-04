package com.example.demo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Integer> {

    List<InventoryBatch> findByProductIdOrderByArrivalDateAscIdAsc(Integer productId);

    Optional<InventoryBatch> findByIdAndProductId(Integer id, Integer productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM InventoryBatch batch WHERE batch.id = :batchId")
    Optional<InventoryBatch> findByIdForUpdate(@Param("batchId") Integer batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT batch FROM InventoryBatch batch WHERE batch.productId = :productId AND batch.isDeleted = false AND batch.remainingQty > :zero AND (batch.expirationDate IS NULL OR batch.expirationDate >= CURRENT_DATE) ORDER BY batch.arrivalDate ASC, batch.id ASC")
    List<InventoryBatch> findUnexpiredAvailableBatchesForUpdate(
            @Param("productId") Integer productId,
            @Param("zero") BigDecimal zero);
}
