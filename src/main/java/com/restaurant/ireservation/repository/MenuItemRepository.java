package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {

    long countByCategoryId(Long categoryId);
}
