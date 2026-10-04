package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductSkuRegistryRepository extends JpaRepository<ProductSkuRegistry, String> {

    @Query(value = "SELECT nextval('product_sku_sequence')", nativeQuery = true)
    long nextSkuNumber();
}
