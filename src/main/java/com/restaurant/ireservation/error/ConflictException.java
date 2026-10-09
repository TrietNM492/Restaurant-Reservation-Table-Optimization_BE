package com.restaurant.ireservation.error;

public class ConflictException extends RuntimeException {

    public ConflictException(String msg) {
        super(msg);
    }
}
