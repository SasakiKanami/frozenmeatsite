package com.example.demo;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductAliasRepository extends JpaRepository<ProductAlias, Integer> {

    List<ProductAlias> findByProductIdIn(Collection<Integer> productIds);

    List<ProductAlias> findByProductId(Integer productId);

    boolean existsByAliasIgnoreCase(String alias);

    @Query("SELECT pa FROM ProductAlias pa WHERE LOWER(pa.alias) LIKE LOWER(CONCAT('%', :term, '%')) AND pa.productId IS NOT NULL")
    List<ProductAlias> searchActiveAliases(@Param("term") String term);
}
