package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.document.WaitingTimeLog;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WaitingTimeLogRepository extends MongoRepository<WaitingTimeLog, String> {
}
