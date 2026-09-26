package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {

    @Autowired
    private ProductStockRepository productStockRepository;

    @Autowired
    private InventoryBatchRepository inventoryBatchRepository;

    @GetMapping("/products")
    public List getCatalog() {
        return productStockRepository.findAll();
    }

    @DeleteMapping("/inventory/{id}")
    public ResponseEntity archiveBatch(@PathVariable Integer id) {
        InventoryBatch batch = inventoryBatchRepository.findById(id).orElse(null);

        if (batch != null) {
            batch.setDeleted(true);
            inventoryBatchRepository.save(batch);
            return ResponseEntity.ok(Map.of("message", "Inventory batch archived successfully"));
        }

        return ResponseEntity.status(404).body(Map.of("error", "Batch not found"));
    }
}
