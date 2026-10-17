package com.restaurant.ireservation.util;

import com.restaurant.ireservation.config.VnPayConfig;
import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

public class VnPayUtil {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private VnPayUtil() {
    }

    public static String hmacSHA512(String key, String data) {
        try {
            if (key == null || data == null) {
                throw new IllegalArgumentException("Key and data must not be null for HMAC-SHA512");
            }
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes(StandardCharsets.US_ASCII);
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.US_ASCII);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA512", ex);
        }
    }

    public static String createPaymentUrl(VnPayConfig config, Long paymentId, String orderNo,
                                         BigDecimal amountInVnd, String clientIp) {
        Map<String, String> vnpParams = new TreeMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", config.getTmnCode());

        long amount = amountInVnd.multiply(BigDecimal.valueOf(100)).longValue();
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");

        String txnRef = paymentId + "T" + System.currentTimeMillis();
        vnpParams.put("vnp_TxnRef", txnRef);

        String sanitizedOrderNo = (orderNo != null && !orderNo.isBlank())
                ? orderNo.replaceAll("[^a-zA-Z0-9]", "")
                : String.valueOf(paymentId);
        String orderInfo = "Thanh toan don hang " + sanitizedOrderNo;
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", config.getReturnUrl());

        String ip = (clientIp != null && !clientIp.isBlank()) ? clientIp : "127.0.0.1";
        vnpParams.put("vnp_IpAddr", ip);

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        String createDate = now.format(DATE_FORMATTER);
        vnpParams.put("vnp_CreateDate", createDate);

        int expireMinutes = config.getExpireMinutes() > 0 ? config.getExpireMinutes() : 15;
        String expireDate = now.plusMinutes(expireMinutes).format(DATE_FORMATTER);
        vnpParams.put("vnp_ExpireDate", expireDate);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (Map.Entry<String, String> entry : vnpParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                if (!hashData.isEmpty()) {
                    hashData.append('&');
                    query.append('&');
                }
                hashData.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
                query.append(URLEncoder.encode(key, StandardCharsets.US_ASCII))
                        .append('=')
                        .append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
            }
        }

        String vnpSecureHash = hmacSHA512(config.getSecretKey(), hashData.toString());
        query.append("&vnp_SecureHash=").append(vnpSecureHash);

        return config.getPayUrl() + "?" + query;
    }

    public static boolean verifySignature(Map<String, String> fields, String secretKey) {
        if (fields == null || secretKey == null || secretKey.isBlank()) {
            return false;
        }
        String vnpSecureHash = fields.get("vnp_SecureHash");
        if (vnpSecureHash == null || vnpSecureHash.isBlank()) {
            return false;
        }

        Map<String, String> params = new TreeMap<>(fields);
        params.remove("vnp_SecureHash");
        params.remove("vnp_SecureHashType");

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value != null && !value.isEmpty()) {
                if (!hashData.isEmpty()) {
                    hashData.append('&');
                }
                hashData.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.US_ASCII));
            }
        }

        String calculatedHash = hmacSHA512(secretKey, hashData.toString());
        return calculatedHash.equalsIgnoreCase(vnpSecureHash);
    }

    public static Long extractPaymentId(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return null;
        }
        try {
            if (txnRef.contains("T")) {
                return Long.parseLong(txnRef.split("T")[0]);
            }
            if (txnRef.contains("_")) {
                return Long.parseLong(txnRef.split("_")[0]);
            }
            return Long.parseLong(txnRef);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        if ("0:0:0:0:0:0:0:1".equals(ipAddress)) {
            ipAddress = "127.0.0.1";
        }
        return ipAddress != null && !ipAddress.isBlank() ? ipAddress : "127.0.0.1";
    }
}
