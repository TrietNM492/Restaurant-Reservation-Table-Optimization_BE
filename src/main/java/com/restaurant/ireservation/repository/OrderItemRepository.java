package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.OrderItem;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
