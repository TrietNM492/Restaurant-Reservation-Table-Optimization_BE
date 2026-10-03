package com.restaurant.ireservation.error;

import com.restaurant.ireservation.util.RestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalException {

    @ExceptionHandler(value = {ResourceNotFoundException.class})
    public ResponseEntity<RestResponse<?>> handleResourceNotFoundException(ResourceNotFoundException e) {
        int statusCode = HttpStatus.NOT_FOUND.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setTimestamp(LocalDateTime.now());
        res.setMessage(e.getMessage());
        res.setError("Not Found");
        return ResponseEntity.status(statusCode).body(res);
    }

    @ExceptionHandler(value = {UserNotActivatedException.class})
    public ResponseEntity<RestResponse<?>> handleUserNotActivatedException(UserNotActivatedException e) {
        int statusCode = HttpStatus.UNAUTHORIZED.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setTimestamp(LocalDateTime.now());
        res.setError("User Inactive!");
        res.setMessage(e.getMessage());
        return ResponseEntity.status(statusCode).body(res);
    }

    @ExceptionHandler(value = {UserAlreadyUsedException.class})
    public ResponseEntity<RestResponse<?>> handleUserAlreadyUsedException(UserAlreadyUsedException e) {
        int statusCode = HttpStatus.CONFLICT.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setTimestamp(LocalDateTime.now());
        res.setError("User Already exist!");
        res.setMessage(e.getMessage());
        return ResponseEntity.status(statusCode).body(res);
    }


    @ExceptionHandler(value = {RequestInvalidException.class})
    public ResponseEntity<RestResponse<?>> handleBadRequestException(RequestInvalidException e) {
        int statusCode = HttpStatus.BAD_REQUEST.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setMessage(e.getMessage());
        res.setError("Bad Request");
        res.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(statusCode).body(res);
    }

    @ExceptionHandler(value = {AccountResourceException.class})
    public ResponseEntity<RestResponse<?>> handleAccountResourceException(AccountResourceException e) {
        int statusCode = HttpStatus.BAD_REQUEST.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setMessage(e.getMessage());
        res.setError("Error with User Profile");
        res.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(statusCode).body(res);
    }

    @ExceptionHandler(value = {UnauthorizeException.class})
    public ResponseEntity<RestResponse<?>> hanldeUnauthorizeException(UnauthorizeException e) {
        int statusCode = HttpStatus.UNAUTHORIZED.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setMessage(e.getMessage());
        res.setError("You Are Not Login");
        res.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(statusCode).body(res);
    }
}
