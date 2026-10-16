package com.restaurant.ireservation.service;

import com.restaurant.ireservation.config.VnPayConfig;
import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.entity.Payment;
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
import com.restaurant.ireservation.domain.entity.OrderItem;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.dto.response.InvoiceItemResponseDTO;
import com.restaurant.ireservation.dto.response.InvoiceResponseDTO;
import com.restaurant.ireservation.repository.OrderItemRepository;
import com.restaurant.ireservation.util.InvoicePdfGenerator;
import com.restaurant.ireservation.util.constants.RestaurantInfoConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PaymentService {

    private static final DecimalFormat VND_FORMAT;
    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        symbols.setDecimalSeparator('.');
        VND_FORMAT = new DecimalFormat("#,##0", symbols);
    }

    private static String formatVnd(BigDecimal amount) {
        if (amount == null) {
            return "0";
        }
        return VND_FORMAT.format(amount);
    }

    PaymentRepository paymentRepository;
    OrderRepository orderRepository;
    OrderItemRepository orderItemRepository;
    VnPayConfig vnPayConfig;

    @Transactional
    public PaymentResponseDTO createPayment(CreatePaymentRequestDTO request, String username,
                                            Collection<? extends GrantedAuthority> authorities,
                                            String clientIp) {
        if (!vnPayConfig.isConfigured()) {
            throw new RequestInvalidException(
                    "VNPay is not configured. Please configure vnpay.tmn-code and vnpay.secret-key in application properties.");
        }

        // Lock order row with pessimistic write lock to prevent race conditions during amount calculation
        Order order = orderRepository.findByIdWithLock(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + request.getOrderId()));

        // Role authorization check: Admin, Manager, Staff can create payment for any order; Customer only for own order
        boolean isStaffOrHigher = authorities != null && authorities.stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_MANAGER".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority()));

        if (!isStaffOrHigher) {
            if (username == null || username.isBlank()
                    || order.getCustomerInformation() == null
                    || order.getCustomerInformation().getUser() == null
                    || !username.equalsIgnoreCase(order.getCustomerInformation().getUser().getUsername())) {
                throw new RequestInvalidException("You can only create payment for your own order!");
            }
        }

        // Validate order status: strictly allow only AWAITING_PAYMENT
        if (order.getOrderStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new RequestInvalidException(
                    "Cannot create payment. Order must be in AWAITING_PAYMENT status (current status: " + order.getOrderStatus() + ")");
        }

        // Validate order total
        if (order.getTotalAmount() <= 0) {
            throw new RequestInvalidException("Order total amount must be greater than 0 to proceed with payment!");
        }

        int discountRate = resolveDiscountRate(order);
        BigDecimal orderTotal = BigDecimal.valueOf(order.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountMultiplier = BigDecimal.valueOf(100 - discountRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal orderTargetTotal = orderTotal.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);

        // Sum amounts already successfully paid (PAID, PARTIALLY_PAID)
        Double paidSum = paymentRepository.sumPaidAmountByOrderIdAndStatuses(order.getId(),
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_PAID));
        BigDecimal alreadyPaid = BigDecimal.valueOf(paidSum != null ? paidSum : 0.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = orderTargetTotal.subtract(alreadyPaid).setScale(2, RoundingMode.HALF_UP);

        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RequestInvalidException("This order has already been paid successfully in full!");
        }

        // Rules for Partial Payment:
        // 1. Minimum VNPay transaction amount is 5,000 VND
        // 2. Minimum partial payment amount is 10% of initial order target total
        // 3. If 10% of order target total is < 5,000 VND, partial payment is NOT supported for this order
        BigDecimal tenPercent = orderTargetTotal.multiply(BigDecimal.valueOf(0.10)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal minVnpayAmount = BigDecimal.valueOf(5000).setScale(2, RoundingMode.HALF_UP);

        BigDecimal amountToPay;
        if (request.getAmount() == null) {
            // Default: pay all remaining balance
            amountToPay = remainingAmount;
        } else {
            BigDecimal requestedAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);

            if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new RequestInvalidException("Payment amount must be greater than 0!");
            }

            if (requestedAmount.compareTo(remainingAmount) > 0) {
                throw new RequestInvalidException(
                        "Payment amount (" + formatVnd(requestedAmount) + " VND) exceeds remaining balance (" + formatVnd(remainingAmount) + " VND)!");
            }

            // Customer wishes to pay partially (less than remaining)
            if (requestedAmount.compareTo(remainingAmount) < 0) {
                if (tenPercent.compareTo(minVnpayAmount) < 0) {
                    throw new RequestInvalidException(
                            "Order 10% value (" + formatVnd(tenPercent) + " VND) is less than minimum 5,000 VND. Partial payment is not supported for this low-value order. Please pay the full remaining amount (" + formatVnd(remainingAmount) + " VND)!");
                }

                if (requestedAmount.compareTo(tenPercent) < 0) {
                    throw new RequestInvalidException(
                            "Partial payment amount must be at least 10% of total order value (" + formatVnd(tenPercent) + " VND)!");
                }

                if (requestedAmount.compareTo(minVnpayAmount) < 0) {
                    throw new RequestInvalidException(
                            "Payment amount must be at least 5,000 VND (minimum transaction on VNPay)!");
                }
            }

            amountToPay = requestedAmount;
        }

        if (amountToPay.compareTo(minVnpayAmount) < 0) {
            throw new RequestInvalidException(
                    "Payment amount (" + formatVnd(amountToPay) + " VND) is below the VNPay minimum transaction limit of 5,000 VND!");
        }

        // Reuse existing PENDING payment if present, otherwise create new
        Payment payment = paymentRepository
                .findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(order.getId(), PaymentStatus.PENDING)
                .map(existing -> {
                    existing.setTotalFinalPrice(amountToPay.doubleValue());
                    existing.setDiscountRate(discountRate);
                    return paymentRepository.save(existing);
                })
                .orElseGet(() -> {
                    Payment newPayment = Payment.builder()
                            .order(order)
                            .discountRate(discountRate)
                            .totalFinalPrice(amountToPay.doubleValue())
                            .paymentStatus(PaymentStatus.PENDING)
                            .build();
                    return paymentRepository.save(newPayment);
                });

        // Generate signed VNPay URL
        String paymentUrl = VnPayUtil.createPaymentUrl(
                vnPayConfig,
                payment.getId(),
                order.getOrderNo(),
                amountToPay,
                clientIp
        );

        log.info("Created payment id: {} for orderId: {}, amountToPay: {}, alreadyPaid: {}, remaining: {}",
                payment.getId(), order.getId(), amountToPay, alreadyPaid, remainingAmount);

        return PaymentResponseDTO.builder()
                .id(payment.getId())
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .totalFinalPrice(amountToPay)
                .paidAmount(alreadyPaid)
                .remainingAmount(remainingAmount)
                .discountRate(payment.getDiscountRate())
                .paymentStatus(payment.getPaymentStatus())
                .paymentUrl(paymentUrl)
                .createdBy(payment.getCreatedBy())
                .createdDate(payment.getCreatedDate())
                .lastModifiedDate(payment.getLastModifiedDate())
                .build();
    }

    @Transactional(readOnly = true)
    public VnPayReturnResponseDTO processReturn(Map<String, String> params) {
        boolean isSignatureValid = VnPayUtil.verifySignature(params, vnPayConfig.getSecretKey());
        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String txnRef = params.get("vnp_TxnRef");
        Long paymentId = VnPayUtil.extractPaymentId(txnRef);

        boolean isSuccess = isSignatureValid && "00".equals(responseCode) && "00".equals(transactionStatus);
        String message;
        if (!isSignatureValid) {
            message = "Invalid signature";
        } else if (isSuccess) {
            message = "Payment transaction successful";
        } else {
            message = "Payment transaction failed or cancelled (Response code: " + responseCode + ")";
        }

        String amountStr = params.get("vnp_Amount");
        String displayAmount = "";
        if (amountStr != null && !amountStr.isBlank()) {
            try {
                long rawAmount = Long.parseLong(amountStr);
                displayAmount = String.valueOf(rawAmount / 100);
            } catch (NumberFormatException ignored) {
                displayAmount = amountStr;
            }
        }

        log.info("Processed VNPay return: txnRef={}, paymentId={}, success={}, responseCode={}",
                txnRef, paymentId, isSuccess, responseCode);

        // Strictly NO DB update on return URL
        return VnPayReturnResponseDTO.builder()
                .success(isSuccess)
                .responseCode(responseCode)
                .transactionStatus(transactionStatus)
                .message(message)
                .paymentId(paymentId)
                .txnRef(txnRef)
                .transactionNo(params.get("vnp_TransactionNo"))
                .bankCode(params.get("vnp_BankCode"))
                .amount(displayAmount)
                .payDate(params.get("vnp_PayDate"))
                .orderInfo(params.get("vnp_OrderInfo"))
                .build();
    }

    @Transactional
    public VnPayIpnResponseDTO processIpn(Map<String, String> params) {
        try {
            log.info("Processing VNPay IPN with params: {}", params);

            // 1. Check signature (fail -> 97)
            if (!VnPayUtil.verifySignature(params, vnPayConfig.getSecretKey())) {
                log.warn("VNPay IPN: Invalid signature for params: {}", params);
                return VnPayIpnResponseDTO.invalidChecksum();
            }

            // 2. Extract paymentId & find Payment with pessimistic lock (not found -> 01)
            String txnRef = params.get("vnp_TxnRef");
            Long paymentId = VnPayUtil.extractPaymentId(txnRef);
            if (paymentId == null) {
                log.warn("VNPay IPN: Unable to extract paymentId from txnRef: {}", txnRef);
                return VnPayIpnResponseDTO.orderNotFound();
            }

            Optional<Payment> paymentOpt = paymentRepository.findByIdWithLock(paymentId);
            if (paymentOpt.isEmpty()) {
                log.warn("VNPay IPN: Payment not found with id: {}", paymentId);
                return VnPayIpnResponseDTO.orderNotFound();
            }

            Payment payment = paymentOpt.get();

            // 3. Check amount (mismatch -> 04)
            String amountStr = params.get("vnp_Amount");
            if (amountStr == null || amountStr.isBlank()) {
                log.warn("VNPay IPN: Missing vnp_Amount");
                return VnPayIpnResponseDTO.invalidAmount();
            }

            long rawAmount;
            try {
                rawAmount = Long.parseLong(amountStr);
            } catch (NumberFormatException e) {
                log.warn("VNPay IPN: Unparseable vnp_Amount: {}", amountStr);
                return VnPayIpnResponseDTO.invalidAmount();
            }

            BigDecimal receivedAmount = BigDecimal.valueOf(rawAmount)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal expectedAmount = BigDecimal.valueOf(payment.getTotalFinalPrice())
                    .setScale(2, RoundingMode.HALF_UP);

            if (receivedAmount.compareTo(expectedAmount) != 0) {
                log.warn("VNPay IPN: Amount mismatch - received: {}, expected: {}", receivedAmount, expectedAmount);
                return VnPayIpnResponseDTO.invalidAmount();
            }

            // 4. Idempotent check: if payment has already reached terminal status, return RspCode 02
            if (payment.getPaymentStatus() == PaymentStatus.PAID
                    || payment.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID
                    || payment.getPaymentStatus() == PaymentStatus.FAILED
                    || payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
                log.info("VNPay IPN: Payment id {} has already reached terminal status ({})", paymentId, payment.getPaymentStatus());
                return VnPayIpnResponseDTO.orderAlreadyConfirmed();
            }

            // 5. Check transaction status
            String responseCode = params.get("vnp_ResponseCode");
            String transactionStatus = params.get("vnp_TransactionStatus");

            if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
                // Success: check if partial or full payment
                Order order = payment.getOrder();
                if (order != null) {
                    // Lock order row to prevent race conditions during cumulative sum check
                    Order lockedOrder = orderRepository.findByIdWithLock(order.getId()).orElse(order);

                    int discountRate = payment.getDiscountRate();
                    BigDecimal orderTotal = BigDecimal.valueOf(lockedOrder.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal discountMultiplier = BigDecimal.valueOf(100 - discountRate)
                            .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    BigDecimal orderTargetTotal = orderTotal.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);

                    Double priorPaidSum = paymentRepository.sumPaidAmountByOrderIdAndStatusesExcludingPayment(
                            lockedOrder.getId(),
                            List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_PAID),
                            payment.getId()
                    );
                    BigDecimal priorPaid = BigDecimal.valueOf(priorPaidSum != null ? priorPaidSum : 0.0)
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal currentPaymentAmount = BigDecimal.valueOf(payment.getTotalFinalPrice())
                            .setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newTotalPaid = priorPaid.add(currentPaymentAmount).setScale(2, RoundingMode.HALF_UP);

                    if (newTotalPaid.compareTo(orderTargetTotal) >= 0) {
                        // Fully paid
                        payment.setPaymentStatus(PaymentStatus.PAID);
                        paymentRepository.save(payment);

                        if (lockedOrder.getOrderStatus() == OrderStatus.AWAITING_PAYMENT) {
                            lockedOrder.setOrderStatus(OrderStatus.OK);
                            orderRepository.save(lockedOrder);
                            log.info("VNPay IPN: Payment {} marked as PAID. Order {} fully paid (total: {}) and transitioned from AWAITING_PAYMENT to OK",
                                    payment.getId(), lockedOrder.getId(), newTotalPaid);
                        } else {
                            log.warn("VNPay IPN: Payment {} marked as PAID, but Order {} was not in AWAITING_PAYMENT (current status: {}). Order status was not changed.",
                                    payment.getId(), lockedOrder.getId(), lockedOrder.getOrderStatus());
                        }
                    } else {
                        // Partially paid
                        payment.setPaymentStatus(PaymentStatus.PARTIALLY_PAID);
                        paymentRepository.save(payment);
                        log.info("VNPay IPN: Payment {} marked as PARTIALLY_PAID. Order {} paid {} of {}. Order remains AWAITING_PAYMENT.",
                                payment.getId(), lockedOrder.getId(), newTotalPaid, orderTargetTotal);
                    }
                } else {
                    payment.setPaymentStatus(PaymentStatus.PAID);
                    paymentRepository.save(payment);
                }
            } else if ("24".equals(responseCode)) {
                // Customer cancelled on VNPay
                payment.setPaymentStatus(PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                log.info("VNPay IPN: Payment {} marked as CANCELLED (customer cancelled on VNPay: responseCode=24)", payment.getId());
            } else {
                // Other transaction failure
                payment.setPaymentStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                log.warn("VNPay IPN: Payment {} marked as FAILED on VNPay: responseCode={}, transactionStatus={}",
                        payment.getId(), responseCode, transactionStatus);
            }

            return VnPayIpnResponseDTO.success();
        } catch (Exception e) {
            log.error("VNPay IPN: Unexpected error while processing IPN", e);
            return VnPayIpnResponseDTO.unknownError();
        }
    }

    @Transactional(readOnly = true)
    public ResultPaginationDTO getMyPaymentHistory(String username, PaymentStatus status, int page, int size) {
        if (username == null || username.isBlank() || "anonymousUser".equalsIgnoreCase(username)) {
            throw new UnauthorizeException("Please login to view your payment history!");
        }

        if (page < 1) {
            throw new RequestInvalidException("Page must be greater than or equal to 1");
        }
        if (size < 1) {
            throw new RequestInvalidException("Size must be greater than or equal to 1");
        }
        int pageSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdDate", "id"));
        Page<Payment> paymentPage = paymentRepository.findMyPaymentHistory(username, status, pageable);

        ResultPaginationDTO res = new ResultPaginationDTO();
        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(page);
        meta.setPageSize(pageSize);
        meta.setPages(paymentPage.getTotalPages());
        meta.setTotal(paymentPage.getTotalElements());
        meta.setHasNext(paymentPage.hasNext());
        meta.setHasPrevious(paymentPage.hasPrevious());
        res.setMeta(meta);

        if (paymentPage.isEmpty()) {
            res.setResult(Collections.emptyList());
            return res;
        }

        Set<Long> orderIds = paymentPage.getContent().stream()
                .filter(p -> p.getOrder() != null && p.getOrder().getId() != null)
                .map(p -> p.getOrder().getId())
                .collect(Collectors.toSet());

        Map<Long, BigDecimal> paidSumsByOrderId = new HashMap<>();
        if (!orderIds.isEmpty()) {
            List<Object[]> rows = paymentRepository.sumPaidAmountByOrderIdsAndStatuses(
                    orderIds,
                    List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_PAID)
            );
            if (rows != null) {
                for (Object[] row : rows) {
                    if (row != null && row.length >= 2 && row[0] != null) {
                        Long oId = (Long) row[0];
                        Double sumVal = (Double) row[1];
                        paidSumsByOrderId.put(oId, BigDecimal.valueOf(sumVal != null ? sumVal : 0.0).setScale(2, RoundingMode.HALF_UP));
                    }
                }
            }
        }

        List<PaymentHistoryResponseDTO> responseList = paymentPage.getContent().stream()
                .map(p -> {
                    Order order = p.getOrder();
                    BigDecimal orderTargetTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal totalPaid = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    BigDecimal remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    Long orderId = null;
                    String orderNo = null;

                    if (order != null) {
                        orderId = order.getId();
                        orderNo = order.getOrderNo();
                        BigDecimal orderTotal = BigDecimal.valueOf(order.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
                        int discountRate = p.getDiscountRate();
                        BigDecimal discountMultiplier = BigDecimal.valueOf(100 - discountRate)
                                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                        orderTargetTotal = orderTotal.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);

                        totalPaid = paidSumsByOrderId.getOrDefault(order.getId(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                        remaining = orderTargetTotal.subtract(totalPaid).setScale(2, RoundingMode.HALF_UP);
                        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                            remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        }
                    }

                    return PaymentHistoryResponseDTO.builder()
                            .id(p.getId())
                            .orderId(orderId)
                            .orderNo(orderNo)
                            .paymentStatus(p.getPaymentStatus())
                            .amount(BigDecimal.valueOf(p.getTotalFinalPrice()).setScale(2, RoundingMode.HALF_UP))
                            .discountRate(p.getDiscountRate())
                            .createdDate(p.getCreatedDate())
                            .orderTotalAmount(orderTargetTotal)
                            .orderPaidAmount(totalPaid)
                            .orderRemainingAmount(remaining)
                            .build();
                })
                .toList();

        res.setResult(responseList);
        return res;
    }

    @Transactional(readOnly = true)
    public InvoiceResponseDTO getInvoiceData(Long paymentId, String username, Collection<? extends GrantedAuthority> authorities) {
        if (paymentId == null) {
            throw new RequestInvalidException("Payment ID must not be null");
        }

        Payment payment = paymentRepository.findWithOrderById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId));

        PaymentStatus status = payment.getPaymentStatus();
        if (status != PaymentStatus.PAID && status != PaymentStatus.PARTIALLY_PAID) {
            throw new RequestInvalidException(
                    "Cannot generate invoice for payment with status " + status + ". Only PAID or PARTIALLY_PAID payments can have invoices.");
        }

        Order order = payment.getOrder();
        if (order == null) {
            throw new ResourceNotFoundException("Associated order not found for payment id: " + paymentId);
        }

        boolean isStaffOrHigher = authorities != null && authorities.stream().anyMatch(a ->
                "ROLE_ADMIN".equals(a.getAuthority())
                        || "ROLE_MANAGER".equals(a.getAuthority())
                        || "ROLE_STAFF".equals(a.getAuthority()));

        if (!isStaffOrHigher) {
            if (username == null || username.isBlank()
                    || order.getCustomerInformation() == null
                    || order.getCustomerInformation().getUser() == null
                    || !username.equalsIgnoreCase(order.getCustomerInformation().getUser().getUsername())) {
                throw new RequestInvalidException("You can only access invoices for your own orders!");
            }
        }

        String customerName = "Walk-in Guest";
        String customerPhone = "";
        String customerEmail = "";
        if (order.getCustomerInformation() != null && order.getCustomerInformation().getUser() != null) {
            User user = order.getCustomerInformation().getUser();
            if (user.getFullname() != null && !user.getFullname().isBlank()) {
                customerName = user.getFullname();
            } else if (user.getUsername() != null && !user.getUsername().isBlank()) {
                customerName = user.getUsername();
            }
            customerPhone = user.getPhone() != null ? user.getPhone() : "";
            customerEmail = user.getEmail() != null ? user.getEmail() : "";
        }

        List<OrderItem> orderItems = orderItemRepository.findAllByOrderId(order.getId());
        List<InvoiceItemResponseDTO> itemDTOs = new ArrayList<>();
        if (orderItems != null) {
            for (OrderItem oi : orderItems) {
                String itemName = (oi.getMenuItem() != null && oi.getMenuItem().getItemName() != null)
                        ? oi.getMenuItem().getItemName() : "Item";
                int qty = oi.getQuantity();
                BigDecimal subtotal = BigDecimal.valueOf(oi.getSubtotal()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal unitPrice = (qty > 0)
                        ? subtotal.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP)
                        : subtotal;

                itemDTOs.add(InvoiceItemResponseDTO.builder()
                        .itemName(itemName)
                        .quantity(qty)
                        .unitPrice(unitPrice)
                        .subtotal(subtotal)
                        .build());
            }
        }

        int discountRate = payment.getDiscountRate();
        BigDecimal orderTotal = BigDecimal.valueOf(order.getTotalAmount()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountMultiplier = BigDecimal.valueOf(100 - discountRate)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        BigDecimal orderTargetTotal = orderTotal.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);

        Double paidSum = paymentRepository.sumPaidAmountByOrderIdAndStatuses(order.getId(),
                List.of(PaymentStatus.PAID, PaymentStatus.PARTIALLY_PAID));
        BigDecimal totalPaid = BigDecimal.valueOf(paidSum != null ? paidSum : 0.0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = orderTargetTotal.subtract(totalPaid).setScale(2, RoundingMode.HALF_UP);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) {
            remaining = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        boolean isFullyPaid = (remaining.compareTo(BigDecimal.ZERO) == 0);
        String remainingMessage = isFullyPaid ? "Fully Paid" : ("Remaining: " + formatVnd(remaining) + " VND");

        String invoiceNo = "INV-" + order.getId() + "-" + payment.getId();

        return InvoiceResponseDTO.builder()
                .restaurantName(RestaurantInfoConstants.RESTAURANT_NAME)
                .restaurantAddress(RestaurantInfoConstants.RESTAURANT_ADDRESS)
                .restaurantPhone(RestaurantInfoConstants.RESTAURANT_PHONE)
                .invoiceNo(invoiceNo)
                .paymentId(payment.getId())
                .paymentStatus(payment.getPaymentStatus())
                .paymentDate(payment.getLastModifiedDate() != null ? payment.getLastModifiedDate() : payment.getCreatedDate())
                .paymentAmount(BigDecimal.valueOf(payment.getTotalFinalPrice()).setScale(2, RoundingMode.HALF_UP))
                .discountRate(payment.getDiscountRate())
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .orderDate(order.getCreatedDate())
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerEmail(customerEmail)
                .items(itemDTOs)
                .orderTotalAmount(orderTargetTotal)
                .orderPaidAmount(totalPaid)
                .orderRemainingAmount(remaining)
                .isFullyPaid(isFullyPaid)
                .remainingMessage(remainingMessage)
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long paymentId, String username, Collection<? extends GrantedAuthority> authorities) {
        InvoiceResponseDTO invoice = getInvoiceData(paymentId, username, authorities);
        return InvoicePdfGenerator.generatePdf(invoice);
    }

    private int resolveDiscountRate(Order order) {
        if (order == null) {
            return 0;
        }

        // If order already has a successful payment (PAID or PARTIALLY_PAID), reuse its discount rate
        List<Payment> existingPayments = paymentRepository.findByOrderId(order.getId());
        if (existingPayments != null) {
            for (Payment p : existingPayments) {
                if (p.getPaymentStatus() == PaymentStatus.PAID || p.getPaymentStatus() == PaymentStatus.PARTIALLY_PAID) {
                    return p.getDiscountRate();
                }
            }
        }

        // Otherwise resolve based on customer rank
        if (order.getCustomerInformation() != null && order.getCustomerInformation().getCustomerRank() != null) {
            RankCustomer rank = order.getCustomerInformation().getCustomerRank();
            if (rank == RankCustomer.CAOTHU) {
                return 15;
            } else if (rank == RankCustomer.DIAMOND) {
                return 10;
            } else if (rank == RankCustomer.GOLDEN) {
                return 5;
            }
        }

        return 0;
    }
}
