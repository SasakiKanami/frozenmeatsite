package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class CheckoutController {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private InventoryBatchRepository batchRepository;

    @PostMapping("/orders")
    @Transactional // Ensures that if stock runs out mid-checkout, the entire database transaction rolls back
    public String processCheckout(@RequestBody CheckoutRequest request) {

        // 1. Save the main order to generate the Order ID
        Order savedOrder = orderRepository.save(request.getOrder());

        // 2. Process each cart item
        for (OrderItem item : request.getItems()) {
            item.setOrderId(savedOrder.getId());
            orderItemRepository.save(item);

            BigDecimal qtyToDeduct = item.getQuantity();

            // 3. FIFO Logic: Fetch batches for this product ordered by oldest arrival date
            List<InventoryBatch> availableBatches = batchRepository
                    .findByProductIdAndRemainingQtyGreaterThanOrderByArrivalDateAsc(item.getProductId(), BigDecimal.ZERO);

            for (InventoryBatch batch : availableBatches) {
                if (qtyToDeduct.compareTo(BigDecimal.ZERO) <= 0) break; // Finished deducting for this item

                BigDecimal availableInBatch = batch.getRemainingQty();

                if (availableInBatch.compareTo(qtyToDeduct) >= 0) {
                    // Batch has enough to cover the remaining deduction
                    batch.setRemainingQty(availableInBatch.subtract(qtyToDeduct));
                    qtyToDeduct = BigDecimal.ZERO;
                } else {
                    // Batch doesn't have enough; drain it entirely and carry over the remainder
                    batch.setRemainingQty(BigDecimal.ZERO);
                    qtyToDeduct = qtyToDeduct.subtract(availableInBatch);
                }
                batchRepository.save(batch);
            }

            if (qtyToDeduct.compareTo(BigDecimal.ZERO) > 0) {
                throw new RuntimeException("Insufficient stock for product ID: " + item.getProductId());
            }
        }
        return "Checkout successful! Order ID: " + savedOrder.getId();
    }
}

// Helper class to structure the incoming JSON payload from the frontend
class CheckoutRequest {
    private Order order;
    private List<OrderItem> items;

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public List<OrderItem> getItems() { return items; }
    public void setItems(List<OrderItem> items) { this.items = items; }
}
