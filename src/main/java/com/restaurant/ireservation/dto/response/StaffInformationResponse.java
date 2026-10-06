package com.restaurant.ireservation.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class StaffInformationResponse extends UserResonseDTO {
    String employeeCode;
    String department;
    double salary;
    LocalDate joinDate;
    LocalDate resignationDate;
}
