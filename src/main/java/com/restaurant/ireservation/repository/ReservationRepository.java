package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCreatedBy(String createdBy);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForAllocation(@Param("id") Long id);

    @Query("SELECT r FROM Reservation r WHERE r.reservationType = com.restaurant.ireservation.domain.enumerations.ReservationType.TABLE "
            + "AND (:reservationId IS NULL OR r.id = :reservationId) "
            + "AND (SELECT COUNT(DISTINCT rt.table.id) FROM ReservationTable rt WHERE rt.reservation = r) >= 2")
    Page<Reservation> findTableCombinations(@Param("reservationId") Long reservationId, Pageable pageable);
}
