package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;

import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Collection;
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

    @Query("SELECT r FROM Reservation r " +
            "WHERE r.startTime >= :startTime AND r.startTime <= :endTime " +
            "AND r.reservationStatus NOT IN :excludedStatuses")
    List<Reservation> findReservationsInDateRange(
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime,
            @Param("excludedStatuses") Collection<ReservationStatus> excludedStatuses
    );


    @Query("""
        SELECT r.id FROM Reservation r
        WHERE r.reservationStatus = :status
          AND r.startTime < :cutoffTime
        ORDER BY r.startTime ASC
    """)
    List<Long> findExpiredPendingReservationIds(
            @Param("status") ReservationStatus status,
            @Param("cutoffTime") Instant cutoffTime,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Reservation r
        SET r.reservationStatus = :newStatus,
            r.lastModifiedDate = :now,
            r.lastModifiedBy = :modifiedBy
        WHERE r.id = :id
          AND r.reservationStatus = :expectedStatus
          AND r.startTime < :cutoffTime
    """)
    int updateStatusIfCurrentStatusAndStartTimeBefore(
            @Param("id") Long id,
            @Param("expectedStatus") ReservationStatus expectedStatus,
            @Param("newStatus") ReservationStatus newStatus,
            @Param("cutoffTime") Instant cutoffTime,
            @Param("now") Instant now,
            @Param("modifiedBy") String modifiedBy
    );

}
