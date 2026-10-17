package com.restaurant.ireservation.util;

import com.restaurant.ireservation.config.VnPayConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnPayUtilTest {

    @Test
    @DisplayName("HMAC-SHA512 generates deterministic hex hash")
    void testHmacSHA512() {
        String key = "testSecretKey";
        String data = "vnp_Amount=1000000&vnp_Command=pay";
        String hash1 = VnPayUtil.hmacSHA512(key, data);
        String hash2 = VnPayUtil.hmacSHA512(key, data);

        assertThat(hash1).isNotNull().isNotEmpty().isEqualTo(hash2);
        assertThat(hash1.length()).isEqualTo(128); // SHA-512 hex is 128 chars
    }

    @Test
    @DisplayName("HMAC-SHA512 throws exception on null arguments")
    void testHmacSHA512_NullArgs() {
        assertThatThrownBy(() -> VnPayUtil.hmacSHA512(null, "data"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("createPaymentUrl includes required VNPay 2.1.0 parameters and hash")
    void testCreatePaymentUrl() {
        VnPayConfig config = new VnPayConfig();
        ReflectionTestUtils.setField(config, "tmnCode", "TESTCODE");
        ReflectionTestUtils.setField(config, "secretKey", "SECRET123456789");
        ReflectionTestUtils.setField(config, "payUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(config, "returnUrl", "http://localhost:8080/api/v1/payment/vnpay-return");
        ReflectionTestUtils.setField(config, "expireMinutes", 15);

        String url = VnPayUtil.createPaymentUrl(
                config,
                100L,
                "ORD-16102026-001",
                BigDecimal.valueOf(50000),
                "127.0.0.1"
        );

        assertThat(url).startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(url).contains("vnp_Version=2.1.0");
        assertThat(url).contains("vnp_Command=pay");
        assertThat(url).contains("vnp_TmnCode=TESTCODE");
        assertThat(url).contains("vnp_Amount=5000000"); // 50000 * 100
        assertThat(url).contains("vnp_CurrCode=VND");
        assertThat(url).contains("vnp_TxnRef=100T");
        assertThat(url).contains("vnp_SecureHash=");
    }

    @Test
    @DisplayName("verifySignature returns true for valid hash, false for tampered data")
    void testVerifySignature() {
        String secretKey = "MY_SECRET_KEY";
        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Amount", "1000000");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", "TESTCODE");
        fields.put("vnp_TxnRef", "123_456789");

        // Generate valid hash
        String dataToHash = "vnp_Amount=1000000&vnp_Command=pay&vnp_TmnCode=TESTCODE&vnp_TxnRef=123_456789";
        String validHash = VnPayUtil.hmacSHA512(secretKey, dataToHash);
        fields.put("vnp_SecureHash", validHash);

        assertThat(VnPayUtil.verifySignature(fields, secretKey)).isTrue();

        // Tamper with amount
        fields.put("vnp_Amount", "2000000");
        assertThat(VnPayUtil.verifySignature(fields, secretKey)).isFalse();
    }

    @Test
    @DisplayName("extractPaymentId correctly parses ID from txnRef")
    void testExtractPaymentId() {
        assertThat(VnPayUtil.extractPaymentId("456T1729000000")).isEqualTo(456L);
        assertThat(VnPayUtil.extractPaymentId("456_1729000000")).isEqualTo(456L);
        assertThat(VnPayUtil.extractPaymentId("789")).isEqualTo(789L);
        assertThat(VnPayUtil.extractPaymentId("invalid")).isNull();
        assertThat(VnPayUtil.extractPaymentId(null)).isNull();
    }
}
