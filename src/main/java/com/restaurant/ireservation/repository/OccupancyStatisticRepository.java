package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.document.OccupancyStatistic;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface OccupancyStatisticRepository extends MongoRepository<OccupancyStatistic, String> {
}
