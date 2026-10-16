package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationRoom;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.Room;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.RoomReservationBookingRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.VipRoomRecommendationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.dto.response.VipRoomRecommendationResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.RoomRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ReservationService {

    static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");

    final ReservationRepository reservationRepository;
    final ReservationTableRepository reservationTableRepository;
    final RestaurantTableRepository restaurantTableRepository;
    final ReservationRoomRepository reservationRoomRepository;
    final RoomRepository roomRepository;
    final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public ReservationResponseDTO createReservation(CreateReservationRequestDTO dto, String username) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new RequestInvalidException("End time must be after start time");
        }

        if (dto.getReservationType() != ReservationType.TABLE && dto.getReservationType() != ReservationType.VIP_ROOM) {
            throw new RequestInvalidException("Only TABLE and VIP_ROOM reservations are supported");
        }

        List<RestaurantTable> tables = Collections.emptyList();
        List<Room> rooms = Collections.emptyList();

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS
        );

        if (dto.getReservationType() == ReservationType.TABLE) {
            if (dto.getTableIds() == null || dto.getTableIds().isEmpty()) {
                throw new RequestInvalidException("At least one table must be selected");
            }

            Set<Long> uniqueTableIds = new HashSet<>(dto.getTableIds());
            if (uniqueTableIds.size() != dto.getTableIds().size()) {
                throw new RequestInvalidException("Duplicate table IDs in request");
            }

            tables = restaurantTableRepository.findAllById(dto.getTableIds());
            if (tables.size() != dto.getTableIds().size()) {
                throw new ResourceNotFoundException("One or more requested tables do not exist");
            }

            for (RestaurantTable table : tables) {
                if (table.getStatusCode() == TableStatus.LOCKED) {
                    throw new RequestInvalidException("Table " + table.getTableNumber() + " is currently locked");
                }
            }

            List<ReservationTable> conflicts = reservationTableRepository.findConflictingReservations(
                    dto.getTableIds(),
                    blockingStatuses,
                    dto.getStartTime(),
                    dto.getEndTime()
            );

            if (!conflicts.isEmpty()) {
                String conflictedTableNumbers = conflicts.stream()
                        .map(c -> String.valueOf(c.getTable().getTableNumber()))
                        .distinct()
                        .collect(Collectors.joining(", "));
                throw new ReservationConflictException("Table " + conflictedTableNumbers + " is already reserved during the requested time slot");
            }
        } else {
            // VIP_ROOM
            if (dto.getRoomIds() == null || dto.getRoomIds().isEmpty()) {
                throw new RequestInvalidException("At least one VIP room must be selected");
            }

            Set<Long> uniqueRoomIds = new HashSet<>(dto.getRoomIds());
            if (uniqueRoomIds.size() != dto.getRoomIds().size()) {
                throw new RequestInvalidException("Duplicate room IDs in request");
            }

            rooms = roomRepository.findAllById(dto.getRoomIds());
            if (rooms.size() != dto.getRoomIds().size()) {
                throw new ResourceNotFoundException("One or more requested rooms do not exist");
            }

            for (Room room : rooms) {
                if (room.getRoomType() != RoomType.VIP_ROOM) {
                    throw new RequestInvalidException("Room " + room.getRoomName() + " is not a VIP room");
                }
                if (room.getRoomStatus() == RoomStatus.MAINTENANCE) {
                    throw new RequestInvalidException("Room " + room.getRoomName() + " is currently under maintenance");
                }
            }

            List<ReservationRoom> conflicts = reservationRoomRepository.findConflictingReservations(
                    dto.getRoomIds(),
                    blockingStatuses,
                    dto.getStartTime(),
                    dto.getEndTime()
            );

            if (!conflicts.isEmpty()) {
                String conflictedRoomNames = conflicts.stream()
                        .map(c -> c.getRoom().getRoomName())
                        .distinct()
                        .collect(Collectors.joining(", "));
                throw new ReservationConflictException("Room " + conflictedRoomNames + " is already reserved during the requested time slot");
            }
        }

        String dateStr = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER);
        String redisKey = "reservation:seq:" + dateStr;
        Long counter = stringRedisTemplate.opsForValue().increment(redisKey);
        if (counter != null && counter == 1L) {
            stringRedisTemplate.expire(redisKey, Duration.ofDays(2));
        }
        long seq = (counter != null) ? counter : 1L;
        String reservationNo = String.format("RES-%s-%03d", dateStr, seq);

        Reservation reservation = Reservation.builder()
                .reservationNo(reservationNo)
                .partySize(dto.getPartySize())
                .reservationType(dto.getReservationType())
                .reservationStatus(ReservationStatus.PENDING)
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .note(dto.getNote())
                .build();
        reservation.setCreatedBy(username);
        Reservation savedReservation = reservationRepository.save(reservation);

        if (dto.getReservationType() == ReservationType.TABLE) {
            List<ReservationTable> resTables = tables.stream()
                    .map(t -> ReservationTable.builder()
                            .reservation(savedReservation)
                            .table(t)
                            .build())
                    .toList();
            reservationTableRepository.saveAll(resTables);
        } else {
            List<ReservationRoom> resRooms = rooms.stream()
                    .map(r -> ReservationRoom.builder()
                            .reservation(savedReservation)
                            .room(r)
                            .build())
                    .toList();
            reservationRoomRepository.saveAll(resRooms);
        }

        return mapToResponseDTO(savedReservation, tables, rooms);
    }

    @Transactional
    public ReservationResponseDTO createRoomReservation(RoomReservationBookingRequestDTO dto, String username) {
        CreateReservationRequestDTO createDto = CreateReservationRequestDTO.builder()
                .partySize(dto.getPartySize())
                .reservationType(ReservationType.VIP_ROOM)
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .roomIds(dto.getRoomIds())
                .note(dto.getNote())
                .build();
        return createReservation(createDto, username);
    }

    @Transactional(readOnly = true)
    public VipRoomRecommendationResponseDTO getVipRoomRecommendations(VipRoomRecommendationRequestDTO dto) {
        if (dto.getStartTime() == null) {
            throw new RequestInvalidException("Start time is required");
        }
        if (dto.getEndTime() == null) {
            throw new RequestInvalidException("End time is required");
        }
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new RequestInvalidException("End time must be after start time");
        }
        if (dto.getPartySize() == null) {
            throw new RequestInvalidException("Party size is required");
        }

        List<Room> vipRooms = roomRepository.findByRoomType(RoomType.VIP_ROOM);
        if (vipRooms.isEmpty()) {
            return VipRoomRecommendationResponseDTO.builder()
                    .partySize(dto.getPartySize())
                    .startTime(dto.getStartTime())
                    .endTime(dto.getEndTime())
                    .totalRooms(0)
                    .availableRooms(0)
                    .recommendedRoomId(null)
                    .rooms(Collections.emptyList())
                    .build();
        }

        List<Long> vipRoomIds = vipRooms.stream().map(Room::getId).toList();

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS
        );

        List<ReservationRoom> conflicts = reservationRoomRepository.findConflictingReservations(
                vipRoomIds,
                blockingStatuses,
                dto.getStartTime(),
                dto.getEndTime()
        );

        Set<Long> conflictedRoomIds = conflicts.stream()
                .map(c -> c.getRoom().getId())
                .collect(Collectors.toSet());

        List<VipRoomRecommendationResponseDTO.VipRoomOptionDTO> options = new ArrayList<>();
        for (Room room : vipRooms) {
            boolean isAvailable;
            String unavailabilityReason = null;

            if (room.getRoomStatus() == RoomStatus.MAINTENANCE) {
                isAvailable = false;
                unavailabilityReason = "Room is currently under maintenance";
            } else if (conflictedRoomIds.contains(room.getId())) {
                isAvailable = false;
                unavailabilityReason = "Reserved for the requested time slot";
            } else {
                isAvailable = true;
            }

            options.add(VipRoomRecommendationResponseDTO.VipRoomOptionDTO.builder()
                    .id(room.getId())
                    .roomName(room.getRoomName())
                    .roomType(room.getRoomType())
                    .capacity(room.getCapacity())
                    .price(room.getPrice())
                    .roomStatus(room.getRoomStatus())
                    .description(room.getDescription())
                    .isAvailable(isAvailable)
                    .isRecommended(false)
                    .unavailabilityReason(unavailabilityReason)
                    .build());
        }

        // Minimal deterministic ordering:
        // 1. Available rooms first
        // 2. Capacity ascending
        // 3. Price ascending
        // 4. Room ID ascending
        options.sort(Comparator
                .comparing(VipRoomRecommendationResponseDTO.VipRoomOptionDTO::isAvailable, Comparator.reverseOrder())
                .thenComparingInt(VipRoomRecommendationResponseDTO.VipRoomOptionDTO::getCapacity)
                .thenComparingDouble(VipRoomRecommendationResponseDTO.VipRoomOptionDTO::getPrice)
                .thenComparing(VipRoomRecommendationResponseDTO.VipRoomOptionDTO::getId));

        Long recommendedRoomId = null;
        for (VipRoomRecommendationResponseDTO.VipRoomOptionDTO opt : options) {
            if (opt.isAvailable()) {
                opt.setRecommended(true);
                recommendedRoomId = opt.getId();
                break;
            }
        }

        long availableCount = options.stream().filter(VipRoomRecommendationResponseDTO.VipRoomOptionDTO::isAvailable).count();

        return VipRoomRecommendationResponseDTO.builder()
                .partySize(dto.getPartySize())
                .startTime(dto.getStartTime())
                .endTime(dto.getEndTime())
                .totalRooms(options.size())
                .availableRooms((int) availableCount)
                .recommendedRoomId(recommendedRoomId)
                .rooms(options)
                .build();
    }

    @Transactional
    public ReservationResponseDTO updateReservation(Long id, UpdateReservationRequestDTO dto, String username) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        if (reservation.getCreatedBy() == null || !reservation.getCreatedBy().equals(username)) {
            throw new AccessDeniedException("You are not allowed to update this reservation");
        }

        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new RequestInvalidException("Only reservations with PENDING status can be updated");
        }

        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new RequestInvalidException("End time must be after start time");
        }

        if (dto.getReservationType() != ReservationType.TABLE && dto.getReservationType() != ReservationType.VIP_ROOM) {
            throw new RequestInvalidException("Only TABLE and VIP_ROOM reservations are supported");
        }

        List<RestaurantTable> tables = Collections.emptyList();
        List<Room> rooms = Collections.emptyList();

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS
        );

        if (dto.getReservationType() == ReservationType.TABLE) {
            if (dto.getTableIds() == null || dto.getTableIds().isEmpty()) {
                throw new RequestInvalidException("At least one table must be selected");
            }

            Set<Long> uniqueTableIds = new HashSet<>(dto.getTableIds());
            if (uniqueTableIds.size() != dto.getTableIds().size()) {
                throw new RequestInvalidException("Duplicate table IDs in request");
            }

            tables = restaurantTableRepository.findAllById(dto.getTableIds());
            if (tables.size() != dto.getTableIds().size()) {
                throw new ResourceNotFoundException("One or more requested tables do not exist");
            }

            for (RestaurantTable table : tables) {
                if (table.getStatusCode() == TableStatus.LOCKED) {
                    throw new RequestInvalidException("Table " + table.getTableNumber() + " is currently locked");
                }
            }

            List<ReservationTable> conflicts = reservationTableRepository.findConflictingReservationsExcludingReservation(
                    dto.getTableIds(),
                    id,
                    blockingStatuses,
                    dto.getStartTime(),
                    dto.getEndTime()
            );

            if (!conflicts.isEmpty()) {
                String conflictedTableNumbers = conflicts.stream()
                        .map(c -> String.valueOf(c.getTable().getTableNumber()))
                        .distinct()
                        .collect(Collectors.joining(", "));
                throw new ReservationConflictException("Table " + conflictedTableNumbers + " is already reserved during the requested time slot");
            }

            reservationTableRepository.deleteByReservationId(id);
            reservationRoomRepository.deleteByReservationId(id);

            List<ReservationTable> newResTables = tables.stream()
                    .map(t -> ReservationTable.builder()
                            .reservation(reservation)
                            .table(t)
                            .build())
                    .toList();
            reservationTableRepository.saveAll(newResTables);
        } else {
            // VIP_ROOM
            if (dto.getRoomIds() == null || dto.getRoomIds().isEmpty()) {
                throw new RequestInvalidException("At least one VIP room must be selected");
            }

            Set<Long> uniqueRoomIds = new HashSet<>(dto.getRoomIds());
            if (uniqueRoomIds.size() != dto.getRoomIds().size()) {
                throw new RequestInvalidException("Duplicate room IDs in request");
            }

            rooms = roomRepository.findAllById(dto.getRoomIds());
            if (rooms.size() != dto.getRoomIds().size()) {
                throw new ResourceNotFoundException("One or more requested rooms do not exist");
            }

            for (Room room : rooms) {
                if (room.getRoomType() != RoomType.VIP_ROOM) {
                    throw new RequestInvalidException("Room " + room.getRoomName() + " is not a VIP room");
                }
                if (room.getRoomStatus() == RoomStatus.MAINTENANCE) {
                    throw new RequestInvalidException("Room " + room.getRoomName() + " is currently under maintenance");
                }
            }

            List<ReservationRoom> conflicts = reservationRoomRepository.findConflictingReservationsExcludingReservation(
                    dto.getRoomIds(),
                    id,
                    blockingStatuses,
                    dto.getStartTime(),
                    dto.getEndTime()
            );

            if (!conflicts.isEmpty()) {
                String conflictedRoomNames = conflicts.stream()
                        .map(c -> c.getRoom().getRoomName())
                        .distinct()
                        .collect(Collectors.joining(", "));
                throw new ReservationConflictException("Room " + conflictedRoomNames + " is already reserved during the requested time slot");
            }

            reservationRoomRepository.deleteByReservationId(id);
            reservationTableRepository.deleteByReservationId(id);

            List<ReservationRoom> newResRooms = rooms.stream()
                    .map(r -> ReservationRoom.builder()
                            .reservation(reservation)
                            .room(r)
                            .build())
                    .toList();
            reservationRoomRepository.saveAll(newResRooms);
        }

        reservation.setPartySize(dto.getPartySize());
        reservation.setReservationType(dto.getReservationType());
        reservation.setStartTime(dto.getStartTime());
        reservation.setEndTime(dto.getEndTime());
        reservation.setNote(dto.getNote());

        Reservation updatedReservation = reservationRepository.save(reservation);

        return mapToResponseDTO(updatedReservation, tables, rooms);
    }

    @Transactional
    public ReservationResponseDTO cancelReservation(Long id, String username) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        if (reservation.getCreatedBy() == null || !reservation.getCreatedBy().equals(username)) {
            throw new AccessDeniedException("You are not allowed to cancel this reservation");
        }

        if (reservation.getReservationStatus() == ReservationStatus.CANCELLED) {
            throw new RequestInvalidException("Reservation is already cancelled");
        }

        if (reservation.getReservationStatus() != ReservationStatus.PENDING
                && reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new RequestInvalidException("Only reservations with PENDING or CONFIRMED status can be cancelled");
        }

        reservation.setReservationStatus(ReservationStatus.CANCELLED);
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationTable> resTables = reservationTableRepository.findByReservationId(id);
        List<RestaurantTable> tables = resTables.stream()
                .map(ReservationTable::getTable)
                .toList();

        List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(id);
        List<Room> rooms = resRooms.stream()
                .map(ReservationRoom::getRoom)
                .toList();

        return mapToResponseDTO(savedReservation, tables, rooms);
    }

    @Transactional
    public ReservationResponseDTO checkInReservation(Long id, String username) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isStaffOrAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.STAFF.equals(a.getAuthority())
                        || AuthoritiesConstants.MANAGER.equals(a.getAuthority())
                        || AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isStaffOrAdmin) {
            if (reservation.getCreatedBy() == null || !reservation.getCreatedBy().equals(username)) {
                throw new AccessDeniedException("You are not allowed to check in this reservation");
            }
        }

        if (reservation.getReservationStatus() == ReservationStatus.IN_PROGRESS) {
            throw new RequestInvalidException("Reservation is already checked in");
        }

        if (reservation.getReservationStatus() != ReservationStatus.PENDING
                && reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new RequestInvalidException("Only reservations with PENDING or CONFIRMED status can be checked in");
        }

        reservation.setReservationStatus(ReservationStatus.IN_PROGRESS);
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationTable> resTables = reservationTableRepository.findByReservationId(id);
        List<RestaurantTable> tables = resTables.stream()
                .map(ReservationTable::getTable)
                .toList();

        List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(id);
        List<Room> rooms = resRooms.stream()
                .map(ReservationRoom::getRoom)
                .toList();

        return mapToResponseDTO(savedReservation, tables, rooms);
    }

    @Transactional
    public ReservationResponseDTO completeReservation(Long id, String username) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isStaffOrAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.STAFF.equals(a.getAuthority())
                        || AuthoritiesConstants.MANAGER.equals(a.getAuthority())
                        || AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isStaffOrAdmin) {
            if (reservation.getCreatedBy() == null || !reservation.getCreatedBy().equals(username)) {
                throw new AccessDeniedException("You are not allowed to complete this reservation");
            }
        }

        if (reservation.getReservationStatus() == ReservationStatus.COMPLETED) {
            throw new RequestInvalidException("Reservation is already completed");
        }

        if (reservation.getReservationStatus() != ReservationStatus.IN_PROGRESS) {
            throw new RequestInvalidException("Only reservations with IN_PROGRESS status can be completed");
        }

        reservation.setReservationStatus(ReservationStatus.COMPLETED);
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationTable> resTables = reservationTableRepository.findByReservationId(id);
        List<RestaurantTable> tables = resTables.stream()
                .map(ReservationTable::getTable)
                .toList();

        List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(id);
        List<Room> rooms = resRooms.stream()
                .map(ReservationRoom::getRoom)
                .toList();

        return mapToResponseDTO(savedReservation, tables, rooms);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponseDTO> getMyReservations(String username) {
        if (username == null || username.isBlank()) {
            throw new AccessDeniedException("User not authenticated");
        }
        List<Reservation> reservations = reservationRepository.findByCreatedBy(username);
        return reservations.stream()
                .map(r -> {
                    List<ReservationTable> resTables = reservationTableRepository.findByReservationId(r.getId());
                    List<RestaurantTable> tables = resTables.stream()
                            .map(ReservationTable::getTable)
                            .toList();

                    List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(r.getId());
                    List<Room> rooms = resRooms.stream()
                            .map(ReservationRoom::getRoom)
                            .toList();

                    return mapToResponseDTO(r, tables, rooms);
                })
                .toList();
    }

    private ReservationResponseDTO mapToResponseDTO(Reservation reservation, List<RestaurantTable> tables, List<Room> rooms) {
        List<ReservationResponseDTO.TableSummaryDTO> tableSummaries = (tables != null) ? tables.stream()
                .map(t -> ReservationResponseDTO.TableSummaryDTO.builder()
                        .id(t.getId())
                        .tableNumber(t.getTableNumber())
                        .capacity(t.getCapacity())
                        .build())
                .toList() : Collections.emptyList();

        List<ReservationResponseDTO.RoomSummaryDTO> roomSummaries = (rooms != null) ? rooms.stream()
                .map(r -> ReservationResponseDTO.RoomSummaryDTO.builder()
                        .id(r.getId())
                        .roomName(r.getRoomName())
                        .roomType(r.getRoomType())
                        .capacity(r.getCapacity())
                        .price(r.getPrice())
                        .build())
                .toList() : Collections.emptyList();

        return ReservationResponseDTO.builder()
                .id(reservation.getId())
                .reservationNo(reservation.getReservationNo())
                .partySize(reservation.getPartySize())
                .reservationType(reservation.getReservationType())
                .reservationStatus(reservation.getReservationStatus())
                .startTime(reservation.getStartTime())
                .endTime(reservation.getEndTime())
                .note(reservation.getNote())
                .tables(tableSummaries)
                .rooms(roomSummaries)
                .createdBy(reservation.getCreatedBy())
                .createdDate(reservation.getCreatedDate())
                .build();
    }
}
