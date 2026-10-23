package com.restaurant.ireservation.service.recommendation;

import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class CombinationScoringTest {
    private static final Instant START = Instant.parse("2026-11-01T11:00:00Z");
    private static final Instant END = START.plusSeconds(5400);

    private Query query(int guests, Long area) {
        return new Query(guests, START, END, area, 5, true, 15, 3);
    }

    private List<TableCandidate> tables(int count, int capacity) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new TableCandidate(i, i, capacity, 1L)).toList();
    }

    @Test
    void fiveGuestsPreferSixSeatsOverTwelve() {
        double snug = TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables(2, 3), List.of());
        double wasteful = TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables(2, 6), List.of());
        assertEquals(72.3, snug);
        assertEquals(46.7, wasteful);
        assertTrue(snug > wasteful);
    }

    @Test
    void sameCapacityWithMoreTablesScoresLower() {
        double two = TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables(2, 3), List.of());
        double three = TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables(3, 2), List.of());
        assertEquals(65.3, three);
        assertTrue(two > three);
    }

    @Test
    void preferredAreaAddsTwentyPointsComparedWithMismatch() {
        double match = TableRecommendationEngine.scoreAllocatedCombination(query(5, 1L), tables(2, 3), List.of());
        double mismatch = TableRecommendationEngine.scoreAllocatedCombination(query(5, 2L), tables(2, 3), List.of());
        assertEquals(77.8, match);
        assertEquals(57.8, mismatch);
    }

    @Test
    void tightlyPackedBookingsBeatUnusableIdleGaps() {
        List<TableCandidate> tables = tables(2, 3);
        List<Booking> tight = tables.stream().flatMap(t -> List.of(
                new Booking(t.id(), START.minusSeconds(7200), START.minusSeconds(900)),
                new Booking(t.id(), END.plusSeconds(900), END.plusSeconds(7200))).stream()).toList();
        List<Booking> deadGaps = tables.stream().flatMap(t -> List.of(
                new Booking(t.id(), START.minusSeconds(7200), START.minusSeconds(3600)),
                new Booking(t.id(), END.plusSeconds(3600), END.plusSeconds(9000))).stream()).toList();
        assertEquals(81.3, TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables, tight));
        assertEquals(57.3, TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables, deadGaps));
    }

    @Test
    void directScoreMatchesRecommendationEngineForSameCombination() {
        Query query = query(5, 1L);
        var tables = tables(2, 3);
        double recommended = TableRecommendationEngine.recommend(query, tables, List.of()).options().getFirst().score();
        assertEquals(recommended, TableRecommendationEngine.scoreAllocatedCombination(query, tables, List.of()));
    }

    @Test
    void scoresNonMinimalAllocationsWithoutRecommendingThem() {
        var tables = tables(2, 6); // Either table alone would fit; staff may still select both.
        assertEquals(46.7, TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables, List.of()));
    }

    @Test
    void manyTablesKeepScoreInRange() {
        for (int n = 2; n <= 20; n++) {
            double score = TableRecommendationEngine.scoreAllocatedCombination(query(5, 1L), tables(n, 3), List.of());
            assertTrue(score >= 0 && score <= 100);
        }
    }

    @Test
    void invalidOrInsufficientCapacityScoresZero() {
        assertEquals(0, TableRecommendationEngine.scoreAllocatedCombination(query(5, null), List.of(), List.of()));
        assertEquals(0, TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables(2, 2), List.of()));
        assertEquals(0, TableRecommendationEngine.scoreAllocatedCombination(query(5, null), tables(2, 0), List.of()));
        assertEquals(0, TableRecommendationEngine.scoreAllocatedCombination(query(0, null), tables(2, 3), List.of()));
    }
}
