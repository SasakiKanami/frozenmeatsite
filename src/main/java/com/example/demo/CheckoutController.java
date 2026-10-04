package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CheckoutController {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private BatchDeductionRepository batchDeductionRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Value("${app.delivery.fee:0.00}") private BigDecimal configuredDeliveryFee = new BigDecimal("0.00");

    @PostConstruct
    void validateDeliveryFee() {
        if (configuredDeliveryFee.signum() < 0
                || configuredDeliveryFee.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalStateException("DELIVERY_FEE must be between 0 and 99999999.99");
        }
    }

    @PostMapping("/orders")
    @Transactional
    public ResponseEntity<?> processCheckout(@RequestBody CheckoutRequest request, Authentication authentication) {
        return createOrder(request, authentication, false);
    }

    @PostMapping("/admin/orders")
    @Transactional
    public ResponseEntity<?> createWalkInOrder(@RequestBody CheckoutRequest request, Authentication authentication) {
        if (request == null || request.getOrder() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Walk-in order details are required"));
        }
        Order submittedOrder = request.getOrder();
        if (isBlank(submittedOrder.getCustomerName())) submittedOrder.setCustomerName("Walk-in Customer");
        if (isBlank(submittedOrder.getCustomerContact())) submittedOrder.setCustomerContact("N/A");
        submittedOrder.setCustomerEmail(null);
        submittedOrder.setCustomerUserId(null);
        submittedOrder.setFulfillmentMethod("Storefront Pickup");
        submittedOrder.setDeliveryAddress(null);
        submittedOrder.setDeliveryNotes(null);
        return createOrder(request, authentication, true);
    }

    @GetMapping("/checkout-settings")
    public Map<String, BigDecimal> checkoutSettings() {
        return Map.of("deliveryFee", configuredDeliveryFee.setScale(2, RoundingMode.HALF_UP));
    }

    private ResponseEntity<?> createOrder(
            CheckoutRequest request,
            Authentication authentication,
            boolean walkIn) {
        if (request == null || request.getOrder() == null || request.getItems() == null || request.getItems().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Customer details and at least one item are required"));
        }

        Order submittedOrder = request.getOrder();
        if (isBlank(submittedOrder.getCustomerName()) || isBlank(submittedOrder.getCustomerContact())
                || !List.of("Storefront Pickup", "Same-Day Delivery").contains(submittedOrder.getFulfillmentMethod())
                || !List.of("Cash on Pickup / Delivery", "GCash Transfer").contains(submittedOrder.getPaymentMethod())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Customer name, contact, fulfillment, and payment details are required"));
        }
        if (submittedOrder.getCustomerName().trim().length() > 100
                || submittedOrder.getCustomerContact().trim().length() > 30
                || length(submittedOrder.getCustomerEmail()) > 255
                || length(submittedOrder.getDeliveryAddress()) > 4000
                || length(submittedOrder.getDeliveryNotes()) > 2000
                || length(submittedOrder.getPaymentReference()) > 100) {
            return ResponseEntity.badRequest().body(Map.of("error", "One or more order fields exceed the allowed length"));
        }
        boolean gcashPayment = "GCash Transfer".equals(submittedOrder.getPaymentMethod());
        if (gcashPayment && isBlank(submittedOrder.getPaymentReference())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter the GCash payment reference number"));
        }
        if ("Same-Day Delivery".equals(submittedOrder.getFulfillmentMethod()) && isBlank(submittedOrder.getDeliveryAddress())) {
            return ResponseEntity.badRequest().body(Map.of("error", "A delivery address is required for same-day delivery"));
        }

        Map<Integer, Product> products = new LinkedHashMap<>();
        Map<Integer, BigDecimal> requestedByProduct = new TreeMap<>();
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);

        for (OrderItem submittedItem : request.getItems()) {
            if (submittedItem == null || submittedItem.getProductId() == null || submittedItem.getQuantity() == null
                    || submittedItem.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                    || submittedItem.getQuantity().stripTrailingZeros().scale() > 2) {
                return ResponseEntity.badRequest().body(Map.of("error", "Each item needs a product and a positive quantity with at most two decimal places"));
            }

            Product product = products.get(submittedItem.getProductId());
            if (product == null) {
                product = productRepository.findById(submittedItem.getProductId()).orElse(null);
                if (product == null || Boolean.TRUE.equals(product.getDeleted())
                        || (!walkIn && !Boolean.TRUE.equals(product.getVisible()))) {
                    return ResponseEntity.badRequest().body(Map.of("error", "A requested product is unavailable"));
                }
                products.put(product.getId(), product);
            }

            BigDecimal quantity = submittedItem.getQuantity();
            BigDecimal subtotal = product.getPricePerUnit().multiply(quantity).setScale(2, RoundingMode.HALF_UP);
            total = total.add(subtotal);
            requestedByProduct.merge(product.getId(), quantity, BigDecimal::add);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductIdSnapshot(product.getId());
            orderItem.setProductNameSnapshot(product.getName());
            orderItem.setProductUnitSnapshot(product.getUnit());
            orderItem.setProductSkuSnapshot(product.getSku());
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(product.getPricePerUnit());
            orderItem.setSubtotal(subtotal);
            orderItems.add(orderItem);
        }

        Map<Integer, List<InventoryBatch>> availableBatchesByProduct = new LinkedHashMap<>();
        for (Map.Entry<Integer, BigDecimal> requested : requestedByProduct.entrySet()) {
            List<InventoryBatch> availableBatches = batchRepository
                    .findUnexpiredAvailableBatchesForUpdate(requested.getKey(), BigDecimal.ZERO);
            BigDecimal availableQuantity = availableBatches.stream()
                    .map(InventoryBatch::getRemainingQty)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (availableQuantity.compareTo(requested.getValue()) < 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Insufficient stock for product ID: " + requested.getKey()));
            }
            availableBatchesByProduct.put(requested.getKey(), availableBatches);
        }

        Order order = new Order();
        order.setReferenceId("CF-" + UUID.randomUUID().toString().replace("-", ""));
        order.setOrderSource(walkIn ? "walk_in" : "online");
        order.setCustomerName(submittedOrder.getCustomerName().trim());
        order.setCustomerContact(submittedOrder.getCustomerContact().trim());
        order.setCustomerEmail(blankToNull(submittedOrder.getCustomerEmail()));
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            User signedInUser = userRepository.findByUsername(authentication.getName());
            if (walkIn) {
                order.setCreatedByUserId(signedInUser == null ? null : signedInUser.getId());
            } else {
                order.setCustomerUserId(signedInUser == null ? null : signedInUser.getId());
            }
        }
        order.setFulfillmentMethod(submittedOrder.getFulfillmentMethod());
        order.setDeliveryAddress(blankToNull(submittedOrder.getDeliveryAddress()));
        order.setDeliveryNotes(blankToNull(submittedOrder.getDeliveryNotes()));
        BigDecimal deliveryFee = "Same-Day Delivery".equals(submittedOrder.getFulfillmentMethod())
                ? configuredDeliveryFee.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
        order.setDeliveryFee(deliveryFee);
        order.setPaymentMethod(submittedOrder.getPaymentMethod());
        order.setPaymentStatus(gcashPayment ? "pending" : "unpaid");
        order.setPaymentReference(gcashPayment ? submittedOrder.getPaymentReference().trim() : null);
        order.setPaymentDeadlineAt(gcashPayment ? LocalDateTime.now().plusHours(12) : null);
        order.setOrderStatus("pending");
        if ("Same-Day Delivery".equals(submittedOrder.getFulfillmentMethod())) {
            order.setDeliveryStatus("Order Being Prepared");
        }
        order.setTotalAmount(total.add(deliveryFee));
        Order savedOrder = orderRepository.save(order);

        for (OrderItem item : orderItems) {
            item.setOrderId(savedOrder.getId());
            OrderItem savedItem = orderItemRepository.save(item);
            BigDecimal qtyToDeduct = item.getQuantity();

            for (InventoryBatch batch : availableBatchesByProduct.get(item.getProductId())) {
                if (qtyToDeduct.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal deduction = batch.getRemainingQty().min(qtyToDeduct);
                batch.setRemainingQty(batch.getRemainingQty().subtract(deduction));
                qtyToDeduct = qtyToDeduct.subtract(deduction);
                batchRepository.save(batch);

                BatchDeduction batchDeduction = new BatchDeduction();
                batchDeduction.setOrderItemId(savedItem.getId());
                batchDeduction.setBatchId(batch.getId());
                batchDeduction.setDeductedQty(deduction);
                batchDeductionRepository.save(batchDeduction);
            }
        }

        Map<String, Object> response = new java.util.LinkedHashMap<>();
        response.put("message", "Checkout successful");
        response.put("orderId", savedOrder.getId());
        response.put("referenceId", savedOrder.getReferenceId());
        response.put("guestOrder", savedOrder.getCustomerUserId() == null);
        response.put("paymentStatus", savedOrder.getPaymentStatus());
        if (savedOrder.getPaymentDeadlineAt() != null) {
            response.put("paymentDeadlineAt", savedOrder.getPaymentDeadlineAt());
        }
        response.put("deliveryFee", deliveryFee);
        response.put("totalAmount", savedOrder.getTotalAmount());
        return ResponseEntity.ok(response);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
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
