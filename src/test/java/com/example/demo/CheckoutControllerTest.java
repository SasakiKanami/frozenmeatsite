package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CheckoutControllerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private BatchDeductionRepository batchDeductionRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private CheckoutController controller;

    @Test
    void recordsFifoBatchDeductionsForEachOrderItem() {
        Product product = new Product();
        product.setId(7);
        product.setName("Test product");
        product.setUnit("kg");
        product.setPricePerUnit(new BigDecimal("10.00"));
        when(productRepository.findById(7)).thenReturn(Optional.of(product));

        InventoryBatch firstBatch = batch(21, "3");
        InventoryBatch secondBatch = batch(22, "8");
        when(batchRepository.findUnexpiredAvailableBatchesForUpdate(7, BigDecimal.ZERO))
                .thenReturn(List.of(firstBatch, secondBatch));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(31);
            return order;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem item = invocation.getArgument(0);
            item.setId(41);
            return item;
        });
        when(batchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.processCheckout(checkoutRequest(), null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("0"), firstBatch.getRemainingQty());
        assertEquals(new BigDecimal("6"), secondBatch.getRemainingQty());
        ArgumentCaptor<BatchDeduction> deductionCaptor = ArgumentCaptor.forClass(BatchDeduction.class);
        verify(batchDeductionRepository, org.mockito.Mockito.times(2)).save(deductionCaptor.capture());
        List<BatchDeduction> deductions = deductionCaptor.getAllValues();
        assertEquals(41, deductions.get(0).getOrderItemId());
        assertEquals(21, deductions.get(0).getBatchId());
        assertEquals(new BigDecimal("3"), deductions.get(0).getDeductedQty());
        assertEquals(41, deductions.get(1).getOrderItemId());
        assertEquals(22, deductions.get(1).getBatchId());
        assertEquals(new BigDecimal("2"), deductions.get(1).getDeductedQty());
    }

    @Test
    void returnsInsufficientStockWhenNoUnexpiredBatchesAreAvailable() {
        Product product = new Product();
        product.setId(7);
        product.setPricePerUnit(new BigDecimal("10.00"));
        when(productRepository.findById(7)).thenReturn(Optional.of(product));
        when(batchRepository.findUnexpiredAvailableBatchesForUpdate(7, BigDecimal.ZERO))
                .thenReturn(List.of());

        ResponseEntity<?> response = controller.processCheckout(checkoutRequest(), null);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        verify(orderRepository, org.mockito.Mockito.never()).save(any(Order.class));
        verify(batchDeductionRepository, org.mockito.Mockito.never()).save(any(BatchDeduction.class));
    }

    @Test
    void hiddenProductCannotBeOrderedThroughCheckout() {
        Product product = new Product();
        product.setId(7);
        product.setPricePerUnit(new BigDecimal("10.00"));
        product.setVisible(false);
        when(productRepository.findById(7)).thenReturn(Optional.of(product));

        ResponseEntity<?> response = controller.processCheckout(checkoutRequest(), null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(batchRepository, org.mockito.Mockito.never())
                .findUnexpiredAvailableBatchesForUpdate(7, BigDecimal.ZERO);
        verify(orderRepository, org.mockito.Mockito.never()).save(any(Order.class));
    }

    @Test
    void walkInOrderUsesSharedFifoCheckoutAndRecordsStaffSource() {
        Product product = new Product();
        product.setId(7);
        product.setPricePerUnit(new BigDecimal("10.00"));
        product.setVisible(false);
        when(productRepository.findById(7)).thenReturn(Optional.of(product));
        when(batchRepository.findUnexpiredAvailableBatchesForUpdate(7, BigDecimal.ZERO))
                .thenReturn(List.of(batch(21, "5")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(31);
            return order;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem item = invocation.getArgument(0);
            item.setId(41);
            return item;
        });
        when(batchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        User cashier = new User();
        cashier.setId(9);
        when(userRepository.findByUsername("cashier")).thenReturn(cashier);
        Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getPrincipal()).thenReturn("cashier");
        when(authentication.getName()).thenReturn("cashier");

        CheckoutRequest request = checkoutRequest();
        request.getOrder().setCustomerName(" ");
        request.getOrder().setCustomerContact(" ");

        ResponseEntity<?> response = controller.createWalkInOrder(request, authentication);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        Order savedOrder = orderCaptor.getValue();
        assertEquals("walk_in", savedOrder.getOrderSource());
        assertEquals("Walk-in Customer", savedOrder.getCustomerName());
        assertEquals("N/A", savedOrder.getCustomerContact());
        assertEquals(9, savedOrder.getCreatedByUserId());
        assertNull(savedOrder.getCustomerUserId());
        assertEquals("pending", savedOrder.getOrderStatus());
    }

    @Test
    void deliveryFeeIsAddedByTheServerToDeliveryOrders() {
        ReflectionTestUtils.setField(controller, "configuredDeliveryFee", new BigDecimal("25.00"));
        Product product = new Product();
        product.setId(7);
        product.setPricePerUnit(new BigDecimal("10.00"));
        when(productRepository.findById(7)).thenReturn(Optional.of(product));
        when(batchRepository.findUnexpiredAvailableBatchesForUpdate(7, BigDecimal.ZERO))
                .thenReturn(List.of(batch(21, "5")));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(31);
            return order;
        });
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem item = invocation.getArgument(0);
            item.setId(41);
            return item;
        });
        when(batchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CheckoutRequest request = checkoutRequest();
        request.getOrder().setFulfillmentMethod("Same-Day Delivery");
        request.getOrder().setDeliveryAddress("10 Main Street, Caloocan");

        ResponseEntity<?> response = controller.processCheckout(request, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertEquals(new BigDecimal("25.00"), orderCaptor.getValue().getDeliveryFee());
        assertEquals(new BigDecimal("75.00"), orderCaptor.getValue().getTotalAmount());
    }

    private InventoryBatch batch(Integer id, String remaining) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(id);
        batch.setProductId(7);
        batch.setBatchNumber("BATCH-" + id);
        batch.setRemainingQty(new BigDecimal(remaining));
        batch.setArrivalDate(LocalDateTime.now());
        batch.setDeleted(false);
        return batch;
    }

    private CheckoutRequest checkoutRequest() {
        Order order = new Order();
        order.setCustomerName("Test customer");
        order.setCustomerContact("09170000000");
        order.setFulfillmentMethod("Storefront Pickup");
        order.setPaymentMethod("Cash on Pickup / Delivery");

        OrderItem item = new OrderItem();
        item.setProductId(7);
        item.setQuantity(new BigDecimal("5"));

        CheckoutRequest request = new CheckoutRequest();
        request.setOrder(order);
        request.setItems(List.of(item));
        return request;
    }
}
