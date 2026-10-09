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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Module: VIP Room Management [6] Unit Tests
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    ReservationRoomRepository reservationRoomRepository;

    @InjectMocks
    RoomService roomService;

    Room sampleRoom;

    @BeforeEach
    void setUp() {
        sampleRoom = Room.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.AVAILABLE)
                .description("Premium VIP Room")
                .build();
        sampleRoom.setId(1L);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fetchRooms
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchRooms: returns paginated rooms")
    void fetchRooms_returnsPaginatedResult() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Room> page = new PageImpl<>(List.of(sampleRoom), pageable, 1);

        when(roomRepository.searchRooms(eq("vip"), eq(RoomType.VIP_ROOM), eq(RoomStatus.AVAILABLE), eq(pageable)))
                .thenReturn(page);

        ResultPaginationDTO result = roomService.fetchRooms("vip", RoomType.VIP_ROOM, RoomStatus.AVAILABLE, pageable);

        assertThat(result).isNotNull();
        assertThat(result.getMeta().getPage()).isEqualTo(1);
        assertThat(result.getMeta().getTotal()).isEqualTo(1);
        List<?> items = (List<?>) result.getResult();
        assertThat(items).hasSize(1);
    }

    @Test
    @DisplayName("fetchRooms: returns all when no filter")
    void fetchRooms_noFilter_returnsAll() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Room> page = new PageImpl<>(List.of(sampleRoom), pageable, 1);

        when(roomRepository.searchRooms(eq(""), isNull(), isNull(), eq(pageable)))
                .thenReturn(page);

        ResultPaginationDTO result = roomService.fetchRooms(null, null, null, pageable);

        assertThat(result.getMeta().getTotal()).isEqualTo(1);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fetchRoomById
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchRoomById: returns room when found")
    void fetchRoomById_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));

        RoomResponse response = roomService.fetchRoomById(1L);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getRoomName()).isEqualTo("VIP Suite A");
        assertThat(response.getRoomType()).isEqualTo(RoomType.VIP_ROOM);
        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    @DisplayName("fetchRoomById: throws ResourceNotFoundException when not found")
    void fetchRoomById_notFound_throwsException() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.fetchRoomById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found with id: 99");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fetchRoomsByType
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchRoomsByType: returns rooms filtered by type")
    void fetchRoomsByType_success() {
        when(roomRepository.findByRoomType(RoomType.VIP_ROOM)).thenReturn(List.of(sampleRoom));

        List<RoomResponse> result = roomService.fetchRoomsByType(RoomType.VIP_ROOM);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoomType()).isEqualTo(RoomType.VIP_ROOM);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // fetchAvailableRooms
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("fetchAvailableRooms: returns only AVAILABLE rooms")
    void fetchAvailableRooms_success() {
        when(roomRepository.findByRoomStatus(RoomStatus.AVAILABLE)).thenReturn(List.of(sampleRoom));

        List<RoomResponse> result = roomService.fetchAvailableRooms();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRoomStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // createRoom
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createRoom: creates and returns room successfully")
    void createRoom_success() {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .description("Premium VIP Room")
                .build();

        when(roomRepository.existsByRoomName("VIP Suite A")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(sampleRoom);

        RoomResponse response = roomService.createRoom(request);

        assertThat(response).isNotNull();
        assertThat(response.getRoomName()).isEqualTo("VIP Suite A");
        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.AVAILABLE);
        verify(roomRepository, times(1)).save(any(Room.class));
    }

    @Test
    @DisplayName("createRoom: uses provided status instead of default AVAILABLE")
    void createRoom_withCustomStatus_success() {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.MAINTENANCE)
                .build();

        Room maintenanceRoom = Room.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.MAINTENANCE)
                .build();
        maintenanceRoom.setId(2L);

        when(roomRepository.existsByRoomName("VIP Suite A")).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(maintenanceRoom);

        RoomResponse response = roomService.createRoom(request);

        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    @Test
    @DisplayName("createRoom: throws RequestInvalidException when room name already exists")
    void createRoom_duplicateName_throwsException() {
        RoomRequest request = RoomRequest.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        when(roomRepository.existsByRoomName("VIP Suite A")).thenReturn(true);

        assertThatThrownBy(() -> roomService.createRoom(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Room name already exists: VIP Suite A");

        verify(roomRepository, never()).save(any());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // updateRoom
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateRoom: updates room successfully")
    void updateRoom_success() {
        RoomRequest request = RoomRequest.builder()
                .id(1L)
                .roomName("VIP Suite A Updated")
                .roomType(RoomType.VIP_ROOM)
                .capacity(12)
                .price(600000)
                .description("Updated description")
                .build();

        Room updatedRoom = Room.builder()
                .roomName("VIP Suite A Updated")
                .roomType(RoomType.VIP_ROOM)
                .capacity(12)
                .price(600000)
                .roomStatus(RoomStatus.AVAILABLE)
                .description("Updated description")
                .build();
        updatedRoom.setId(1L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(roomRepository.existsByRoomNameAndIdNot("VIP Suite A Updated", 1L)).thenReturn(false);
        when(roomRepository.save(any(Room.class))).thenReturn(updatedRoom);

        RoomResponse response = roomService.updateRoom(request);

        assertThat(response.getRoomName()).isEqualTo("VIP Suite A Updated");
        assertThat(response.getCapacity()).isEqualTo(12);
    }

    @Test
    @DisplayName("updateRoom: throws RequestInvalidException when id is null")
    void updateRoom_nullId_throwsException() {
        RoomRequest request = RoomRequest.builder()
                .roomName("Some Room")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        assertThatThrownBy(() -> roomService.updateRoom(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Id is required");
    }

    @Test
    @DisplayName("updateRoom: throws ResourceNotFoundException when room not found")
    void updateRoom_notFound_throwsException() {
        RoomRequest request = RoomRequest.builder()
                .id(99L)
                .roomName("Some Room")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.updateRoom(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found with id: 99");
    }

    @Test
    @DisplayName("updateRoom: throws RequestInvalidException when name conflicts with another room")
    void updateRoom_duplicateName_throwsException() {
        RoomRequest request = RoomRequest.builder()
                .id(1L)
                .roomName("Existing Room Name")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .build();

        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(roomRepository.existsByRoomNameAndIdNot("Existing Room Name", 1L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.updateRoom(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Room name already exists: Existing Room Name");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // changeRoomStatus
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeRoomStatus: changes status successfully")
    void changeRoomStatus_success() {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .id(1L)
                .roomStatus(RoomStatus.OCCUPIED)
                .build();

        Room occupiedRoom = Room.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.OCCUPIED)
                .build();
        occupiedRoom.setId(1L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(occupiedRoom);

        RoomResponse response = roomService.changeRoomStatus(request);

        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.OCCUPIED);
    }

    @Test
    @DisplayName("changeRoomStatus: throws RequestInvalidException when id is null")
    void changeRoomStatus_nullId_throwsException() {
        ChangeRoomStatusRequest request = ChangeRoomStatusRequest.builder()
                .roomStatus(RoomStatus.OCCUPIED)
                .build();

        assertThatThrownBy(() -> roomService.changeRoomStatus(request))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Id is required");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // toggleMaintenanceRoom
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toggleMaintenanceRoom: sets MAINTENANCE when room is AVAILABLE")
    void toggleMaintenance_fromAvailable_setsMaintenance() {
        Room maintenanceRoom = Room.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.MAINTENANCE)
                .build();
        maintenanceRoom.setId(1L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(maintenanceRoom);

        RoomResponse response = roomService.toggleMaintenanceRoom(1L);

        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.MAINTENANCE);
    }

    @Test
    @DisplayName("toggleMaintenanceRoom: sets AVAILABLE when room is in MAINTENANCE")
    void toggleMaintenance_fromMaintenance_setsAvailable() {
        sampleRoom.setRoomStatus(RoomStatus.MAINTENANCE);

        Room availableRoom = Room.builder()
                .roomName("VIP Suite A")
                .roomType(RoomType.VIP_ROOM)
                .capacity(10)
                .price(500000)
                .roomStatus(RoomStatus.AVAILABLE)
                .build();
        availableRoom.setId(1L);

        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(roomRepository.save(any(Room.class))).thenReturn(availableRoom);

        RoomResponse response = roomService.toggleMaintenanceRoom(1L);

        assertThat(response.getRoomStatus()).isEqualTo(RoomStatus.AVAILABLE);
    }

    @Test
    @DisplayName("toggleMaintenanceRoom: throws ResourceNotFoundException when room not found")
    void toggleMaintenance_notFound_throwsException() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.toggleMaintenanceRoom(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found with id: 99");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // deleteRoom
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteRoom: deletes successfully when no reservations")
    void deleteRoom_success() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(reservationRoomRepository.existsByRoomId(1L)).thenReturn(false);

        roomService.deleteRoom(1L);

        verify(roomRepository, times(1)).delete(sampleRoom);
    }

    @Test
    @DisplayName("deleteRoom: throws RequestInvalidException when room has associated reservations")
    void deleteRoom_hasReservations_throwsException() {
        when(roomRepository.findById(1L)).thenReturn(Optional.of(sampleRoom));
        when(reservationRoomRepository.existsByRoomId(1L)).thenReturn(true);

        assertThatThrownBy(() -> roomService.deleteRoom(1L))
                .isInstanceOf(RequestInvalidException.class)
                .hasMessageContaining("Cannot delete room because it has associated reservations");

        verify(roomRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteRoom: throws ResourceNotFoundException when room not found")
    void deleteRoom_notFound_throwsException() {
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.deleteRoom(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room not found with id: 99");

        verify(roomRepository, never()).delete(any());
    }
}
