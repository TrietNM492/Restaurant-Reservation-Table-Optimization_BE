package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.Reservation;
import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.ReservationType;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.CreateReservationRequestDTO;
import com.restaurant.ireservation.dto.request.UpdateReservationRequestDTO;
import com.restaurant.ireservation.dto.response.ReservationResponseDTO;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ReservationConflictException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.security.AuthoritiesConstants;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.restaurant.ireservation.repository.ReservationRepository;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
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
    final StringRedisTemplate stringRedisTemplate;

    @Transactional
    public ReservationResponseDTO createReservation(CreateReservationRequestDTO dto, String username) {
        if (!dto.getEndTime().isAfter(dto.getStartTime())) {
            throw new RequestInvalidException("End time must be after start time");
        }

        if (dto.getReservationType() != ReservationType.TABLE) {
            throw new RequestInvalidException("Only TABLE reservations are supported in Phase 1");
        }

        Set<Long> uniqueTableIds = new HashSet<>(dto.getTableIds());
        if (uniqueTableIds.size() != dto.getTableIds().size()) {
            throw new RequestInvalidException("Duplicate table IDs in request");
        }

        List<RestaurantTable> tables = restaurantTableRepository.findAllById(dto.getTableIds());
        if (tables.size() != dto.getTableIds().size()) {
            throw new ResourceNotFoundException("One or more requested tables do not exist");
        }

        for (RestaurantTable table : tables) {
            if (table.getStatusCode() == TableStatus.LOCKED) {
                throw new RequestInvalidException("Table " + table.getTableNumber() + " is currently locked");
            }
        }

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS
        );

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

        List<ReservationTable> resTables = tables.stream()
                .map(t -> ReservationTable.builder()
                        .reservation(savedReservation)
                        .table(t)
                        .build())
                .toList();
        reservationTableRepository.saveAll(resTables);

        return mapToResponseDTO(savedReservation, tables);
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

        if (dto.getReservationType() != ReservationType.TABLE) {
            throw new RequestInvalidException("Only TABLE reservations are supported");
        }

        Set<Long> uniqueTableIds = new HashSet<>(dto.getTableIds());
        if (uniqueTableIds.size() != dto.getTableIds().size()) {
            throw new RequestInvalidException("Duplicate table IDs in request");
        }

        List<RestaurantTable> tables = restaurantTableRepository.findAllById(dto.getTableIds());
        if (tables.size() != dto.getTableIds().size()) {
            throw new ResourceNotFoundException("One or more requested tables do not exist");
        }

        for (RestaurantTable table : tables) {
            if (table.getStatusCode() == TableStatus.LOCKED) {
                throw new RequestInvalidException("Table " + table.getTableNumber() + " is currently locked");
            }
        }

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.CONFIRMED,
                ReservationStatus.IN_PROGRESS
        );

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

        reservation.setPartySize(dto.getPartySize());
        reservation.setReservationType(dto.getReservationType());
        reservation.setStartTime(dto.getStartTime());
        reservation.setEndTime(dto.getEndTime());
        reservation.setNote(dto.getNote());

        reservationTableRepository.deleteByReservationId(id);

        List<ReservationTable> newResTables = tables.stream()
                .map(t -> ReservationTable.builder()
                        .reservation(reservation)
                        .table(t)
                        .build())
                .toList();
        reservationTableRepository.saveAll(newResTables);

        Reservation updatedReservation = reservationRepository.save(reservation);

        return mapToResponseDTO(updatedReservation, tables);
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

        return mapToResponseDTO(savedReservation, tables);
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

        return mapToResponseDTO(savedReservation, tables);
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

        return mapToResponseDTO(savedReservation, tables);
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
                    return mapToResponseDTO(r, tables);
                })
                .toList();
    }

    private ReservationResponseDTO mapToResponseDTO(Reservation reservation, List<RestaurantTable> tables) {
        List<ReservationResponseDTO.TableSummaryDTO> tableSummaries = tables.stream()
                .map(t -> ReservationResponseDTO.TableSummaryDTO.builder()
                        .id(t.getId())
                        .tableNumber(t.getTableNumber())
                        .capacity(t.getCapacity())
                        .build())
                .toList();

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
                .createdBy(reservation.getCreatedBy())
                .createdDate(reservation.getCreatedDate())
                .build();
    }
}
