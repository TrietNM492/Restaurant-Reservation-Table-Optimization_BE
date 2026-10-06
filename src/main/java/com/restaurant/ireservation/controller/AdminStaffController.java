package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.dto.request.ChangeUserStatus;
import com.restaurant.ireservation.dto.request.StaffInformationRequest;
import com.restaurant.ireservation.dto.request.UpdateUserStaffRequest;
import com.restaurant.ireservation.dto.response.StaffInformationResponse;
import com.restaurant.ireservation.repository.StaffInformationRepository;
import com.restaurant.ireservation.service.StaffInformationService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import jakarta.validation.Valid;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/staffs")
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
public class AdminStaffController {


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

//    View staff detail
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffInformationResponse> fetchStaffById(@Positive @PathVariable("id") @NotNull(message = "Id is missing") Long staffId) {

        return ResponseEntity.ok(staffInformationService.fetchStaffById(staffId));

    }

//    Update staff by id

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StaffInformationResponse> updateStaff(@Valid @RequestBody UpdateUserStaffRequest request) {
        return ResponseEntity.ok(staffInformationService.updateStaff(request));
    }

//    inactive staff
    @PatchMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ChangeUserStatus> changeStatusStaff(@Valid @RequestBody ChangeUserStatus request) {
        ChangeUserStatus message = staffInformationService.toggleChangeStaffStatus(request);
        return ResponseEntity.ok(message);
    }




}
