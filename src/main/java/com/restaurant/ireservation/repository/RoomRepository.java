package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Room;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Module: VIP Room Management [6]
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {

    boolean existsByRoomName(String roomName);

    boolean existsByRoomNameAndIdNot(String roomName, Long id);

    long countByRoomStatus(RoomStatus roomStatus);

    List<Room> findByRoomType(RoomType roomType);

    List<Room> findByRoomStatus(RoomStatus roomStatus);

    @Query(value = "SELECT r FROM Room r WHERE " +
            "(:roomType IS NULL OR r.roomType = :roomType) AND " +
            "(:roomStatus IS NULL OR r.roomStatus = :roomStatus) AND " +
            "(:q IS NULL OR :q = '' OR " +
            "LOWER(r.roomName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(CAST(r.roomType AS string)) LIKE LOWER(CONCAT('%', :q, '%')))",
            countQuery = "SELECT COUNT(r) FROM Room r WHERE " +
            "(:roomType IS NULL OR r.roomType = :roomType) AND " +
            "(:roomStatus IS NULL OR r.roomStatus = :roomStatus) AND " +
            "(:q IS NULL OR :q = '' OR " +
            "LOWER(r.roomName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(CAST(r.roomType AS string)) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Room> searchRooms(
            @Param("q") String q,
            @Param("roomType") RoomType roomType,
            @Param("roomStatus") RoomStatus roomStatus,
            Pageable pageable
    );
}
