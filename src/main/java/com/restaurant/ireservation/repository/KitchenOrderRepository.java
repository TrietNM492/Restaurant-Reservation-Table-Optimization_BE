package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.KitchenOrder;
import com.restaurant.ireservation.domain.enumerations.OrderKitchenStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, Long>, JpaSpecificationExecutor<KitchenOrder> {

    List<KitchenOrder> findAllByStatusIn(Collection<OrderKitchenStatus> statuses);

    List<KitchenOrder> findAllByStatusInOrderByPriorityDescCreatedDateAsc(Collection<OrderKitchenStatus> statuses);

    Optional<KitchenOrder> findByOrderId(Long orderId);

    List<KitchenOrder> findAllByOrderIdOrderByCreatedDateAsc(Long orderId);

    Optional<KitchenOrder> findFirstByOrderIdOrderByCreatedDateDesc(Long orderId);

    boolean existsByOrderId(Long orderId);

    boolean existsByOrderIdAndStatusIn(Long orderId, Collection<OrderKitchenStatus> statuses);

    List<KitchenOrder> findByOrderIdAndStatusIn(Long orderId, Collection<OrderKitchenStatus> statuses);

    long countByStatusIn(Collection<OrderKitchenStatus> statuses);

    @Query("SELECT ko FROM KitchenOrder ko " +
           "LEFT JOIN ko.order o " +
           "WHERE (:status IS NULL OR ko.status = :status) " +
           "AND (:isVip IS NULL OR o.isVipRoom = :isVip) " +
           "AND (:search IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(ko.note) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY ko.priority DESC, ko.createdDate ASC")
    List<KitchenOrder> findKitchenQueue(
            @Param("status") OrderKitchenStatus status,
            @Param("isVip") Boolean isVip,
            @Param("search") String search
    );

    @Query("SELECT ko FROM KitchenOrder ko " +
           "LEFT JOIN ko.order o " +
           "WHERE ko.status IN :activeStatuses " +
           "AND (:isVip IS NULL OR o.isVipRoom = :isVip) " +
           "AND (:search IS NULL OR LOWER(o.orderNo) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "     OR LOWER(ko.note) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY ko.priority DESC, ko.createdDate ASC")
    List<KitchenOrder> findActiveKitchenQueue(
            @Param("activeStatuses") Collection<OrderKitchenStatus> activeStatuses,
            @Param("isVip") Boolean isVip,
            @Param("search") String search
    );

    /**
     * FR-030: Average Preparation Time
     * Lấy các đơn đã hoàn thành có đủ actualStartTime và actualReadyTime.
     * Tính toán trung bình được thực hiện phía Java để tránh lỗi JPQL dialect.
     */
    @Query("SELECT ko FROM KitchenOrder ko " +
           "WHERE ko.actualStartTime IS NOT NULL AND ko.actualReadyTime IS NOT NULL " +
           "AND ko.status IN :completedStatuses")
    List<KitchenOrder> findCompletedWithTimestamps(
            @Param("completedStatuses") Collection<OrderKitchenStatus> completedStatuses
    );

    /**
     * FR-030: Kitchen Utilization — đếm số đơn theo từng trạng thái để tính tải bếp.
     * Trả về list [status, count].
     */
    @Query("SELECT ko.status, COUNT(ko) FROM KitchenOrder ko GROUP BY ko.status")
    List<Object[]> countGroupedByStatus();
}
