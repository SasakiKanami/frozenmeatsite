package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

@Repository
public interface BatchDeductionRepository extends JpaRepository<BatchDeduction, Integer> {

    List<BatchDeduction> findByOrderItemIdIn(Collection<Integer> orderItemIds);
}
