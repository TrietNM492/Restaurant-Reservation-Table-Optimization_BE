package com.restaurant.ireservation.error;

public class UnauthorizeException extends RuntimeException {

    public UnauthorizeException(String msg) {
        super(msg);
    }
}
