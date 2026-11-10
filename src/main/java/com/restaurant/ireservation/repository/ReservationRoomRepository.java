package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.ReservationRoom;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Module: VIP Room Management [6] & VIP Room Reservation [4]
 */
@Repository
public interface ReservationRoomRepository extends JpaRepository<ReservationRoom, Long> {

    boolean existsByRoomId(Long roomId);

    @Query("SELECT rr FROM ReservationRoom rr " +
           "WHERE rr.room.id IN :roomIds " +
           "AND rr.reservation.reservationStatus IN :statuses " +
           "AND rr.reservation.startTime < :endTime " +
           "AND rr.reservation.endTime > :startTime")
    List<ReservationRoom> findConflictingReservations(
        @Param("roomIds") List<Long> roomIds,
        @Param("statuses") Collection<ReservationStatus> statuses,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    @EntityGraph(attributePaths = {"room"})
    List<ReservationRoom> findByReservationId(Long reservationId);

    @EntityGraph(attributePaths = {"room"})
    List<ReservationRoom> findByReservationIdIn(Collection<Long> reservationIds);

    @Query("SELECT rr FROM ReservationRoom rr " +
           "WHERE rr.room.id IN :roomIds " +
           "AND rr.reservation.id != :excludedReservationId " +
           "AND rr.reservation.reservationStatus IN :statuses " +
           "AND rr.reservation.startTime < :endTime " +
           "AND rr.reservation.endTime > :startTime")
    List<ReservationRoom> findConflictingReservationsExcludingReservation(
        @Param("roomIds") List<Long> roomIds,
        @Param("excludedReservationId") Long excludedReservationId,
        @Param("statuses") Collection<ReservationStatus> statuses,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    @Modifying
    @Query("DELETE FROM ReservationRoom rr WHERE rr.reservation.id = :reservationId")
    void deleteByReservationId(@Param("reservationId") Long reservationId);

    // ─── VIP Room Analytics ───────────────────────────────────────────────────

    /**
     * Đếm số lượt đặt phòng theo từng room_id (Booking Frequency).
     * Trả về Object[]{roomId (Long), bookingCount (Long)}
     */
    @Query("SELECT rr.room.id, COUNT(rr) FROM ReservationRoom rr GROUP BY rr.room.id")
    List<Object[]> countBookingsGroupByRoom();

    /**
     * Tính tổng doanh thu (PAID payments) theo từng room_id (Revenue By VIP Room).
     * Join qua reservation -> orders -> payments.
     * Trả về Object[]{roomId (Long), totalRevenue (Double)}
     */
    @Query("SELECT rr.room.id, COALESCE(SUM(p.totalFinalPrice), 0.0) " +
           "FROM ReservationRoom rr " +
           "JOIN rr.reservation res " +
           "JOIN Order o ON o.reservation.id = res.id " +
           "JOIN Payment p ON p.order.id = o.id " +
           "WHERE p.paymentStatus IN :paidStatuses " +
           "GROUP BY rr.room.id")
    List<Object[]> sumRevenueGroupByRoom(@Param("paidStatuses") Collection<com.restaurant.ireservation.domain.enumerations.PaymentStatus> paidStatuses);
}
