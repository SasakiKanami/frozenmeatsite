package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductStockRepository productStockRepository;
    @Mock
    private InventoryBatchRepository inventoryBatchRepository;
    @Mock
    private ProductRepository productRepository;
    @InjectMocks
    private ProductController controller;

    @Test
    void addingStockCreatesANewInventoryBatch() {
        when(productRepository.findById(7)).thenReturn(Optional.of(activeProduct(7)));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.adjustStock(7, adjustment(BigDecimal.TEN));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        InventoryBatch savedBatch = batchCaptor.getValue();
        assertEquals(7, savedBatch.getProductId());
        assertEquals("Direct Meat Supplier", savedBatch.getSupplierName());
        assertEquals(BigDecimal.TEN, savedBatch.getInitialQty());
        assertEquals(BigDecimal.TEN, savedBatch.getRemainingQty());
        assertFalse(savedBatch.getDeleted());
        assertNotNull(savedBatch.getArrivalDate());
        assertTrue(savedBatch.getBatchNumber().startsWith("ADMIN-"));
        assertTrue(savedBatch.getBatchNumber().length() <= 50);
    }

    @Test
    void addingOneUnitCreatesANewInventoryBatch() {
        when(productRepository.findById(7)).thenReturn(Optional.of(activeProduct(7)));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.adjustStock(7, adjustment(BigDecimal.ONE));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        assertEquals(BigDecimal.ONE, batchCaptor.getValue().getRemainingQty());
    }

    @Test
    void addingCustomFractionalAmountCreatesANewInventoryBatch() {
        when(productRepository.findById(7)).thenReturn(Optional.of(activeProduct(7)));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.adjustStock(7, adjustment(new BigDecimal("2.50")));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        assertEquals(new BigDecimal("2.50"), batchCaptor.getValue().getRemainingQty());
    }

    @Test
    void reducingStockConsumesBatchesInFifoOrder() {
        when(productRepository.findById(7)).thenReturn(Optional.of(activeProduct(7)));
        InventoryBatch first = batch(7, "FIRST", "6");
        InventoryBatch second = batch(7, "SECOND", "8");
        when(inventoryBatchRepository.findAvailableBatchesForUpdate(7, BigDecimal.ZERO))
                .thenReturn(List.of(first, second));
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = controller.adjustStock(7, adjustment(BigDecimal.TEN.negate()));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new BigDecimal("0"), first.getRemainingQty());
        assertEquals(new BigDecimal("4"), second.getRemainingQty());
        verify(inventoryBatchRepository).save(first);
        verify(inventoryBatchRepository).save(second);
    }

    @Test
    void reductionCannotMakeStockNegative() {
        when(productRepository.findById(7)).thenReturn(Optional.of(activeProduct(7)));
        InventoryBatch batch = batch(7, "ONLY", "9");
        when(inventoryBatchRepository.findAvailableBatchesForUpdate(7, BigDecimal.ZERO))
                .thenReturn(List.of(batch));

        ResponseEntity<?> response = controller.adjustStock(7, adjustment(BigDecimal.TEN.negate()));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(new BigDecimal("9"), batch.getRemainingQty());
        verify(inventoryBatchRepository, never()).save(any(InventoryBatch.class));
    }

    @Test
    void rejectsZeroAdjustment() {
        ResponseEntity<?> response = controller.adjustStock(7, adjustment(BigDecimal.ZERO));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(productRepository, never()).findById(7);
    }

    @Test
    void rejectsCustomAdjustmentWithMoreThanTwoDecimalPlaces() {
        ResponseEntity<?> response = controller.adjustStock(7, adjustment(new BigDecimal("1.001")));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(productRepository, never()).findById(7);
    }

    private Product activeProduct(Integer id) {
        Product product = new Product();
        product.setId(id);
        product.setDeleted(false);
        return product;
    }

    private InventoryBatch batch(Integer productId, String number, String remaining) {
        InventoryBatch batch = new InventoryBatch();
        batch.setProductId(productId);
        batch.setBatchNumber(number);
        batch.setArrivalDate(LocalDateTime.now());
        batch.setRemainingQty(new BigDecimal(remaining));
        batch.setDeleted(false);
        return batch;
    }

    private AdjustStockRequest adjustment(BigDecimal amount) {
        AdjustStockRequest request = new AdjustStockRequest();
        request.setAdjustment(amount);
        return request;
    }
}
