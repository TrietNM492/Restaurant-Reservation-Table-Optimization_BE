package com.restaurant.ireservation.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class OtpRateLimitService {

    private final RedisRateLimiter rateLimiter;
    private final int emailMaxAttempts;
    private final long emailWindowSeconds;
    private final int ipMaxAttempts;
    private final long ipWindowSeconds;

    public OtpRateLimitService(
            RedisRateLimiter rateLimiter,
            @Value("${app.mail.rate-limit.otp.email.max-attempts:3}")
            int emailMaxAttempts,
            @Value("${app.mail.rate-limit.otp.email.window-seconds:900}")
            long emailWindowSeconds,
            @Value("${app.mail.rate-limit.otp.ip.max-attempts:10}")
            int ipMaxAttempts,
            @Value("${app.mail.rate-limit.otp.ip.window-seconds:900}")
            long ipWindowSeconds
    ) {
        this.rateLimiter = rateLimiter;
        this.emailMaxAttempts = emailMaxAttempts;
        this.emailWindowSeconds = emailWindowSeconds;
        this.ipMaxAttempts = ipMaxAttempts;
        this.ipWindowSeconds = ipWindowSeconds;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", e
            );
        }
    }

    public boolean isAllowed(String email, String clientIp) {
        String normalizedEmail = email.trim().toUpperCase(Locale.ROOT);

        String hashEmail = sha256(normalizedEmail);
        boolean ipAllowed = rateLimiter.isAllowed(
                "rate-limit:otp:ip:" + clientIp,
                ipMaxAttempts,
                ipWindowSeconds
        );

        boolean emailAllowed = rateLimiter.isAllowed(
                "rate-limit:otp:email:" + hashEmail,
                emailMaxAttempts,
                emailWindowSeconds
        );

        return ipAllowed && emailAllowed;

    }

}
