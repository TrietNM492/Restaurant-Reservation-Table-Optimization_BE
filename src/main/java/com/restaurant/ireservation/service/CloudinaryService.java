package com.restaurant.ireservation.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.restaurant.ireservation.error.FileUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Slf4j
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final int timeoutMs;

    public CloudinaryService(
            Cloudinary cloudinary,
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret,
            @Value("${cloudinary.folder:restaurant/user-avatars}") String folder,
            @Value("${cloudinary.timeout-ms:10000}") int timeoutMs) {
        this.cloudinary = cloudinary;
        this.cloudName = cloudName != null ? cloudName.trim() : "";
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.apiSecret = apiSecret != null ? apiSecret.trim() : "";
        this.folder = sanitizeFolder(folder);
        this.timeoutMs = timeoutMs;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(cloudName)
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(apiSecret);
    }

    public String uploadAvatar(byte[] fileBytes, Long userId, String contentType) {
        if (!isConfigured()) {
            throw new FileUploadException("Cloudinary storage is not configured. Missing required credentials.");
        }

        String publicIdSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String publicIdName = (userId != null)
                ? "avatar-" + userId + "-" + publicIdSuffix
                : "avatar-" + publicIdSuffix;

        Map<String, Object> params = new HashMap<>();
        params.put("folder", folder);
        params.put("public_id", publicIdName);
        params.put("overwrite", false);
        params.put("resource_type", "image");

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(fileBytes, params);
            String secureUrl = (String) uploadResult.get("secure_url");
            if (!StringUtils.hasText(secureUrl)) {
                throw new FileUploadException("Cloudinary upload did not return a secure URL");
            }
            return secureUrl;
        } catch (IOException e) {
            log.error("IO error uploading avatar to Cloudinary: {}", e.getMessage());
            throw new FileUploadException("Failed to upload image to Cloudinary: " + e.getMessage(), e);
        } catch (FileUploadException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error uploading avatar to Cloudinary: {}", e.getMessage());
            throw new FileUploadException("Unexpected error during image upload: " + e.getMessage(), e);
        }
    }

    public void deleteAsset(String assetUrlOrPublicId) {
        if (!StringUtils.hasText(assetUrlOrPublicId)) {
            return;
        }

        String publicId = extractPublicIdIfOwned(assetUrlOrPublicId);
        if (publicId == null) {
            if (StringUtils.hasText(folder) && assetUrlOrPublicId.startsWith(folder + "/")) {
                publicId = assetUrlOrPublicId;
            } else {
                log.info("Skipping deletion for unowned or legacy asset: {}", sanitizeLog(assetUrlOrPublicId));
                return;
            }
        }

        if (!isConfigured()) {
            log.warn("Cloudinary is not configured; skipping asset deletion for: {}", publicId);
            return;
        }

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary destroy called for [{}], result: {}", publicId, result);
        } catch (Exception e) {
            log.warn("Failed to destroy asset [{}] on Cloudinary: {}", publicId, e.getMessage());
        }
    }

    public String extractPublicIdIfOwned(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl) || !StringUtils.hasText(cloudName) || !StringUtils.hasText(folder)) {
            return null;
        }

        try {
            URI uri = URI.create(avatarUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                return null;
            }
            if (!"res.cloudinary.com".equalsIgnoreCase(uri.getHost())) {
                return null;
            }

            String path = uri.getPath();
            if (path == null) {
                return null;
            }

            String[] rawSegments = path.split("/");
            List<String> segments = new ArrayList<>();
            for (String s : rawSegments) {
                if (!s.isEmpty()) {
                    segments.add(s);
                }
            }

            // Path must at least contain: <cloudName>/image/upload/<folder>/<publicId>
            if (segments.size() < 4) {
                return null;
            }

            // Segment 0 must match configured cloudName
            if (!cloudName.equals(segments.get(0))) {
                return null;
            }

            // Segment 1 must be "image", Segment 2 must be "upload"
            if (!"image".equals(segments.get(1)) || !"upload".equals(segments.get(2))) {
                return null;
            }

            // Find the start of the configured folder path in the segments
            String[] folderParts = folder.split("/");
            int folderStartIndex = -1;
            for (int i = 3; i <= segments.size() - folderParts.length; i++) {
                boolean match = true;
                for (int j = 0; j < folderParts.length; j++) {
                    if (!segments.get(i + j).equals(folderParts[j])) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    folderStartIndex = i;
                    break;
                }
            }

            if (folderStartIndex == -1) {
                return null;
            }

            List<String> remainderSegments = segments.subList(folderStartIndex, segments.size());
            String remainder = String.join("/", remainderSegments);

            // Strip extension if present
            int dotIndex = remainder.lastIndexOf('.');
            return dotIndex > 0 ? remainder.substring(0, dotIndex) : remainder;
        } catch (Exception e) {
            log.warn("Unable to safely parse avatar URL for Cloudinary deletion: [{}]", sanitizeLog(avatarUrl));
            return null;
        }
    }

    private String sanitizeFolder(String folderPath) {
        if (!StringUtils.hasText(folderPath)) {
            return "restaurant/user-avatars";
        }
        return folderPath.trim().replaceAll("^/+|/+$", "");
    }

    private String sanitizeLog(String input) {
        if (input == null) return null;
        return input.replaceAll("[\r\n]", "");
    }
}
