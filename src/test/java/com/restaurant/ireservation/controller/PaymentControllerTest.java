package com.restaurant.ireservation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.dto.request.CreatePaymentRequestDTO;
import com.restaurant.ireservation.dto.response.PaymentHistoryResponseDTO;
import com.restaurant.ireservation.dto.response.PaymentResponseDTO;
import com.restaurant.ireservation.dto.response.VnPayIpnResponseDTO;
import com.restaurant.ireservation.dto.response.VnPayReturnResponseDTO;
import com.restaurant.ireservation.error.GlobalException;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.error.UnauthorizeException;
import com.restaurant.ireservation.service.PaymentService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    PaymentService paymentService;

    PaymentController paymentController;

    MockMvc mockMvc;
    ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        paymentController = new PaymentController(paymentService, objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(paymentController)
                .setControllerAdvice(new GlobalException())
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/payment/create returns 201 Created and paymentUrl on success")
    void testCreatePayment_Success() throws Exception {
        PaymentResponseDTO responseDTO = PaymentResponseDTO.builder()
                .id(1L)
                .orderId(100L)
                .orderNo("ORD-16102026-001")
                .totalFinalPrice(BigDecimal.valueOf(150000.0))
                .discountRate(0)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=15000000")
                .build();

        when(paymentService.createPayment(any(CreatePaymentRequestDTO.class), any(), any(), any()))
                .thenReturn(responseDTO);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();

        mockMvc.perform(post("/api/v1/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.orderId").value(100L))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.paymentUrl").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/payment/create with amount returns 201 Created for partial payment")
    void testCreatePayment_PartialPayment_Success() throws Exception {
        PaymentResponseDTO responseDTO = PaymentResponseDTO.builder()
                .id(2L)
                .orderId(100L)
                .orderNo("ORD-16102026-001")
                .totalFinalPrice(BigDecimal.valueOf(50000.0))
                .paidAmount(BigDecimal.ZERO)
                .remainingAmount(BigDecimal.valueOf(150000.0))
                .discountRate(0)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=5000000")
                .build();

        when(paymentService.createPayment(any(CreatePaymentRequestDTO.class), any(), any(), any()))
                .thenReturn(responseDTO);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(50000))
                .build();

        mockMvc.perform(post("/api/v1/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2L))
                .andExpect(jsonPath("$.totalFinalPrice").value(50000.0))
                .andExpect(jsonPath("$.remainingAmount").value(150000.0));
    }

    @Test
    @DisplayName("POST /api/v1/payment/create returns 400 Bad Request when orderId is missing")
    void testCreatePayment_ValidationError() throws Exception {
        CreatePaymentRequestDTO request = new CreatePaymentRequestDTO(); // orderId is null

        mockMvc.perform(post("/api/v1/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
    }

    @Test
    @DisplayName("POST /api/v1/payment/create returns 404 when order not found")
    void testCreatePayment_OrderNotFound() throws Exception {
        when(paymentService.createPayment(any(CreatePaymentRequestDTO.class), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Order not found with id: 999"));

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(999L).build();

        mockMvc.perform(post("/api/v1/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("POST /api/v1/payment/create returns 400 when business validation fails")
    void testCreatePayment_BusinessValidationFails() throws Exception {
        when(paymentService.createPayment(any(CreatePaymentRequestDTO.class), any(), any(), any()))
                .thenThrow(new RequestInvalidException("You can only create payment for your own order!"));

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();

        mockMvc.perform(post("/api/v1/payment/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    @DisplayName("GET /api/v1/payment/vnpay-return returns 200 OK with return result")
    void testProcessVnPayReturn_Success() throws Exception {
        VnPayReturnResponseDTO returnDTO = VnPayReturnResponseDTO.builder()
                .success(true)
                .responseCode("00")
                .transactionStatus("00")
                .message("Payment transaction successful")
                .paymentId(1L)
                .amount("150000")
                .txnRef("1T1729000000")
                .bankCode("NCB")
                .build();

        when(paymentService.processReturn(any())).thenReturn(returnDTO);

        mockMvc.perform(get("/api/v1/payment/vnpay-return")
                        .param("vnp_Amount", "15000000")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00")
                        .param("vnp_TxnRef", "1T1729000000")
                        .param("vnp_SecureHash", "VALID_HASH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.responseCode").value("00"))
                .andExpect(jsonPath("$.paymentId").value(1L));
    }

    // ==========================================
    // Webhook (IPN) Controller Unit Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/payment/webhook returns raw JSON 200 OK with RspCode 00")
    void testHandleVnPayWebhook_Success() throws Exception {
        when(paymentService.processIpn(any())).thenReturn(VnPayIpnResponseDTO.success());

        mockMvc.perform(get("/api/v1/payment/webhook")
                        .param("vnp_Amount", "15000000")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionStatus", "00")
                        .param("vnp_TxnRef", "100T1729000000")
                        .param("vnp_SecureHash", "VALID_HASH"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.RspCode").value("00"))
                .andExpect(jsonPath("$.Message").value("Confirm Success"));
    }

    @Test
    @DisplayName("GET /api/v1/payment/webhook returns RspCode 97 on invalid checksum")
    void testHandleVnPayWebhook_InvalidChecksum() throws Exception {
        when(paymentService.processIpn(any())).thenReturn(VnPayIpnResponseDTO.invalidChecksum());

        mockMvc.perform(get("/api/v1/payment/webhook")
                        .param("vnp_SecureHash", "INVALID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"))
                .andExpect(jsonPath("$.Message").value("Invalid Checksum"));
    }

    @Test
    @DisplayName("GET /api/v1/payment/webhook returns RspCode 01 when order not found")
    void testHandleVnPayWebhook_OrderNotFound() throws Exception {
        when(paymentService.processIpn(any())).thenReturn(VnPayIpnResponseDTO.orderNotFound());

        mockMvc.perform(get("/api/v1/payment/webhook")
                        .param("vnp_TxnRef", "999T1729000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("01"))
                .andExpect(jsonPath("$.Message").value("Order not found"));
    }

    @Test
    @DisplayName("GET /api/v1/payment/webhook returns RspCode 02 when order already confirmed")
    void testHandleVnPayWebhook_OrderAlreadyConfirmed() throws Exception {
        when(paymentService.processIpn(any())).thenReturn(VnPayIpnResponseDTO.orderAlreadyConfirmed());

        mockMvc.perform(get("/api/v1/payment/webhook")
                        .param("vnp_TxnRef", "100T1729000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"))
                .andExpect(jsonPath("$.Message").value("Order already confirmed"));
    }

    @Test
    @DisplayName("GET /api/v1/payment/webhook returns RspCode 04 on invalid amount")
    void testHandleVnPayWebhook_InvalidAmount() throws Exception {
        when(paymentService.processIpn(any())).thenReturn(VnPayIpnResponseDTO.invalidAmount());

        mockMvc.perform(get("/api/v1/payment/webhook")
                        .param("vnp_Amount", "99999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("04"))
                .andExpect(jsonPath("$.Message").value("Invalid Amount"));
    }

    @Test
    @DisplayName("GET /api/v1/payment/webhook returns RspCode 99 on unexpected exception")
    void testHandleVnPayWebhook_UnexpectedException() throws Exception {
        when(paymentService.processIpn(any())).thenThrow(new RuntimeException("Database down"));

        mockMvc.perform(get("/api/v1/payment/webhook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("99"))
                .andExpect(jsonPath("$.Message").value("Unknown error"));
    }

    // ==========================================
    // Phase 3: GET /my-history Controller Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/payments/my-history returns 200 with ResultPaginationDTO for authenticated customer")
    void testMyHistory_Success() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        PaymentHistoryResponseDTO item = PaymentHistoryResponseDTO.builder()
                .id(1L)
                .orderId(100L)
                .orderNo("ORD-16102026-001")
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .amount(BigDecimal.valueOf(100000.0))
                .discountRate(0)
                .createdDate(Instant.now())
                .orderTotalAmount(BigDecimal.valueOf(150000.0))
                .orderPaidAmount(BigDecimal.valueOf(100000.0))
                .orderRemainingAmount(BigDecimal.valueOf(50000.0))
                .build();

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(List.of(item));
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(1);
        meta.setPageSize(10);
        meta.setPages(1);
        meta.setTotal(1);
        paginationDTO.setMeta(meta);

        when(paymentService.getMyPaymentHistory(eq("customer1"), isNull(), eq(1), eq(10)))
                .thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/payments/my-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(1))
                .andExpect(jsonPath("$.meta.pageSize").value(10))
                .andExpect(jsonPath("$.meta.total").value(1))
                .andExpect(jsonPath("$.result[0].id").value(1L))
                .andExpect(jsonPath("$.result[0].orderId").value(100L))
                .andExpect(jsonPath("$.result[0].paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.result[0].amount").value(100000.0))
                .andExpect(jsonPath("$.result[0].orderRemainingAmount").value(50000.0));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payment/my-history also works (compatibility with singular prefix)")
    void testMyHistory_SingularUrlPath_Success() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(Collections.emptyList());
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(1);
        meta.setPageSize(10);
        meta.setPages(0);
        meta.setTotal(0);
        paginationDTO.setMeta(meta);

        when(paymentService.getMyPaymentHistory(eq("customer1"), isNull(), eq(1), eq(10)))
                .thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/payment/my-history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.total").value(0));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payments/my-history with status and pagination params calls service correctly")
    void testMyHistory_WithStatusAndPagination() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(Collections.emptyList());
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(2);
        meta.setPageSize(5);
        meta.setPages(0);
        meta.setTotal(0);
        paginationDTO.setMeta(meta);

        when(paymentService.getMyPaymentHistory(eq("customer1"), eq(PaymentStatus.PAID), eq(2), eq(5)))
                .thenReturn(paginationDTO);

        mockMvc.perform(get("/api/v1/payments/my-history")
                        .param("status", "PAID")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(2))
                .andExpect(jsonPath("$.meta.pageSize").value(5));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payments/my-history returns 401 Unauthorized when unauthenticated")
    void testMyHistory_Unauthenticated_Returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/payments/my-history"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // ==========================================
    // Phase 4: Invoice / Receipt Controller Tests
    // ==========================================

    @Test
    @DisplayName("GET /api/v1/payment/{paymentId}/invoice returns 200 with InvoiceResponseDTO")
    void testGetInvoice_Success_ReturnsJson() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        com.restaurant.ireservation.dto.response.InvoiceResponseDTO invoice =
                com.restaurant.ireservation.dto.response.InvoiceResponseDTO.builder()
                        .restaurantName("Castorant")
                        .invoiceNo("INV-100-1")
                        .paymentId(1L)
                        .paymentStatus(PaymentStatus.PAID)
                        .paymentAmount(BigDecimal.valueOf(150000.0))
                        .orderTotalAmount(BigDecimal.valueOf(150000.0))
                        .orderPaidAmount(BigDecimal.valueOf(150000.0))
                        .orderRemainingAmount(BigDecimal.ZERO)
                        .isFullyPaid(true)
                        .remainingMessage("Fully Paid")
                        .build();

        when(paymentService.getInvoiceData(eq(1L), eq("customer1"), any()))
                .thenReturn(invoice);

        mockMvc.perform(get("/api/v1/payment/1/invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Castorant"))
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.fullyPaid").value(true))
                .andExpect(jsonPath("$.remainingMessage").value("Fully Paid"));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payments/{paymentId}/invoice plural URL also works")
    void testGetInvoice_PluralPath_Success() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        com.restaurant.ireservation.dto.response.InvoiceResponseDTO invoice =
                com.restaurant.ireservation.dto.response.InvoiceResponseDTO.builder()
                        .restaurantName("Castorant")
                        .paymentId(1L)
                        .paymentStatus(PaymentStatus.PAID)
                        .build();

        when(paymentService.getInvoiceData(eq(1L), eq("customer1"), any()))
                .thenReturn(invoice);

        mockMvc.perform(get("/api/v1/payments/1/invoice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.restaurantName").value("Castorant"));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payment/{paymentId}/invoice?download=true returns PDF byte array with attachment header")
    void testGetInvoice_DownloadPdf_Success() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        byte[] fakePdf = "%PDF-1.4 Fake PDF Content".getBytes();
        when(paymentService.generateInvoicePdf(eq(1L), eq("customer1"), any()))
                .thenReturn(fakePdf);

        mockMvc.perform(get("/api/v1/payment/1/invoice").param("download", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(fakePdf));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payment/{paymentId}/invoice returns 401 when unauthenticated")
    void testGetInvoice_Unauthenticated_Returns401() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(get("/api/v1/payment/1/invoice"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.statusCode").value(401));
    }

    @Test
    @DisplayName("GET /api/v1/payment/{paymentId}/invoice returns 400 when status is PENDING or not eligible")
    void testGetInvoice_PendingStatus_Returns400() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(paymentService.getInvoiceData(eq(2L), eq("customer1"), any()))
                .thenThrow(new RequestInvalidException("Cannot generate invoice for payment with status PENDING"));

        mockMvc.perform(get("/api/v1/payment/2/invoice"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400))
                .andExpect(jsonPath("$.message").value("Cannot generate invoice for payment with status PENDING"));

        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/payment/{paymentId}/invoice returns 404 when paymentId not found")
    void testGetInvoice_NotFound_Returns404() throws Exception {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "customer1", "pass", List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(paymentService.getInvoiceData(eq(9999L), eq("customer1"), any()))
                .thenThrow(new ResourceNotFoundException("Payment not found with id: 9999"));

        mockMvc.perform(get("/api/v1/payment/9999/invoice"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.statusCode").value(404));

        SecurityContextHolder.clearContext();
    }
}
