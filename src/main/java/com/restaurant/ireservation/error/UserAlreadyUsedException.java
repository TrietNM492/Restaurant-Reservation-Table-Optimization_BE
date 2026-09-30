package com.restaurant.ireservation.error;

public class UserAlreadyUsedException extends RuntimeException {

    public UserAlreadyUsedException(String message) {
        super(message);
    }
}
