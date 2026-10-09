package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.FoodOrigin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodOriginRepository extends JpaRepository<FoodOrigin, Long> {

}
