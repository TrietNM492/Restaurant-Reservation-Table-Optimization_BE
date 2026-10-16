package com.restaurant.ireservation.service;

import com.restaurant.ireservation.domain.entity.ReservationTable;
import com.restaurant.ireservation.domain.entity.RestaurantTable;
import com.restaurant.ireservation.domain.enumerations.PartySize;
import com.restaurant.ireservation.domain.enumerations.ReservationStatus;
import com.restaurant.ireservation.domain.enumerations.TableStatus;
import com.restaurant.ireservation.dto.request.TableRecommendationRequest;
import com.restaurant.ireservation.dto.request.TableRecommendationRequest.AlternativeMode;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.AlternativeOption;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.AlternativeType;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationOption;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.RecommendationType;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.ScoreBreakdown;
import com.restaurant.ireservation.dto.response.TableRecommendationResponse.TableSummary;
import com.restaurant.ireservation.error.RequestInvalidException;
import com.restaurant.ireservation.error.ResourceNotFoundException;
import com.restaurant.ireservation.repository.ReservationTableRepository;
import com.restaurant.ireservation.repository.RestaurantTableRepository;
import com.restaurant.ireservation.repository.TableAreaRepository;
import com.restaurant.ireservation.service.recommendation.AlternativeRecommendationEngine;
import com.restaurant.ireservation.service.recommendation.AlternativeRecommendationEngine.Alternative;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Booking;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Option;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Query;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Result;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TableRecommendationService {

    private static final int DEFAULT_DURATION_MINUTES = 90;
    private static final int DEFAULT_MAX_RESULTS = 5;
    /** Cleaning / turnaround time kept free between two bookings of the same table. */
    private static final int TURNAROUND_BUFFER_MINUTES = 15;
    private static final int MAX_TABLES_PER_COMBINATION = 3;
    /** How far around the requested slot we look for neighbouring bookings (for gap scoring and alternatives). */
    private static final Duration NEIGHBOUR_LOOKAROUND = Duration.ofHours(6);
    /** A slot starting within this time from now is blocked by tables that are physically IN_USE / CLEANING. */
    private static final Duration OCCUPANCY_LOOKAHEAD = Duration.ofMinutes(30);
    /** Small tolerance so a "now" request that spends a few seconds on the wire is not rejected. */
    private static final Duration PAST_TOLERANCE = Duration.ofMinutes(5);

    private static final int DEFAULT_MAX_ALTERNATIVES = 5;
    private static final int DEFAULT_MAX_SHIFT_MINUTES = 120;
    /** Alternatives never shorten the stay below this many minutes. */
    private static final int MIN_ALTERNATIVE_DURATION_MINUTES = 60;
    /** Big parties: how many tables of ONE area a MORE_TABLES alternative may merge (usual limit is 3). */
    private static final int EXTENDED_MAX_TABLES_PER_COMBINATION = 5;

    /** Same blocking statuses used by ReservationService when it checks conflicts. */
    private static final List<ReservationStatus> BLOCKING_STATUSES = List.of(
            ReservationStatus.PENDING,
            ReservationStatus.CONFIRMED,
            ReservationStatus.IN_PROGRESS
    );

    RestaurantTableRepository restaurantTableRepository;
    ReservationTableRepository reservationTableRepository;
    TableAreaRepository tableAreaRepository;

    /** Recommends tables for the requested slot; alternatives follow the request's alternativeMode (AUTO). */
    @Transactional(readOnly = true)
    public TableRecommendationResponse recommend(TableRecommendationRequest request) {
        AlternativeMode mode = request.getAlternativeMode() != null ? request.getAlternativeMode() : AlternativeMode.AUTO;
        return process(request, mode);
    }

    /** Same as {@link #recommend} but always computes alternatives ("what else can we offer?"). */
    @Transactional(readOnly = true)
    public TableRecommendationResponse recommendAlternatives(TableRecommendationRequest request) {
        return process(request, AlternativeMode.ALWAYS);
    }

    private TableRecommendationResponse process(TableRecommendationRequest request, AlternativeMode mode) {
        Instant now = Instant.now();
        Instant start = request.getStartTime() != null ? request.getStartTime() : now;
        if (start.isBefore(now.minus(PAST_TOLERANCE))) {
            throw new RequestInvalidException("Start time cannot be in the past");
        }

        int duration = request.getDurationMinutes() != null ? request.getDurationMinutes() : DEFAULT_DURATION_MINUTES;
        int maxResults = request.getMaxResults() != null ? request.getMaxResults() : DEFAULT_MAX_RESULTS;
        boolean allowCombination = request.getAllowCombination() == null || request.getAllowCombination();
        Instant end = start.plus(Duration.ofMinutes(duration));
        int guests = request.getGuestCount();

        Long preferredAreaId = request.getPreferredAreaId();
        if (preferredAreaId != null && !tableAreaRepository.existsById(preferredAreaId)) {
            throw new ResourceNotFoundException("Table area not found with id: " + preferredAreaId);
        }

        // Every table that is not LOCKED can host a booking at SOME time; whether it is usable for a given
        // start time (IN_USE / CLEANING only matter for slots starting soon) is decided per probe.
        Map<Long, RestaurantTable> bookable = new LinkedHashMap<>();
        for (RestaurantTable t : restaurantTableRepository.findAllByOrderByTableNumberAsc()) {
            if (t.getStatusCode() != TableStatus.LOCKED) {
                bookable.put(t.getId(), t);
            }
        }
        List<Booking> bookings = bookable.isEmpty() ? List.of() : loadBookings(bookable.keySet(), start, end);

        Query query = new Query(guests, start, end, preferredAreaId, maxResults, allowCombination,
                TURNAROUND_BUFFER_MINUTES, MAX_TABLES_PER_COMBINATION);
        Result result = TableRecommendationEngine.recommend(query, candidatesFor(bookable, now).apply(start), bookings);

        List<RecommendationOption> options = new ArrayList<>();
        int rank = 1;
        for (Option o : result.options()) {
            options.add(toOption(rank++, o, guests, bookable));
        }

        boolean searchAlternatives = mode == AlternativeMode.ALWAYS
                || (mode == AlternativeMode.AUTO && options.isEmpty());
        int maxShift = request.getMaxShiftMinutes() != null ? request.getMaxShiftMinutes() : DEFAULT_MAX_SHIFT_MINUTES;
        List<AlternativeOption> alternatives = searchAlternatives
                ? findAlternatives(request, query, now, maxShift, bookable, bookings, guests)
                : List.of();

        return TableRecommendationResponse.builder()
                .guestCount(guests)
                .suggestedPartySize(toPartySize(guests))
                .startTime(start)
                .endTime(end)
                .durationMinutes(duration)
                .availableTableCount(result.freeTableCount())
                .recommendations(options)
                .alternatives(alternatives)
                .message(buildMessage(options, result.freeTableCount(), guests, alternatives.size(),
                        searchAlternatives, maxShift))
                .build();
    }

    /**
     * Maps a head count to the PartySize bucket stored on Reservation.
     * Buckets: SINGLE = 1, SMALL = 2-4, MEDIUM = 5-8, LARGE = 9-12, XLARGE = 13+.
     */
    public static PartySize toPartySize(int guests) {
        if (guests <= 1) return PartySize.SINGLE;
        if (guests <= 4) return PartySize.SMALL;
        if (guests <= 8) return PartySize.MEDIUM;
        if (guests <= 12) return PartySize.LARGE;
        return PartySize.XLARGE;
    }

    // alternatives

    private List<AlternativeOption> findAlternatives(TableRecommendationRequest request, Query query, Instant now,
                                                     int maxShift, Map<Long, RestaurantTable> bookable,
                                                     List<Booking> bookings, int guests) {
        int maxAlternatives = request.getMaxAlternatives() != null
                ? request.getMaxAlternatives() : DEFAULT_MAX_ALTERNATIVES;
        // earliestStart = now: never suggest a slot in the past. latestEnd = null: opening hours are not
        // modelled in this project yet; pass the closing time here once they are.
        AlternativeRecommendationEngine.Params params = new AlternativeRecommendationEngine.Params(
                AlternativeRecommendationEngine.STEP_MINUTES, maxShift, maxAlternatives,
                MIN_ALTERNATIVE_DURATION_MINUTES, EXTENDED_MAX_TABLES_PER_COMBINATION, now, null);

        List<AlternativeOption> result = new ArrayList<>();
        int rank = 1;
        for (Alternative a : AlternativeRecommendationEngine.find(params, query, candidatesFor(bookable, now), bookings)) {
            result.add(AlternativeOption.builder()
                    .rank(rank)
                    .type(AlternativeType.valueOf(a.kind().name()))
                    .startTime(a.start())
                    .endTime(a.end())
                    .durationMinutes(a.durationMinutes())
                    .shiftMinutes(a.shiftMinutes())
                    .durationChangeMinutes(a.durationChangeMinutes())
                    .convenienceScore(a.convenienceScore())
                    .score(a.score())
                    .explanation(a.explanation())
                    .recommendation(toOption(1, a.option(), guests, bookable))
                    .build());
            rank++;
        }
        return result;
    }

    // data access / mapping

    /**
     * Tables usable for a slot starting at the given instant. IN_USE / CLEANING describe the table right now,
     * so they only block slots starting within {@link #OCCUPANCY_LOOKAHEAD}; for later slots real conflicts
     * are detected from the bookings instead.
     */
    private Function<Instant, List<TableCandidate>> candidatesFor(
            Map<Long, RestaurantTable> bookable, Instant now) {
        return slotStart -> {
            boolean startsSoon = slotStart.isBefore(now.plus(OCCUPANCY_LOOKAHEAD));
            List<TableCandidate> list = new ArrayList<>();
            for (RestaurantTable t : bookable.values()) {
                TableStatus status = t.getStatusCode();
                if (startsSoon && (status == TableStatus.IN_USE || status == TableStatus.CLEANING)) {
                    continue;
                }
                list.add(new TableCandidate(
                        t.getId(),
                        t.getTableNumber() != null ? t.getTableNumber() : 0,
                        t.getCapacity(),
                        t.getArea() != null ? t.getArea().getId() : null));
            }
            return list;
        };
    }

    private List<Booking> loadBookings(Collection<Long> tableIds, Instant start, Instant end) {
        List<ReservationTable> rows = reservationTableRepository.findActiveBookingsInWindow(
                tableIds,
                BLOCKING_STATUSES,
                start.minus(NEIGHBOUR_LOOKAROUND),
                end.plus(NEIGHBOUR_LOOKAROUND)
        );
        return rows.stream()
                .map(rt -> new Booking(
                        rt.getTable().getId(),
                        rt.getReservation().getStartTime(),
                        rt.getReservation().getEndTime()))
                .toList();
    }

    private RecommendationOption toOption(int rank, Option option, int guests, Map<Long, RestaurantTable> tables) {
        List<TableSummary> summaries = option.tables().stream()
                .map(c -> toSummary(tables.get(c.id())))
                .toList();

        return RecommendationOption.builder()
                .rank(rank)
                .type(option.isCombination() ? RecommendationType.TABLE_COMBINATION : RecommendationType.SINGLE_TABLE)
                .tableIds(option.tables().stream().map(TableCandidate::id).toList())
                .tables(summaries)
                .totalCapacity(option.totalCapacity())
                .spareSeats(option.totalCapacity() - guests)
                .score(option.score())
                .scoreBreakdown(ScoreBreakdown.builder()
                        .capacityFit(option.fitScore())
                        .schedulePacking(option.gapScore())
                        .areaMatch(option.areaScore())
                        .build())
                .reasons(option.reasons())
                .build();
    }

    private TableSummary toSummary(RestaurantTable t) {
        return TableSummary.builder()
                .id(t.getId())
                .tableNumber(t.getTableNumber())
                .capacity(t.getCapacity())
                .areaId(t.getArea() != null ? t.getArea().getId() : null)
                .areaCode(t.getArea() != null ? t.getArea().getCode() : null)
                .areaName(t.getArea() != null ? t.getArea().getName() : null)
                .status(t.getStatusCode())
                .build();
    }

    private String buildMessage(List<RecommendationOption> options, int freeTables, int guests,
                                int alternativeCount, boolean searchedAlternatives, int maxShift) {
        if (options.isEmpty()) {
            if (alternativeCount > 0) {
                return "No table can seat " + guests + " guests at the requested time, but " + alternativeCount
                        + (alternativeCount == 1 ? " alternative was" : " alternatives were")
                        + " found (see 'alternatives').";
            }
            if (searchedAlternatives) {
                return "No table can seat " + guests + " guests at the requested time, and no alternative was found "
                        + "within " + maxShift + " minutes earlier or later. Please try another date or consider a room.";
            }
            if (freeTables == 0) {
                return "No tables are available in the requested time slot. Please try a different time.";
            }
            return "No table or table combination can seat " + guests
                    + " guests in the requested time slot. Please try a different time or consider a room.";
        }
        if (options.get(0).getType() == RecommendationType.TABLE_COMBINATION) {
            return "No single table can seat " + guests + " guests; showing table combinations instead.";
        }
        return null;
    }
}
