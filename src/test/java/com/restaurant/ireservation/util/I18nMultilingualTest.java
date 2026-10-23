package com.restaurant.ireservation.util;

import com.restaurant.ireservation.controller.PaymentController;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.response.InvoiceItemResponseDTO;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class I18nMultilingualTest {

    private ResourceBundleMessageSource messageSource;
    private SpringTemplateEngine templateEngine;
    private InvoiceResponseDTO testInvoice;

    @BeforeEach
    void setUp() {
        messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);

        templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        templateEngine.setMessageSource(messageSource);

        new InvoicePdfGenerator(templateEngine);

        InvoiceItemResponseDTO item1 = InvoiceItemResponseDTO.builder()
                .itemName("Phở bò tái nạm đặc biệt")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(75000))
                .subtotal(BigDecimal.valueOf(150000))
                .build();

        testInvoice = InvoiceResponseDTO.builder()
                .restaurantName("Castorant")
                .restaurantAddress("123 Đường 3/2, Quận 10, TP.HCM")
                .restaurantPhone("1900 6868")
                .invoiceNo("INV-8-6")
                .paymentId(6L)
                .paymentStatus(PaymentStatus.PAID)
                .paymentDate(Instant.now())
                .paymentAmount(BigDecimal.valueOf(150000))
                .discountRate(10)
                .orderId(8L)
                .orderNo("ORD-22102026-008")
                .orderDate(Instant.now())
                .customerName("Phan Bảo Thắng")
                .customerPhone("0912345678")
                .customerEmail("thangpb@example.com")
                .items(List.of(item1))
                .orderTotalAmount(BigDecimal.valueOf(150000))
                .orderPaidAmount(BigDecimal.valueOf(150000))
                .orderRemainingAmount(BigDecimal.ZERO)
                .isFullyPaid(true)
                .remainingMessage("Fully Paid")
                .build();
    }

    // -------------------------------------------------------------
    // 1. Message Bundle Resolution Tests
    // -------------------------------------------------------------

    @Test
    @DisplayName("MessageSource resolves Vietnamese bundle correctly")
    void testMessageBundle_Vietnamese() {
        Locale vi = Locale.forLanguageTag("vi");

        assertThat(messageSource.getMessage("restaurant.brand.name", null, vi)).isEqualTo("Castorant");
        assertThat(messageSource.getMessage("email.invoice.title", null, vi)).isEqualTo("HÓA ĐƠN THANH TOÁN");
        assertThat(messageSource.getMessage("email.invoice.status.paid", null, vi)).isEqualTo("ĐÃ THANH TOÁN");
        assertThat(messageSource.getMessage("email.reservation.heading", null, vi)).isEqualTo("Đặt bàn thành công!");
        assertThat(messageSource.getMessage("email.reminder.heading", null, vi)).isEqualTo("Nhắc lịch đặt bàn");
    }

    @Test
    @DisplayName("MessageSource resolves English bundle correctly")
    void testMessageBundle_English() {
        Locale en = Locale.ENGLISH;

        assertThat(messageSource.getMessage("restaurant.brand.name", null, en)).isEqualTo("Castorant");
        assertThat(messageSource.getMessage("email.invoice.title", null, en)).isEqualTo("PAYMENT INVOICE");
        assertThat(messageSource.getMessage("email.invoice.status.paid", null, en)).isEqualTo("PAID");
        assertThat(messageSource.getMessage("email.reservation.heading", null, en)).isEqualTo("Reservation Confirmed!");
        assertThat(messageSource.getMessage("email.reminder.heading", null, en)).isEqualTo("Reservation Reminder");
    }

    @Test
    @DisplayName("MessageSource formats parameterized strings in both languages")
    void testMessageBundle_Parameters() {
        Locale vi = Locale.forLanguageTag("vi");
        Locale en = Locale.ENGLISH;

        String subjectVi = messageSource.getMessage("email.invoice.subject", new Object[]{"INV-001"}, vi);
        String subjectEn = messageSource.getMessage("email.invoice.subject", new Object[]{"INV-001"}, en);

        assertThat(subjectVi).isEqualTo("Castorant - Hóa đơn thanh toán #INV-001");
        assertThat(subjectEn).isEqualTo("Castorant - Payment Invoice #INV-001");

        String guestsVi = messageSource.getMessage("email.reservation.guests", new Object[]{4}, vi);
        String guestsEn = messageSource.getMessage("email.reservation.guests", new Object[]{4}, en);

        assertThat(guestsVi).isEqualTo("4 khách");
        assertThat(guestsEn).isEqualTo("4 guests");
    }

    // -------------------------------------------------------------
    // 2. Thymeleaf Template Rendering Tests
    // -------------------------------------------------------------

    @Test
    @DisplayName("Invoice HTML renders in Vietnamese with all localized text")
    void testInvoiceNotificationHtml_Vietnamese() {
        String html = InvoicePdfGenerator.buildHtml(
                testInvoice,
                "http://localhost:8080/api/v1/payment/6/invoice/download?lang=vi",
                false,
                "vi"
        );

        assertThat(html).contains("HÓA ĐƠN THANH TOÁN");
        assertThat(html).contains("ĐÃ THANH TOÁN");
        assertThat(html).contains("Mã đơn hàng:");
        assertThat(html).contains("Khách hàng:");
        assertThat(html).contains("Lưu lại hóa đơn (PDF)");
        assertThat(html).contains("Phan Bảo Thắng");
        assertThat(html).contains("INV-8-6");
    }

    @Test
    @DisplayName("Invoice HTML renders in English with all localized text")
    void testInvoiceNotificationHtml_English() {
        String html = InvoicePdfGenerator.buildHtml(
                testInvoice,
                "http://localhost:8080/api/v1/payment/6/invoice/download?lang=en",
                false,
                "en"
        );

        assertThat(html).contains("PAYMENT INVOICE");
        assertThat(html).contains("PAID");
        assertThat(html).contains("Order No:");
        assertThat(html).contains("Customer:");
        assertThat(html).contains("Download Invoice (PDF)");
        assertThat(html).contains("Phan Bảo Thắng");
        assertThat(html).contains("INV-8-6");
    }

    @Test
    @DisplayName("Reservation confirmation email template renders in Vietnamese")
    void testReservationNotificationHtml_Vietnamese() {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("brandName", "Castorant");
        context.setVariable("customerName", "Trần Văn B");
        context.setVariable("reservationCode", "RES-9988");
        context.setVariable("reservationTime", "19:00 - 25/10/2026");
        context.setVariable("guestCount", 2);

        String html = templateEngine.process("email/reservation-notification", context);

        assertThat(html).contains("Đặt bàn thành công!");
        assertThat(html).contains("CHI TIẾT ĐẶT BÀN");
        assertThat(html).contains("MÃ ĐẶT BÀN");
        assertThat(html).contains("RES-9988");
        assertThat(html).contains("Trần Văn B");
    }

    @Test
    @DisplayName("Reservation confirmation email template renders in English")
    void testReservationNotificationHtml_English() {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("brandName", "Castorant");
        context.setVariable("customerName", "John Doe");
        context.setVariable("reservationCode", "RES-9988");
        context.setVariable("reservationTime", "19:00 - 25/10/2026");
        context.setVariable("guestCount", 2);

        String html = templateEngine.process("email/reservation-notification", context);

        assertThat(html).contains("Reservation Confirmed!");
        assertThat(html).contains("RESERVATION DETAILS");
        assertThat(html).contains("RESERVATION CODE");
        assertThat(html).contains("RES-9988");
        assertThat(html).contains("John Doe");
    }

    @Test
    @DisplayName("Reservation reminder email template renders in Vietnamese")
    void testReminderNotificationHtml_Vietnamese() {
        Context context = new Context(Locale.forLanguageTag("vi"));
        context.setVariable("brandName", "Castorant");
        context.setVariable("customerName", "Lê Thị C");
        context.setVariable("reservationId", "RES-5544");
        context.setVariable("startTime", "18:30 - 23/10/2026");

        String html = templateEngine.process("email/reminder-notification", context);

        assertThat(html).contains("Nhắc lịch đặt bàn");
        assertThat(html).contains("RES-5544");
        assertThat(html).contains("Lê Thị C");
    }

    @Test
    @DisplayName("Reservation reminder email template renders in English")
    void testReminderNotificationHtml_English() {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("brandName", "Castorant");
        context.setVariable("customerName", "Alice Smith");
        context.setVariable("reservationId", "RES-5544");
        context.setVariable("startTime", "18:30 - 23/10/2026");

        String html = templateEngine.process("email/reminder-notification", context);

        assertThat(html).contains("Reservation Reminder");
        assertThat(html).contains("RES-5544");
        assertThat(html).contains("Alice Smith");
    }

    // -------------------------------------------------------------
    // 3. Invoice PDF Generation Tests
    // -------------------------------------------------------------

    @Test
    @DisplayName("generatePdf produces valid PDF byte array for Vietnamese")
    void testGeneratePdf_Vietnamese() {
        byte[] pdfBytes = InvoicePdfGenerator.generatePdf(testInvoice, "vi");
        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("generatePdf produces valid PDF byte array for English")
    void testGeneratePdf_English() {
        byte[] pdfBytes = InvoicePdfGenerator.generatePdf(testInvoice, "en");
        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("generatePdf defaults to English when language is null or blank")
    void testGeneratePdf_DefaultFallback() {
        byte[] pdfBytesNull = InvoicePdfGenerator.generatePdf(testInvoice, null);
        assertThat(pdfBytesNull).isNotNull();
        assertThat(new String(pdfBytesNull, 0, 5)).isEqualTo("%PDF-");

        byte[] pdfBytesBlank = InvoicePdfGenerator.generatePdf(testInvoice, "   ");
        assertThat(pdfBytesBlank).isNotNull();
        assertThat(new String(pdfBytesBlank, 0, 5)).isEqualTo("%PDF-");
    }

    // -------------------------------------------------------------
    // 4. Controller Language Resolution Tests
    // -------------------------------------------------------------

    @Test
    @DisplayName("resolveLanguage respects query param with highest priority")
    void testResolveLanguage_QueryParamPriority() {
        assertThat(PaymentController.resolveLanguage("en", "vi-VN,vi;q=0.9")).isEqualTo("en");
        assertThat(PaymentController.resolveLanguage("vi", "en-US,en;q=0.9")).isEqualTo("vi");
    }

    @Test
    @DisplayName("resolveLanguage falls back to Accept-Language header when query param is null")
    void testResolveLanguage_AcceptLanguageFallback() {
        assertThat(PaymentController.resolveLanguage(null, "en-US,en;q=0.9")).isEqualTo("en");
        assertThat(PaymentController.resolveLanguage(null, "en")).isEqualTo("en");
        assertThat(PaymentController.resolveLanguage(null, "vi-VN,vi;q=0.9")).isEqualTo("vi");
    }

    @Test
    @DisplayName("resolveLanguage defaults to en when both are null or empty")
    void testResolveLanguage_DefaultToEn() {
        assertThat(PaymentController.resolveLanguage(null, null)).isEqualTo("en");
        assertThat(PaymentController.resolveLanguage("", "")).isEqualTo("en");
        assertThat(PaymentController.resolveLanguage("fr", "de")).isEqualTo("en");
    }
}
