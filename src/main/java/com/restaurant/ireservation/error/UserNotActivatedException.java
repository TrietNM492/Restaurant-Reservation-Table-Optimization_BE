package com.restaurant.ireservation.error;

import javax.naming.AuthenticationException;

public class UserNotActivatedException extends AuthenticationException {

    public UserNotActivatedException(String message) {
        super(message);

    }

}
