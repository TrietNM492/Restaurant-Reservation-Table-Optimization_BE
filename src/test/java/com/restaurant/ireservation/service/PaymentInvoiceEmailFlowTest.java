package com.restaurant.ireservation.service;

import com.restaurant.ireservation.config.VnPayConfig;
import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.Payment;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.response.InvoiceItemResponseDTO;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import com.restaurant.ireservation.repository.PaymentRepository;
import com.restaurant.ireservation.util.InvoicePdfGenerator;
import com.restaurant.ireservation.util.VnPayUtil;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentInvoiceEmailFlowTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @Mock
    VnPayConfig vnPayConfig;

    @Mock
    MailService mailService;

    @Mock
    JavaMailSender javaMailSender;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
                paymentRepository,
                orderRepository,
                orderItemRepository,
                vnPayConfig,
                mailService
        );
    }

    @Test
    @DisplayName("1. Invoice HTML rendering and PDF byte generation using Thymeleaf template")
    void testInvoicePdfAndHtmlGeneration() {
        InvoiceItemResponseDTO item1 = InvoiceItemResponseDTO.builder()
                .itemName("Bò Wagyu Nướng Đá")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(250000))
                .subtotal(BigDecimal.valueOf(500000))
                .build();

        InvoiceResponseDTO invoice = InvoiceResponseDTO.builder()
                .restaurantName("Castorant Restaurant")
                .restaurantAddress("123 Hai Bà Trưng, Quận 1, TP.HCM")
                .restaurantPhone("1900 6868")
                .invoiceNo("INV-100-1")
                .paymentId(1L)
                .paymentStatus(PaymentStatus.PAID)
                .paymentDate(Instant.now())
                .paymentAmount(BigDecimal.valueOf(500000))
                .discountRate(10)
                .orderId(100L)
                .orderNo("ORD-21102026-001")
                .orderDate(Instant.now())
                .customerName("Phan Bảo Thắng")
                .customerPhone("0912345678")
                .customerEmail("thangpb.ce191554@gmail.com")
                .items(List.of(item1))
                .orderTotalAmount(BigDecimal.valueOf(450000))
                .orderPaidAmount(BigDecimal.valueOf(500000))
                .orderRemainingAmount(BigDecimal.ZERO)
                .isFullyPaid(true)
                .remainingMessage("Fully Paid")
                .build();

        // Verify HTML generation contains Thymeleaf formatted data
        String downloadUrl = "http://localhost:8080/api/v1/payment/1/invoice/download";
        String html = InvoicePdfGenerator.buildHtml(invoice, downloadUrl, false);

        assertThat(html).isNotNull();
        assertThat(html).contains("Castorant Restaurant");
        assertThat(html).contains("INV-100-1");
        assertThat(html).contains("Phan Bảo Thắng");
        assertThat(html).contains("Bò Wagyu Nướng Đá");
        assertThat(html).contains("Download Invoice (PDF)");
        assertThat(html).contains(downloadUrl);

        // Verify PDF generation produces valid PDF header
        byte[] pdfBytes = InvoicePdfGenerator.generatePdf(invoice);
        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("2. Successful VNPay IPN automatically triggers invoice email to customer")
    void testProcessIpnSuccess_TriggersInvoiceEmail() {
        String secretKey = "TEST_SECRET_KEY";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);
        when(vnPayConfig.getReturnUrl()).thenReturn("http://localhost:8080/api/v1/payment/vnpay-return");

        User user = User.builder()
                .username("thangpb")
                .fullname("Phan Bảo Thắng")
                .email("thangpb.ce191554@gmail.com")
                .phone("0912345678")
                .build();

        CustomerInformation customerInfo = CustomerInformation.builder()
                .id(10L)
                .user(user)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-001")
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .totalAmount(100000.0)
                .customerInformation(customerInfo)
                .build();
        order.setId(1L);

        Payment payment = Payment.builder()
                .order(order)
                .totalFinalPrice(100000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(10L);

        when(paymentRepository.findByIdWithLock(10L)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithLock(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.findWithOrderById(10L)).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(1L)).thenReturn(Collections.emptyList());

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_TxnRef", "10T1729000000");
        params.put("vnp_Amount", "10000000"); // 100,000 * 100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");

        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!hashData.isEmpty()) {
                hashData.append('&');
            }
            hashData.append(entry.getKey()).append('=').append(java.net.URLEncoder.encode(entry.getValue(), java.nio.charset.StandardCharsets.US_ASCII));
        }
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, hashData.toString()));

        // Execute IPN
        paymentService.processIpn(params);

        // Verify payment marked as PAID
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

        // Verify invoice email was triggered
        ArgumentCaptor<InvoiceResponseDTO> invoiceCaptor = ArgumentCaptor.forClass(InvoiceResponseDTO.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService, times(1)).sendInvoiceEmail(invoiceCaptor.capture(), urlCaptor.capture(), eq(null), eq("en"));

        InvoiceResponseDTO capturedInvoice = invoiceCaptor.getValue();
        assertThat(capturedInvoice.getPaymentId()).isEqualTo(10L);
        assertThat(capturedInvoice.getCustomerEmail()).isEqualTo("thangpb.ce191554@gmail.com");
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080/api/v1/payment/10/invoice/download?lang=en");
    }

    @Test
    @DisplayName("3. Manual send invoice triggers MailService with custom recipient email")
    void testSendInvoiceEmailManually() {
        when(vnPayConfig.getReturnUrl()).thenReturn("http://localhost:8080/api/v1/payment/vnpay-return");

        User user = User.builder()
                .username("thangpb")
                .fullname("Phan Bảo Thắng")
                .email("thangpb.ce191554@gmail.com")
                .phone("0912345678")
                .build();

        CustomerInformation customerInfo = CustomerInformation.builder()
                .id(10L)
                .user(user)
                .build();

        Order order = Order.builder()
                .orderNo("ORD-001")
                .orderStatus(OrderStatus.OK)
                .totalAmount(100000.0)
                .customerInformation(customerInfo)
                .build();
        order.setId(1L);

        Payment payment = Payment.builder()
                .order(order)
                .totalFinalPrice(100000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        payment.setId(20L);

        when(paymentRepository.findWithOrderById(20L)).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(1L)).thenReturn(Collections.emptyList());

        paymentService.sendInvoiceEmailManually(20L, "thangpb", Collections.emptyList(), "custom-destination@gmail.com", "en");

        ArgumentCaptor<InvoiceResponseDTO> invoiceCaptor = ArgumentCaptor.forClass(InvoiceResponseDTO.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> emailCaptor = ArgumentCaptor.forClass(String.class);

        verify(mailService, times(1)).sendInvoiceEmail(invoiceCaptor.capture(), urlCaptor.capture(), emailCaptor.capture(), eq("en"));
        assertThat(emailCaptor.getValue()).isEqualTo("custom-destination@gmail.com");
        assertThat(urlCaptor.getValue()).isEqualTo("http://localhost:8080/api/v1/payment/20/invoice/download?lang=en");
    }

    @Test
    @DisplayName("4. Public invoice PDF generation creates valid PDF byte array")
    void testGenerateInvoicePdfPublic() {
        Order order = Order.builder()
                .orderNo("ORD-999")
                .orderStatus(OrderStatus.OK)
                .totalAmount(50000.0)
                .build();
        order.setId(99L);

        Payment payment = Payment.builder()
                .order(order)
                .totalFinalPrice(50000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        payment.setId(999L);

        when(paymentRepository.findWithOrderById(999L)).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(99L)).thenReturn(Collections.emptyList());

        byte[] pdfBytes = paymentService.generateInvoicePdfPublic(999L);
        assertThat(pdfBytes).isNotNull();
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertThat(header).isEqualTo("%PDF-");
    }
}
