package com.restaurant.ireservation.error;

import com.restaurant.ireservation.util.RestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

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

    @ExceptionHandler(value = {ReservationConflictException.class})
    public ResponseEntity<RestResponse<?>> handleReservationConflictException(ReservationConflictException e) {
        int statusCode = HttpStatus.CONFLICT.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setMessage(e.getMessage());
        res.setError("Reservation Conflict");
        res.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(statusCode).body(res);
    }

    @ExceptionHandler(value = {MethodArgumentNotValidException.class})
    public ResponseEntity<RestResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        int statusCode = HttpStatus.BAD_REQUEST.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        res.setMessage(errors.size() == 1 ? errors.getFirst() : errors);
        res.setError("Validation Failed");
        res.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(statusCode).body(res);
    }

    @ExceptionHandler(value = {AccessDeniedException.class})
    public ResponseEntity<RestResponse<?>> handleAccessDeniedException(AccessDeniedException e) {
        int statusCode = HttpStatus.FORBIDDEN.value();
        var res = new RestResponse<>();
        res.setStatusCode(statusCode);
        res.setMessage(e.getMessage());
        res.setError("Forbidden");
        res.setTimestamp(LocalDateTime.now());
        return ResponseEntity.status(statusCode).body(res);
    }
}
