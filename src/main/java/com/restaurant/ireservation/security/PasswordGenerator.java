package com.restaurant.ireservation.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class PasswordGenerator {

    private static final String CHARACTERS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%";

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(int length) {
        StringBuilder password = new StringBuilder();
        for (int i = 0; i< length; i++) {
            int index = RANDOM.nextInt(CHARACTERS.length());
            password.append(CHARACTERS.charAt(index));
        }
        return password.toString();
    }
}
