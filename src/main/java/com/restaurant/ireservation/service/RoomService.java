package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Room;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.dto.request.ChangeRoomStatusRequest;
import com.restaurant.ireservation.dto.request.RoomRequest;
import com.restaurant.ireservation.dto.response.RoomResponse;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.RoomRepository;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Module: VIP Room Management [6]
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomService {

    RoomRepository roomRepository;
    ReservationRoomRepository reservationRoomRepository;

    @Transactional(readOnly = true)
    public ResultPaginationDTO fetchRooms(String q, RoomType roomType, RoomStatus roomStatus, Pageable pageable) {
        String keyword = q != null ? q.trim().toLowerCase() : "";

        Page<Room> roomPage = roomRepository.searchRooms(keyword, roomType, roomStatus, pageable);
        List<RoomResponse> responseList = roomPage.getContent().stream()
                .map(this::toRoomResponse)
                .toList();

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(responseList);

        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(roomPage.getTotalPages());
        meta.setTotal(roomPage.getTotalElements());
        meta.setHasNext(roomPage.hasNext());
        meta.setHasPrevious(roomPage.hasPrevious());
        paginationDTO.setMeta(meta);

        return paginationDTO;
    }

    @Transactional(readOnly = true)
    public RoomResponse fetchRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));
        return toRoomResponse(room);
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> fetchRoomsByType(RoomType roomType) {
        List<Room> rooms = roomRepository.findByRoomType(roomType);
        return rooms.stream().map(this::toRoomResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<RoomResponse> fetchAvailableRooms() {
        List<Room> rooms = roomRepository.findByRoomStatus(RoomStatus.AVAILABLE);
        return rooms.stream().map(this::toRoomResponse).toList();
    }

    @Transactional
    public RoomResponse createRoom(RoomRequest request) {
        String roomName = request.getRoomName().trim();
        if (roomRepository.existsByRoomName(roomName)) {
            throw new RequestInvalidException("Room name already exists: " + roomName);
        }

        RoomStatus status = request.getRoomStatus() != null ? request.getRoomStatus() : RoomStatus.AVAILABLE;

        Room room = Room.builder()
                .roomName(roomName)
                .roomType(request.getRoomType())
                .capacity(request.getCapacity())
                .price(request.getPrice())
                .roomStatus(status)
                .description(request.getDescription())
                .build();

        Room saved = roomRepository.save(room);
        return toRoomResponse(saved);
    }

    @Transactional
    public RoomResponse updateRoom(RoomRequest request) {
        if (request.getId() == null) {
            throw new RequestInvalidException("Id is required for updating room");
        }

        Room existing = roomRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.getId()));

        String roomName = request.getRoomName().trim();
        if (roomRepository.existsByRoomNameAndIdNot(roomName, existing.getId())) {
            throw new RequestInvalidException("Room name already exists: " + roomName);
        }

        existing.setRoomName(roomName);
        existing.setRoomType(request.getRoomType());
        existing.setCapacity(request.getCapacity());
        existing.setPrice(request.getPrice());
        existing.setDescription(request.getDescription());
        if (request.getRoomStatus() != null) {
            existing.setRoomStatus(request.getRoomStatus());
        }

        Room updated = roomRepository.save(existing);
        return toRoomResponse(updated);
    }

    @Transactional
    public RoomResponse changeRoomStatus(ChangeRoomStatusRequest request) {
        if (request.getId() == null) {
            throw new RequestInvalidException("Id is required for changing room status");
        }

        Room existing = roomRepository.findById(request.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + request.getId()));

        existing.setRoomStatus(request.getRoomStatus());
        Room updated = roomRepository.save(existing);
        return toRoomResponse(updated);
    }

    @Transactional
    public RoomResponse toggleMaintenanceRoom(Long id) {
        Room existing = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));

        if (existing.getRoomStatus() == RoomStatus.MAINTENANCE) {
            existing.setRoomStatus(RoomStatus.AVAILABLE);
        } else {
            existing.setRoomStatus(RoomStatus.MAINTENANCE);
        }

        Room updated = roomRepository.save(existing);
        return toRoomResponse(updated);
    }

    @Transactional
    public void deleteRoom(Long id) {
        Room existing = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found with id: " + id));

        if (reservationRoomRepository.existsByRoomId(id)) {
            throw new RequestInvalidException("Cannot delete room because it has associated reservations");
        }

        roomRepository.delete(existing);
    }

    public RoomResponse toRoomResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .roomName(room.getRoomName())
                .roomType(room.getRoomType())
                .capacity(room.getCapacity())
                .price(room.getPrice())
                .roomStatus(room.getRoomStatus())
                .description(room.getDescription())
                .createdBy(room.getCreatedBy())
                .createdDate(room.getCreatedDate())
                .lastModifiedBy(room.getLastModifiedBy())
                .lastModifiedDate(room.getLastModifiedDate())
                .build();
    }
}
