package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.util.Optional;
import java.util.Map;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Sort;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private BatchDeductionRepository batchDeductionRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private ArchivedOrderRepository archivedOrderRepository;

    @InjectMocks
    private OrderController controller;

    @Test
    void updatesPaymentStatusToPaid() {
        Order order = activeOrder();
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));
        PaymentStatusRequest request = request("paid");

        ResponseEntity<?> response = controller.updatePaymentStatus(12, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("paid", order.getPaymentStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void rejectsStatusesOtherThanPaidOrUnpaid() {
        ResponseEntity<?> response = controller.updatePaymentStatus(12, request("pending"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(orderRepository, never()).findByIdForUpdate(12);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void returnsNotFoundForMissingOrder() {
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updatePaymentStatus(12, request("unpaid"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void orderHistoryUsesProductSnapshotAfterPermanentDeletion() {
        Order order = activeOrder();
        when(orderRepository.findAll(any(Sort.class))).thenReturn(List.of(order));
        OrderItem item = new OrderItem();
        item.setId(22);
        item.setOrderId(12);
        item.setProductId(null);
        item.setProductIdSnapshot(7);
        item.setProductNameSnapshot("Chicken Nuggets");
        item.setProductUnitSnapshot("pack");
        item.setQuantity(new BigDecimal("1.00"));
        when(orderItemRepository.findByOrderIdIn(Set.of(12))).thenReturn(List.of(item));

        List<Map<String, Object>> response = controller.listOrders();

        Object orderItems = response.get(0).get("items");
        assertTrue(orderItems instanceof List<?>);
        Object firstItem = ((List<?>) orderItems).get(0);
        assertTrue(firstItem instanceof Map<?, ?>);
        Map<?, ?> responseItem = (Map<?, ?>) firstItem;
        assertEquals("Chicken Nuggets", responseItem.get("productName"));
        assertEquals("pack", responseItem.get("unit"));
        assertEquals(7, responseItem.get("productId"));
    }

    @Test
    void cancellingUnpaidOrderRestoresItsRecordedBatchQuantitiesOnlyOnce() {
        Order order = activeOrder();
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));
        OrderItem item = new OrderItem();
        item.setId(22);
        when(orderItemRepository.findByOrderId(12)).thenReturn(List.of(item));
        BatchDeduction deduction = new BatchDeduction();
        deduction.setBatchId(31);
        deduction.setOrderItemId(22);
        deduction.setDeductedQty(new BigDecimal("2.00"));
        when(batchDeductionRepository.findByOrderItemIdIn(List.of(22))).thenReturn(List.of(deduction));
        InventoryBatch batch = new InventoryBatch();
        batch.setId(31);
        batch.setRemainingQty(new BigDecimal("3.00"));
        when(batchRepository.findByIdForUpdate(31)).thenReturn(Optional.of(batch));

        ResponseEntity<?> first = controller.updateOrderStatus(12, statusRequest("cancelled"));
        ResponseEntity<?> repeated = controller.updateOrderStatus(12, statusRequest("cancelled"));

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, repeated.getStatusCode());
        assertEquals("cancelled", order.getOrderStatus());
        assertEquals(new BigDecimal("5.00"), batch.getRemainingQty());
        verify(batchRepository, times(1)).save(batch);
    }

    @Test
    void paidOrderCannotBeCancelled() {
        Order order = activeOrder();
        order.setPaymentStatus("paid");
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.updateOrderStatus(12, statusRequest("cancelled"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(batchDeductionRepository, never()).findByOrderItemIdIn(any());
        verify(orderRepository, never()).save(order);
    }

    @Test
    void completedOrderIsSoftArchivedAndRetainedForAudit() {
        Order order = activeOrder();
        order.setOrderStatus("completed");
        order.setReferenceId("CF-ARCHIVE-12");
        order.setCustomerName("Archive Customer");
        order.setTotalAmount(new BigDecimal("20.00"));
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.archiveOrder(12);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Boolean.TRUE, order.getDeleted());
        verify(archivedOrderRepository).save(any(ArchivedOrder.class));
        verify(orderRepository).save(order);
    }

    private Order activeOrder() {
        Order order = new Order();
        order.setId(12);
        order.setPaymentStatus("unpaid");
        order.setOrderStatus("pending");
        return order;
    }

    private PaymentStatusRequest request(String paymentStatus) {
        PaymentStatusRequest request = new PaymentStatusRequest();
        request.setPaymentStatus(paymentStatus);
        return request;
    }

    private OrderStatusRequest statusRequest(String orderStatus) {
        OrderStatusRequest request = new OrderStatusRequest();
        request.setOrderStatus(orderStatus);
        return request;
    }
}
