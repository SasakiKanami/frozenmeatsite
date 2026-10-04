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
    @Mock private OrderInventoryService orderInventoryService;

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
    void rejectsPaymentStatusesNotValidForCashOrders() {
        Order order = activeOrder();
        order.setPaymentMethod("Cash on Pickup / Delivery");
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));
        ResponseEntity<?> response = controller.updatePaymentStatus(12, request("pending"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void confirmsPendingGcashPayment() {
        Order order = activeOrder();
        order.setPaymentMethod("GCash Transfer");
        order.setPaymentStatus("pending");
        order.setPaymentDeadlineAt(java.time.LocalDateTime.now().plusHours(1));
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.updatePaymentStatus(12, request("paid"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("paid", order.getPaymentStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void lateGcashConfirmationCancelsOrderAndRestoresStock() {
        Order order = activeOrder();
        order.setPaymentMethod("GCash Transfer");
        order.setPaymentStatus("pending");
        order.setPaymentDeadlineAt(java.time.LocalDateTime.now().minusSeconds(1));
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));
        when(orderInventoryService.restoreOrderStock(order)).thenReturn(true);

        ResponseEntity<?> response = controller.updatePaymentStatus(12, request("paid"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("cancelled", order.getPaymentStatus());
        assertEquals("cancelled", order.getOrderStatus());
        verify(orderInventoryService).restoreOrderStock(order);
        verify(orderRepository).save(order);
    }

    @Test
    void updatesDeliveryStatusForDeliveryOrder() {
        Order order = activeOrder();
        order.setFulfillmentMethod("Same-Day Delivery");
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));
        DeliveryStatusRequest request = deliveryStatusRequest("Delivery On the Way");

        ResponseEntity<?> response = controller.updateDeliveryStatus(12, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Delivery On the Way", order.getDeliveryStatus());
        verify(orderRepository).save(order);
    }

    @Test
    void guestCanCheckDeliveryStatusWithReferenceAndContact() {
        Order order = activeOrder();
        order.setReferenceId("CF-GUEST-123");
        order.setCustomerContact("09170000000");
        order.setFulfillmentMethod("Same-Day Delivery");
        order.setDeliveryStatus("Delivery On the Way");
        when(orderRepository.findByReferenceIdAndCustomerContactAndCustomerUserIdIsNull(
                "CF-GUEST-123", "09170000000")).thenReturn(Optional.of(order));
        GuestDeliveryStatusRequest request = guestDeliveryStatusRequest(" CF-GUEST-123 ", " 09170000000 ");

        ResponseEntity<?> response = controller.getGuestDeliveryStatus(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Delivery On the Way", ((Map<?, ?>) response.getBody()).get("deliveryStatus"));
        verify(orderRepository).findByReferenceIdAndCustomerContactAndCustomerUserIdIsNull(
                "CF-GUEST-123", "09170000000");
    }

    @Test
    void guestStatusLookupHidesUnknownOrders() {
        when(orderRepository.findByReferenceIdAndCustomerContactAndCustomerUserIdIsNull(
                "CF-GUEST-123", "09170000000")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.getGuestDeliveryStatus(
                guestDeliveryStatusRequest("CF-GUEST-123", "09170000000"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void guestStatusLookupDoesNotExposePickupOrders() {
        Order order = activeOrder();
        order.setReferenceId("CF-GUEST-123");
        order.setCustomerContact("09170000000");
        order.setFulfillmentMethod("Storefront Pickup");
        when(orderRepository.findByReferenceIdAndCustomerContactAndCustomerUserIdIsNull(
                "CF-GUEST-123", "09170000000")).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.getGuestDeliveryStatus(
                guestDeliveryStatusRequest("CF-GUEST-123", "09170000000"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void rejectsInvalidDeliveryStatus() {
        ResponseEntity<?> response = controller.updateDeliveryStatus(12, deliveryStatusRequest("Shipped"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(orderRepository, never()).findByIdForUpdate(12);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void rejectsDeliveryStatusForPickupOrders() {
        Order order = activeOrder();
        order.setFulfillmentMethod("Storefront Pickup");
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.updateDeliveryStatus(12, deliveryStatusRequest("Delivered"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(orderRepository, never()).save(order);
    }

    @Test
    void preventsUnconfirmedGcashOrderFromBeingDispatched() {
        Order order = activeOrder();
        order.setPaymentMethod("GCash Transfer");
        order.setPaymentStatus("pending");
        order.setFulfillmentMethod("Same-Day Delivery");
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.updateDeliveryStatus(
                12, deliveryStatusRequest("Delivery On the Way"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(orderRepository, never()).save(order);
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
        when(orderInventoryService.restoreOrderStock(order)).thenReturn(true);

        ResponseEntity<?> first = controller.updateOrderStatus(12, statusRequest("cancelled"));
        ResponseEntity<?> repeated = controller.updateOrderStatus(12, statusRequest("cancelled"));

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, repeated.getStatusCode());
        assertEquals("cancelled", order.getOrderStatus());
        verify(orderInventoryService, times(1)).restoreOrderStock(order);
    }

    @Test
    void paidOrderCannotBeCancelled() {
        Order order = activeOrder();
        order.setPaymentStatus("paid");
        when(orderRepository.findByIdForUpdate(12)).thenReturn(Optional.of(order));

        ResponseEntity<?> response = controller.updateOrderStatus(12, statusRequest("cancelled"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(orderInventoryService, never()).restoreOrderStock(any());
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
        order.setPaymentMethod("Cash on Pickup / Delivery");
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

    private DeliveryStatusRequest deliveryStatusRequest(String deliveryStatus) {
        DeliveryStatusRequest request = new DeliveryStatusRequest();
        request.setDeliveryStatus(deliveryStatus);
        return request;
    }

    private GuestDeliveryStatusRequest guestDeliveryStatusRequest(String referenceId, String customerContact) {
        GuestDeliveryStatusRequest request = new GuestDeliveryStatusRequest();
        request.setReferenceId(referenceId);
        request.setCustomerContact(customerContact);
        return request;
    }
}
