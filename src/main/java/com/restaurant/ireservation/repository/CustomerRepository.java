package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.entity.CustomerInformation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<CustomerInformation, Long> {

    Optional<CustomerInformation> findOneByUserId(Long userId);

}
