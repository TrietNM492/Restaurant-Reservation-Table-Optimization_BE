package com.restaurant.ireservation.service;

import com.restaurant.ireservation.error.RequestInvalidException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    // Valid 1x1 WebP binary (RIFF....WEBPVP8...)
    private static final byte[] VALID_WEBP_BYTES = Base64.getDecoder().decode(
            "UklGRkAAAABXRUJQVlA4WAoAAAAQAAAAAAAAAAAAQUxQSAIAAAAAAFZQOCAYAAAAMAEAnQEqAQABAAFAJiWkAANwAP79NvgA"
    );

    @Mock
    CloudinaryService cloudinaryService;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(cloudinaryService);
    }

    private byte[] createSampleImageBytes(String format) throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }

    @Test
    @DisplayName("Valid PNG avatar passes validation and uploads to Cloudinary")
    void upload_validPng_success() throws Exception {
        byte[] pngBytes = createSampleImageBytes("png");
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "avatar.png", "image/png", pngBytes);
        String expectedUrl = "https://res.cloudinary.com/test-cloud/image/upload/v1/restaurant/user-avatars/avatar-1.png";
        when(cloudinaryService.uploadAvatar(eq(pngBytes), eq(1L), eq("image/png"))).thenReturn(expectedUrl);

        String result = fileService.getNameAvatarUrl(file, 1L);

        assertEquals(expectedUrl, result);
        verify(cloudinaryService).uploadAvatar(eq(pngBytes), eq(1L), eq("image/png"));
    }

    @Test
    @DisplayName("Valid JPEG avatar passes validation and uploads to Cloudinary")
    void upload_validJpeg_success() throws Exception {
        byte[] jpegBytes = createSampleImageBytes("jpeg");
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "avatar.jpg", "image/jpeg", jpegBytes);
        String expectedUrl = "https://res.cloudinary.com/test-cloud/image/upload/v1/restaurant/user-avatars/avatar-1.jpg";
        when(cloudinaryService.uploadAvatar(eq(jpegBytes), eq(1L), eq("image/jpeg"))).thenReturn(expectedUrl);

        String result = fileService.getNameAvatarUrl(file, 1L);

        assertEquals(expectedUrl, result);
        verify(cloudinaryService).uploadAvatar(eq(jpegBytes), eq(1L), eq("image/jpeg"));
    }

    @Test
    @DisplayName("Valid WebP avatar passes validation and uploads to Cloudinary")
    void upload_validWebp_success() throws Exception {
        byte[] webpBytes = VALID_WEBP_BYTES;
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "avatar.webp", "image/webp", webpBytes);
        String expectedUrl = "https://res.cloudinary.com/test-cloud/image/upload/v1/restaurant/user-avatars/avatar-1.webp";
        when(cloudinaryService.uploadAvatar(eq(webpBytes), eq(1L), eq("image/webp"))).thenReturn(expectedUrl);

        String result = fileService.getNameAvatarUrl(file, 1L);

        assertEquals(expectedUrl, result);
        verify(cloudinaryService).uploadAvatar(eq(webpBytes), eq(1L), eq("image/webp"));
    }

    @Test
    @DisplayName("Empty file throws RequestInvalidException")
    void upload_emptyFile_throwsRequestInvalidException() {
        MockMultipartFile emptyFile = new MockMultipartFile("avatarUrl", "empty.png", "image/png", new byte[0]);

        assertThrows(RequestInvalidException.class, () -> fileService.getNameAvatarUrl(emptyFile, 1L));
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("File exceeding 5MB throws RequestInvalidException")
    void upload_fileExceeding5MB_throwsRequestInvalidException() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile("avatarUrl", "large.png", "image/png", largeBytes);

        RequestInvalidException ex = assertThrows(RequestInvalidException.class, () -> fileService.getNameAvatarUrl(largeFile, 1L));
        assertTrue(ex.getMessage().contains("exceeds the 5MB limit"));
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("Disallowed MIME type throws RequestInvalidException")
    void upload_disallowedMime_throwsRequestInvalidException() {
        MockMultipartFile pdfFile = new MockMultipartFile("avatarUrl", "document.pdf", "application/pdf", new byte[]{1, 2, 3});

        RequestInvalidException ex = assertThrows(RequestInvalidException.class, () -> fileService.getNameAvatarUrl(pdfFile, 1L));
        assertTrue(ex.getMessage().contains("Unsupported image content type"));
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("Magic byte mismatch throws RequestInvalidException")
    void upload_magicByteMismatch_throwsRequestInvalidException() {
        byte[] fakePng = "This is definitely a text file disguised as PNG".getBytes();
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "fake.png", "image/png", fakePng);

        RequestInvalidException ex = assertThrows(RequestInvalidException.class, () -> fileService.getNameAvatarUrl(file, 1L));
        assertTrue(ex.getMessage().contains("File content does not match declared image format"));
        verifyNoInteractions(cloudinaryService);
    }

    @Test
    @DisplayName("Corrupt image data with valid magic bytes throws RequestInvalidException")
    void upload_corruptImageData_throwsRequestInvalidException() {
        // Valid PNG header (8 bytes) followed by non-image garbage
        byte[] corruptPng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                (byte) 0xFF, (byte) 0xFF, (byte) 0xFF // truncated / corrupt
        };
        MockMultipartFile file = new MockMultipartFile("avatarUrl", "corrupt.png", "image/png", corruptPng);

        RequestInvalidException ex = assertThrows(RequestInvalidException.class, () -> fileService.getNameAvatarUrl(file, 1L));
        assertTrue(ex.getMessage().contains("Corrupted or undecodable image file"));
        verifyNoInteractions(cloudinaryService);
    }
}
