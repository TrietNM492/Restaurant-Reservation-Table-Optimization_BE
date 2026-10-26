package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @EntityGraph(attributePaths = {"menuItem"})
    List<OrderItem> findAllByOrderId(Long orderId);

    Optional<OrderItem> findByOrderIdAndMenuItemId(Long orderId, Long menuItemId);

    @EntityGraph(attributePaths = {"order", "menuItem"})
    Optional<OrderItem> findWithDetailsById(Long id);

    // ─── Kitchen Performance Analytics: Most Ordered Dishes ──────────────────

    /**
     * Thống kê Top món ăn được gọi nhiều nhất.
     * Trả về Object[]{menuItemId, dishName, categoryName, totalQuantity, totalRevenue, orderCount}
     * Sắp xếp giảm dần theo tổng số lượng gọi.
     */
    @Query("SELECT oi.menuItem.id, " +
           "       oi.menuItem.itemName, " +
           "       oi.menuItem.category.name, " +
           "       SUM(oi.quantity), " +
           "       SUM(oi.subtotal), " +
           "       COUNT(DISTINCT oi.order.id) " +
           "FROM OrderItem oi " +
           "WHERE oi.menuItem IS NOT NULL " +
           "GROUP BY oi.menuItem.id, oi.menuItem.itemName, oi.menuItem.category.name " +
           "ORDER BY SUM(oi.quantity) DESC")
    List<Object[]> findMostOrderedDishes(@Param("limit") int limit);
}
