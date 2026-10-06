package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReservationTableRepository extends JpaRepository<ReservationTable, Long> {

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
}
