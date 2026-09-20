package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.StaffInformation;
import com.restaurant.ireservation.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffInformation, Long> {

    Optional<StaffInformation> findByUserId(Long userId);
}
