package com.restaurant.ireservation.dto.response;

import com.restaurant.ireservation.domain.enumerations.Department;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor@AllArgsConstructor
public class StaffInformationDTO {

    String employeeCode;
    Department department;
    LocalDate joinDate;
    LocalDate resignationDate;
    long tenure;
    double salary;
    UserRes user;

    @Getter
    @Setter
    public static class UserRes {
        UserSecurityRes userSecurityRes;
        String phone;


    }
}
