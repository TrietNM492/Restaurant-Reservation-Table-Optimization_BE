package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.Notification;
import com.restaurant.ireservation.domain.enumerations.NotificationDeliveryStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationStatus;
import com.restaurant.ireservation.domain.enumerations.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {



    @Query("""
            SELECT n.id
            FROM Notification n
            WHERE n.type = :type
              AND n.deliveryStatus = :status
              AND n.scheduledAt <= :now
            ORDER BY n.scheduledAt ASC
            """)
    List<Long> findDueNotificationIds(
            @Param("type") NotificationType type,
            @Param("status") NotificationDeliveryStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    @Modifying
    @Query("""
        UPDATE Notification n
        SET n.deliveryStatus = :processing
        WHERE n.id = :id
          AND n.deliveryStatus = :pending
        """)
    int claimNotification(
            @Param("id") Long id,
            @Param("pending") NotificationDeliveryStatus pending,
            @Param("processing") NotificationDeliveryStatus processing
    );

    Page<Notification> findByUser_IdOrderByCreatedDateDesc(Long userId, Pageable pageable);

    long countByUser_IdAndStatus(Long userId, NotificationStatus status);

    Optional<Notification> findByIdAndUser_Id(Long id, Long userId);

}
