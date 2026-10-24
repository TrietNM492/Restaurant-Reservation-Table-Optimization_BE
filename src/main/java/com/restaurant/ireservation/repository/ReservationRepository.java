package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCreatedBy(String createdBy);

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
