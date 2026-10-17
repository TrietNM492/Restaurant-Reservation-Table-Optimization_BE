package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Payment;
import com.restaurant.ireservation.domain.enumerations.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findFirstByOrderIdAndPaymentStatusOrderByCreatedDateDesc(Long orderId, PaymentStatus paymentStatus);

    boolean existsByOrderIdAndPaymentStatus(Long orderId, PaymentStatus paymentStatus);

    @EntityGraph(attributePaths = {"order"})
    Optional<Payment> findWithOrderById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(p.totalFinalPrice), 0.0) FROM Payment p " +
            "WHERE p.order.id = :orderId AND p.paymentStatus IN :statuses")
    Double sumPaidAmountByOrderIdAndStatuses(@Param("orderId") Long orderId,
                                            @Param("statuses") Collection<PaymentStatus> statuses);

    @Query("SELECT COALESCE(SUM(p.totalFinalPrice), 0.0) FROM Payment p " +
            "WHERE p.order.id = :orderId AND p.paymentStatus IN :statuses AND p.id != :excludedPaymentId")
    Double sumPaidAmountByOrderIdAndStatusesExcludingPayment(@Param("orderId") Long orderId,
                                                           @Param("statuses") Collection<PaymentStatus> statuses,
                                                           @Param("excludedPaymentId") Long excludedPaymentId);

    @EntityGraph(attributePaths = {"order", "order.customerInformation", "order.customerInformation.user"})
    @Query(value = "SELECT p FROM Payment p " +
            "WHERE LOWER(p.order.customerInformation.user.username) = LOWER(:username) " +
            "AND (:status IS NULL OR p.paymentStatus = :status)",
           countQuery = "SELECT COUNT(p) FROM Payment p " +
            "JOIN p.order o " +
            "JOIN o.customerInformation ci " +
            "JOIN ci.user u " +
            "WHERE LOWER(u.username) = LOWER(:username) " +
            "AND (:status IS NULL OR p.paymentStatus = :status)")
    Page<Payment> findMyPaymentHistory(@Param("username") String username,
                                      @Param("status") PaymentStatus status,
                                      Pageable pageable);

    @Query("SELECT p.order.id, COALESCE(SUM(p.totalFinalPrice), 0.0) FROM Payment p " +
            "WHERE p.order.id IN :orderIds AND p.paymentStatus IN :statuses " +
            "GROUP BY p.order.id")
    List<Object[]> sumPaidAmountByOrderIdsAndStatuses(@Param("orderIds") Collection<Long> orderIds,
                                                     @Param("statuses") Collection<PaymentStatus> statuses);
}
