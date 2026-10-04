package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

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
    @Autowired private ArchivedOrderRepository archivedOrderRepository;
    @Autowired private OrderInventoryService orderInventoryService;

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
        return mapOrders(orders);
    }

    @GetMapping("/account/orders")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCustomerPendingOrders(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName());
        if (user == null) {
            return new ArrayList<>();
        }
        List<Order> orders = orderRepository.findByCustomerUserIdOrderByIdDesc(user.getId()).stream()
                .filter(order -> !Boolean.TRUE.equals(order.getDeleted()))
                .toList();
        return mapOrders(orders);
    }

    @PostMapping("/orders/guest-delivery-status")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getGuestDeliveryStatus(@RequestBody GuestDeliveryStatusRequest request) {
        if (request == null || request.getReferenceId() == null || request.getCustomerContact() == null
                || request.getReferenceId().isBlank() || request.getCustomerContact().isBlank()
                || request.getReferenceId().length() > 50 || request.getCustomerContact().length() > 30) {
            return ResponseEntity.badRequest().body(Map.of("error", "Order reference and contact number are required"));
        }

        Order order = orderRepository.findByReferenceIdAndCustomerContactAndCustomerUserIdIsNull(
                request.getReferenceId().trim(), request.getCustomerContact().trim()).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())
                || (!"Same-Day Delivery".equals(order.getFulfillmentMethod())
                    && !"GCash Transfer".equals(order.getPaymentMethod()))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("paymentStatus", order.getPaymentStatus());
        status.put("deliveryStatus", order.getDeliveryStatus());
        status.put("orderStatus", order.getOrderStatus());
        return ResponseEntity.ok(status);
    }

    private List<Map<String, Object>> mapOrders(List<Order> orders) {
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
            if (item.getProductId() != null) {
                productIds.add(item.getProductId());
            }
        }

        Map<Integer, Product> productsById = new HashMap<>();
        if (!productIds.isEmpty()) {
            for (Product product : productRepository.findAllById(productIds)) {
                productsById.put(product.getId(), product);
            }
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
            row.put("deliveryStatus", order.getDeliveryStatus());
            row.put("deliveryAddress", order.getDeliveryAddress());
            row.put("deliveryNotes", order.getDeliveryNotes());
            row.put("paymentMethod", order.getPaymentMethod());
            row.put("paymentStatus", order.getPaymentStatus());
            row.put("paymentReference", order.getPaymentReference());
            row.put("paymentDeadlineAt", order.getPaymentDeadlineAt());
            row.put("orderStatus", order.getOrderStatus());
            row.put("totalAmount", order.getTotalAmount());
            row.put("deliveryFee", order.getDeliveryFee());

            List<Map<String, Object>> items = new ArrayList<>();
            for (OrderItem item : itemsByOrder.getOrDefault(order.getId(), new ArrayList<>())) {
                Product product = productsById.get(item.getProductId());
                Map<String, Object> itemRow = new LinkedHashMap<>();
                itemRow.put("productId", item.getProductIdSnapshot() != null
                        ? item.getProductIdSnapshot() : item.getProductId());
                itemRow.put("productName", item.getProductNameSnapshot() != null
                        ? item.getProductNameSnapshot()
                        : product == null ? "Product #" + item.getProductId() : product.getName());
                itemRow.put("sku", item.getProductSkuSnapshot() != null
                        ? item.getProductSkuSnapshot() : product == null ? null : product.getSku());
                itemRow.put("unit", item.getProductUnitSnapshot() != null
                        ? item.getProductUnitSnapshot() : product == null ? null : product.getUnit());
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

    @PutMapping("/orders/{orderId}/delivery-status")
    @Transactional
    public ResponseEntity<?> updateDeliveryStatus(
            @PathVariable Integer orderId,
            @RequestBody DeliveryStatusRequest request) {
        if (request == null || request.getDeliveryStatus() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose a delivery status"));
        }
        String status = request.getDeliveryStatus().trim();
        if (!List.of("Order Being Prepared", "Delivery On the Way", "Delivered").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Delivery status must be Order Being Prepared, Delivery On the Way, or Delivered"));
        }

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        if (!"Same-Day Delivery".equals(order.getFulfillmentMethod())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Delivery status only applies to delivery orders"));
        }
        if ("cancelled".equalsIgnoreCase(order.getOrderStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Delivery status cannot be changed for a cancelled order"));
        }
        if ("GCash Transfer".equals(order.getPaymentMethod())
                && !"paid".equals(order.getPaymentStatus())
                && !"Order Being Prepared".equals(status)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "GCash payment must be confirmed before the order can be dispatched or delivered"));
        }

        order.setDeliveryStatus(status);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("message", "Delivery status updated", "deliveryStatus", status));
    }

    @PutMapping("/orders/{orderId}/payment-status")
    @Transactional
    public ResponseEntity<Map<String, String>> updatePaymentStatus(
            @PathVariable Integer orderId,
            @RequestBody PaymentStatusRequest request) {
        if (request == null || request.getPaymentStatus() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "A valid payment status is required"));
        }

        String paymentStatus = request.getPaymentStatus().trim().toLowerCase(Locale.ROOT);
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            return ResponseEntity.status(404).body(Map.of("error", "Order not found"));
        }
        boolean gcashPayment = "GCash Transfer".equals(order.getPaymentMethod());
        if (gcashPayment && !List.of("pending", "paid").contains(paymentStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "GCash payment status must be pending or paid"));
        }
        if (!gcashPayment && !List.of("paid", "unpaid").contains(paymentStatus)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cash payment status must be paid or unpaid"));
        }
        if ("cancelled".equalsIgnoreCase(order.getOrderStatus())
                || "cancelled".equalsIgnoreCase(order.getPaymentStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Payment status cannot be changed for a cancelled order"));
        }
        if (gcashPayment && "pending".equals(order.getPaymentStatus())
                && "paid".equals(paymentStatus)
                && order.getPaymentDeadlineAt() != null
                && !order.getPaymentDeadlineAt().isAfter(LocalDateTime.now())) {
            if (!orderInventoryService.restoreOrderStock(order)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "The payment window expired, but stock could not be restored because an original batch is missing"));
            }
            order.setPaymentStatus("cancelled");
            order.setOrderStatus("cancelled");
            orderRepository.save(order);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "The 12-hour payment window expired. The order was cancelled and its stock released"));
        }
        if (gcashPayment && "paid".equals(order.getPaymentStatus()) && "pending".equals(paymentStatus)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "A confirmed GCash payment cannot be changed back to pending"));
        }

        order.setPaymentStatus(paymentStatus);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of(
                "message", "Payment status updated",
                "paymentStatus", paymentStatus
        ));
    }

    @PutMapping("/orders/{orderId}/status")
    @Transactional
    public ResponseEntity<Map<String, String>> updateOrderStatus(
            @PathVariable Integer orderId,
            @RequestBody OrderStatusRequest request) {
        if (request == null || request.getOrderStatus() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Order status must be completed or cancelled"));
        }
        String requestedStatus = request.getOrderStatus().trim().toLowerCase(Locale.ROOT);
        if (!requestedStatus.equals("completed") && !requestedStatus.equals("cancelled")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Order status must be completed or cancelled"));
        }

        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        String currentStatus = order.getOrderStatus().toLowerCase(Locale.ROOT);
        if (currentStatus.equals(requestedStatus)) {
            return ResponseEntity.ok(Map.of("message", "Order status unchanged", "orderStatus", requestedStatus));
        }
        if (!currentStatus.equals("pending")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only pending orders can be completed or cancelled"));
        }
        if (requestedStatus.equals("completed") && "GCash Transfer".equals(order.getPaymentMethod())
                && !"paid".equals(order.getPaymentStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "GCash payment must be confirmed before completing the order"));
        }
        if (requestedStatus.equals("cancelled")) {
            boolean unpaidCashOrder = "Cash on Pickup / Delivery".equals(order.getPaymentMethod())
                    && "unpaid".equals(order.getPaymentStatus());
            boolean pendingGcashOrder = "GCash Transfer".equals(order.getPaymentMethod())
                    && "pending".equals(order.getPaymentStatus());
            if (!unpaidCashOrder && !pendingGcashOrder) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Only unpaid cash or pending GCash orders can be cancelled"));
            }
            if (!orderInventoryService.restoreOrderStock(order)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "This order cannot be cancelled because a deducted inventory batch is missing"));
            }
            if (pendingGcashOrder) {
                order.setPaymentStatus("cancelled");
            }
        }

        order.setOrderStatus(requestedStatus);
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("message", "Order status updated", "orderStatus", requestedStatus));
    }

    @PutMapping("/orders/{orderId}/archive")
    @Transactional
    public ResponseEntity<Map<String, String>> archiveOrder(@PathVariable Integer orderId) {
        Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
        if (order == null || Boolean.TRUE.equals(order.getDeleted())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Order not found"));
        }
        if (!"completed".equalsIgnoreCase(order.getOrderStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Only completed orders can be archived"));
        }

        ArchivedOrder archivedOrder = new ArchivedOrder();
        archivedOrder.setOriginalOrderId(order.getId());
        archivedOrder.setReferenceId(order.getReferenceId());
        archivedOrder.setCustomerName(order.getCustomerName());
        archivedOrder.setTotalAmount(order.getTotalAmount());
        archivedOrder.setArchivedReason("Completed order archived by staff");
        archivedOrderRepository.save(archivedOrder);

        order.setDeleted(true);
        order.setDeletedAt(LocalDateTime.now());
        orderRepository.save(order);
        return ResponseEntity.ok(Map.of("message", "Completed order archived"));
    }

}

class PaymentStatusRequest {
    private String paymentStatus;

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
}

class OrderStatusRequest {
    private String orderStatus;

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
}

class DeliveryStatusRequest {
    private String deliveryStatus;

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }
}

class GuestDeliveryStatusRequest {
    private String referenceId;
    private String customerContact;

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public String getCustomerContact() { return customerContact; }
    public void setCustomerContact(String customerContact) { this.customerContact = customerContact; }
}
