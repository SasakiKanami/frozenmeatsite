package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductStockRepository productStockRepository;
    @Mock
    private InventoryBatchRepository inventoryBatchRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CloudinaryImageUploadService imageUploadService;
    @InjectMocks
    private ProductController controller;

    @Test
    void uploadsProductImageAndReturnsItsCloudinaryUrl() {
        MockMultipartFile image = new MockMultipartFile(
                "file", "product.png", "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0}
        );
        when(imageUploadService.upload(image)).thenReturn("https://res.cloudinary.com/demo/image/upload/product.png");

        ResponseEntity<?> response = controller.uploadProductImage(image);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(imageUploadService).upload(image);
    }

    @Test
    void rejectsEmptyProductImageUpload() {
        MockMultipartFile image = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        ResponseEntity<?> response = controller.uploadProductImage(image);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(imageUploadService, never()).upload(image);
    }

    @Test
    void addingProductSavesBatchExpirationDate() {
        LocalDate expirationDate = LocalDate.now().plusDays(10);
        when(productRepository.existsByName("Test product")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            product.setId(7);
            return product;
        });
        when(inventoryBatchRepository.save(any(InventoryBatch.class)))
                .thenAnswer(invocation -> {
                    InventoryBatch batch = invocation.getArgument(0);
                    batch.setId(81);
                    return batch;
                });

        ResponseEntity<?> response = controller.addProduct(addProductRequest(expirationDate));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        assertEquals(expirationDate, batchCaptor.getValue().getExpirationDate());
        assertEquals("Supplier A", batchCaptor.getValue().getSupplierName());
    }

    @Test
    void receivingStockCreatesItsOwnBatchWithExpiry() {
        LocalDate expirationDate = LocalDate.now().plusDays(20);
        when(productRepository.findById(7)).thenReturn(Optional.of(activeProduct(7)));
        when(inventoryBatchRepository.save(any(InventoryBatch.class))).thenAnswer(invocation -> {
            InventoryBatch batch = invocation.getArgument(0);
            batch.setId(91);
            return batch;
        });

        ResponseEntity<?> response = controller.receiveBatch(7, receiveBatchRequest(expirationDate));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        ArgumentCaptor<InventoryBatch> batchCaptor = ArgumentCaptor.forClass(InventoryBatch.class);
        verify(inventoryBatchRepository).save(batchCaptor.capture());
        InventoryBatch savedBatch = batchCaptor.getValue();
        assertEquals("LOT-91", savedBatch.getBatchNumber());
        assertEquals("Supplier A", savedBatch.getSupplierName());
        assertEquals(new BigDecimal("7.50"), savedBatch.getInitialQty());
        assertEquals(new BigDecimal("7.50"), savedBatch.getRemainingQty());
        assertEquals(expirationDate, savedBatch.getExpirationDate());
    }

    @Test
    void receivingBatchRequiresAnExpiryDate() {
        ResponseEntity<?> response = controller.receiveBatch(7, receiveBatchRequest(null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(productRepository);
        verifyNoInteractions(inventoryBatchRepository);
    }

    @Test
    void updatesExpiryForBatchBelongingToProduct() {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(91);
        batch.setProductId(7);
        batch.setExpirationDate(LocalDate.now().plusDays(10));
        when(inventoryBatchRepository.findByIdAndProductId(91, 7)).thenReturn(Optional.of(batch));
        UpdateBatchExpirationRequest request = new UpdateBatchExpirationRequest();
        LocalDate correctedDate = LocalDate.now().plusDays(30);
        request.setExpirationDate(correctedDate);

        ResponseEntity<?> response = controller.updateBatchExpiration(7, 91, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(correctedDate, batch.getExpirationDate());
        verify(inventoryBatchRepository).save(batch);
    }

    @Test
    void productDetailsCombineOnlyActiveUnexpiredStockAndReturnFifoBatches() {
        Product product = activeProduct(7);
        product.setName("Test product");
        product.setCategory("Pork");
        product.setTemperatureTier("Fresh Chilled");
        product.setUnit("kg");
        product.setPricePerUnit(new BigDecimal("10.00"));
        product.setReorderLevel(new BigDecimal("5"));
        product.setImageUrl("https://example.test/product.png");
        when(productRepository.findById(7)).thenReturn(Optional.of(product));

        InventoryBatch available = new InventoryBatch();
        available.setId(1);
        available.setProductId(7);
        available.setBatchNumber("OLDER");
        available.setInitialQty(new BigDecimal("8"));
        available.setRemainingQty(new BigDecimal("3"));
        available.setExpirationDate(LocalDate.now().plusDays(2));
        available.setDeleted(false);
        InventoryBatch expired = new InventoryBatch();
        expired.setId(2);
        expired.setProductId(7);
        expired.setBatchNumber("EXPIRED");
        expired.setInitialQty(new BigDecimal("9"));
        expired.setRemainingQty(new BigDecimal("9"));
        expired.setExpirationDate(LocalDate.now().minusDays(1));
        expired.setDeleted(false);
        when(inventoryBatchRepository.findByProductIdOrderByArrivalDateAscIdAsc(7))
                .thenReturn(List.of(available, expired));

        ResponseEntity<?> response = controller.getAdminProductDetails(7);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        AdminProductDetails details = (AdminProductDetails) response.getBody();
        assertEquals("https://example.test/product.png", details.imageUrl());
        assertEquals(new BigDecimal("3"), details.inStockQty());
        assertEquals(List.of(1, 2), details.batches().stream().map(AdminBatchDetails::id).toList());
    }

    @Test
    void rejectsPastBatchExpirationDate() {
        ResponseEntity<?> response = controller.addProduct(addProductRequest(LocalDate.now().minusDays(1)));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(productRepository);
        verifyNoInteractions(inventoryBatchRepository);
    }

    @Test
    void rejectsNewProductBatchWithoutExpiryDate() {
        ResponseEntity<?> response = controller.addProduct(addProductRequest(null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(productRepository);
        verifyNoInteractions(inventoryBatchRepository);
    }

    @Test
    void visibilityCanBeChangedWithoutDeletingProduct() {
        Product product = activeProduct(7);
        when(productRepository.findById(7)).thenReturn(Optional.of(product));
        ProductVisibilityRequest request = new ProductVisibilityRequest();
        request.setVisible(false);

        ResponseEntity<?> response = controller.updateProductVisibility(7, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(product.getVisible());
        assertFalse(product.getDeleted());
        verify(productRepository).save(product);
    }

    @Test
    void rejectsVisibilityRequestWithoutValue() {
        ResponseEntity<?> response = controller.updateProductVisibility(7, new ProductVisibilityRequest());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verify(productRepository, never()).findById(7);
    }

    private Product activeProduct(Integer id) {
        Product product = new Product();
        product.setId(id);
        product.setDeleted(false);
        product.setVisible(true);
        return product;
    }

    private ReceiveBatchRequest receiveBatchRequest(LocalDate expirationDate) {
        ReceiveBatchRequest request = new ReceiveBatchRequest();
        request.setBatchNumber("LOT-91");
        request.setSupplierName("Supplier A");
        request.setQuantity(new BigDecimal("7.50"));
        request.setExpirationDate(expirationDate);
        return request;
    }

    private AddProductRequest addProductRequest(LocalDate expirationDate) {
        AddProductRequest request = new AddProductRequest();
        request.setName("Test product");
        request.setCategory("Pork");
        request.setTemperatureTier("Fresh Chilled");
        request.setUnit("kg");
        request.setPricePerUnit(new BigDecimal("10.00"));
        request.setReorderLevel(new BigDecimal("5"));
        request.setBatchNumber("BATCH-TEST");
        request.setInitialQty(new BigDecimal("12"));
        request.setExpirationDate(expirationDate);
        request.setSupplierName("Supplier A");
        return request;
    }
}
