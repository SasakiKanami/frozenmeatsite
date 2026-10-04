package com.example.demo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

@Repository
public interface OrderRepository extends JpaRepository<Order, Integer> {

    List<Order> findByCustomerUserIdOrderByIdDesc(Integer customerUserId);

    Optional<Order> findByReferenceIdAndCustomerContactAndCustomerUserIdIsNull(
            String referenceId, String customerContact);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SalesOrder o WHERE o.id = :orderId")
    java.util.Optional<Order> findByIdForUpdate(@Param("orderId") Integer orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM SalesOrder o
            WHERE o.paymentMethod = 'GCash Transfer'
              AND o.paymentStatus = 'pending'
              AND o.paymentDeadlineAt <= :now
              AND o.orderStatus = 'pending'
              AND o.isDeleted = false
            ORDER BY o.paymentDeadlineAt ASC
            """)
    List<Order> findExpiredGcashOrdersForUpdate(@Param("now") LocalDateTime now);
}
