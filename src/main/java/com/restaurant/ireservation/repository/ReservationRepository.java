package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCreatedBy(String createdBy);

    @Query("SELECT r FROM Reservation r " +
            "WHERE r.startTime >= :startTime AND r.startTime <= :endTime " +
            "AND r.reservationStatus NOT IN :excludedStatuses")
    List<Reservation> findReservationsInDateRange(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses
    );
}