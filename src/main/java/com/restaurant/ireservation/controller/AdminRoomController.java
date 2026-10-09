package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.dto.request.ChangeRoomStatusRequest;
import com.restaurant.ireservation.dto.request.RoomRequest;
import com.restaurant.ireservation.dto.response.RoomResponse;
import com.restaurant.ireservation.service.RoomService;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import com.restaurant.ireservation.util.annotation.ApiMessage;
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

/**
 * Module: VIP Room Management [6]
 */
@RestController
@RequestMapping("/api/v1/admin/rooms")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AdminRoomController {

    RoomService roomService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Fetch all rooms")
    public ResponseEntity<ResultPaginationDTO> fetchAllRooms(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "roomType", required = false) RoomType roomType,
            @RequestParam(name = "roomStatus", required = false) RoomStatus roomStatus,
            Pageable pageable
    ) {
        return ResponseEntity.ok(roomService.fetchRooms(q, roomType, roomStatus, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Fetch room by id")
    public ResponseEntity<RoomResponse> fetchRoomById(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(roomService.fetchRoomById(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Create room successfully")
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody RoomRequest request
    ) {
        RoomResponse response = roomService.createRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Update room successfully")
    public ResponseEntity<RoomResponse> updateRoomWithId(
            @Positive(message = "Id must be positive")
            @PathVariable("id") Long id,
            @Valid @RequestBody RoomRequest request
    ) {
        request.setId(id);
        return ResponseEntity.ok(roomService.updateRoom(request));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Update room successfully")
    public ResponseEntity<RoomResponse> updateRoom(
            @Valid @RequestBody RoomRequest request
    ) {
        return ResponseEntity.ok(roomService.updateRoom(request));
    }

    @PatchMapping("/status")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Change room status successfully")
    public ResponseEntity<RoomResponse> changeRoomStatus(
            @Valid @RequestBody ChangeRoomStatusRequest request
    ) {
        return ResponseEntity.ok(roomService.changeRoomStatus(request));
    }

    @PatchMapping("/{id}/toggle-maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Toggle room maintenance successfully")
    public ResponseEntity<RoomResponse> toggleMaintenanceRoom(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(roomService.toggleMaintenanceRoom(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ApiMessage("Delete room successfully")
    public ResponseEntity<Void> deleteRoom(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        roomService.deleteRoom(id);
        return ResponseEntity.ok().build();
    }
}
