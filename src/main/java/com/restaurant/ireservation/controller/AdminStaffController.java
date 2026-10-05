package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.StaffInformationRequest;
import com.restaurant.ireservation.dto.response.StaffInformationDTO;
import com.restaurant.ireservation.dto.response.StaffInformationResponse;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.repository.UserRepository;
import com.restaurant.ireservation.service.StaffInformationService;
import com.restaurant.ireservation.service.UserService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    public ResponseEntity<StaffInformationResponse> createStaff(@Valid @RequestBody StaffInformationRequest request) {
        var staff = staffInformationService.createStaff(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(staff);
    }



}
