package com.restaurant.ireservation.service.recommendation;

import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.Demand;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.OptTable;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.Params;
import com.restaurant.ireservation.service.recommendation.AllocationOptimizationEngine.Plan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AllocationOptimizationEngineTest {

    static final Instant T18 = Instant.parse("2026-10-20T18:00:00Z");
    static final long AREA = 1L;
    static final Params PARAMS = new Params(15, 10, 3);

    static OptTable table(long id, int capacity) {
        return new OptTable(id, (int) id, capacity, AREA, true);
    }

    static Demand res(long id, int seats, Instant start, int minutes, boolean movable, long... tables) {
        return new Demand(id, seats, start, start.plus(Duration.ofMinutes(minutes)),
                java.util.Arrays.stream(tables).boxed().toList(), movable, true);
    }

    @Test
    @DisplayName("Moves a small party off an oversized table when a better free table exists")
    void relocatesToSmallerTable() {
        var tables = List.of(table(1, 2), table(2, 8));
        var demands = List.of(res(10, 2, T18, 90, true, 2));

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertEquals(1, plan.moves().size());
        assertEquals(List.of(2L), plan.moves().get(0).fromTableIds());
        assertEquals(List.of(1L), plan.moves().get(0).toTableIds());
        assertEquals(6, plan.moves().get(0).wastedSeatsBefore());
        assertEquals(0, plan.moves().get(0).wastedSeatsAfter());
        assertEquals(6, plan.before().wastedSeats());
        assertEquals(0, plan.after().wastedSeats());
        assertTrue(plan.after().seatUtilizationPercent() > plan.before().seatUtilizationPercent());
    }

    @Test
    @DisplayName("Marginal improvements are not worth moving a customer")
    void ignoresMarginalImprovement() {
        var tables = List.of(table(1, 4), table(2, 5));
        var demands = List.of(res(10, 4, T18, 90, true, 2));    // 1 wasted seat -> 0 would save only 1

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertTrue(plan.moves().isEmpty());
        assertEquals(plan.before(), plan.after());
    }

    @Test
    @DisplayName("Pinned reservations are never moved")
    void pinnedReservationsStay() {
        var tables = List.of(table(1, 2), table(2, 8));
        var demands = List.of(res(10, 2, T18, 90, false, 2));

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertTrue(plan.moves().isEmpty());
    }

    @Test
    @DisplayName("Never moves onto a table that has an overlapping booking (turnaround buffer included)")
    void respectsConflictsAndBuffer() {
        var tables = List.of(table(1, 2), table(2, 8));
        var demands = List.of(
                res(10, 2, T18, 90, true, 2),
                // pinned booking on table 1 ends 10 min before our slot -> inside the 15 min buffer
                res(11, 2, T18.minus(Duration.ofMinutes(100)), 90, false, 1)
        );

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertTrue(plan.moves().isEmpty());
    }

    @Test
    @DisplayName("A booking exactly one buffer after another one may share the table")
    void allowsExactBuffer() {
        var tables = List.of(table(1, 2), table(2, 8));
        var demands = List.of(
                res(10, 2, T18, 90, true, 2),
                res(11, 2, T18.minus(Duration.ofMinutes(105)), 90, false, 1)   // ends 15 min before
        );

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertEquals(1, plan.moves().size());
        assertEquals(List.of(1L), plan.moves().get(0).toTableIds());
    }

    @Test
    @DisplayName("Replaces a multi-table booking by a single table when one is free")
    void mergesCombinationIntoSingleTable() {
        var tables = List.of(table(1, 4), table(2, 4), table(3, 8));
        var demands = List.of(res(10, 8, T18, 90, true, 1, 2));

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertEquals(1, plan.moves().size());
        assertEquals(List.of(3L), plan.moves().get(0).toTableIds());
        assertEquals(1, plan.before().multiTableReservations());
        assertEquals(0, plan.after().multiTableReservations());
    }

    @Test
    @DisplayName("Removes a short unusable idle gap between two bookings")
    void removesIdleGap() {
        var tables = List.of(table(1, 4), table(2, 4));
        // 18:00-19:30 and 20:30-22:00 on table 1: 60 min hole - 15 buffer = 45 min dead gap
        var demands = List.of(
                res(10, 4, T18, 90, true, 1),
                res(11, 4, T18.plus(Duration.ofMinutes(150)), 90, true, 1)
        );

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertEquals(1, plan.before().idleGaps());
        assertEquals(0, plan.after().idleGaps());
        assertEquals(1, plan.moves().size());
    }

    @Test
    @DisplayName("maxMoves caps the number of reservations that change table")
    void respectsMaxMoves() {
        var tables = List.of(table(1, 2), table(2, 2), table(3, 2), table(4, 8), table(5, 8), table(6, 8));
        var demands = List.of(
                res(10, 2, T18, 90, true, 4),
                res(11, 2, T18, 90, true, 5),
                res(12, 2, T18, 90, true, 6)
        );

        Plan plan = AllocationOptimizationEngine.optimize(new Params(15, 2, 3), tables, demands);

        assertEquals(2, plan.moves().size());
    }

    @Test
    @DisplayName("Unusable (locked) tables never receive new bookings")
    void doesNotMoveToUnusableTable() {
        var tables = List.of(new OptTable(1, 1, 2, AREA, false), table(2, 8));
        var demands = List.of(res(10, 2, T18, 90, true, 2));

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertTrue(plan.moves().isEmpty());
    }

    @Test
    @DisplayName("Reservations outside the scope are blockers but not counted in metrics")
    void outOfScopeNotCounted() {
        var tables = List.of(table(1, 4));
        var outside = new Demand(11, 4, T18.plus(Duration.ofDays(2)), T18.plus(Duration.ofDays(2)).plusSeconds(5400),
                List.of(1L), false, false);
        var inside = res(10, 4, T18, 90, true, 1);

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, List.of(inside, outside));

        assertEquals(1, plan.before().reservations());
    }

    @Test
    @DisplayName("Result is never worse than the current allocation")
    void neverWorse() {
        var tables = List.of(table(1, 2), table(2, 4), table(3, 6), table(4, 8));
        var demands = List.of(
                res(10, 2, T18, 90, true, 4),
                res(11, 3, T18, 90, true, 3),
                res(12, 4, T18.plus(Duration.ofMinutes(30)), 90, true, 4),
                res(13, 6, T18.plus(Duration.ofMinutes(200)), 90, true, 3)
        );

        Plan plan = AllocationOptimizationEngine.optimize(PARAMS, tables, demands);

        assertTrue(plan.after().wastedSeats() <= plan.before().wastedSeats());
        assertTrue(plan.after().idleGaps() <= plan.before().idleGaps());
    }
}
