package com.restaurant.ireservation.error;

public class DuplicationException extends RuntimeException {

    public DuplicationException(String msg) {
        super(msg);
    }
}
