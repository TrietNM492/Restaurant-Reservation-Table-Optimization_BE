package com.restaurant.ireservation.service;

import com.restaurant.ireservation.config.VnPayConfig;
import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.Payment;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.domain.enumerations.RankCustomer;
import com.restaurant.ireservation.dto.request.CreatePaymentRequestDTO;
import com.restaurant.ireservation.dto.response.PaymentHistoryResponseDTO;
import com.restaurant.ireservation.dto.response.PaymentResponseDTO;
import com.restaurant.ireservation.dto.response.VnPayIpnResponseDTO;
import com.restaurant.ireservation.dto.response.VnPayReturnResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.error.UnauthorizeException;
import com.restaurant.ireservation.repository.OrderRepository;
import com.restaurant.ireservation.repository.PaymentRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.VnPayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.restaurant.ireservation.domain.entity.MenuItem;
import com.restaurant.ireservation.domain.entity.OrderItem;
import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.ReservationRepository;

import java.time.Duration;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    PaymentRepository paymentRepository;

    @Mock
    OrderRepository orderRepository;

    @Mock
    OrderItemRepository orderItemRepository;

    @Mock
    ReservationRepository reservationRepository;

    @Mock
    VnPayConfig vnPayConfig;

    @InjectMocks
    PaymentService paymentService;

    User sampleUser;
    CustomerInformation sampleCustomer;
    Reservation sampleReservation;
    Order sampleOrder;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .username("john_doe")
                .fullname("John Doe")
                .build();
        sampleUser.setId(10L);

        sampleCustomer = CustomerInformation.builder()
                .user(sampleUser)
                .build();
        sampleCustomer.setId(20L);

        sampleReservation = Reservation.builder()
                .reservationNo("RES-24102026-001")
                .startTime(Instant.now().plus(Duration.ofDays(1)))
                .endTime(Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(2)))
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        sampleReservation.setId(30L);

        sampleOrder = Order.builder()
                .orderNo("ORD-16102026-001")
                .customerInformation(sampleCustomer)
                .orderStatus(OrderStatus.AWAITING_PAYMENT)
                .totalAmount(150000.0) // 150,000 VND
                .reservation(sampleReservation)
                .build();
        sampleOrder.setId(100L);
    }

    private void mockVnPayConfig() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn("SANDBOXTMN");
        when(vnPayConfig.getSecretKey()).thenReturn("SANDBOXSECRETKEY1234567890");
        when(vnPayConfig.getPayUrl()).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        when(vnPayConfig.getReturnUrl()).thenReturn("http://localhost:8080/api/v1/payment/vnpay-return");
        when(vnPayConfig.getExpireMinutes()).thenReturn(15);
    }

    // ==========================================
    // Create Payment Tests
    // ==========================================

    @Test
    @DisplayName("createPayment succeeds when creating a new payment with default amount (full remaining)")
    void testCreatePayment_Success_DefaultFullAmount() {
        mockVnPayConfig();
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(100L, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .discountRate(0)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(500L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(500L);
        assertThat(response.getOrderId()).isEqualTo(100L);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(150000.0));
        assertThat(response.getPaidAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getRemainingAmount()).isEqualByComparingTo(BigDecimal.valueOf(150000.0));
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getPaymentUrl()).contains("vnp_Amount=15000000");

        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("createPayment succeeds when creating a partial payment (>= 50% and >= 5000)")
    void testCreatePayment_Success_PartialPayment() {
        mockVnPayConfig();
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(100L, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .discountRate(0)
                .totalFinalPrice(75000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(501L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // 75,000 VND is exactly 50% of 150,000 VND
        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(75000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(501L);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(75000.0));
        assertThat(response.getPaymentUrl()).contains("vnp_Amount=7500000");
    }

    @Test
    @DisplayName("createPayment reuses existing PENDING payment and updates its amount")
    void testCreatePayment_Success_ReusePendingPayment() {
        mockVnPayConfig();
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);

        Payment existingPayment = Payment.builder()
                .order(sampleOrder)
                .discountRate(0)
                .totalFinalPrice(30000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        existingPayment.setId(555L);
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(100L, PaymentStatus.PENDING))
                .thenReturn(Optional.of(existingPayment));
        when(paymentRepository.save(existingPayment)).thenReturn(existingPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(80000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(555L);
        assertThat(existingPayment.getTotalFinalPrice()).isEqualTo(80000.0);
        verify(paymentRepository, times(1)).save(existingPayment);
    }

    @Test
    @DisplayName("createPayment succeeds when staff or admin creates payment for customer order")
    void testCreatePayment_Success_StaffRole() {
        mockVnPayConfig();
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(100L, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .discountRate(0)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(600L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();
        var staffAuthorities = List.of(new SimpleGrantedAuthority("ROLE_STAFF"));

        PaymentResponseDTO response = paymentService.createPayment(request, "staff_member", staffAuthorities, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(600L);
    }

    @Test
    @DisplayName("createPayment throws exception when partial amount is less than 50% of order total")
    void testCreatePayment_ThrowsWhenPartialAmountLessThan50Percent() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);

        // 50% of 150,000 is 75,000. Customer requests 50,000 -> should throw
        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(50000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("at least 50%");
    }

    @Test
    @DisplayName("createPayment throws exception when 50% is below 5,000 VND (order is low-value, partial pay disabled)")
    void testCreatePayment_ThrowsWhenLowValueOrderTriesPartialPayment() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        sampleOrder.setTotalAmount(8000.0); // 50% is 4,000 VND < 5,000 VND
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(4000)) // Tried partial pay
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Partial payment is not supported for this low-value order");
    }

    @Test
    @DisplayName("createPayment with GOLDEN rank applies 5% discount and calculates remaining and 50% deposit on discounted total")
    void testCreatePayment_GoldenRank_Applies5PercentDiscount() {
        mockVnPayConfig();
        sampleCustomer.setCustomerRank(RankCustomer.GOLDEN);
        sampleOrder.setTotalAmount(100000.0);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findByOrderId(100L)).thenReturn(Collections.emptyList());

        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(95000.0)
                .discountRate(5)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(101L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response.getDiscountRate()).isEqualTo(5);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(95000));
    }

    @Test
    @DisplayName("createPayment with DIAMOND rank applies 10% discount and allows partial payment at least 50% of discounted total")
    void testCreatePayment_DiamondRank_Applies10PercentDiscount() {
        mockVnPayConfig();
        sampleCustomer.setCustomerRank(RankCustomer.DIAMOND);
        sampleOrder.setTotalAmount(200000.0);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findByOrderId(100L)).thenReturn(Collections.emptyList());

        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        // 200,000 with 10% discount is 180,000. 50% deposit is 90,000.
        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(90000.0)
                .discountRate(10)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(102L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(90000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response.getDiscountRate()).isEqualTo(10);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(90000));
    }

    @Test
    @DisplayName("createPayment throws exception when dining time has passed and customer tries partial payment")
    void testCreatePayment_ThrowsWhenDiningTimePassedTriesPartialPayment() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        // Reservation start time was 1 hour ago
        sampleReservation.setStartTime(Instant.now().minus(Duration.ofHours(1)));
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(75000)) // Trying partial payment after arrival time
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Dining time has started or passed. Please pay the full remaining amount");
    }

    @Test
    @DisplayName("createPayment transitions PENDING order to AWAITING_PAYMENT")
    void testCreatePayment_TransitionsPendingOrderToAwaitingPayment() {
        mockVnPayConfig();
        sampleOrder.setOrderStatus(OrderStatus.PENDING);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(103L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(sampleOrder.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(orderRepository, times(1)).save(sampleOrder);
    }

    @Test
    @DisplayName("createPayment calculates required cumulative deposit correctly when dishes are added")
    void testCreatePayment_CumulativeDepositWhenDishesAdded() {
        mockVnPayConfig();
        // Total order is 200,000 VND. 50% target is 100,000 VND.
        sampleOrder.setTotalAmount(200000.0);
        // Already paid 50,000 VND previously.
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(50000.0);

        // Trying to pay 40,000 VND (less than remaining required deposit 50,000 VND) -> throws
        CreatePaymentRequestDTO lowRequest = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(40000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(lowRequest, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("at least 50%");

        // Now trying to pay 50,000 VND (exact remaining required deposit) -> succeeds
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());
        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(104L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO validRequest = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(50000))
                .build();

        PaymentResponseDTO response = paymentService.createPayment(validRequest, "john_doe", authorities, "127.0.0.1");
        assertThat(response).isNotNull();
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(50000.0));
    }

    @Test
    @DisplayName("createPayment with CAOTHU rank applies 15% discount")
    void testCreatePayment_CaoThuRank_Applies15PercentDiscount() {
        mockVnPayConfig();
        sampleCustomer.setCustomerRank(RankCustomer.CAOTHU);
        sampleOrder.setTotalAmount(100000.0);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findByOrderId(100L)).thenReturn(Collections.emptyList());

        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(85000.0)
                .discountRate(15)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(103L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response.getDiscountRate()).isEqualTo(15);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(85000));
    }

    @Test
    @DisplayName("createPayment reuses existing discountRate when order already has a PARTIALLY_PAID payment")
    void testCreatePayment_ReusesPriorDiscountRateOnSubsequentPayment() {
        mockVnPayConfig();
        sampleCustomer.setCustomerRank(RankCustomer.CAOTHU);
        sampleOrder.setTotalAmount(200000.0);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));

        Payment priorPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(20000.0)
                .discountRate(10)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .build();
        priorPayment.setId(90L);
        when(paymentRepository.findByOrderId(100L)).thenReturn(List.of(priorPayment));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(20000.0);

        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(160000.0)
                .discountRate(10)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(104L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1");

        assertThat(response.getDiscountRate()).isEqualTo(10);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(160000));
    }

    @Test
    @DisplayName("createPayment throws exception when amount exceeds remaining balance")
    void testCreatePayment_ThrowsWhenAmountExceedsRemaining() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        // Already paid 100,000, remaining is 50,000
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(100000.0);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(60000)) // Exceeds 50,000
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("exceeds remaining balance");
    }

    @Test
    @DisplayName("createPayment throws exception when order is already fully paid")
    void testCreatePayment_ThrowsWhenOrderAlreadyFullyPaid() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(150000.0);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("already been paid successfully in full");
    }

    @Test
    @DisplayName("createPayment throws exception when customer attempts to pay another customer's order")
    void testCreatePayment_ThrowsWhenCustomerPaysAnotherCustomerOrder() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "hacker_user", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("You can only create payment for your own order");
    }

    @Test
    @DisplayName("createPayment throws exception when order is in invalid status (e.g. COMPLETED)")
    void testCreatePayment_ThrowsWhenOrderNotAwaitingPayment() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        sampleOrder.setOrderStatus(OrderStatus.COMPLETED);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder().orderId(100L).build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "john_doe", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Order must be in PENDING or AWAITING_PAYMENT status");
    }

    // ==========================================
    // IPN (Webhook) Unit Tests
    // ==========================================

    @Test
    @DisplayName("processIpn marks payment as PARTIALLY_PAID and keeps Order in AWAITING_PAYMENT when order remains incomplete")
    void testProcessIpn_Success_PartialPayment() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0) // 50,000 VND
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(500L);

        when(paymentRepository.findByIdWithLock(500L)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatusesExcludingPayment(eq(100L), any(), eq(500L)))
                .thenReturn(0.0); // No prior payments

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "5000000"); // 50,000 VND * 100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=5000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("00");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(sampleOrder.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT); // Order stays in AWAITING_PAYMENT
        assertThat(sampleReservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(paymentRepository, times(1)).save(payment);
        verify(orderRepository, never()).save(sampleOrder);
        verify(reservationRepository, times(1)).save(sampleReservation);
    }

    @Test
    @DisplayName("processIpn marks payment as PAID and transitions Order to OK when remaining balance is fully paid")
    void testProcessIpn_Success_FullPayment_OrderTransitionsToOk() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(100000.0) // 100,000 VND (final installment)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(502L);

        when(paymentRepository.findByIdWithLock(502L)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(sampleOrder));
        // Prior installment paid 50,000 VND. Total with this payment is 150,000 VND (100% of order)
        when(paymentRepository.sumPaidAmountByOrderIdAndStatusesExcludingPayment(eq(100L), any(), eq(502L)))
                .thenReturn(50000.0);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "502T1729000000");

        String data = "vnp_Amount=10000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TxnRef=502T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("00");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(sampleOrder.getOrderStatus()).isEqualTo(OrderStatus.OK);
        verify(paymentRepository, times(1)).save(payment);
        verify(orderRepository, times(1)).save(sampleOrder);
    }

    @Test
    @DisplayName("processIpn marks payment as CANCELLED when customer cancels on VNPay (vnp_ResponseCode = 24)")
    void testProcessIpn_CancelledByCustomer() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(500L);

        when(paymentRepository.findByIdWithLock(500L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", "24"); // Cancelled
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=15000000&vnp_ResponseCode=24&vnp_TransactionStatus=02&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("00");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(sampleOrder.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT); // Order untouched
        verify(paymentRepository, times(1)).save(payment);
        verify(orderRepository, never()).save(sampleOrder);
    }

    @Test
    @DisplayName("processIpn marks payment as FAILED when transaction fails on VNPay (e.g. responseCode = 09)")
    void testProcessIpn_FailedTransaction() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(500L);

        when(paymentRepository.findByIdWithLock(500L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", "09"); // Failed
        params.put("vnp_TransactionStatus", "02");
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=15000000&vnp_ResponseCode=09&vnp_TransactionStatus=02&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("00");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(sampleOrder.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT); // Order untouched
        verify(paymentRepository, times(1)).save(payment);
        verify(orderRepository, never()).save(sampleOrder);
    }

    @Test
    @DisplayName("processIpn returns RspCode 02 (idempotent) for already confirmed PARTIALLY_PAID payment")
    void testProcessIpn_RspCode02_AlreadyPartiallyPaid() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID) // Terminal state
                .build();
        payment.setId(500L);

        when(paymentRepository.findByIdWithLock(500L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "5000000");
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=5000000&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("02");
        assertThat(result.getMessage()).isEqualTo("Order already confirmed");
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    @DisplayName("processIpn returns RspCode 02 (idempotent) for already CANCELLED payment")
    void testProcessIpn_RspCode02_AlreadyCancelled() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.CANCELLED)
                .build();
        payment.setId(500L);

        when(paymentRepository.findByIdWithLock(500L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=15000000&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("02");
        verify(paymentRepository, never()).save(payment);
    }

    @Test
    @DisplayName("processIpn returns RspCode 97 on invalid checksum")
    void testProcessIpn_RspCode97_InvalidChecksum() {
        when(vnPayConfig.getSecretKey()).thenReturn("SECRET123");

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_TxnRef", "500T1729000000");
        params.put("vnp_SecureHash", "INVALID_HASH");

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("97");
        assertThat(result.getMessage()).isEqualTo("Invalid Checksum");
        verifyNoInteractions(paymentRepository);
    }

    @Test
    @DisplayName("processIpn returns RspCode 01 when payment not found")
    void testProcessIpn_RspCode01_OrderNotFound() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);
        when(paymentRepository.findByIdWithLock(999L)).thenReturn(Optional.empty());

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_TxnRef", "999T1729000000");

        String data = "vnp_Amount=15000000&vnp_TxnRef=999T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("01");
        assertThat(result.getMessage()).isEqualTo("Order not found");
    }

    @Test
    @DisplayName("processIpn returns RspCode 04 on amount mismatch")
    void testProcessIpn_RspCode04_AmountMismatch() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(500L);

        when(paymentRepository.findByIdWithLock(500L)).thenReturn(Optional.of(payment));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "20000000"); // mismatch
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=20000000&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("04");
        assertThat(result.getMessage()).isEqualTo("Invalid Amount");
    }

    @Test
    @DisplayName("processIpn returns RspCode 99 on unexpected exception")
    void testProcessIpn_RspCode99_UnexpectedException() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);
        when(paymentRepository.findByIdWithLock(500L)).thenThrow(new RuntimeException("Database error"));

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_TxnRef", "500T1729000000");

        String data = "vnp_Amount=15000000&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO result = paymentService.processIpn(params);

        assertThat(result.getRspCode()).isEqualTo("99");
        assertThat(result.getMessage()).isEqualTo("Unknown error");
    }

    // ==========================================
    // Return URL Unit Tests
    // ==========================================

    @Test
    @DisplayName("processReturn returns success when signature is valid and responseCode is 00")
    void testProcessReturn_Success() {
        String secretKey = "SECRET123";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "500T1729000000");
        params.put("vnp_TransactionNo", "14000123");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_PayDate", "20261016120000");

        String data = "vnp_Amount=15000000&vnp_BankCode=NCB&vnp_PayDate=20261016120000&vnp_ResponseCode=00&vnp_TransactionNo=14000123&vnp_TransactionStatus=00&vnp_TxnRef=500T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        Payment mockPayment = Payment.builder().paymentStatus(PaymentStatus.PAID).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(mockPayment));

        VnPayReturnResponseDTO result = paymentService.processReturn(params);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.isSignatureValid()).isTrue();
        assertThat(result.getPaymentStatus()).isEqualTo("PAID");
        assertThat(result.getPaymentId()).isEqualTo(500L);
        assertThat(result.getAmount()).isEqualTo("150000");
        assertThat(result.getResponseCode()).isEqualTo("00");
        verify(paymentRepository, never()).save(any()); // Strictly NO DB modification
        verify(paymentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("processReturn returns failure when signature is invalid")
    void testProcessReturn_InvalidSignature() {
        when(vnPayConfig.getSecretKey()).thenReturn("SECRET123");

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "15000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "500T1729000000");
        params.put("vnp_SecureHash", "INCORRECT_HASH");

        VnPayReturnResponseDTO result = paymentService.processReturn(params);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("Invalid signature");
        verifyNoInteractions(paymentRepository);
    }

    // ==========================================
    // Phase 3: Payment History Unit Tests
    // ==========================================

    @Test
    @DisplayName("getMyPaymentHistory returns only payments of current authenticated customer")
    void testMyHistory_Success_ReturnsOnlyOwnPayments() {
        String username = "customer1";
        Payment p1 = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(100000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .build();
        p1.setId(1L);

        Page<Payment> mockPage = new PageImpl<>(List.of(p1), PageRequest.of(0, 10), 1);
        when(paymentRepository.findMyPaymentHistory(eq(username), isNull(), any(Pageable.class)))
                .thenReturn(mockPage);

        List<Object[]> mockSums = new ArrayList<>();
        mockSums.add(new Object[]{sampleOrder.getId(), 100000.0});
        when(paymentRepository.sumPaidAmountByOrderIdsAndStatuses(anyCollection(), anyCollection()))
                .thenReturn(mockSums);

        ResultPaginationDTO result = paymentService.getMyPaymentHistory(username, null, 1, 10);

        assertThat(result).isNotNull();
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getPageSize()).isEqualTo(10);

        @SuppressWarnings("unchecked")
        List<PaymentHistoryResponseDTO> content = (List<PaymentHistoryResponseDTO>) result.getResult();
        assertThat(content).hasSize(1);
        PaymentHistoryResponseDTO item = content.get(0);
        assertThat(item.getId()).isEqualTo(1L);
        assertThat(item.getOrderId()).isEqualTo(sampleOrder.getId());
        assertThat(item.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000.0));
        assertThat(item.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(item.getOrderTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150000.0));
        assertThat(item.getOrderPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000.0));
        assertThat(item.getOrderRemainingAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000.0));

        verify(paymentRepository, times(1)).findMyPaymentHistory(eq(username), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("getMyPaymentHistory ensures two different customers only view their own payment data")
    void testMyHistory_TwoDifferentCustomers_OnlySeeOwnData() {
        String customerA = "customer_a";
        String customerB = "customer_b";

        Payment pA = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .build();
        pA.setId(10L);

        Page<Payment> mockPageA = new PageImpl<>(List.of(pA), PageRequest.of(0, 10), 1);
        when(paymentRepository.findMyPaymentHistory(eq(customerA), isNull(), any(Pageable.class)))
                .thenReturn(mockPageA);

        Page<Payment> mockPageB = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(paymentRepository.findMyPaymentHistory(eq(customerB), isNull(), any(Pageable.class)))
                .thenReturn(mockPageB);

        ResultPaginationDTO resultA = paymentService.getMyPaymentHistory(customerA, null, 1, 10);
        assertThat(resultA.getMeta().getTotal()).isEqualTo(1);

        ResultPaginationDTO resultB = paymentService.getMyPaymentHistory(customerB, null, 1, 10);
        assertThat(resultB.getMeta().getTotal()).isEqualTo(0);

        verify(paymentRepository, times(1)).findMyPaymentHistory(eq(customerA), isNull(), any(Pageable.class));
        verify(paymentRepository, times(1)).findMyPaymentHistory(eq(customerB), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("getMyPaymentHistory filters by PaymentStatus properly")
    void testMyHistory_FilterByStatus() {
        String username = "customer1";
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(paymentRepository.findMyPaymentHistory(eq(username), eq(PaymentStatus.PAID), any(Pageable.class)))
                .thenReturn(emptyPage);

        ResultPaginationDTO result = paymentService.getMyPaymentHistory(username, PaymentStatus.PAID, 1, 10);

        assertThat(result.getMeta().getTotal()).isEqualTo(0);
        verify(paymentRepository, times(1)).findMyPaymentHistory(eq(username), eq(PaymentStatus.PAID), any(Pageable.class));
    }

    @Test
    @DisplayName("getMyPaymentHistory returns empty list when customer has no payments")
    void testMyHistory_NoPayments_ReturnsEmptyList() {
        String username = "new_customer";
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        when(paymentRepository.findMyPaymentHistory(eq(username), isNull(), any(Pageable.class)))
                .thenReturn(emptyPage);

        ResultPaginationDTO result = paymentService.getMyPaymentHistory(username, null, 1, 10);

        assertThat(result).isNotNull();
        assertThat(result.getMeta().getTotal()).isEqualTo(0);
        assertThat(result.getMeta().getPages()).isEqualTo(0);
        assertThat((List<?>) result.getResult()).isEmpty();
    }

    @Test
    @DisplayName("getMyPaymentHistory throws UnauthorizeException when user is unauthenticated or null")
    void testMyHistory_Unauthenticated_ThrowsException() {
        assertThatThrownBy(() -> paymentService.getMyPaymentHistory(null, null, 1, 10))
                .isInstanceOf(UnauthorizeException.class)
                .hasMessageContaining("Please login");

        assertThatThrownBy(() -> paymentService.getMyPaymentHistory("", null, 1, 10))
                .isInstanceOf(UnauthorizeException.class)
                .hasMessageContaining("Please login");

        assertThatThrownBy(() -> paymentService.getMyPaymentHistory("anonymousUser", null, 1, 10))
                .isInstanceOf(UnauthorizeException.class)
                .hasMessageContaining("Please login");
    }

    @Test
    @DisplayName("getMyPaymentHistory throws RequestInvalidException when page or size is less than 1")
    void testMyHistory_InvalidPageOrSize_ThrowsBadRequest() {
        assertThatThrownBy(() -> paymentService.getMyPaymentHistory("customer1", null, 0, 10))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Page must be greater than or equal to 1");

        assertThatThrownBy(() -> paymentService.getMyPaymentHistory("customer1", null, 1, 0))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Size must be greater than or equal to 1");
    }

    @Test
    @DisplayName("getMyPaymentHistory enforces newest-first sorting (createdDate DESC, id DESC)")
    void testMyHistory_NewestFirstSorting() {
        String username = "customer1";
        Page<Payment> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 10), 0);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(paymentRepository.findMyPaymentHistory(eq(username), isNull(), pageableCaptor.capture()))
                .thenReturn(emptyPage);

        paymentService.getMyPaymentHistory(username, null, 2, 5);

        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable.getPageNumber()).isEqualTo(1); // 0-indexed for page 2
        assertThat(capturedPageable.getPageSize()).isEqualTo(5);
        assertThat(capturedPageable.getSort().getOrderFor("createdDate")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("createdDate").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(capturedPageable.getSort().getOrderFor("id")).isNotNull();
        assertThat(capturedPageable.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    // ==========================================
    // Phase 4: Invoice / Receipt Unit Tests
    // ==========================================

    @Test
    @DisplayName("getInvoiceData returns full invoice for PAID payment with 'Fully Paid'")
    void testInvoice_PaidStatus_Success() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        payment.setId(10L);

        when(paymentRepository.findWithOrderById(10L)).thenReturn(Optional.of(payment));

        MenuItem item = MenuItem.builder().itemName("Bò nướng tảng").price(150000.0).build();
        OrderItem orderItem = OrderItem.builder()
                .order(sampleOrder)
                .menuItem(item)
                .quantity(1)
                .subtotal(150000.0)
                .build();
        when(orderItemRepository.findAllByOrderId(sampleOrder.getId())).thenReturn(List.of(orderItem));

        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(sampleOrder.getId()), anyCollection()))
                .thenReturn(150000.0);

        InvoiceResponseDTO invoice = paymentService.getInvoiceData(10L, sampleUser.getUsername(), null);

        assertThat(invoice).isNotNull();
        assertThat(invoice.getRestaurantName()).isEqualTo("Castorant");
        assertThat(invoice.getPaymentId()).isEqualTo(10L);
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(invoice.isFullyPaid()).isTrue();
        assertThat(invoice.getRemainingMessage()).isEqualTo("Fully Paid");
        assertThat(invoice.getOrderTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(150000.0));
        assertThat(invoice.getOrderPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(150000.0));
        assertThat(invoice.getOrderRemainingAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getItems()).hasSize(1);
        assertThat(invoice.getItems().get(0).getItemName()).isEqualTo("Bò nướng tảng");
    }

    @Test
    @DisplayName("getInvoiceData returns invoice for PARTIALLY_PAID payment with remaining amount")
    void testInvoice_PartiallyPaid_Success() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PARTIALLY_PAID)
                .build();
        payment.setId(11L);

        when(paymentRepository.findWithOrderById(11L)).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(sampleOrder.getId())).thenReturn(Collections.emptyList());
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(sampleOrder.getId()), anyCollection()))
                .thenReturn(50000.0);

        InvoiceResponseDTO invoice = paymentService.getInvoiceData(11L, sampleUser.getUsername(), null);

        assertThat(invoice).isNotNull();
        assertThat(invoice.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(invoice.isFullyPaid()).isFalse();
        assertThat(invoice.getRemainingMessage()).contains("Remaining:");
        assertThat(invoice.getOrderRemainingAmount()).isEqualByComparingTo(BigDecimal.valueOf(100000.0));
        assertThat(invoice.getOrderPaidAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000.0));
    }

    @Test
    @DisplayName("getInvoiceData throws RequestInvalidException when payment is PENDING")
    void testInvoice_PendingStatus_ThrowsBadRequest() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(12L);

        when(paymentRepository.findWithOrderById(12L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getInvoiceData(12L, sampleUser.getUsername(), null))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Cannot generate invoice for payment with status PENDING");
    }

    @Test
    @DisplayName("getInvoiceData throws RequestInvalidException when payment is FAILED")
    void testInvoice_FailedStatus_ThrowsBadRequest() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .paymentStatus(PaymentStatus.FAILED)
                .build();
        payment.setId(13L);

        when(paymentRepository.findWithOrderById(13L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getInvoiceData(13L, sampleUser.getUsername(), null))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Cannot generate invoice for payment with status FAILED");
    }

    @Test
    @DisplayName("getInvoiceData throws RequestInvalidException when payment is CANCELLED")
    void testInvoice_CancelledStatus_ThrowsBadRequest() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(50000.0)
                .paymentStatus(PaymentStatus.CANCELLED)
                .build();
        payment.setId(14L);

        when(paymentRepository.findWithOrderById(14L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getInvoiceData(14L, sampleUser.getUsername(), null))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Cannot generate invoice for payment with status CANCELLED");
    }

    @Test
    @DisplayName("getInvoiceData throws ResourceNotFoundException when paymentId does not exist")
    void testInvoice_NotFound_Throws404() {
        when(paymentRepository.findWithOrderById(9999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getInvoiceData(9999L, sampleUser.getUsername(), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Payment not found with id: 9999");
    }

    @Test
    @DisplayName("getInvoiceData throws RequestInvalidException when customer attempts to access someone else's invoice")
    void testInvoice_OtherCustomerOrder_ThrowsBadRequest() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        payment.setId(15L);

        when(paymentRepository.findWithOrderById(15L)).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getInvoiceData(15L, "other_customer", null))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("You can only access invoices for your own orders!");
    }

    @Test
    @DisplayName("getInvoiceData allows Staff or Admin to access any customer's invoice")
    void testInvoice_StaffOrAdmin_CanAccessAnyCustomerInvoice() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        payment.setId(16L);

        when(paymentRepository.findWithOrderById(16L)).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(sampleOrder.getId())).thenReturn(Collections.emptyList());
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(sampleOrder.getId()), anyCollection()))
                .thenReturn(150000.0);

        List<SimpleGrantedAuthority> staffAuthorities = List.of(new SimpleGrantedAuthority("ROLE_STAFF"));

        InvoiceResponseDTO invoice = paymentService.getInvoiceData(16L, "staff_user", staffAuthorities);

        assertThat(invoice).isNotNull();
        assertThat(invoice.getPaymentId()).isEqualTo(16L);
    }

    @Test
    @DisplayName("generateInvoicePdf produces non-empty PDF byte array")
    void testGenerateInvoicePdf_Success() {
        Payment payment = Payment.builder()
                .order(sampleOrder)
                .totalFinalPrice(150000.0)
                .discountRate(0)
                .paymentStatus(PaymentStatus.PAID)
                .build();
        payment.setId(17L);

        when(paymentRepository.findWithOrderById(17L)).thenReturn(Optional.of(payment));
        when(orderItemRepository.findAllByOrderId(sampleOrder.getId())).thenReturn(Collections.emptyList());
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(sampleOrder.getId()), anyCollection()))
                .thenReturn(150000.0);

        byte[] pdfBytes = paymentService.generateInvoicePdf(17L, sampleUser.getUsername(), null);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(100);
        String header = new String(pdfBytes, 0, Math.min(pdfBytes.length, 5));
        assertThat(header).isEqualTo("%PDF-");
    }
}
