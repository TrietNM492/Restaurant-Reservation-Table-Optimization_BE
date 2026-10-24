package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Notification;
import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.dto.response.UserResonseDTO;
import com.restaurant.ireservation.util.constants.Constants;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class MailService {

    private final JavaMailSender javaMailSender;
    private final MessageSource messageSource;
    private final SpringTemplateEngine springTemplateEngine;
    private static final Logger LOGGER = LoggerFactory.getLogger(MailService.class);
    private static final String USER = "user";
    private static final String BASE_URL = "baseUrl";

    private final String fromName;
    private final String from;

    private final int rate_limit_email_max_attempt;
    private final int rate_limit_email_window_seconds;
    private final int rate_limit_ip_max_attempt;
    private final int rate_limit_ip_window_seconds;

    private final RedisRateLimiter limiter;

    public MailService(JavaMailSender javaMailSender,
                       MessageSource messageSource,
                       SpringTemplateEngine springTemplateEngine,
                       @Value("${app.mail.from}") String from,
                       @Value("${app.mail.from-name}") String fromName,
                       @Value("${app.mail.rate-limit.otp.email.max-attempts}") int rateLimitEmailMaxAttempt,
                       @Value("${app.mail.rate-limit.otp.email.window-seconds}") int rateLimitEmailWindowSeconds,
                       @Value("${app.mail.rate-limit.otp.ip.max-attempts}") int rateLimitIpMaxAttempt,
                       @Value("${app.mail.rate-limit.otp.ip.window-seconds}") int rateLimitIpWindowSeconds,
                       RedisRateLimiter limiter) {
        this.javaMailSender = javaMailSender;
        this.messageSource = messageSource;
        this.springTemplateEngine = springTemplateEngine;
        this.from = from;
        this.fromName = fromName;
        rate_limit_email_max_attempt = rateLimitEmailMaxAttempt;
        rate_limit_email_window_seconds = rateLimitEmailWindowSeconds;
        rate_limit_ip_max_attempt = rateLimitIpMaxAttempt;
        rate_limit_ip_window_seconds = rateLimitIpWindowSeconds;
        this.limiter = limiter;
    }

    @Value("${app.mail.base-url}")
    private static final String URL = "";

    public void sendEmail(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        LOGGER.debug("Send email[multipart '{}' and html '{}'] to '{}' with subject '{}' and content={}",
                isMultipart,
                isHtml,
                to,
                subject,
                content);

        MimeMessage message = javaMailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, isMultipart, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(content, isHtml);
            javaMailSender.send(message);
            LOGGER.debug("Sent email to User '{}'", to);

        } catch (MailException | MessagingException e) {
            LOGGER.warn("Email could not be sent to user '{}'", to, e);
            throw new RuntimeException(e);
        }
    }

    @Async
    public void sendEmailAsync(String to, String subject, String content, boolean isMultipart, boolean isHtml) {
        sendEmail(to, subject, content, isMultipart, isHtml);
    }

    public void sendEmailFromTemplate(UserResonseDTO user, String templateName, String titleKey) {
        if (user.getEmail() == null) {
            return;
        }

        Locale locale = Locale.forLanguageTag(Constants.DEFAULT_LANGUAGE);
        Context context = new Context(locale);
        context.setVariable(USER, user);
        context.setVariable(BASE_URL, URL);
        String content = springTemplateEngine.process(templateName, context);
        String subject = messageSource.getMessage(titleKey, null, locale);
        sendEmail(user.getEmail(), subject, content, false, true);
    }

    @Async
    public void sendEmailFromTemplateAsync(UserResonseDTO user, String templateName, String titleKey) {
        sendEmailFromTemplate(user, templateName, titleKey);
    }

    public void sendReservationConfirmation(
            String to,
            String customerName,
            String reservationCode,
            String reservationTime,
            int partySize
    ) throws MessagingException, UnsupportedEncodingException {

        boolean allowed = limiter.isAllowed(
                "rate-limit:otp:email:" + to.trim().toLowerCase(),
                rate_limit_email_max_attempt,
                rate_limit_email_window_seconds
        );

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many OTP requests. Please try again later."
            );
        }

        Context context = new Context();
        context.setVariable("brandName", fromName);
        context.setVariable("customerName", customerName);
        context.setVariable("reservationCode", reservationCode);
        context.setVariable("reservationTime", reservationTime);
        context.setVariable("partySize", partySize);

        String htmlContent = springTemplateEngine.process(
                "email/reservation-notification",
                context
        );

        MimeMessage message = javaMailSender.createMimeMessage();

        MimeMessageHelper helper = new MimeMessageHelper(
                message,
                true,
                StandardCharsets.UTF_8.name()
        );

        helper.setFrom(from, fromName);
        helper.setTo(to);
        helper.setSubject("Castorant - Xác nhận đặt bàn");
        helper.setText(htmlContent, true);

        // 4. Send the email
        javaMailSender.send(message);
    }

    public void sendReservationReminder(Notification notification) throws Exception {
        Reservation reservation = notification.getReservation();
        String to = notification.getUser().getEmail();
        String customerName = notification.getUser().getFullname();

        ZoneId restaurantZone = ZoneId.of("Asia/Ho_Chi_Minh");

        String startTime = reservation.getStartTime()
                .atZone(restaurantZone)
                .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag(Constants.DEFAULT_LANGUAGE)));
        Context context = new Context(new Locale("en", "EN"));
        context.setVariable("customerName", customerName);
        context.setVariable("startTime", startTime);
        context.setVariable("reservationId", reservation.getReservationNo());
        String html = springTemplateEngine.process("email/reminder-notification", context);
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from);
        helper.setTo(to);
        helper.setSubject(notification.getTitle());
        helper.setText(html, true);
        javaMailSender.send(message);
    }

    public void sendReservationAutoCancellation(
            String to,
            String customerName,
            String reservationNo,
            Instant startTime
    ) throws Exception {
        ZoneId restaurantZone = ZoneId.of("Asia/Ho_Chi_Minh");
        String formattedTime = (startTime != null)
                ? startTime.atZone(restaurantZone)
                        .format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag(Constants.DEFAULT_LANGUAGE)))
                : "N/A";

        Context context = new Context(Locale.forLanguageTag(Constants.DEFAULT_LANGUAGE));
        context.setVariable("customerName", (customerName != null && !customerName.isBlank()) ? customerName : "Quý khách");
        context.setVariable("reservationNo", reservationNo != null ? reservationNo : "");
        context.setVariable("reservationTime", formattedTime);
        context.setVariable("brandName", (fromName != null && !fromName.isBlank()) ? fromName : "Castorant");

        String html = springTemplateEngine.process("email/cancellation-notification", context);

        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
        helper.setFrom(from, fromName);
        helper.setTo(to);
        helper.setSubject("Castorant - Thông báo hủy đặt bàn do quá giờ nhận bàn");
        helper.setText(html, true);

        javaMailSender.send(message);
    }
}
