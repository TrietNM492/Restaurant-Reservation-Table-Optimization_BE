package com.restaurant.ireservation.service.recommendation;

import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class AllocationOptimizationEngine {

    static final double WASTED_SEAT_COST = 1.0;
    static final double EXTRA_TABLE_COST = 4.0;
    static final double IDLE_GAP_COST = 4.0;
    static final double MOVE_COST = 3.0;
    static final double MIN_NET_GAIN = 0.01;
    static final int MAX_ITERATIONS = 1000;

    private AllocationOptimizationEngine() {
    }

    // models

    public record Params(int bufferMinutes, int maxMoves, int maxTablesPerCombination) {
    }

    /** {@code usable=false} (e.g. LOCKED) tables keep their current bookings but receive no new ones. */
    public record OptTable(long id, int tableNumber, int capacity, Long areaId, boolean usable) {
    }

    /**
     * @param seats    seats the party needs
     * @param tableIds tables currently assigned (must exist in the table list)
     * @param movable  false = pinned (in progress, about to arrive, outside window...), only blocks tables
     * @param inScope  counted in the metrics (inside the optimisation window)
     */
    public record Demand(long reservationId, int seats, Instant start, Instant end,
                         List<Long> tableIds, boolean movable, boolean inScope) {
    }

    public record Move(long reservationId, List<Long> fromTableIds, List<Long> toTableIds,
                       int wastedSeatsBefore, int wastedSeatsAfter) {
    }

    public record Metrics(int reservations, int demandSeats, int assignedSeats, int wastedSeats,
                          double seatUtilizationPercent, int idleGaps, int multiTableReservations) {
    }

    public record Plan(Metrics before, Metrics after, List<Move> moves) {
    }

    // public API

    public static Plan optimize(Params params, Collection<OptTable> tableList, Collection<Demand> demandList) {
        State st = new State(params.bufferMinutes(), tableList, demandList);
        Metrics before = st.metrics();
        Map<Long, List<Long>> initial = st.snapshot();

        List<Long> movable = demandList.stream().filter(Demand::movable)
                .map(Demand::reservationId).sorted().toList();
        List<OptTable> usable = tableList.stream().filter(OptTable::usable)
                .sorted(Comparator.comparingInt(OptTable::capacity)
                        .thenComparingInt(OptTable::tableNumber)
                        .thenComparingLong(OptTable::id))
                .toList();
        int maxSingleCapacity = usable.stream().mapToInt(OptTable::capacity).max().orElse(0);
        Map<Integer, List<List<Long>>> comboCache = new HashMap<>();
        Set<Long> changed = new HashSet<>();

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            long bestRes = -1;
            List<Long> bestTarget = null;
            double bestNet = Double.NEGATIVE_INFINITY;

            for (long rid : movable) {
                Demand d = st.demand(rid);
                List<Long> current = st.assignment(rid);
                boolean wasChanged = changed.contains(rid);

                for (List<Long> target : targets(d.seats(), current, usable, maxSingleCapacity,
                        params.maxTablesPerCombination(), comboCache)) {
                    if (sameSet(target, current) || !st.isFree(target, d)) {
                        continue;
                    }
                    boolean willChange = !sameSet(target, initial.get(rid));
                    int delta = (willChange ? 1 : 0) - (wasChanged ? 1 : 0);
                    if (changed.size() + delta > params.maxMoves()) {
                        continue;
                    }
                    double net = st.gain(d, current, target) - MOVE_COST * delta;
                    if (net > bestNet) {
                        bestNet = net;
                        bestRes = rid;
                        bestTarget = target;
                    }
                }
            }

            if (bestTarget == null || bestNet < MIN_NET_GAIN) {
                break;
            }
            st.set(bestRes, bestTarget);
            if (sameSet(bestTarget, initial.get(bestRes))) {
                changed.remove(bestRes);
            } else {
                changed.add(bestRes);
            }
        }

        List<Move> moves = new ArrayList<>();
        for (long rid : movable) {
            if (changed.contains(rid)) {
                Demand d = st.demand(rid);
                List<Long> from = initial.get(rid);
                List<Long> to = st.assignment(rid);
                moves.add(new Move(rid, from, to, st.waste(d, from), st.waste(d, to)));
            }
        }
        return new Plan(before, st.metrics(), moves);
    }

    // candidate targets

    private static List<List<Long>> targets(int seats, List<Long> current, List<OptTable> usable,
                                            int maxSingleCapacity, int maxTables,
                                            Map<Integer, List<List<Long>>> comboCache) {
        List<List<Long>> result = new ArrayList<>();
        for (OptTable t : usable) {
            if (t.capacity() >= seats) {
                result.add(List.of(t.id()));
            }
        }
        // Combinations are only explored when needed: no single table is big enough, or the
        // reservation already holds several tables (so it may be reduced / re-combined).
        if (seats > maxSingleCapacity || current.size() > 1) {
            result.addAll(comboCache.computeIfAbsent(seats, s -> buildCombinations(s, usable, maxTables)));
        }
        return result;
    }

    private static List<List<Long>> buildCombinations(int seats, List<OptTable> usable, int maxTables) {
        Map<Long, List<OptTable>> byArea = usable.stream()
                .filter(t -> t.areaId() != null)
                .collect(Collectors.groupingBy(OptTable::areaId));

        List<List<Long>> result = new ArrayList<>();
        for (List<OptTable> areaTables : byArea.values()) {
            List<TableCandidate> pool = areaTables.stream()
                    .sorted(Comparator.comparingInt(OptTable::capacity).reversed()
                            .thenComparingInt(OptTable::tableNumber))
                    .limit(TableRecommendationEngine.MAX_COMBINATION_POOL)
                    .map(t -> new TableCandidate(t.id(), t.tableNumber(), t.capacity(), t.areaId()))
                    .toList();
            List<List<TableCandidate>> combos = new ArrayList<>();
            TableRecommendationEngine.enumerate(pool, 0, Math.max(2, maxTables), seats,
                    new ArrayDeque<>(), 0, combos);
            for (List<TableCandidate> c : combos) {
                result.add(c.stream().map(TableCandidate::id).toList());
            }
        }
        return result;
    }

    private static boolean sameSet(List<Long> a, List<Long> b) {
        return new HashSet<>(a).equals(new HashSet<>(b));
    }

    // mutable working state

    private static final class State {
        final int bufferMinutes;
        final Duration buffer;
        final Map<Long, OptTable> tables = new HashMap<>();
        final Map<Long, Demand> demands = new LinkedHashMap<>();
        final Map<Long, List<Long>> assignment = new HashMap<>();
        final Map<Long, Set<Long>> onTable = new HashMap<>();

        State(int bufferMinutes, Collection<OptTable> tableList, Collection<Demand> demandList) {
            this.bufferMinutes = bufferMinutes;
            this.buffer = Duration.ofMinutes(bufferMinutes);
            tableList.forEach(t -> tables.put(t.id(), t));
            for (Demand d : demandList) {
                demands.put(d.reservationId(), d);
                assignment.put(d.reservationId(), List.copyOf(d.tableIds()));
                for (long t : d.tableIds()) {
                    onTable.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(d.reservationId());
                }
            }
        }

        Demand demand(long rid) {
            return demands.get(rid);
        }

        List<Long> assignment(long rid) {
            return assignment.get(rid);
        }

        Map<Long, List<Long>> snapshot() {
            return new HashMap<>(assignment);
        }

        void set(long rid, List<Long> target) {
            for (long t : assignment.get(rid)) {
                onTable.get(t).remove(rid);
            }
            assignment.put(rid, List.copyOf(target));
            for (long t : target) {
                onTable.computeIfAbsent(t, k -> new LinkedHashSet<>()).add(rid);
            }
        }

        /** True when no OTHER reservation on any target table overlaps d (widened by the buffer). */
        boolean isFree(List<Long> target, Demand d) {
            Instant from = d.start().minus(buffer);
            Instant to = d.end().plus(buffer);
            for (long t : target) {
                for (long other : onTable.getOrDefault(t, Set.of())) {
                    if (other == d.reservationId()) continue;
                    Demand o = demands.get(other);
                    if (o.start().isBefore(to) && o.end().isAfter(from)) {
                        return false;
                    }
                }
            }
            return true;
        }

        int capacity(List<Long> tableIds) {
            int sum = 0;
            for (long t : tableIds) sum += tables.get(t).capacity();
            return sum;
        }

        int waste(Demand d, List<Long> tableIds) {
            return Math.max(0, capacity(tableIds) - d.seats());
        }

        /** Number of holes between consecutive bookings that are too short to be resold. */
        int idleGaps(long tableId) {
            Set<Long> ids = onTable.get(tableId);
            if (ids == null || ids.size() < 2) return 0;
            List<Demand> list = ids.stream().map(demands::get)
                    .sorted(Comparator.comparing(Demand::start)).toList();
            int count = 0;
            for (int i = 1; i < list.size(); i++) {
                long idle = Duration.between(list.get(i - 1).end(), list.get(i).start()).toMinutes() - bufferMinutes;
                if (idle > TableRecommendationEngine.TIGHT_GAP_MINUTES
                        && idle < TableRecommendationEngine.USABLE_GAP_MINUTES) {
                    count++;
                }
            }
            return count;
        }

        double localCost(Demand d, Collection<Long> touchedTables) {
            List<Long> assigned = assignment.get(d.reservationId());
            double cost = WASTED_SEAT_COST * waste(d, assigned) + EXTRA_TABLE_COST * (assigned.size() - 1);
            for (long t : touchedTables) cost += IDLE_GAP_COST * idleGaps(t);
            return cost;
        }

        /** Cost reduction obtained by moving d from current to target (temporarily applies the move). */
        double gain(Demand d, List<Long> current, List<Long> target) {
            Set<Long> touched = new LinkedHashSet<>(current);
            touched.addAll(target);
            double before = localCost(d, touched);
            set(d.reservationId(), target);
            double after = localCost(d, touched);
            set(d.reservationId(), current);
            return before - after;
        }

        Metrics metrics() {
            int n = 0, demandSeats = 0, assigned = 0, wasted = 0, used = 0, multi = 0;
            for (Demand d : demands.values()) {
                if (!d.inScope()) continue;
                List<Long> a = assignment.get(d.reservationId());
                int cap = capacity(a);
                n++;
                demandSeats += d.seats();
                assigned += cap;
                wasted += Math.max(0, cap - d.seats());
                used += Math.min(cap, d.seats());
                if (a.size() > 1) multi++;
            }
            int gaps = 0;
            for (long t : onTable.keySet()) gaps += idleGaps(t);
            double util = assigned == 0 ? 0 : Math.round(1000.0 * used / assigned) / 10.0;
            return new Metrics(n, demandSeats, assigned, wasted, util, gaps, multi);
        }
    }
}
