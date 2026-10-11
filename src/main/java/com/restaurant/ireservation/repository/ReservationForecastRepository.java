package com.restaurant.ireservation.repository;

import com.restaurant.ireservation.domain.document.ReservationForecast;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReservationForecastRepository extends MongoRepository<ReservationForecast, String> {
}
