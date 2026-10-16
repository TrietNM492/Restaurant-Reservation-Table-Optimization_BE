package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.ReservationRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Module: VIP Room Management [6]
 */
@Repository
public interface ReservationRoomRepository extends JpaRepository<ReservationRoom, Long> {

    boolean existsByRoomId(Long roomId);

    List<ReservationRoom> findByReservationId(Long reservationId);
}
