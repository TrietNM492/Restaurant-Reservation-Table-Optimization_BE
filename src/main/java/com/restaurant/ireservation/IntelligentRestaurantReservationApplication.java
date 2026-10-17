package com.restaurant.ireservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class IntelligentRestaurantReservationApplication {

	public static void main(String[] args) {
		SpringApplication.run(IntelligentRestaurantReservationApplication.class, args);
	}

}
