package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
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

    List<ReservationTable> findByReservationId(Long reservationId);

    @Modifying
    @Query("DELETE FROM ReservationTable rt WHERE rt.reservation.id = :reservationId")
    void deleteByReservationId(@Param("reservationId") Long reservationId);
}
