package com.example.demo;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class GcashPaymentExpiryService {
    private static final Logger logger = LoggerFactory.getLogger(GcashPaymentExpiryService.class);

    private final OrderRepository orderRepository;
    private final OrderInventoryService orderInventoryService;

    public GcashPaymentExpiryService(
            OrderRepository orderRepository,
            OrderInventoryService orderInventoryService) {
        this.orderRepository = orderRepository;
        this.orderInventoryService = orderInventoryService;
    }

    @Scheduled(fixedDelayString = "${app.payment.gcash-expiry-check-ms:60000}")
    @Transactional
    public void cancelExpiredGcashOrders() {
        List<Order> expiredOrders = orderRepository.findExpiredGcashOrdersForUpdate(LocalDateTime.now());
        for (Order order : expiredOrders) {
            if (!orderInventoryService.restoreOrderStock(order)) {
                logger.error("Unable to expire GCash order {} because its original batch deduction could not be restored",
                        order.getReferenceId());
                continue;
            }
            order.setOrderStatus("cancelled");
            order.setPaymentStatus("cancelled");
            orderRepository.save(order);
            logger.info("Automatically cancelled unpaid GCash order {} after its 12-hour payment window",
                    order.getReferenceId());
        }
    }
}
