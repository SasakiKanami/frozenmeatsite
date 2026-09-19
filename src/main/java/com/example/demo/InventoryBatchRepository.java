package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, Integer> {

    List<InventoryBatch> findByProductIdAndRemainingQtyGreaterThanOrderByArrivalDateAsc(Integer productId, BigDecimal zero);
}

