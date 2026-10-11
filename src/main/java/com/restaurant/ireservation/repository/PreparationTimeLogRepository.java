package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.document.PreparationTimeLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PreparationTimeLogRepository extends MongoRepository<PreparationTimeLog, String> {
}
