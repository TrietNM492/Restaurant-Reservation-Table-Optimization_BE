package com.restaurant.ireservation.config;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VnPayConfig {

    @Value("${vnpay.tmn-code:}")
    String tmnCode;

    @Value("${vnpay.secret-key:}")
    String secretKey;

    @Value("${vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    String payUrl;

    @Value("${vnpay.return-url:http://localhost:8080/api/v1/payment/vnpay-return}")
    String returnUrl;

    @Value("${vnpay.expire-minutes:15}")
    int expireMinutes;

    public boolean isConfigured() {
        return tmnCode != null && !tmnCode.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
