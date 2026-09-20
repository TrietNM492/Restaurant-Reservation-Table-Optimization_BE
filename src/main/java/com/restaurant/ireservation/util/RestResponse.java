package com.restaurant.ireservation.util;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class RestResponse<T> {
    private LocalDateTime timestamp;
    private int statusCode;
    private String error;
    private Object message;
    private T data;
}
