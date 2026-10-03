package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    @InjectMocks
    private OrderController controller;

    @Test
    void updatesPaymentStatusToPaid() {
        Order order = activeOrder();
        when(orderRepository.findById(12)).thenReturn(Optional.of(order));
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
        verify(orderRepository, never()).findById(12);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void returnsNotFoundForMissingOrder() {
        when(orderRepository.findById(12)).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller.updatePaymentStatus(12, request("unpaid"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(orderRepository, never()).save(any(Order.class));
    }

    private Order activeOrder() {
        Order order = new Order();
        order.setId(12);
        order.setPaymentStatus("unpaid");
        return order;
    }

    private PaymentStatusRequest request(String paymentStatus) {
        PaymentStatusRequest request = new PaymentStatusRequest();
        request.setPaymentStatus(paymentStatus);
        return request;
    }
}
