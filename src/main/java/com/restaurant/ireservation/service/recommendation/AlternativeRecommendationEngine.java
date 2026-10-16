package com.restaurant.ireservation.service.recommendation;

import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Booking;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Option;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Query;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Result;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public final class AlternativeRecommendationEngine {

    public static final int STEP_MINUTES = 15;

    static final double W_CONVENIENCE = 0.6;
    static final double W_TABLE = 0.4;
    /** A shift of this many minutes costs 50 convenience points; the cost grows linearly. */
    static final double SHIFT_REFERENCE_MINUTES = 120.0;
    /** Each minute cut from the stay costs one convenience point. */
    static final double SHORTER_COST_PER_MINUTE = 1.0;
    static final double COMBINATION_CONVENIENCE = 80.0;
    /** Each table beyond the usual limit costs this many convenience points (more tables to merge = more hassle). */
    static final double EXTRA_TABLE_CONVENIENCE_COST = 10.0;
    static final double MIN_CONVENIENCE = 20.0;

    private AlternativeRecommendationEngine() {
    }

    public enum Kind {
        EARLIER_START, LATER_START, SHORTER_DURATION, TABLE_COMBINATION, MORE_TABLES
    }

    /**
     * @param extendedMaxTables highest number of tables a MORE_TABLES alternative may merge; the feature is off
     *                          when this is not greater than the request's own limit
     * @param earliestStart alternatives may not start before this instant (null = no limit), normally "now"
     * @param latestEnd     alternatives may not end after this instant (null = no limit), e.g. closing time
     */
    public record Params(int stepMinutes, int maxShiftMinutes, int maxAlternatives, int minDurationMinutes,
                         int extendedMaxTables, Instant earliestStart, Instant latestEnd) {
    }

    /**
     * @param shiftMinutes          signed start offset versus the request (negative = earlier)
     * @param durationChangeMinutes signed stay change versus the request (negative = shorter)
     */
    public record Alternative(Kind kind, Instant start, Instant end, int shiftMinutes, int durationMinutes,
                              int durationChangeMinutes, Option option, double convenienceScore,
                              double score, String explanation) {
    }

    // public API

    /**
     * @param candidatesFor tables that may be used for a slot starting at the given instant. It is a function
     *                      (not a list) because eligibility can depend on the start time, e.g. a table that is
     *                      IN_USE right now is usable for a slot starting in an hour.
     */
    public static List<Alternative> find(Params params, Query original,
                                         Function<Instant, ? extends Collection<TableCandidate>> candidatesFor,
                                         Collection<Booking> bookings) {
        int step = Math.max(1, params.stepMinutes());
        int originalDuration = (int) Duration.between(original.start(), original.end()).toMinutes();
        List<Alternative> found = new ArrayList<>();

        // 1) same stay, different start
        for (int shift = step; shift <= params.maxShiftMinutes(); shift += step) {
            for (int direction : new int[]{-1, 1}) {
                int signed = direction * shift;
                Instant start = original.start().plus(Duration.ofMinutes(signed));
                Instant end = original.end().plus(Duration.ofMinutes(signed));
                if (!withinBounds(params, start, end)) continue;

                Option option = probe(original, start, end, original.allowCombination(), false,
                        original.maxTablesPerCombination(), candidatesFor, bookings);
                if (option != null) {
                    found.add(timeShift(direction < 0 ? Kind.EARLIER_START : Kind.LATER_START,
                            signed, start, end, originalDuration, option));
                }
            }
        }

        // 2) same start, shorter stay
        for (int cut = step; originalDuration - cut >= params.minDurationMinutes(); cut += step) {
            Instant end = original.start().plus(Duration.ofMinutes(originalDuration - cut));
            if (!withinBounds(params, original.start(), end)) continue;

            Option option = probe(original, original.start(), end, original.allowCombination(), false,
                    original.maxTablesPerCombination(), candidatesFor, bookings);
            if (option != null) {
                found.add(shorterStay(cut, originalDuration, end, option));
            }
        }

        // 3) same time, merging tables (only meaningful when the caller had switched it off)
        if (!original.allowCombination()) {
            Option option = probe(original, original.start(), original.end(), true, true,
                    original.maxTablesPerCombination(), candidatesFor, bookings);
            if (option != null) {
                found.add(combination(originalDuration, original, option));
            }
        }

        // 4) same time, merging MORE tables than the usual limit - only when nothing fits within that limit
        //    (no single table and no combination of up to the usual number of tables)
        if (params.extendedMaxTables() > original.maxTablesPerCombination()) {
            Option withinUsualLimit = probe(original, original.start(), original.end(), true, false,
                    original.maxTablesPerCombination(), candidatesFor, bookings);
            if (withinUsualLimit == null) {
                Option more = probe(original, original.start(), original.end(), true, true,
                        params.extendedMaxTables(), candidatesFor, bookings);
                if (more != null && more.tables().size() > original.maxTablesPerCombination()) {
                    found.add(moreTables(originalDuration, original, more));
                }
            }
        }

        found.sort(Comparator.comparingDouble(Alternative::score).reversed()
                .thenComparingInt((Alternative a) -> Math.abs(a.shiftMinutes()))
                .thenComparing(Alternative::start)
                .thenComparing(Alternative::kind));
        return found.size() > params.maxAlternatives()
                ? List.copyOf(found.subList(0, params.maxAlternatives()))
                : List.copyOf(found);
    }

    // probing

    private static boolean withinBounds(Params p, Instant start, Instant end) {
        if (p.earliestStart() != null && start.isBefore(p.earliestStart())) return false;
        return p.latestEnd() == null || !end.isAfter(p.latestEnd());
    }

    /** Runs the normal recommendation for a relaxed query and returns the best option, or null if none. */
    private static Option probe(Query original, Instant start, Instant end, boolean allowCombination,
                                boolean requireCombination, int maxTables,
                                Function<Instant, ? extends Collection<TableCandidate>> candidatesFor,
                                Collection<Booking> bookings) {
        Query relaxed = new Query(original.guestCount(), start, end, original.preferredAreaId(),
                requireCombination ? Math.max(original.maxResults(), 5) : 1,
                allowCombination, original.bufferMinutes(), maxTables);
        Result result = TableRecommendationEngine.recommend(relaxed, candidatesFor.apply(start), bookings);

        if (requireCombination) {
            return result.options().stream().filter(Option::isCombination).findFirst().orElse(null);
        }
        return result.options().isEmpty() ? null : result.options().get(0);
    }

    // building + scoring

    private static Alternative timeShift(Kind kind, int signedShift, Instant start, Instant end,
                                         int duration, Option option) {
        double convenience = clampConvenience(100 - 50 * Math.abs(signedShift) / SHIFT_REFERENCE_MINUTES);
        String text = "Starts " + Math.abs(signedShift) + " minutes "
                + (signedShift < 0 ? "earlier" : "later") + " than requested";
        return new Alternative(kind, start, end, signedShift, duration, 0, option,
                round1(convenience), score(convenience, option), text);
    }

    private static Alternative shorterStay(int cut, int originalDuration, Instant end, Option option) {
        double convenience = clampConvenience(100 - SHORTER_COST_PER_MINUTE * cut);
        int newDuration = originalDuration - cut;
        String text = "Same start time, but the stay is limited to " + newDuration + " minutes (instead of "
                + originalDuration + ") because the table is booked afterwards";
        Instant start = end.minus(Duration.ofMinutes(newDuration));
        return new Alternative(Kind.SHORTER_DURATION, start, end, 0, newDuration, -cut, option,
                round1(convenience), score(convenience, option), text);
    }

    private static Alternative combination(int duration, Query original, Option option) {
        String text = "Same time, using " + option.tables().size() + " tables merged together";
        return new Alternative(Kind.TABLE_COMBINATION, original.start(), original.end(), 0, duration, 0, option,
                round1(COMBINATION_CONVENIENCE), score(COMBINATION_CONVENIENCE, option), text);
    }

    private static Alternative moreTables(int duration, Query original, Option option) {
        int n = option.tables().size();
        int extra = n - original.maxTablesPerCombination();
        double convenience = clampConvenience(COMBINATION_CONVENIENCE - EXTRA_TABLE_CONVENIENCE_COST * extra);
        String text = "Same time, but the party needs " + n + " tables merged together (more than the usual "
                + original.maxTablesPerCombination() + "), all in the same area";
        return new Alternative(Kind.MORE_TABLES, original.start(), original.end(), 0, duration, 0, option,
                round1(convenience), score(convenience, option), text);
    }

    private static double score(double convenience, Option option) {
        return round1(W_CONVENIENCE * convenience + W_TABLE * option.score());
    }

    private static double clampConvenience(double v) {
        return Math.max(MIN_CONVENIENCE, v);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
