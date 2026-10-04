package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class CloudinaryImageUploadServiceTest {

    private final CloudinaryImageUploadService service = new CloudinaryImageUploadService("");

    @Test
    void rejectsFileWithImageMimeTypeButInvalidImageSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-an-image.png", "image/png", "not an image".getBytes()
        );

        assertThrows(InvalidProductImageException.class, () -> service.upload(file));
    }

    @Test
    void reportsMissingCloudinaryConfigurationForValidImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "product.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 0, 0, 0, 0, 0, 0, 0, 0}
        );

        assertThrows(CloudinaryNotConfiguredException.class, () -> service.upload(file));
    }
}
