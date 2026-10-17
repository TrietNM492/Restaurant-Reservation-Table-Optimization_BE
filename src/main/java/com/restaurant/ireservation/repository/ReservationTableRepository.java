package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.ReservationTable;
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

@Repository
public interface ReservationTableRepository extends JpaRepository<ReservationTable, Long> {

    boolean existsByTableId(Long tableId);

    @Query("SELECT rt FROM ReservationTable rt " +
           "WHERE rt.table.id IN :tableIds " +
           "AND rt.reservation.reservationStatus IN :statuses " +
           "AND rt.reservation.startTime < :endTime " +
           "AND rt.reservation.endTime > :startTime")
    List<ReservationTable> findConflictingReservations(
        @Param("tableIds") List<Long> tableIds,
        @Param("statuses") Collection<ReservationStatus> statuses,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );

    @EntityGraph(attributePaths = {"table"})
    List<ReservationTable> findByReservationId(Long reservationId);

    @Query("SELECT rt FROM ReservationTable rt " +
           "WHERE rt.table.id IN :tableIds " +
           "AND rt.reservation.id != :excludedReservationId " +
           "AND rt.reservation.reservationStatus IN :statuses " +
           "AND rt.reservation.startTime < :endTime " +
           "AND rt.reservation.endTime > :startTime")
    List<ReservationTable> findConflictingReservationsExcludingReservation(
        @Param("tableIds") List<Long> tableIds,
        @Param("excludedReservationId") Long excludedReservationId,
        @Param("statuses") Collection<ReservationStatus> statuses,
        @Param("startTime") Instant startTime,
        @Param("endTime") Instant endTime
    );


    @Modifying
    @Query("DELETE FROM ReservationTable rt WHERE rt.reservation.id = :reservationId")
    void deleteByReservationId(@Param("reservationId") Long reservationId);

    @Query("SELECT rt FROM ReservationTable rt " +
            "JOIN FETCH rt.reservation r " +
            "WHERE rt.table.id IN :tableIds " +
            "AND r.reservationStatus IN :statuses " +
            "AND r.startTime < :windowEnd " +
            "AND r.endTime > :windowStart")
    List<ReservationTable> findActiveBookingsInWindow(
            @Param("tableIds") Collection<Long> tableIds,
            @Param("statuses") Collection<ReservationStatus> statuses,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd
    );
}
