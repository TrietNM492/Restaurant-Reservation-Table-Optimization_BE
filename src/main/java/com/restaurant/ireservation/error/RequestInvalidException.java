package com.restaurant.ireservation.error;

public class RequestInvalidException extends RuntimeException {

    public RequestInvalidException(String msg) {
        super(msg);
    }
}
