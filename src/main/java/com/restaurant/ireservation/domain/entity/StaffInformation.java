package com.restaurant.ireservation.domain.entity;

import com.restaurant.ireservation.domain.enumerations.Department;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "staff_information")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class StaffInformation {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", unique = true)
    User user;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @Column(length = 50)
    String employeeCode;

    @Enumerated(EnumType.STRING)
    Department department;

    LocalDate joinDate;

    LocalDate resignationDate;

    double salary;

    public long tenureStaff() {
        LocalDate endDate = (resignationDate == null) ? LocalDate.now() : resignationDate;

        return ChronoUnit.YEARS.between(joinDate, endDate);
    }

    public double baseSalary() {
        double luong_co_ban = 5000000;
        return luong_co_ban * (tenureStaff() + 1);
    }


}
