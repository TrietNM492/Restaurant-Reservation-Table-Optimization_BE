package com.restaurant.ireservation.dto.request;

import java.time.LocalDate;


public record ChangeCustomerProfileDTO(
        String fullname,
        String gender,
        String phone,
        LocalDate dateOfBirth,
        String indentityCard,
        String address

) {
}
