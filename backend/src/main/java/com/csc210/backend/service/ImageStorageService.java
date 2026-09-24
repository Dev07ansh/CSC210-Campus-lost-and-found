package com.csc210.backend.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/gif", "image/webp");
    private final Path uploadDirectory = Path.of("uploads").toAbsolutePath().normalize();

    public String store(MultipartFile image, boolean required) {
        if (image == null || image.isEmpty()) {
            if (required) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An image is required for a found-item report.");
            return "";
        }
        if (image.getSize() > MAX_FILE_SIZE || !ALLOWED_TYPES.contains(image.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a JPG, PNG, GIF, or WebP image up to 5 MB.");
        }
        String filename = UUID.randomUUID() + extensionFor(image.getContentType());
        try {
            Files.createDirectories(uploadDirectory);
            Files.copy(image.getInputStream(), uploadDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + filename;
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The image could not be saved.", exception);
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type.");
        };
    }
}
