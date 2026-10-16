package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.request.CreatePaymentRequestDTO;
import com.restaurant.ireservation.dto.response.PaymentResponseDTO;
import com.restaurant.ireservation.dto.response.VnPayReturnResponseDTO;
import com.restaurant.ireservation.error.UnauthorizeException;
import com.restaurant.ireservation.service.PaymentService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.VnPayUtil;
import com.restaurant.ireservation.util.annotation.ApiMessage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurant.ireservation.dto.response.VnPayIpnResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping({"/api/v1/payment", "/api/v1/payments"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PaymentController {

    PaymentService paymentService;
    ObjectMapper objectMapper;

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'MANAGER', 'CUSTOMER') or hasAnyAuthority('ROLE_ADMIN', 'ROLE_STAFF', 'ROLE_MANAGER', 'ROLE_CUSTOMER')")
    @ApiMessage("Create payment successfully")
    public PaymentResponseDTO createPayment(
            @Valid @RequestBody CreatePaymentRequestDTO request,
            HttpServletRequest servletRequest
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null) ? auth.getName() : null;
        Collection<? extends GrantedAuthority> authorities = (auth != null)
                ? auth.getAuthorities()
                : Collections.emptyList();
        String clientIp = VnPayUtil.getClientIpAddress(servletRequest);

        return paymentService.createPayment(request, username, authorities, clientIp);
    }

    @GetMapping("/vnpay-return")
    @ResponseStatus(HttpStatus.OK)
    @ApiMessage("Process VNPay return successfully")
    public VnPayReturnResponseDTO processVnPayReturn(
            @RequestParam Map<String, String> params
    ) {
        return paymentService.processReturn(params);
    }

    @GetMapping(value = "/webhook", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> handleVnPayWebhook(@RequestParam Map<String, String> params) {
        try {
            VnPayIpnResponseDTO responseDTO = paymentService.processIpn(params);
            String json = objectMapper.writeValueAsString(responseDTO);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json);
        } catch (Exception e) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"RspCode\":\"99\",\"Message\":\"Unknown error\"}");
        }
    }

    @GetMapping("/my-history")
    @PreAuthorize("isAuthenticated()")
    @ApiMessage("Fetch payment history successfully")
    public ResponseEntity<ResultPaginationDTO> getMyPaymentHistory(
            @RequestParam(name = "status", required = false) PaymentStatus status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int size
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            throw new UnauthorizeException("Please login to view your payment history!");
        }
        String username = auth.getName();
        ResultPaginationDTO result = paymentService.getMyPaymentHistory(username, status, page, size);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{paymentId}/invoice")
    @PreAuthorize("isAuthenticated()")
    @ApiMessage("Get invoice successfully")
    public ResponseEntity<?> getInvoice(
            @PathVariable("paymentId") Long paymentId,
            @RequestParam(name = "download", defaultValue = "false") boolean download
    ) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            throw new UnauthorizeException("Please login to view invoice!");
        }
        String username = auth.getName();
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        if (download) {
            byte[] pdfBytes = paymentService.generateInvoicePdf(paymentId, username, authorities);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("invoice-" + paymentId + ".pdf")
                    .build());
            headers.setContentLength(pdfBytes.length);
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
        } else {
            InvoiceResponseDTO invoice = paymentService.getInvoiceData(paymentId, username, authorities);
            return ResponseEntity.ok(invoice);
        }
    }
}
