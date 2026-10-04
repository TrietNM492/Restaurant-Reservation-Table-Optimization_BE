package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.service.StaffInformationService;
import com.restaurant.ireservation.service.UserService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/staffs")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class AdminStaffController {

    final UserRepository userRepository;
    final UserService userService;
    final StaffInformationRepository staffInformationRepository;
    final StaffInformationService staffInformationService;

    @GetMapping
    public ResponseEntity<ResultPaginationDTO> fetchAllPublishStaff(
            @RequestParam(name = "q", required = false) String q,
            Pageable pageable
    ) {
        String key = q != null ? q.toLowerCase().trim() : "";
        return ResponseEntity.ok(staffInformationService.fetchStaff(key, pageable));
    }


}
