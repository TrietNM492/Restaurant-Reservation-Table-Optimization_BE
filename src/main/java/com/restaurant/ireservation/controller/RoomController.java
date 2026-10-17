package com.restaurant.ireservation.controller;

import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.dto.request.ChangeRoomStatusRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module: VIP Room Management [6]
 */
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomController {

    RoomService roomService;

    @GetMapping
    @ApiMessage("Fetch rooms list")
    public ResponseEntity<ResultPaginationDTO> fetchAllRooms(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "roomType", required = false) RoomType roomType,
            @RequestParam(name = "roomStatus", required = false) RoomStatus roomStatus,
            Pageable pageable
    ) {
        return ResponseEntity.ok(roomService.fetchRooms(q, roomType, roomStatus, pageable));
    }

    @GetMapping("/available")
    @ApiMessage("Fetch available rooms")
    public ResponseEntity<List<RoomResponse>> fetchAvailableRooms() {
        return ResponseEntity.ok(roomService.fetchAvailableRooms());
    }

    @GetMapping("/type/{roomType}")
    @ApiMessage("Fetch rooms by type")
    public ResponseEntity<List<RoomResponse>> fetchRoomsByType(
            @PathVariable("roomType") RoomType roomType
    ) {
        return ResponseEntity.ok(roomService.fetchRoomsByType(roomType));
    }

    @GetMapping("/{id}")
    @ApiMessage("Fetch room details")
    public ResponseEntity<RoomResponse> fetchRoomById(
            @Positive(message = "Id must be positive")
            @PathVariable("id")
            @NotNull(message = "Id is required") Long id
    ) {
        return ResponseEntity.ok(roomService.fetchRoomById(id));
    }

    @PatchMapping("/{id}/status")
    @ApiMessage("Update room status successfully")
    public ResponseEntity<RoomResponse> changeRoomStatusById(
            @Positive(message = "Id must be positive")
            @PathVariable("id") Long id,
            @Valid @RequestBody ChangeRoomStatusRequest request
    ) {
        request.setId(id);
        return ResponseEntity.ok(roomService.changeRoomStatus(request));
    }

    @PatchMapping("/status")
    @ApiMessage("Update room status successfully")
    public ResponseEntity<RoomResponse> changeRoomStatus(
            @Valid @RequestBody ChangeRoomStatusRequest request
    ) {
        return ResponseEntity.ok(roomService.changeRoomStatus(request));
    }
}
