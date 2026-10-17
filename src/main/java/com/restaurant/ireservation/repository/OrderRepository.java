package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Order;
import com.restaurant.ireservation.domain.enumerations.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Order o " +
            "JOIN o.reservation r " +
            "JOIN ReservationTable rt ON rt.reservation.id = r.id " +
            "WHERE rt.table.id = :tableId " +
            "AND o.orderStatus NOT IN :closedStatuses")
    boolean existsActiveOrderByTableId(@Param("tableId") Long tableId,
            @Param("closedStatuses") Collection<OrderStatus> closedStatuses);

    boolean existsByReservationIdAndOrderStatusNotIn(Long reservationId, Collection<OrderStatus> closedStatuses);

    @EntityGraph(attributePaths = { "reservation", "customerInformation", "customerInformation.user" })
    Optional<Order> findWithDetailsById(Long id);

    @Query("SELECT o FROM Order o " +
            "LEFT JOIN FETCH o.reservation r " +
            "LEFT JOIN FETCH o.customerInformation ci " +
            "WHERE ci.id = :customerInfoId " +
            "AND o.orderStatus NOT IN :closedStatuses " +
            "ORDER BY o.createdDate DESC, o.id DESC")
    List<Order> findActiveOrdersByCustomerInfoId(@Param("customerInfoId") Long customerInfoId,
            @Param("closedStatuses") Collection<OrderStatus> closedStatuses);
}
