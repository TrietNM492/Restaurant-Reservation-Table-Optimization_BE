package com.restaurant.ireservation.service.recommendation;

import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Booking;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Option;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Query;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Result;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TableRecommendationEngineTest {

    static final Instant START = Instant.parse("2026-10-20T18:00:00Z");
    static final Instant END = START.plus(Duration.ofMinutes(90));
    static final long AREA_INDOOR = 1L;
    static final long AREA_GARDEN = 2L;

    static TableCandidate table(long id, int capacity, Long areaId) {
        return new TableCandidate(id, (int) id, capacity, areaId);
    }

    static Query query(int guests, Long preferredArea, int maxResults, boolean combos) {
        return new Query(guests, START, END, preferredArea, maxResults, combos, 15, 3);
    }

    static Booking booking(long tableId, Instant start, Instant end) {
        return new Booking(tableId, start, end);
    }

    @Test
    @DisplayName("Best fit: smallest table that seats the party ranks first")
    void prefersSmallestSufficientTable() {
        var tables = List.of(table(1, 2, AREA_INDOOR), table(2, 4, AREA_INDOOR), table(3, 8, AREA_INDOOR));

        Result r = TableRecommendationEngine.recommend(query(3, null, 5, true), tables, List.of());

        assertEquals(2, r.options().size());
        assertEquals(2L, r.options().get(0).tables().get(0).id());
        assertEquals(3L, r.options().get(1).tables().get(0).id());
        assertTrue(r.options().get(0).score() > r.options().get(1).score());
        assertNull(r.options().get(0).areaScore());
    }

    @Test
    @DisplayName("Tables smaller than the party are never recommended as a single table")
    void neverRecommendsTooSmallSingleTable() {
        var tables = List.of(table(1, 2, AREA_INDOOR), table(2, 4, AREA_INDOOR));

        Result r = TableRecommendationEngine.recommend(query(5, null, 5, false), tables, List.of());

        assertTrue(r.options().isEmpty());
        assertEquals(2, r.freeTableCount());
    }

    @Test
    @DisplayName("Overlapping booking removes the table")
    void excludesTablesWithConflictingBooking() {
        var tables = List.of(table(1, 4, AREA_INDOOR), table(2, 4, AREA_INDOOR));
        var bookings = List.of(booking(1, START.plusSeconds(1800), END.plusSeconds(1800)));

        Result r = TableRecommendationEngine.recommend(query(4, null, 5, false), tables, bookings);

        assertEquals(1, r.options().size());
        assertEquals(2L, r.options().get(0).tables().get(0).id());
        assertEquals(1, r.freeTableCount());
    }

    @Test
    @DisplayName("Turnaround buffer: a booking ending 10 min before the slot still blocks (buffer = 15)")
    void bufferBlocksBackToBackBookings() {
        var tables = List.of(table(1, 4, AREA_INDOOR));
        var tooClose = List.of(booking(1, START.minus(Duration.ofMinutes(100)), START.minus(Duration.ofMinutes(10))));
        var exactBuffer = List.of(booking(1, START.minus(Duration.ofMinutes(100)), START.minus(Duration.ofMinutes(15))));

        assertTrue(TableRecommendationEngine.recommend(query(4, null, 5, false), tables, tooClose).options().isEmpty());
        assertEquals(1, TableRecommendationEngine.recommend(query(4, null, 5, false), tables, exactBuffer).options().size());
    }

    @Test
    @DisplayName("Packing: table that fits snugly next to a booking beats one leaving a dead gap")
    void prefersTightPackingOverDeadGap() {
        var tables = List.of(table(1, 4, AREA_INDOOR), table(2, 4, AREA_INDOOR));
        // table 1: previous booking ends exactly buffer before start -> tight
        // table 2: previous booking ends 60 min before start (45 idle after buffer) -> dead gap
        var bookings = List.of(
                booking(1, START.minus(Duration.ofMinutes(120)), START.minus(Duration.ofMinutes(15))),
                booking(2, START.minus(Duration.ofMinutes(150)), START.minus(Duration.ofMinutes(60)))
        );

        Result r = TableRecommendationEngine.recommend(query(4, null, 5, false), tables, bookings);

        assertEquals(2, r.options().size());
        assertEquals(1L, r.options().get(0).tables().get(0).id());
        assertTrue(r.options().get(0).gapScore() > r.options().get(1).gapScore());
        assertTrue(r.options().get(1).reasons().stream().anyMatch(s -> s.contains("idle gap")));
    }

    @Test
    @DisplayName("Preferred area boosts score but does not exclude other areas")
    void preferredAreaIsSoft() {
        var tables = List.of(table(1, 4, AREA_INDOOR), table(2, 4, AREA_GARDEN));

        Result r = TableRecommendationEngine.recommend(query(4, AREA_GARDEN, 5, false), tables, List.of());

        assertEquals(2, r.options().size());
        assertEquals(2L, r.options().get(0).tables().get(0).id());
        assertEquals(1.0, r.options().get(0).areaScore().doubleValue());
        assertEquals(0.0, r.options().get(1).areaScore().doubleValue());
    }

    @Test
    @DisplayName("Combination is used when no single table is big enough, same area only")
    void combinesTablesInSameArea() {
        var tables = List.of(
                table(1, 4, AREA_INDOOR), table(2, 4, AREA_INDOOR),
                table(3, 4, AREA_GARDEN)
        );

        Result r = TableRecommendationEngine.recommend(query(8, null, 5, true), tables, List.of());

        assertEquals(1, r.options().size());
        Option o = r.options().get(0);
        assertTrue(o.isCombination());
        assertEquals(8, o.totalCapacity());
        assertEquals(List.of(1L, 2L), o.tables().stream().map(TableCandidate::id).sorted().toList());
    }

    @Test
    @DisplayName("Combinations are minimal: no redundant table")
    void combinationsAreMinimal() {
        var tables = List.of(table(1, 6, AREA_INDOOR), table(2, 4, AREA_INDOOR), table(3, 2, AREA_INDOOR));

        Result r = TableRecommendationEngine.recommend(query(8, null, 10, true), tables, List.of());

        // 6+4=10 (ok), 6+2=8 (ok), 4+2=6 (too small), 6+4+2 is redundant
        assertEquals(2, r.options().size());
        for (Option o : r.options()) {
            assertEquals(2, o.tables().size());
        }
        assertEquals(8, r.options().get(0).totalCapacity());   // exact fit ranks first
    }

    @Test
    @DisplayName("Single tables rank before combinations and combinations only fill remaining slots")
    void singlesBeforeCombinations() {
        var tables = List.of(table(1, 6, AREA_INDOOR), table(2, 4, AREA_INDOOR),
                table(3, 3, AREA_INDOOR), table(4, 2, AREA_INDOOR));

        // single: 6. minimal combos for 5 guests: 3+2 (5), 4+2 (6), 4+3 (7)
        Result r = TableRecommendationEngine.recommend(query(5, null, 3, true), tables, List.of());

        assertEquals(3, r.options().size());
        assertFalse(r.options().get(0).isCombination());
        assertTrue(r.options().get(1).isCombination());
        assertTrue(r.options().get(2).isCombination());
        assertEquals(5, r.options().get(1).totalCapacity());   // exact-fit combination first
    }

    @Test
    @DisplayName("allowCombination=false never returns combinations")
    void combinationsCanBeDisabled() {
        var tables = List.of(table(1, 4, AREA_INDOOR), table(2, 4, AREA_INDOOR));

        Result r = TableRecommendationEngine.recommend(query(8, null, 5, false), tables, List.of());

        assertTrue(r.options().isEmpty());
    }

    @Test
    @DisplayName("Tables without area are never combined")
    void tablesWithoutAreaAreNotCombined() {
        var tables = List.of(table(1, 4, null), table(2, 4, null));

        Result r = TableRecommendationEngine.recommend(query(8, null, 5, true), tables, List.of());

        assertTrue(r.options().isEmpty());
    }

    @Test
    @DisplayName("maxResults limits the output")
    void respectsMaxResults() {
        var tables = List.of(table(1, 4, AREA_INDOOR), table(2, 4, AREA_INDOOR), table(3, 4, AREA_INDOOR));

        Result r = TableRecommendationEngine.recommend(query(4, null, 2, true), tables, List.of());

        assertEquals(2, r.options().size());
    }

    @Test
    @DisplayName("Exact capacity match scores higher than oversized table and explains why")
    void exactMatchReason() {
        var tables = List.of(table(1, 4, AREA_INDOOR), table(2, 6, AREA_INDOOR));

        Result r = TableRecommendationEngine.recommend(query(4, null, 5, false), tables, List.of());

        assertTrue(r.options().get(0).reasons().get(0).startsWith("Exact capacity match"));
        assertEquals(1.0, r.options().get(0).fitScore());
    }
}
