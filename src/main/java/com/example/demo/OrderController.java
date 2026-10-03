package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read side of the orders feature. POST /api/orders (checkout) lives in CheckoutController;
 * this controller lets the admin dashboard list what customers have placed.
 */
@RestController
@RequestMapping("/api")
public class OrderController {

    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;

    // Newest first. Guest orders have customerUserId == null; registered-account orders carry the user's id.
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listOrders() {
        List<Order> orders = new ArrayList<>();
        for (Order order : orderRepository.findAll(Sort.by(Sort.Direction.DESC, "id"))) {
            if (!Boolean.TRUE.equals(order.getDeleted())) {
                orders.add(order);
            }
        }
        if (orders.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Integer> orderIds = new HashSet<>();
        Set<Integer> userIds = new HashSet<>();
        for (Order order : orders) {
            orderIds.add(order.getId());
            if (order.getCustomerUserId() != null) {
                userIds.add(order.getCustomerUserId());
            }
        }

        Map<Integer, List<OrderItem>> itemsByOrder = new HashMap<>();
        Set<Integer> productIds = new HashSet<>();
        for (OrderItem item : orderItemRepository.findByOrderIdIn(orderIds)) {
            itemsByOrder.computeIfAbsent(item.getOrderId(), key -> new ArrayList<>()).add(item);
            productIds.add(item.getProductId());
        }

        Map<Integer, Product> productsById = new HashMap<>();
        for (Product product : productRepository.findAllById(productIds)) {
            productsById.put(product.getId(), product);
        }

        Map<Integer, String> usernamesById = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.findAllById(userIds)) {
                usernamesById.put(user.getId(), user.getUsername());
            }
        }

        List<Map<String, Object>> response = new ArrayList<>();
        for (Order order : orders) {
            // LinkedHashMap (not Map.of) because many of these values can legitimately be null.
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", order.getId());
            row.put("referenceId", order.getReferenceId());
            row.put("orderSource", order.getOrderSource());
            row.put("createdAt", order.getCreatedAt());
            row.put("customerName", order.getCustomerName());
            row.put("customerContact", order.getCustomerContact());
            row.put("customerEmail", order.getCustomerEmail());
            row.put("customerUserId", order.getCustomerUserId());
            row.put("accountUsername", order.getCustomerUserId() == null ? null : usernamesById.get(order.getCustomerUserId()));
            row.put("fulfillmentMethod", order.getFulfillmentMethod());
            row.put("deliveryAddress", order.getDeliveryAddress());
            row.put("deliveryNotes", order.getDeliveryNotes());
            row.put("paymentMethod", order.getPaymentMethod());
            row.put("paymentStatus", order.getPaymentStatus());
            row.put("orderStatus", order.getOrderStatus());
            row.put("totalAmount", order.getTotalAmount());

            List<Map<String, Object>> items = new ArrayList<>();
            for (OrderItem item : itemsByOrder.getOrDefault(order.getId(), new ArrayList<>())) {
                Product product = productsById.get(item.getProductId());
                Map<String, Object> itemRow = new LinkedHashMap<>();
                itemRow.put("productId", item.getProductId());
                itemRow.put("productName", product == null ? "Product #" + item.getProductId() : product.getName());
                itemRow.put("unit", product == null ? null : product.getUnit());
                itemRow.put("quantity", item.getQuantity());
                itemRow.put("unitPrice", item.getUnitPrice());
                itemRow.put("subtotal", item.getSubtotal());
                items.add(itemRow);
            }
            row.put("items", items);
            response.add(row);
        }
        return response;
    }

    @PutMapping("/orders/{orderId}/payment-status")
    public ResponseEntity<Map<String, String>> updatePaymentStatus(
            @PathVariable Integer orderId,
            @RequestBody PaymentStatusRequest request) {
        if (request == null || request.getPaymentStatus() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment status must be paid or unpaid"));
        }

        String paymentStatus = request.getPaymentStatus().trim().toLowerCase(Locale.ROOT);
        if (!paymentStatus.equals("paid") && !paymentStatus.equals("unpaid")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Payment status must be paid or unpaid"));
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Order not found"));
        }

        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of(
                "message", "Payment status updated",
                "paymentStatus", paymentStatus
        ));
    }
}

class PaymentStatusRequest {
    private String paymentStatus;

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}
