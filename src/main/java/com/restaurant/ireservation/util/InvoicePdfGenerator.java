package com.restaurant.ireservation.util;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.response.InvoiceItemResponseDTO;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import com.restaurant.ireservation.util.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class InvoicePdfGenerator {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DecimalFormat CURRENCY_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        CURRENCY_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private static SpringTemplateEngine templateEngine;

    public InvoicePdfGenerator(SpringTemplateEngine engine) {
        InvoicePdfGenerator.templateEngine = engine;
    }

    // Default constructor for cases when Spring does not inject (fallback in unit tests)
    public InvoicePdfGenerator() {
    }

    private static SpringTemplateEngine getTemplateEngine() {
        if (templateEngine != null) {
            return templateEngine;
        }
        synchronized (InvoicePdfGenerator.class) {
            if (templateEngine == null) {
                ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
                resolver.setPrefix("templates/");
                resolver.setSuffix(".html");
                resolver.setTemplateMode(TemplateMode.HTML);
                resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
                resolver.setCacheable(false);

                org.springframework.context.support.ResourceBundleMessageSource messageSource = new org.springframework.context.support.ResourceBundleMessageSource();
                messageSource.setBasename("messages");
                messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());

                SpringTemplateEngine engine = new SpringTemplateEngine();
                engine.setTemplateResolver(resolver);
                engine.setMessageSource(messageSource);
                templateEngine = engine;
            }
        }
        return templateEngine;
    }

    public static Locale resolveLocale(String language) {
        if (language == null || language.isBlank()) {
            return Locale.ENGLISH;
        }
        String clean = language.trim().toLowerCase();
        if (clean.startsWith("vi")) {
            return Locale.forLanguageTag("vi");
        }
        return Locale.ENGLISH;
    }

    public static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return CURRENCY_FORMAT.format(amount);
    }

    public static String formatDate(Instant instant) {
        if (instant == null) {
            return "";
        }
        return instant.atZone(VIETNAM_ZONE).format(DATE_FORMATTER);
    }

    public static String buildHtml(InvoiceResponseDTO invoice, String downloadUrl, boolean isPdfMode, String language) {
        Locale locale = resolveLocale(language);
        Context context = new Context(locale);
        context.setVariable("invoice", invoice);
        context.setVariable("downloadUrl", downloadUrl);
        context.setVariable("isPdfMode", isPdfMode);
        context.setVariable("formattedPaymentDate", formatDate(invoice.getPaymentDate()));
        context.setVariable("formattedOrderDate", formatDate(invoice.getOrderDate()));
        context.setVariable("formattedPaymentAmount", formatVnd(invoice.getPaymentAmount()));
        context.setVariable("formattedOrderTotal", formatVnd(invoice.getOrderTotalAmount()));
        context.setVariable("formattedOrderPaid", formatVnd(invoice.getOrderPaidAmount()));
        context.setVariable("formattedOrderRemaining", formatVnd(invoice.getOrderRemainingAmount()));

        BigDecimal itemsSubtotal = BigDecimal.ZERO;
        if (invoice.getItems() != null) {
            for (InvoiceItemResponseDTO item : invoice.getItems()) {
                if (item.getSubtotal() != null) {
                    itemsSubtotal = itemsSubtotal.add(item.getSubtotal());
                }
            }
        }
        context.setVariable("formattedItemsSubtotal", formatVnd(itemsSubtotal));

        return getTemplateEngine().process("email/invoice-notification", context);
    }

    public static String buildHtml(InvoiceResponseDTO invoice, String downloadUrl, boolean isPdfMode) {
        return buildHtml(invoice, downloadUrl, isPdfMode, "en");
    }

    public static String buildHtml(InvoiceResponseDTO invoice) {
        return buildHtml(invoice, null, true, "en");
    }

    public static byte[] generatePdf(InvoiceResponseDTO invoice, String language) {
        String html = buildHtml(invoice, null, true, language);
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

    public static byte[] generatePdf(InvoiceResponseDTO invoice) {
        return generatePdf(invoice, "en");
    }
}
