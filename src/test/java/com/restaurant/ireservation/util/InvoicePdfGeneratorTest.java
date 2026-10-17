package com.restaurant.ireservation.util;

import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.response.InvoiceItemResponseDTO;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfGeneratorTest {

    @Test
    @DisplayName("generatePdf produces valid PDF byte array with UTF-8 Vietnamese characters")
    void testGeneratePdf_Success() {
        InvoiceItemResponseDTO item1 = InvoiceItemResponseDTO.builder()
                .itemName("Phở bò tái nạm đặc biệt")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(75000))
                .subtotal(BigDecimal.valueOf(150000))
                .build();

        InvoiceItemResponseDTO item2 = InvoiceItemResponseDTO.builder()
                .itemName("Trà đào cam sả")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(35000))
                .subtotal(BigDecimal.valueOf(70000))
                .build();

        InvoiceResponseDTO invoice = InvoiceResponseDTO.builder()
                .restaurantName("Castorant")
                .restaurantAddress("123 Đường 3/2, Quận 10, TP.HCM")
                .restaurantPhone("1900 6868")
                .invoiceNo("INV-5-2")
                .paymentId(2L)
                .paymentStatus(PaymentStatus.PAID)
                .paymentDate(Instant.now())
                .paymentAmount(BigDecimal.valueOf(220000))
                .discountRate(0)
                .orderId(5L)
                .orderNo("ORD-16102026-005")
                .orderDate(Instant.now())
                .customerName("Nguyễn Văn A")
                .customerPhone("0987654321")
                .customerEmail("nguyenvana@gmail.com")
                .items(List.of(item1, item2))
                .orderTotalAmount(BigDecimal.valueOf(220000))
                .orderPaidAmount(BigDecimal.valueOf(220000))
                .orderRemainingAmount(BigDecimal.ZERO)
                .isFullyPaid(true)
                .remainingMessage("Fully Paid")
                .build();

        byte[] pdfBytes = InvoicePdfGenerator.generatePdf(invoice);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);

        // Verify PDF Magic Bytes (%PDF-)
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertThat(header).isEqualTo("%PDF-");
    }
}
