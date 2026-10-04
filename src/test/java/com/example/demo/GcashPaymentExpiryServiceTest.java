package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GcashPaymentExpiryServiceTest {
    @Mock private OrderRepository orderRepository;
    @Mock private OrderInventoryService orderInventoryService;

    @InjectMocks
    private GcashPaymentExpiryService service;

    @Test
    void expiresPendingOrderAndRestoresStock() {
        Order order = new Order();
        order.setId(7);
        order.setReferenceId("CF-GCASH-7");
        order.setPaymentMethod("GCash Transfer");
        order.setPaymentStatus("pending");
        order.setOrderStatus("pending");
        when(orderRepository.findExpiredGcashOrdersForUpdate(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(orderInventoryService.restoreOrderStock(order)).thenReturn(true);

        service.cancelExpiredGcashOrders();

        assertEquals("cancelled", order.getPaymentStatus());
        assertEquals("cancelled", order.getOrderStatus());
        verify(orderRepository).save(order);
        verify(orderInventoryService).restoreOrderStock(order);
    }

    @Test
    void keepsOrderPendingWhenOriginalBatchCannotBeRestored() {
        Order order = new Order();
        order.setId(7);
        order.setReferenceId("CF-GCASH-7");
        order.setPaymentMethod("GCash Transfer");
        order.setPaymentStatus("pending");
        order.setOrderStatus("pending");
        when(orderRepository.findExpiredGcashOrdersForUpdate(org.mockito.ArgumentMatchers.any(LocalDateTime.class)))
                .thenReturn(List.of(order));
        when(orderInventoryService.restoreOrderStock(order)).thenReturn(false);

        service.cancelExpiredGcashOrders();

        assertEquals("pending", order.getPaymentStatus());
        assertEquals("pending", order.getOrderStatus());
        verify(orderRepository, never()).save(order);
    }
}
