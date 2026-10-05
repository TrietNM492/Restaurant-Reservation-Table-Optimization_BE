package com.restaurant.ireservation.dto.request;

import com.restaurant.ireservation.domain.enumerations.Department;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffInformationRequest extends UserRequestDTO {
    String department;

}
