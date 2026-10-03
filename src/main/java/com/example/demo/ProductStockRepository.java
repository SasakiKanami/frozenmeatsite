package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductStockRepository extends JpaRepository<ProductStock, Integer> {
    @Query("""
            SELECT new com.example.demo.AdminProductStock(
                p.id, p.name, p.category, p.temperatureTier, p.unit, p.pricePerUnit,
                p.imageUrl, p.reorderLevel, COALESCE(SUM(batch.remainingQty), 0), p.visible
            )
            FROM Product p
            LEFT JOIN InventoryBatch batch
                ON batch.productId = p.id
                AND batch.isDeleted = false
                AND batch.remainingQty > 0
                AND (batch.expirationDate IS NULL OR batch.expirationDate >= CURRENT_DATE)
            WHERE p.isDeleted = false
            GROUP BY p.id, p.name, p.category, p.temperatureTier, p.unit,
                p.pricePerUnit, p.imageUrl, p.reorderLevel, p.visible
            ORDER BY p.name
            """)
    List<AdminProductStock> findAllForAdmin();
}
