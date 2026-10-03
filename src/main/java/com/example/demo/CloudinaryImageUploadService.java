package com.example.demo;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

@Service
public class CloudinaryImageUploadService {
    private static final Logger logger = LoggerFactory.getLogger(CloudinaryImageUploadService.class);
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private final String cloudinaryUrl;

    public CloudinaryImageUploadService(@Value("${cloudinary.url:}") String cloudinaryUrl) {
        this.cloudinaryUrl = cloudinaryUrl;
    }

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProductImageException("Choose an image file to upload");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new InvalidProductImageException("Image must be 5 MB or smaller");
        }

        String contentType = file.getContentType();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidProductImageException("Unable to read the selected image");
        }
        if (!matchesSupportedImage(contentType, bytes)) {
            throw new InvalidProductImageException("Choose a valid JPEG, PNG, GIF, or WebP image");
        }
        if (cloudinaryUrl == null || cloudinaryUrl.isBlank()) {
            throw new CloudinaryNotConfiguredException("Image uploads are not configured. Set the CLOUDINARY_URL environment variable and restart the app.");
        }

        try {
            Cloudinary cloudinary = new Cloudinary(cloudinaryUrl);
            Map<?, ?> result = cloudinary.uploader().upload(bytes, ObjectUtils.asMap(
                    "folder", "frozenmeatsite/products",
                    "resource_type", "image"
            ));
            Object secureUrl = result.get("secure_url");
            if (secureUrl instanceof String url && !url.isBlank()) {
                return url;
            }
            logger.error("Cloudinary upload response did not contain a secure URL");
            throw new CloudinaryImageUploadException("Image upload did not return a usable URL");
        } catch (IOException | IllegalArgumentException exception) {
            logger.error("Cloudinary image upload failed", exception);
            throw new CloudinaryImageUploadException("Image upload failed. Check your Cloudinary configuration and try again.");
        }
    }

    private boolean matchesSupportedImage(String contentType, byte[] bytes) {
        if (contentType == null || bytes.length < 12) {
            return false;
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e
                    && bytes[3] == 0x47 && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/gif" -> startsWith(bytes, "GIF87a") || startsWith(bytes, "GIF89a");
            case "image/webp" -> startsWith(bytes, "RIFF") && startsWithAt(bytes, "WEBP", 8);
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, String value) {
        return startsWithAt(bytes, value, 0);
    }

    private boolean startsWithAt(byte[] bytes, String value, int offset) {
        if (bytes.length < offset + value.length()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (bytes[offset + i] != (byte) value.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}

class InvalidProductImageException extends RuntimeException {
    InvalidProductImageException(String message) {
        super(message);
    }
}

class CloudinaryNotConfiguredException extends RuntimeException {
    CloudinaryNotConfiguredException(String message) {
        super(message);
    }
}

class CloudinaryImageUploadException extends RuntimeException {
    CloudinaryImageUploadException(String message) {
        super(message);
    }
}
