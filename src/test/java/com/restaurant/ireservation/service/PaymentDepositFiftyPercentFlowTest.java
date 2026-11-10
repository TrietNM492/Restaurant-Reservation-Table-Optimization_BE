package com.restaurant.ireservation.service;

import com.restaurant.ireservation.config.VnPayConfig;
import com.restaurant.ireservation.domain.entity.CustomerInformation;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.Payment;
import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.dto.request.CreatePaymentRequestDTO;
import com.restaurant.ireservation.dto.response.PaymentResponseDTO;
import com.restaurant.ireservation.dto.response.VnPayIpnResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.repository.OrderRepository;
import com.restaurant.ireservation.repository.PaymentRepository;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.util.VnPayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentDepositFiftyPercentFlowTest {

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

    @Mock
    MailService mailService;

    @InjectMocks
    PaymentService paymentService;

    User customerUser;
    CustomerInformation customerInfo;
    Reservation reservation;
    Order order;

    @BeforeEach
    void setUp() {
        customerUser = User.builder()
                .username("thang_customer")
                .fullname("Bảo Thắng")
                .build();
        customerUser.setId(1L);

        customerInfo = CustomerInformation.builder()
                .user(customerUser)
                .build();
        customerInfo.setId(1L);

        // Reservation 2 days in the future
        reservation = Reservation.builder()
                .reservationNo("RES-24102026-VIP01")
                .startTime(Instant.now().plus(Duration.ofDays(2)))
                .endTime(Instant.now().plus(Duration.ofDays(2)).plus(Duration.ofHours(3)))
                .reservationStatus(ReservationStatus.PENDING)
                .build();
        reservation.setId(10L);

        // Order total: 1,500,000 VND (e.g. 500k VIP room + 1,000k food)
        order = Order.builder()
                .orderNo("ORD-24102026-001")
                .reservation(reservation)
                .customerInformation(customerInfo)
                .orderStatus(OrderStatus.PENDING)
                .isVipRoom(true)
                .totalAmount(1500000.0)
                .build();
        order.setId(100L);
    }

    private void mockVnPay() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(vnPayConfig.getTmnCode()).thenReturn("SANDBOXTMN");
        when(vnPayConfig.getSecretKey()).thenReturn("SANDBOXSECRETKEY1234567890");
        when(vnPayConfig.getPayUrl()).thenReturn("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        when(vnPayConfig.getReturnUrl()).thenReturn("http://localhost:8080/api/v1/payment/vnpay-return");
        when(vnPayConfig.getExpireMinutes()).thenReturn(15);
    }

    @Test
    @DisplayName("1. Đặt phòng trước giờ hẹn: Cọc đúng 50% (750.000đ / 1.500.000đ) -> Thành công và Order chuyển sang AWAITING_PAYMENT")
    void testDepositFiftyPercentBeforeArrivalTime_Success() {
        mockVnPay();
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(order)
                .totalFinalPrice(750000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(1001L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(750000)) // Exact 50%
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        PaymentResponseDTO response = paymentService.createPayment(request, "thang_customer", authorities, "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1001L);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(750000.0));
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(orderRepository, times(1)).save(order);
    }

    @Test
    @DisplayName("2. Đặt phòng trước giờ hẹn: Cọc thiếu (< 50%, ví dụ 500.000đ / 1.500.000đ) -> Bị từ chối ngoại lệ 50%")
    void testDepositLessThanFiftyPercent_ThrowsException() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(0.0);

        CreatePaymentRequestDTO request = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(500000)) // 500k < 50% (750k)
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(request, "thang_customer", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("at least 50%");
    }

    @Test
    @DisplayName("3. Khách đổi ý gọi thêm món (thêm 1.000.000đ, tổng thành 2.500.000đ): Đã cọc 750.000đ, cọc bù đúng 500.000đ (50% món mới) -> Thành công")
    void testCumulativeDepositWhenAddingDishes_Success() {
        mockVnPay();
        // New order total: 2,500,000 VND. Target 50% = 1,250,000 VND.
        order.setTotalAmount(2500000.0);
        order.setOrderStatus(OrderStatus.AWAITING_PAYMENT);

        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        // Already paid 750,000 VND
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(750000.0);

        // Case A: Customer tries to pay only 300,000 VND (< 500,000 VND needed) -> throws
        CreatePaymentRequestDTO insufficientRequest = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(300000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(insufficientRequest, "thang_customer", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("at least 50%");

        // Case B: Customer pays exact 500,000 VND (50% of the newly added 1,000,000 VND dishes) -> succeeds!
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(order)
                .totalFinalPrice(500000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(1002L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO validRequest = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(500000))
                .build();

        PaymentResponseDTO response = paymentService.createPayment(validRequest, "thang_customer", authorities, "127.0.0.1");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1002L);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(500000.0));
    }

    @Test
    @DisplayName("4. Đã đến/qua giờ hẹn (Khách đang dùng bữa tại quán): Cọc 50% bị chặn, bắt buộc thanh toán 100% phần còn lại")
    void testPaymentAfterArrivalTime_RequiresFullPayment() {
        when(vnPayConfig.isConfigured()).thenReturn(true);
        // Reservation start time was 30 minutes ago
        reservation.setStartTime(Instant.now().minus(Duration.ofMinutes(30)));
        order.setOrderStatus(OrderStatus.AWAITING_PAYMENT);

        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        // Already paid 750,000 VND previously. Remaining is 750,000 VND.
        when(paymentRepository.sumPaidAmountByOrderIdAndStatuses(eq(100L), any())).thenReturn(750000.0);

        // Case A: Customer tries partial payment (e.g. 300,000 VND) -> Throws exception
        CreatePaymentRequestDTO partialRequest = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(300000))
                .build();
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));

        assertThatThrownBy(() -> paymentService.createPayment(partialRequest, "thang_customer", authorities, "127.0.0.1"))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Dining time has started or passed. Please pay the full remaining amount");

        // Case B: Customer pays the full remaining 750,000 VND -> Succeeds!
        mockVnPay();
        when(paymentRepository.findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(eq(100L), eq(PaymentStatus.PENDING)))
                .thenReturn(Optional.empty());

        Payment savedPayment = Payment.builder()
                .order(order)
                .totalFinalPrice(750000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        savedPayment.setId(1003L);
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        CreatePaymentRequestDTO fullRequest = CreatePaymentRequestDTO.builder()
                .orderId(100L)
                .amount(BigDecimal.valueOf(750000))
                .build();

        PaymentResponseDTO response = paymentService.createPayment(fullRequest, "thang_customer", authorities, "127.0.0.1");
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1003L);
        assertThat(response.getTotalFinalPrice()).isEqualByComparingTo(BigDecimal.valueOf(750000.0));
    }

    @Test
    @DisplayName("5. VNPay IPN cọc thành công: Tự động chuyển Reservation từ PENDING sang CONFIRMED")
    void testIpnSuccess_AutoConfirmsReservation() {
        String secretKey = "SANDBOXSECRETKEY1234567890";
        when(vnPayConfig.getSecretKey()).thenReturn(secretKey);

        Payment payment = Payment.builder()
                .order(order)
                .totalFinalPrice(750000.0)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        payment.setId(1001L);

        when(paymentRepository.findByIdWithLock(1001L)).thenReturn(Optional.of(payment));
        when(orderRepository.findByIdWithLock(100L)).thenReturn(Optional.of(order));
        when(paymentRepository.sumPaidAmountByOrderIdAndStatusesExcludingPayment(eq(100L), any(), eq(1001L)))
                .thenReturn(0.0);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "75000000"); // 750,000 VND * 100
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "1001T1729000000");

        String data = "vnp_Amount=75000000&vnp_ResponseCode=00&vnp_TransactionStatus=00&vnp_TxnRef=1001T1729000000";
        params.put("vnp_SecureHash", VnPayUtil.hmacSHA512(secretKey, data));

        VnPayIpnResponseDTO ipnResponse = paymentService.processIpn(params);

        assertThat(ipnResponse.getRspCode()).isEqualTo("00");
        assertThat(payment.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_PAID);
        assertThat(order.getOrderStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(reservation.getReservationStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository, times(1)).save(reservation);
    }
}
