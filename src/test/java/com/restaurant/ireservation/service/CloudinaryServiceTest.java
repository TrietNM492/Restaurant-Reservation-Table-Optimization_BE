package com.restaurant.ireservation.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.restaurant.ireservation.error.FileUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    Cloudinary cloudinary;

    @Mock
    Uploader uploader;

    private CloudinaryService cloudinaryService;

    private static final String CLOUD_NAME = "test-cloud";
    private static final String API_KEY = "test-api-key";
    private static final String API_SECRET = "test-api-secret";
    private static final String FOLDER = "restaurant/user-avatars";
    private static final int TIMEOUT = 5000;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        cloudinaryService = new CloudinaryService(cloudinary, CLOUD_NAME, API_KEY, API_SECRET, FOLDER, TIMEOUT);
    }

    @Test
    @DisplayName("isConfigured returns true when all credentials are set")
    void isConfigured_true() {
        assertTrue(cloudinaryService.isConfigured());
    }

    @Test
    @DisplayName("isConfigured returns false when any credential is empty or blank")
    void isConfigured_false() {
        CloudinaryService unconfigured = new CloudinaryService(cloudinary, "", API_KEY, API_SECRET, FOLDER, TIMEOUT);
        assertFalse(unconfigured.isConfigured());

        CloudinaryService noKey = new CloudinaryService(cloudinary, CLOUD_NAME, "  ", API_SECRET, FOLDER, TIMEOUT);
        assertFalse(noKey.isConfigured());

        CloudinaryService noSecret = new CloudinaryService(cloudinary, CLOUD_NAME, API_KEY, null, FOLDER, TIMEOUT);
        assertFalse(noSecret.isConfigured());
    }

    @Test
    @DisplayName("uploadAvatar throws FileUploadException when Cloudinary is not configured")
    void uploadAvatar_notConfigured_throwsException() {
        CloudinaryService unconfigured = new CloudinaryService(cloudinary, "", "", "", FOLDER, TIMEOUT);
        byte[] bytes = new byte[]{1, 2, 3};

        assertThrows(FileUploadException.class, () -> unconfigured.uploadAvatar(bytes, 1L, "image/jpeg"));
        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("uploadAvatar uploads successfully and returns secure URL")
    void uploadAvatar_success() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        String expectedUrl = "https://res.cloudinary.com/test-cloud/image/upload/v12345/restaurant/user-avatars/avatar-1-abc12345.jpg";
        when(uploader.upload(eq(bytes), anyMap())).thenReturn(Map.of("secure_url", expectedUrl));

        String result = cloudinaryService.uploadAvatar(bytes, 1L, "image/jpeg");

        assertEquals(expectedUrl, result);
        verify(uploader).upload(eq(bytes), anyMap());
    }

    @Test
    @DisplayName("uploadAvatar throws FileUploadException when SDK throws IOException")
    void uploadAvatar_sdkIOException_throwsFileUploadException() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        when(uploader.upload(eq(bytes), anyMap())).thenThrow(new IOException("Connection timed out"));

        assertThrows(FileUploadException.class, () -> cloudinaryService.uploadAvatar(bytes, 1L, "image/jpeg"));
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns publicId for valid owned URL")
    void extractPublicIdIfOwned_validUrl() {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/v1729000000/restaurant/user-avatars/avatar-1-abc.webp";
        String publicId = cloudinaryService.extractPublicIdIfOwned(url);
        assertEquals("restaurant/user-avatars/avatar-1-abc", publicId);
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns publicId for URL with transformations")
    void extractPublicIdIfOwned_withTransformations() {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/w_200,h_200,c_fill/v1729000000/restaurant/user-avatars/avatar-2-xyz.jpg";
        String publicId = cloudinaryService.extractPublicIdIfOwned(url);
        assertEquals("restaurant/user-avatars/avatar-2-xyz", publicId);
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns publicId for URL without version")
    void extractPublicIdIfOwned_withoutVersion() {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/restaurant/user-avatars/avatar-3-def.png";
        String publicId = cloudinaryService.extractPublicIdIfOwned(url);
        assertEquals("restaurant/user-avatars/avatar-3-def", publicId);
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns null for HTTP insecure URL")
    void extractPublicIdIfOwned_insecureHttp() {
        String url = "http://res.cloudinary.com/test-cloud/image/upload/v123/restaurant/user-avatars/avatar-1.jpg";
        assertNull(cloudinaryService.extractPublicIdIfOwned(url));
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns null for different host")
    void extractPublicIdIfOwned_differentHost() {
        String url = "https://otherhost.com/test-cloud/image/upload/restaurant/user-avatars/avatar-1.jpg";
        assertNull(cloudinaryService.extractPublicIdIfOwned(url));
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns null for different cloud name")
    void extractPublicIdIfOwned_differentCloud() {
        String url = "https://res.cloudinary.com/other-cloud/image/upload/restaurant/user-avatars/avatar-1.jpg";
        assertNull(cloudinaryService.extractPublicIdIfOwned(url));
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns null for different folder")
    void extractPublicIdIfOwned_differentFolder() {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/restaurant/other-folder/avatar-1.jpg";
        assertNull(cloudinaryService.extractPublicIdIfOwned(url));
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns null for legacy local path")
    void extractPublicIdIfOwned_legacyLocalPath() {
        String url = "user-avatars/cascas-12345.webp";
        assertNull(cloudinaryService.extractPublicIdIfOwned(url));
    }

    @Test
    @DisplayName("extractPublicIdIfOwned returns null for null or blank input")
    void extractPublicIdIfOwned_nullOrBlank() {
        assertNull(cloudinaryService.extractPublicIdIfOwned(null));
        assertNull(cloudinaryService.extractPublicIdIfOwned("   "));
    }

    @Test
    @DisplayName("deleteAsset destroys valid owned asset")
    void deleteAsset_validUrl_callsDestroy() throws Exception {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/v12345/restaurant/user-avatars/avatar-1-abc.png";
        when(uploader.destroy(eq("restaurant/user-avatars/avatar-1-abc"), anyMap())).thenReturn(Map.of("result", "ok"));

        cloudinaryService.deleteAsset(url);

        verify(uploader).destroy(eq("restaurant/user-avatars/avatar-1-abc"), anyMap());
    }

    @Test
    @DisplayName("deleteAsset skips unowned URL or legacy local path")
    void deleteAsset_unownedUrl_skipsDestroy() {
        cloudinaryService.deleteAsset("user-avatars/cascas-123.webp");
        cloudinaryService.deleteAsset("https://otherhost.com/image.jpg");
        cloudinaryService.deleteAsset(null);

        verifyNoInteractions(uploader);
    }

    @Test
    @DisplayName("deleteAsset handles SDK destroy exception gracefully without rethrowing")
    void deleteAsset_sdkException_doesNotThrow() throws Exception {
        String url = "https://res.cloudinary.com/test-cloud/image/upload/v12345/restaurant/user-avatars/avatar-1-abc.png";
        when(uploader.destroy(any(), anyMap())).thenThrow(new RuntimeException("Cloudinary API error"));

        assertDoesNotThrow(() -> cloudinaryService.deleteAsset(url));
    }
}
