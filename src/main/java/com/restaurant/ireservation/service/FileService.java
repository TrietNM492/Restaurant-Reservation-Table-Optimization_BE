package com.restaurant.ireservation.service;

import com.restaurant.ireservation.error.RequestInvalidException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final CloudinaryService cloudinaryService;

    @Value("${cloudinary.avatar.max-file-size:5242880}")
    private long maxFileSize = 5 * 1024 * 1024; // 5 MB

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    public String getNameAvatarUrl(MultipartFile file) throws URISyntaxException, IOException {
        return getNameAvatarUrl(file, null);
    }

    public String getNameAvatarUrl(MultipartFile file, Long userId) throws URISyntaxException, IOException {
        validateAvatarFile(file);
        byte[] bytes = file.getBytes();
        return cloudinaryService.uploadAvatar(bytes, userId, file.getContentType());
    }

    public void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new RequestInvalidException("File is empty or missing");
        }

        if (file.getSize() > maxFileSize) {
            throw new RequestInvalidException("Avatar file size exceeds the 5MB limit");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new RequestInvalidException("Unsupported image content type: " + contentType);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RequestInvalidException("Unable to read file content: " + e.getMessage());
        }

        if (bytes == null || bytes.length == 0) {
            throw new RequestInvalidException("File content is empty");
        }

        if (!matchesMagicBytes(bytes, contentType.toLowerCase())) {
            throw new RequestInvalidException("File content does not match declared image format");
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new RequestInvalidException("Corrupted or undecodable image file");
            }
        } catch (RequestInvalidException e) {
            throw e;
        } catch (Exception e) {
            throw new RequestInvalidException("Corrupted or undecodable image file: " + e.getMessage());
        }
    }

    private boolean matchesMagicBytes(byte[] bytes, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;
            case "image/png" -> bytes.length >= 8
                    && bytes[0] == (byte) 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A;
            case "image/webp" -> bytes.length >= 12
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }
}
