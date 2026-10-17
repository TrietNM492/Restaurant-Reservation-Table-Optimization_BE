package com.restaurant.ireservation.service.recommendation;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class TableRecommendationEngine {

    // scoring weights (sum to 1.0 in each mode)
    static final double W_FIT_WITH_AREA = 0.55;
    static final double W_GAP_WITH_AREA = 0.25;
    static final double W_AREA = 0.20;
    static final double W_FIT = 0.70;
    static final double W_GAP = 0.30;

    /** Each extra table in a combination reduces the fit score by this fraction. */
    static final double COMBINATION_PENALTY_PER_EXTRA_TABLE = 0.12;

    /** Idle time (beyond the buffer) up to this value counts as a "tight" fit with a neighbour booking. */
    static final int TIGHT_GAP_MINUTES = 15;
    /** Idle time at or above this value is long enough to host another booking. */
    static final int USABLE_GAP_MINUTES = 90;

    static final double GAP_TIGHT = 1.0;
    static final double GAP_OPEN = 0.7;
    static final double GAP_DEAD = 0.2;

    /** Upper bound of tables per area taken into combination search (keeps enumeration cheap). */
    static final int MAX_COMBINATION_POOL = 30;

    private TableRecommendationEngine() {
    }

    // input / output models

    public record TableCandidate(long id, int tableNumber, int capacity, Long areaId) {
    }

    /** A booking that blocks a table (caller must only pass PENDING / CONFIRMED / IN_PROGRESS ones). */
    public record Booking(long tableId, Instant start, Instant end) {
    }

    public record Query(
            int guestCount,
            Instant start,
            Instant end,
            Long preferredAreaId,
            int maxResults,
            boolean allowCombination,
            int bufferMinutes,
            int maxTablesPerCombination
    ) {
    }

    /** {@code areaScore} is null when the caller did not specify a preferred area. */
    public record Option(
            List<TableCandidate> tables,
            int totalCapacity,
            double score,
            double fitScore,
            double gapScore,
            Double areaScore,
            List<String> reasons
    ) {
        public boolean isCombination() {
            return tables.size() > 1;
        }
    }

    public record Result(List<Option> options, int freeTableCount) {
    }

    private record GapInfo(Long idleBeforeMinutes, Long idleAfterMinutes, double score) {
    }

    // public API

    public static Result recommend(Query query, Collection<TableCandidate> candidates, Collection<Booking> bookings) {
        Map<Long, List<Booking>> bookingsByTable = bookings.stream()
                .collect(Collectors.groupingBy(Booking::tableId));

        Duration buffer = Duration.ofMinutes(query.bufferMinutes());
        Instant blockedFrom = query.start().minus(buffer);
        Instant blockedTo = query.end().plus(buffer);

        List<TableCandidate> free = candidates.stream()
                .filter(t -> bookingsByTable.getOrDefault(t.id(), List.of()).stream()
                        .noneMatch(b -> b.start().isBefore(blockedTo) && b.end().isAfter(blockedFrom)))
                .toList();

        Map<Long, GapInfo> gaps = new HashMap<>();
        for (TableCandidate t : free) {
            gaps.put(t.id(), computeGap(bookingsByTable.getOrDefault(t.id(), List.of()), query, buffer));
        }

        List<Option> options = new ArrayList<>();
        options.addAll(rankSingles(query, free, gaps));

        if (query.allowCombination() && options.size() < query.maxResults()) {
            options.addAll(rankCombinations(query, free, gaps, query.maxResults() - options.size()));
        }

        List<Option> top = options.size() > query.maxResults()
                ? List.copyOf(options.subList(0, query.maxResults()))
                : List.copyOf(options);
        return new Result(top, free.size());
    }

    // singles

    private static List<Option> rankSingles(Query q, List<TableCandidate> free, Map<Long, GapInfo> gaps) {
        return free.stream()
                .filter(t -> t.capacity() >= q.guestCount())
                .map(t -> buildSingle(q, t, gaps.get(t.id())))
                .sorted(Comparator.comparingDouble(Option::score).reversed()
                        .thenComparingInt(Option::totalCapacity)
                        .thenComparingInt(o -> o.tables().get(0).tableNumber()))
                .toList();
    }

    private static Option buildSingle(Query q, TableCandidate t, GapInfo gap) {
        double fit = (double) q.guestCount() / t.capacity();
        Double area = areaScore(q.preferredAreaId(), List.of(t));

        List<String> reasons = new ArrayList<>();
        int spare = t.capacity() - q.guestCount();
        if (spare == 0) {
            reasons.add("Exact capacity match: seats " + t.capacity());
        } else {
            reasons.add("Seats " + t.capacity() + " for " + q.guestCount() + " guests (" + spare
                    + (spare == 1 ? " spare seat)" : " spare seats)"));
        }
        addAreaReason(reasons, q.preferredAreaId(), area);
        addGapReason(reasons, gap);

        return new Option(List.of(t), t.capacity(), combine(fit, gap.score(), area),
                round2(fit), round2(gap.score()), area, reasons);
    }

    // combinations

    /** Lightweight scored combination: the expensive Option (with reason strings) is built only if it is kept. */
    private record ScoredCombo(List<TableCandidate> tables, int total, double fit, double gap, Double area,
                               double score, int[] sortedTableNumbers) {
    }

    private static List<Option> rankCombinations(Query q, List<TableCandidate> free, Map<Long, GapInfo> gaps,
                                                 int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int maxSize = Math.max(2, q.maxTablesPerCombination());

        Map<Long, List<TableCandidate>> byArea = free.stream()
                .filter(t -> t.areaId() != null)   // tables without an area cannot be safely merged
                .collect(Collectors.groupingBy(TableCandidate::areaId));

        List<ScoredCombo> scored = new ArrayList<>();
        for (List<TableCandidate> areaTables : byArea.values()) {
            List<TableCandidate> pool = areaTables.stream()
                    .sorted(Comparator.comparingInt(TableCandidate::capacity).reversed()
                            .thenComparingInt(TableCandidate::tableNumber))
                    .limit(MAX_COMBINATION_POOL)
                    .toList();

            List<List<TableCandidate>> combos = new ArrayList<>();
            enumerate(pool, 0, maxSize, q.guestCount(), new ArrayDeque<>(), 0, combos);
            for (List<TableCandidate> combo : combos) {
                scored.add(scoreCombination(q, combo, gaps));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredCombo::score).reversed()
                .thenComparingInt((ScoredCombo c) -> c.tables().size())
                .thenComparingInt(ScoredCombo::total)
                .thenComparing(ScoredCombo::sortedTableNumbers, (int[] x, int[] y) -> Arrays.compare(x, y)));

        return scored.stream().limit(limit).map(c -> buildCombination(q, c)).toList();
    }

    static void enumerate(List<TableCandidate> pool, int from, int maxSize, int guests,
                          Deque<TableCandidate> current, int sum, List<List<TableCandidate>> out) {
        if (sum >= guests) {
            if (current.size() >= 2 && isMinimal(current, sum, guests)) {
                out.add(List.copyOf(current));
            }
            return;
        }
        if (current.size() == maxSize) {
            return;
        }
        for (int i = from; i < pool.size(); i++) {
            TableCandidate t = pool.get(i);
            current.addLast(t);
            enumerate(pool, i + 1, maxSize, guests, current, sum + t.capacity(), out);
            current.removeLast();
        }
    }

    private static boolean isMinimal(Collection<TableCandidate> combo, int sum, int guests) {
        int smallest = combo.stream().mapToInt(TableCandidate::capacity).min().orElse(0);
        return sum - smallest < guests;
    }

    private static ScoredCombo scoreCombination(Query q, List<TableCandidate> combo, Map<Long, GapInfo> gaps) {
        int total = 0;
        double gapSum = 0;
        int[] numbers = new int[combo.size()];
        for (int i = 0; i < combo.size(); i++) {
            TableCandidate t = combo.get(i);
            total += t.capacity();
            gapSum += gaps.get(t.id()).score();
            numbers[i] = t.tableNumber();
        }
        Arrays.sort(numbers);
        int n = combo.size();

        double fit = ((double) q.guestCount() / total) * (1 - COMBINATION_PENALTY_PER_EXTRA_TABLE * (n - 1));
        double gap = gapSum / n;
        Double area = areaScore(q.preferredAreaId(), combo);
        return new ScoredCombo(combo, total, fit, gap, area, combine(fit, gap, area), numbers);
    }

    private static Option buildCombination(Query q, ScoredCombo c) {
        List<TableCandidate> combo = c.tables();
        int n = combo.size();

        List<String> reasons = new ArrayList<>();
        String caps = combo.stream().map(t -> String.valueOf(t.capacity())).collect(Collectors.joining(" + "));
        reasons.add("Combination of " + n + " tables in the same area (" + caps + " = " + c.total() + " seats)");
        reasons.add("No single table can seat " + q.guestCount() + " guests at this time");
        addAreaReason(reasons, q.preferredAreaId(), c.area());

        return new Option(List.copyOf(combo), c.total(), c.score(),
                round2(c.fit()), round2(c.gap()), c.area(), reasons);
    }

    // scoring helpers

    private static GapInfo computeGap(List<Booking> bookings, Query q, Duration buffer) {
        Instant blockedFrom = q.start().minus(buffer);
        Instant blockedTo = q.end().plus(buffer);

        Instant prevEnd = null;
        Instant nextStart = null;
        for (Booking b : bookings) {
            if (!b.end().isAfter(blockedFrom)) {                       // ends before our slot
                if (prevEnd == null || b.end().isAfter(prevEnd)) prevEnd = b.end();
            } else if (!b.start().isBefore(blockedTo)) {                // starts after our slot
                if (nextStart == null || b.start().isBefore(nextStart)) nextStart = b.start();
            }
        }

        Long idleBefore = prevEnd == null ? null
                : Math.max(0, Duration.between(prevEnd, q.start()).toMinutes() - q.bufferMinutes());
        Long idleAfter = nextStart == null ? null
                : Math.max(0, Duration.between(q.end(), nextStart).toMinutes() - q.bufferMinutes());

        double score = (sideScore(idleBefore) + sideScore(idleAfter)) / 2.0;
        return new GapInfo(idleBefore, idleAfter, score);
    }

    private static double sideScore(Long idleMinutes) {
        if (idleMinutes == null) return GAP_OPEN;
        if (idleMinutes <= TIGHT_GAP_MINUTES) return GAP_TIGHT;
        if (idleMinutes >= USABLE_GAP_MINUTES) return GAP_OPEN;
        return GAP_DEAD;
    }

    private static Double areaScore(Long preferredAreaId, List<TableCandidate> tables) {
        if (preferredAreaId == null) return null;
        long matching = tables.stream().filter(t -> Objects.equals(t.areaId(), preferredAreaId)).count();
        return (double) matching / tables.size();
    }

    private static double combine(double fit, double gap, Double area) {
        double s = area == null
                ? W_FIT * fit + W_GAP * gap
                : W_FIT_WITH_AREA * fit + W_GAP_WITH_AREA * gap + W_AREA * area;
        return round1(s * 100);
    }

    private static void addAreaReason(List<String> reasons, Long preferredAreaId, Double area) {
        if (preferredAreaId == null || area == null) return;
        reasons.add(area >= 1.0 ? "Located in your preferred area" : "Not in your preferred area");
    }

    private static void addGapReason(List<String> reasons, GapInfo gap) {
        boolean tight = isTight(gap.idleBeforeMinutes()) || isTight(gap.idleAfterMinutes());
        boolean dead = isDead(gap.idleBeforeMinutes()) || isDead(gap.idleAfterMinutes());
        if (dead) {
            reasons.add("Leaves a short idle gap (under " + USABLE_GAP_MINUTES + " min) next to another booking");
        } else if (tight) {
            reasons.add("Fits snugly next to an existing booking, keeping the table schedule compact");
        } else {
            reasons.add("Table is free around the requested time");
        }
    }

    private static boolean isTight(Long idle) {
        return idle != null && idle <= TIGHT_GAP_MINUTES;
    }

    private static boolean isDead(Long idle) {
        return idle != null && idle > TIGHT_GAP_MINUTES && idle < USABLE_GAP_MINUTES;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
