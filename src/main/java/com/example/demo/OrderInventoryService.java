package com.example.demo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

@Service
public class OrderInventoryService {
    private final OrderItemRepository orderItemRepository;
    private final BatchDeductionRepository batchDeductionRepository;
    private final InventoryBatchRepository batchRepository;

    public OrderInventoryService(
            OrderItemRepository orderItemRepository,
            BatchDeductionRepository batchDeductionRepository,
            InventoryBatchRepository batchRepository) {
        this.orderItemRepository = orderItemRepository;
        this.batchDeductionRepository = batchDeductionRepository;
        this.batchRepository = batchRepository;
    }

    public boolean restoreOrderStock(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        List<Integer> itemIds = items.stream().map(OrderItem::getId).toList();
        if (itemIds.isEmpty()) return true;

        List<BatchDeduction> recordedDeductions = batchDeductionRepository.findByOrderItemIdIn(itemIds);
        if (recordedDeductions.stream().anyMatch(deduction -> deduction.getBatchId() == null)) return false;
        List<BatchDeduction> deductions = recordedDeductions.stream()
                .sorted(java.util.Comparator.comparing(BatchDeduction::getBatchId))
                .toList();

        Map<Integer, InventoryBatch> batches = new LinkedHashMap<>();
        for (BatchDeduction deduction : deductions) {
            if (batches.containsKey(deduction.getBatchId())) continue;
            InventoryBatch batch = batchRepository.findByIdForUpdate(deduction.getBatchId()).orElse(null);
            if (batch == null) return false;
            batches.put(batch.getId(), batch);
        }
        for (BatchDeduction deduction : deductions) {
            InventoryBatch batch = batches.get(deduction.getBatchId());
            batch.setRemainingQty(batch.getRemainingQty().add(deduction.getDeductedQty()));
        }
        for (InventoryBatch batch : batches.values()) {
            batchRepository.save(batch);
        }
        return true;
    }
}
