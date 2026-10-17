package com.restaurant.ireservation.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.response.InvoiceItemResponseDTO;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
public final class InvoicePdfGenerator {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DecimalFormat CURRENCY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        CURRENCY_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private InvoicePdfGenerator() {
    }

    public static byte[] generatePdf(InvoiceResponseDTO invoice) {
        String html = buildHtml(invoice);
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();

            // Register Unicode TrueType Font for character support
            boolean fontLoaded = false;
            try (InputStream is = InvoicePdfGenerator.class.getResourceAsStream("/fonts/arial.ttf")) {
                if (is != null) {
                    byte[] fontBytes = is.readAllBytes();
                    builder.useFont(() -> new ByteArrayInputStream(fontBytes), "Arial");
                    fontLoaded = true;
                }
            } catch (Exception e) {
                log.warn("Could not load /fonts/arial.ttf from classpath: {}", e.getMessage());
            }

            if (!fontLoaded) {
                log.warn("Custom font /fonts/arial.ttf not found in classpath. Fallback to default system renderer font.");
            }

            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF invoice", e);
            throw new IllegalStateException("Failed to generate PDF invoice: " + e.getMessage(), e);
        }
    }

    public static String buildHtml(InvoiceResponseDTO invoice) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"en\">\n");
        sb.append("<head>\n");
        sb.append("  <meta charset=\"UTF-8\"/>\n");
        sb.append("  <title>Payment Invoice</title>\n");
        sb.append("  <style>\n");
        sb.append("    @page { size: A4 portrait; margin: 15mm; }\n");
        sb.append("    body { font-family: 'Arial', sans-serif; color: #222; font-size: 13px; line-height: 1.4; }\n");
        sb.append("    .receipt-container { width: 100%; max-width: 700px; margin: 0 auto; }\n");
        sb.append("    .restaurant-title { text-align: center; font-size: 24px; font-weight: bold; margin-bottom: 4px; color: #111; }\n");
        sb.append("    .restaurant-info { text-align: center; font-size: 12px; color: #666; margin-bottom: 2px; }\n");
        sb.append("    .dashed-divider { border-top: 1px dashed #999; margin: 12px 0; }\n");
        sb.append("    .invoice-title { text-align: center; font-size: 18px; font-weight: bold; text-transform: uppercase; margin: 8px 0; color: #2c3e50; }\n");
        sb.append("    .info-table { width: 100%; border-collapse: collapse; margin-bottom: 12px; }\n");
        sb.append("    .info-table td { padding: 4px 6px; font-size: 12px; vertical-align: top; }\n");
        sb.append("    .info-label { font-weight: bold; width: 22%; color: #444; }\n");
        sb.append("    .badge { display: inline-block; padding: 2px 8px; font-size: 11px; font-weight: bold; border-radius: 3px; }\n");
        sb.append("    .badge-paid { background-color: #e8f8f5; color: #27ae60; border: 1px solid #27ae60; }\n");
        sb.append("    .badge-partial { background-color: #fef9e7; color: #f39c12; border: 1px solid #f39c12; }\n");
        sb.append("    .items-table { width: 100%; border-collapse: collapse; margin-top: 8px; margin-bottom: 12px; }\n");
        sb.append("    .items-table th { background-color: #f2f4f4; border: 1px solid #bdc3c7; padding: 6px; font-size: 12px; font-weight: bold; text-align: left; }\n");
        sb.append("    .items-table td { border: 1px solid #e5e8e8; padding: 6px; font-size: 12px; }\n");
        sb.append("    .text-center { text-align: center; }\n");
        sb.append("    .text-right { text-align: right; }\n");
        sb.append("    .summary-table { width: 100%; border-collapse: collapse; margin-top: 8px; }\n");
        sb.append("    .summary-table td { padding: 4px 6px; font-size: 12px; }\n");
        sb.append("    .summary-label { text-align: right; font-weight: bold; width: 70%; color: #333; }\n");
        sb.append("    .summary-value { text-align: right; width: 30%; font-weight: bold; }\n");
        sb.append("    .highlight-paid { color: #27ae60; font-size: 14px; }\n");
        sb.append("    .highlight-remaining { color: #c0392b; font-size: 14px; }\n");
        sb.append("    .footer { text-align: center; margin-top: 25px; font-size: 12px; color: #7f8c8d; font-style: italic; }\n");
        sb.append("  </style>\n");
        sb.append("</head>\n");
        sb.append("<body>\n");
        sb.append("<div class=\"receipt-container\">\n");

        // Header: Restaurant Name
        String restaurantName = (invoice.getRestaurantName() != null && !invoice.getRestaurantName().isBlank())
                ? invoice.getRestaurantName() : "Castorant";
        sb.append("  <div class=\"restaurant-title\">").append(escapeXml(restaurantName)).append("</div>\n");

        if (invoice.getRestaurantAddress() != null && !invoice.getRestaurantAddress().isBlank()) {
            sb.append("  <div class=\"restaurant-info\">Address: ").append(escapeXml(invoice.getRestaurantAddress())).append("</div>\n");
        }
        if (invoice.getRestaurantPhone() != null && !invoice.getRestaurantPhone().isBlank()) {
            sb.append("  <div class=\"restaurant-info\">Hotline: ").append(escapeXml(invoice.getRestaurantPhone())).append("</div>\n");
        }

        sb.append("  <div class=\"dashed-divider\"></div>\n");
        sb.append("  <div class=\"invoice-title\">PAYMENT INVOICE</div>\n");

        // Info Table
        sb.append("  <table class=\"info-table\">\n");
        sb.append("    <tr>\n");
        sb.append("      <td class=\"info-label\">Invoice No:</td>\n");
        sb.append("      <td>").append(escapeXml(invoice.getInvoiceNo())).append("</td>\n");
        sb.append("      <td class=\"info-label\">Order No:</td>\n");
        sb.append("      <td>").append(escapeXml(invoice.getOrderNo())).append("</td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td class=\"info-label\">Payment ID:</td>\n");
        sb.append("      <td>#").append(invoice.getPaymentId()).append("</td>\n");
        sb.append("      <td class=\"info-label\">Date:</td>\n");
        sb.append("      <td>").append(formatInstant(invoice.getPaymentDate())).append("</td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td class=\"info-label\">Customer:</td>\n");
        sb.append("      <td>").append(escapeXml(invoice.getCustomerName() != null ? invoice.getCustomerName() : "Walk-in Guest")).append("</td>\n");
        sb.append("      <td class=\"info-label\">Status:</td>\n");
        sb.append("      <td>\n");
        if (invoice.getPaymentStatus() == PaymentStatus.PAID) {
            sb.append("        <span class=\"badge badge-paid\">PAID</span>\n");
        } else if (invoice.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
            sb.append("        <span class=\"badge badge-partial\">PARTIALLY PAID</span>\n");
        } else {
            sb.append("        <span>").append(invoice.getPaymentStatus()).append("</span>\n");
        }
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        if (invoice.getCustomerPhone() != null && !invoice.getCustomerPhone().isBlank()) {
            sb.append("    <tr>\n");
            sb.append("      <td class=\"info-label\">Phone:</td>\n");
            sb.append("      <td colspan=\"3\">").append(escapeXml(invoice.getCustomerPhone())).append("</td>\n");
            sb.append("    </tr>\n");
        }
        sb.append("  </table>\n");

        // Items Table
        sb.append("  <table class=\"items-table\">\n");
        sb.append("    <thead>\n");
        sb.append("      <tr>\n");
        sb.append("        <th class=\"text-center\" style=\"width: 8%;\">No.</th>\n");
        sb.append("        <th style=\"width: 47%;\">Item Name</th>\n");
        sb.append("        <th class=\"text-center\" style=\"width: 12%;\">Qty</th>\n");
        sb.append("        <th class=\"text-right\" style=\"width: 15%;\">Unit Price</th>\n");
        sb.append("        <th class=\"text-right\" style=\"width: 18%;\">Subtotal</th>\n");
        sb.append("      </tr>\n");
        sb.append("    </thead>\n");
        sb.append("    <tbody>\n");

        if (invoice.getItems() != null && !invoice.getItems().isEmpty()) {
            int index = 1;
            for (InvoiceItemResponseDTO item : invoice.getItems()) {
                sb.append("      <tr>\n");
                sb.append("        <td class=\"text-center\">").append(index++).append("</td>\n");
                sb.append("        <td>").append(escapeXml(item.getItemName())).append("</td>\n");
                sb.append("        <td class=\"text-center\">").append(item.getQuantity()).append("</td>\n");
                sb.append("        <td class=\"text-right\">").append(formatMoney(item.getUnitPrice())).append("</td>\n");
                sb.append("        <td class=\"text-right\">").append(formatMoney(item.getSubtotal())).append("</td>\n");
                sb.append("      </tr>\n");
            }
        } else {
            sb.append("      <tr>\n");
            sb.append("        <td colspan=\"5\" class=\"text-center\">Order service payment</td>\n");
            sb.append("      </tr>\n");
        }
        sb.append("    </tbody>\n");
        sb.append("  </table>\n");

        // Financial Summary
        sb.append("  <table class=\"summary-table\">\n");
        sb.append("    <tr>\n");
        sb.append("      <td class=\"summary-label\">Order Total:</td>\n");
        sb.append("      <td class=\"summary-value\">").append(formatMoney(invoice.getOrderTotalAmount())).append(" VND</td>\n");
        sb.append("    </tr>\n");

        if (invoice.getDiscountRate() > 0) {
            sb.append("    <tr>\n");
            sb.append("      <td class=\"summary-label\">Discount:</td>\n");
            sb.append("      <td class=\"summary-value\">").append(invoice.getDiscountRate()).append("%</td>\n");
            sb.append("    </tr>\n");
        }

        sb.append("    <tr>\n");
        sb.append("      <td class=\"summary-label\">Payment Amount:</td>\n");
        sb.append("      <td class=\"summary-value\">").append(formatMoney(invoice.getPaymentAmount())).append(" VND</td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td class=\"summary-label\">Total Paid:</td>\n");
        sb.append("      <td class=\"summary-value\">").append(formatMoney(invoice.getOrderPaidAmount())).append(" VND</td>\n");
        sb.append("    </tr>\n");
        sb.append("    <tr>\n");
        sb.append("      <td class=\"summary-label\">Remaining Balance:</td>\n");
        sb.append("      <td class=\"summary-value\">\n");
        if (invoice.isFullyPaid()) {
            sb.append("        <span class=\"highlight-paid\">Fully Paid</span>\n");
        } else {
            sb.append("        <span class=\"highlight-remaining\">").append(formatMoney(invoice.getOrderRemainingAmount())).append(" VND</span>\n");
        }
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
        sb.append("  </table>\n");

        sb.append("  <div class=\"dashed-divider\"></div>\n");
        sb.append("  <div class=\"footer\">\n");
        sb.append("    <p>Thank you for dining with us!</p>\n");
        sb.append("  </div>\n");

        sb.append("</div>\n");
        sb.append("</body>\n");
        sb.append("</html>\n");

        return sb.toString();
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return CURRENCY_FORMAT.format(amount);
    }

    private static String formatInstant(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.atZone(VIETNAM_ZONE).format(DATE_FORMATTER);
    }

    private static String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
