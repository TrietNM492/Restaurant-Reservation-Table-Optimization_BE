package com.restaurant.ireservation.util.constants;

public class Constants {

    public static final String LOGIN_REGEX = "^(?>[a-zA-Z0-9!$&*+=?^_`{|}~.-]+@[a-zA-Z0-9-]+(?:\\.[a-zA-Z0-9-]+)*)|(?>[_.@A-Za-z0-9-]+)$";
    public static final String REFRESH_TOKEN_EXPIRED = "161803398872309981550653";
    public static final String SYSTEM = "system";
    public static final String DEFAULT_LANGUAGE = "en";
    public static final String PHONE_REGEX = "^(0|\\+84)[0-9]{9}$";
    public static final String VERSION_API = "/api/v1";
    // Regex rule: At least 8 chars, one upper, one lower, one digit, one special char
    public static final String PASSWORD_REGEX = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@#$%^&*!?])[A-Za-z\\d@#$%^&*!?]{8,}$";

    public Constants() {

    }
}
