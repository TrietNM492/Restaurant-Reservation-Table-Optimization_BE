package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationRoom;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.entity.Room;
import com.restaurant.ireservation.domain.entity.User;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.RoomStatus;
import com.restaurant.ireservation.domain.enumerations.RoomType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.RejectReservationRequestDTO;
import com.restaurant.ireservation.dto.request.RoomReservationBookingRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.VipRoomRecommendationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.dto.response.VipRoomRecommendationResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import com.restaurant.ireservation.util.ResultPaginationDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationRoomRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.RoomRepository;
import com.restaurant.ireservation.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
    final UserRepository userRepository;
    final StringRedisTemplate stringRedisTemplate;
    Clock clock = Clock.systemUTC();

    public void setClock(Clock clock) {
        this.clock = clock;
    }

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
        boolean isAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isAdmin) {
            throw new AccessDeniedException("You are not allowed to check in this reservation");
        }

        if (reservation.getReservationStatus() == ReservationStatus.IN_PROGRESS) {
            throw new RequestInvalidException("Reservation is already checked in");
        }

        if (reservation.getReservationStatus() != ReservationStatus.PENDING
                && reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new RequestInvalidException("Only reservations with PENDING or CONFIRMED status can be checked in");
        }

        Instant now = (clock != null) ? clock.instant() : Instant.now();
        if (reservation.getStartTime() == null || now.isBefore(reservation.getStartTime())) {
            throw new RequestInvalidException("Cannot check in before the scheduled reservation time");
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
        boolean isAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isAdmin) {
            throw new AccessDeniedException("You are not allowed to complete this reservation");
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

    @Transactional(readOnly = true)
    public ResultPaginationDTO getReservations(
            String q,
            ReservationStatus status,
            ReservationType type,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        if (pageable.getPageNumber() < 0) {
            throw new RequestInvalidException("Page index must not be less than 0");
        }
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw new RequestInvalidException("Page size must be between 1 and 100");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isAdmin) {
            throw new AccessDeniedException("You are not allowed to view all reservations");
        }

        String keyword = (q != null && !q.isBlank()) ? q.trim() : null;
        Page<Reservation> reservationPage = reservationRepository.searchReservations(
                keyword, status, type, from, to, pageable
        );

        List<Reservation> reservations = reservationPage.getContent();
        List<Long> resIds = reservations.stream().map(Reservation::getId).toList();

        Map<Long, List<RestaurantTable>> tableMap = resIds.isEmpty() ? Collections.emptyMap() :
                reservationTableRepository.findByReservationIdIn(resIds).stream()
                        .filter(rt -> rt.getReservation() != null && rt.getTable() != null)
                        .collect(Collectors.groupingBy(
                                rt -> rt.getReservation().getId(),
                                Collectors.mapping(ReservationTable::getTable, Collectors.toList())
                        ));

        Map<Long, List<Room>> roomMap = resIds.isEmpty() ? Collections.emptyMap() :
                reservationRoomRepository.findByReservationIdIn(resIds).stream()
                        .filter(rr -> rr.getReservation() != null && rr.getRoom() != null)
                        .collect(Collectors.groupingBy(
                                rr -> rr.getReservation().getId(),
                                Collectors.mapping(ReservationRoom::getRoom, Collectors.toList())
                        ));

        Set<String> usernames = reservations.stream()
                .map(Reservation::getCreatedBy)
                .filter(u -> u != null && !u.isBlank())
                .collect(Collectors.toSet());

        Map<String, User> userMap = usernames.isEmpty() ? Collections.emptyMap() :
                userRepository.findByUsernameIn(usernames).stream()
                        .filter(u -> u.getUsername() != null)
                        .collect(Collectors.toMap(User::getUsername, Function.identity(), (u1, u2) -> u1));

        List<ReservationResponseDTO> responseList = reservations.stream()
                .map(r -> {
                    List<RestaurantTable> tables = tableMap.getOrDefault(r.getId(), Collections.emptyList());
                    List<Room> rooms = roomMap.getOrDefault(r.getId(), Collections.emptyList());
                    User user = (r.getCreatedBy() != null) ? userMap.get(r.getCreatedBy()) : null;
                    return mapToResponseDTO(r, tables, rooms, user);
                })
                .toList();

        ResultPaginationDTO paginationDTO = new ResultPaginationDTO();
        paginationDTO.setResult(responseList);

        ResultPaginationDTO.Meta meta = new ResultPaginationDTO.Meta();
        meta.setPage(pageable.getPageNumber() + 1);
        meta.setPageSize(pageable.getPageSize());
        meta.setPages(reservationPage.getTotalPages());
        meta.setTotal(reservationPage.getTotalElements());
        meta.setHasNext(reservationPage.hasNext());
        meta.setHasPrevious(reservationPage.hasPrevious());
        paginationDTO.setMeta(meta);

        return paginationDTO;
    }

    @Transactional
    public ReservationResponseDTO confirmReservation(Long id, String username) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isAdmin) {
            throw new AccessDeniedException("You are not allowed to confirm this reservation");
        }

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        if (reservation.getReservationStatus() == ReservationStatus.CONFIRMED) {
            throw new RequestInvalidException("Reservation is already confirmed");
        }

        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new RequestInvalidException("Only reservations with PENDING status can be confirmed");
        }

        Instant now = (clock != null) ? clock.instant() : Instant.now();
        if (reservation.getEndTime() != null && now.isAfter(reservation.getEndTime())) {
            throw new RequestInvalidException("Cannot confirm an expired reservation");
        }

        List<ReservationTable> resTables = reservationTableRepository.findByReservationId(id);
        List<RestaurantTable> tables = resTables.stream()
                .map(ReservationTable::getTable)
                .toList();

        for (RestaurantTable table : tables) {
            if (table.getStatusCode() == TableStatus.LOCKED) {
                throw new RequestInvalidException("Table " + table.getTableNumber() + " is currently locked");
            }
        }

        List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(id);
        List<Room> rooms = resRooms.stream()
                .map(ReservationRoom::getRoom)
                .toList();

        for (Room room : rooms) {
            if (room.getRoomStatus() == RoomStatus.MAINTENANCE) {
                throw new RequestInvalidException("Room " + room.getRoomName() + " is currently under maintenance");
            }
        }

        reservation.setReservationStatus(ReservationStatus.CONFIRMED);
        reservation.setLastModifiedBy(username);
        Reservation savedReservation = reservationRepository.save(reservation);

        User user = (savedReservation.getCreatedBy() != null)
                ? userRepository.findUserByUsername(savedReservation.getCreatedBy()).orElse(null)
                : null;

        return mapToResponseDTO(savedReservation, tables, rooms, user);
    }

    @Transactional
    public ReservationResponseDTO rejectReservation(Long id, RejectReservationRequestDTO dto, String username) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream().anyMatch(a ->
                        AuthoritiesConstants.ADMIN.equals(a.getAuthority())
                );

        if (!isAdmin) {
            throw new AccessDeniedException("You are not allowed to reject this reservation");
        }

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found with id: " + id));

        if (reservation.getReservationStatus() == ReservationStatus.CANCELLED) {
            throw new RequestInvalidException("Reservation is already cancelled");
        }

        if (reservation.getReservationStatus() == ReservationStatus.CONFIRMED) {
            throw new RequestInvalidException("Only reservations with PENDING status can be rejected. Cancelling confirmed reservations is not supported in this operation.");
        }

        if (reservation.getReservationStatus() != ReservationStatus.PENDING) {
            throw new RequestInvalidException("Only reservations with PENDING status can be rejected");
        }

        String reason = (dto != null && dto.getReason() != null) ? dto.getReason().trim() : null;
        if (reason != null && !reason.isEmpty()) {
            String existingNote = (reservation.getNote() != null) ? reservation.getNote().trim() : "";
            String prefix = "[Admin Rejection: " + reason + "]";
            String combinedNote = existingNote.isEmpty() ? prefix : existingNote + " | " + prefix;
            if (combinedNote.length() > 500) {
                throw new RequestInvalidException("Rejection reason cannot be appended because combined note length ("
                        + combinedNote.length() + " chars) exceeds the 500-character database limit. Existing note length: "
                        + existingNote.length() + " chars. Please shorten the reason.");
            }
            reservation.setNote(combinedNote);
        }

        reservation.setReservationStatus(ReservationStatus.CANCELLED);
        reservation.setLastModifiedBy(username);
        Reservation savedReservation = reservationRepository.save(reservation);

        List<ReservationTable> resTables = reservationTableRepository.findByReservationId(id);
        List<RestaurantTable> tables = resTables.stream()
                .map(ReservationTable::getTable)
                .toList();

        List<ReservationRoom> resRooms = reservationRoomRepository.findByReservationId(id);
        List<Room> rooms = resRooms.stream()
                .map(ReservationRoom::getRoom)
                .toList();

        User user = (savedReservation.getCreatedBy() != null)
                ? userRepository.findUserByUsername(savedReservation.getCreatedBy()).orElse(null)
                : null;

        return mapToResponseDTO(savedReservation, tables, rooms, user);
    }

    private ReservationResponseDTO mapToResponseDTO(Reservation reservation, List<RestaurantTable> tables, List<Room> rooms, User user) {
        ReservationResponseDTO dto = mapToResponseDTO(reservation, tables, rooms);
        if (user != null) {
            dto.setCustomerName((user.getFullname() != null && !user.getFullname().isBlank()) ? user.getFullname() : user.getUsername());
            dto.setCustomerPhone(user.getPhone());
            dto.setCustomerEmail(user.getEmail());
        } else if (reservation.getCreatedBy() != null) {
            dto.setCustomerName(reservation.getCreatedBy());
        }
        return dto;
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
