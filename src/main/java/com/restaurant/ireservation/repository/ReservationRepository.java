package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    List<Reservation> findByCreatedBy(String createdBy);

    long countByCreatedDateGreaterThanEqualAndCreatedDateLessThan(Instant from, Instant to);


    @Query("""
            SELECT r.reservationStatus, COUNT(r)
                    FROM Reservation r
                    WHERE r.startTime >= :from
                      AND r.startTime < :to
                      AND r.reservationStatus IN (
                          :completed, :cancelled, :noShow
                      )
                    GROUP BY r.reservationStatus
            
            """)
    List<Object[]> countFinalReservationByStatus(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("completed") ReservationStatus completed,
            @Param("cancelled") ReservationStatus cancelled,
            @Param("noShow") ReservationStatus noShow
    );


    @Query("""
            SELECT FUNCTION('DATE', r.createdDate), COUNT(r)
                    FROM Reservation r
                    WHERE r.createdDate >= :from
                      AND r.createdDate < :to
                    GROUP BY FUNCTION('DATE', r.createdDate)
                    ORDER BY FUNCTION('DATE', r.createdDate)
            
            """)
    List<Object[]> countReservationByCreatedDate(@Param("from") Instant from, @Param("to") Instant to);
}