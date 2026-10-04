package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Integer> {

	boolean existsByNameIgnoreCase(String name);

	List<Product> findBySkuContainingIgnoreCaseAndIsDeletedFalse(String sku);

	@Query("SELECT product FROM Product product WHERE COALESCE(product.isDeleted, false) = false AND LOWER(product.name) LIKE LOWER(CONCAT('%', :term, '%')) ORDER BY product.name")
	List<Product> searchActiveByName(@Param("term") String term);

	List<Product> findByIsDeletedTrueOrderByDeletedAtDesc();
}
