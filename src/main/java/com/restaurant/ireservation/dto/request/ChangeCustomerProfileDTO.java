package com.restaurant.ireservation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;


public record ChangeCustomerProfileDTO(
        @NotBlank(message = "Customer name can't be MT")
        String fullname,
        String gender,
        String phone,
        @Past(message = "The date of birth must be in the past.")
        @DateTimeFormat(pattern = "yyyy-MM-dd")
        LocalDate dateOfBirth,
        String indentityCard,
        String address

) {
}
