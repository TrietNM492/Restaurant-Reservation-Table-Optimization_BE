package com.restaurant.ireservation.service.recommendation;

import com.restaurant.ireservation.service.recommendation.AlternativeRecommendationEngine.Alternative;
import com.restaurant.ireservation.service.recommendation.AlternativeRecommendationEngine.Kind;
import com.restaurant.ireservation.service.recommendation.AlternativeRecommendationEngine.Params;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Booking;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.Query;
import com.restaurant.ireservation.service.recommendation.TableRecommendationEngine.TableCandidate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AlternativeRecommendationEngineTest {

    static final Instant T18 = Instant.parse("2026-10-20T18:00:00Z");
    static final long AREA = 1L;

    static TableCandidate table(long id, int capacity) {
        return new TableCandidate(id, (int) id, capacity, AREA);
    }

    static Instant at(int minutesFrom18) {
        return T18.plus(Duration.ofMinutes(minutesFrom18));
    }

    static Booking booking(long tableId, int fromMin, int toMin) {
        return new Booking(tableId, at(fromMin), at(toMin));
    }

    /** Request: 18:00 - 19:30 (90 min). */
    static Query query(int guests, boolean combos) {
        return new Query(guests, T18, at(90), null, 5, combos, 15, 3);
    }

    static Params params(int maxShift, int maxAlternatives) {
        return new Params(15, maxShift, maxAlternatives, 60, 5, null, null);
    }

    static Function<Instant, List<TableCandidate>> always(List<TableCandidate> tables) {
        return start -> tables;
    }

    static List<Alternative> ofKind(List<Alternative> list, Kind kind) {
        return list.stream().filter(a -> a.kind() == kind).toList();
    }

    @Test
    @DisplayName("Finds the nearest earlier and later start when the table is booked at the requested time")
    void findsEarlierAndLaterStart() {
        var tables = List.of(table(1, 4));
        var bookings = List.of(booking(1, 0, 90));            // table busy 18:00 - 19:30

        var result = AlternativeRecommendationEngine.find(params(105, 10), query(4, true), always(tables), bookings);

        // later: needs start >= 19:30 + 15 buffer = +105 ; earlier: needs end + 15 <= 18:00 -> -105
        var later = ofKind(result, Kind.LATER_START);
        var earlier = ofKind(result, Kind.EARLIER_START);
        assertEquals(1, later.size());
        assertEquals(105, later.get(0).shiftMinutes());
        assertEquals(at(105), later.get(0).start());
        assertEquals(at(195), later.get(0).end());
        assertEquals(1, earlier.size());
        assertEquals(-105, earlier.get(0).shiftMinutes());
        assertNotNull(later.get(0).option());
        assertTrue(later.get(0).explanation().contains("105 minutes later"));
    }

    @Test
    @DisplayName("Closest shift ranks first")
    void closestShiftRanksFirst() {
        var tables = List.of(table(1, 4));
        var bookings = List.of(booking(1, -60, 15));          // blocked until 18:15 (+15 buffer -> free from 18:30)

        var result = AlternativeRecommendationEngine.find(params(120, 3), query(4, true), always(tables), bookings);

        assertEquals(3, result.size());
        assertEquals(30, result.get(0).shiftMinutes());
        assertEquals(Kind.LATER_START, result.get(0).kind());
        assertTrue(result.get(0).score() >= result.get(1).score());
        assertTrue(result.get(1).score() >= result.get(2).score());
    }

    @Test
    @DisplayName("Offers a shorter stay when a booking follows right after the requested slot")
    void offersShorterStay() {
        var tables = List.of(table(1, 4));
        var bookings = List.of(booking(1, 75, 165));          // next booking starts 19:15

        var result = AlternativeRecommendationEngine.find(params(15, 10), query(4, true), always(tables), bookings);

        // 19:30 end conflicts; 19:15 end is inside buffer; 19:00 end (60 min) is the first that fits
        var shorter = ofKind(result, Kind.SHORTER_DURATION);
        assertEquals(1, shorter.size());
        assertEquals(60, shorter.get(0).durationMinutes());
        assertEquals(-30, shorter.get(0).durationChangeMinutes());
        assertEquals(T18, shorter.get(0).start());
        assertEquals(at(60), shorter.get(0).end());
    }

    @Test
    @DisplayName("Never shortens the stay below the minimum duration")
    void respectsMinimumDuration() {
        var tables = List.of(table(1, 4));
        var bookings = List.of(booking(1, 45, 135));          // would need a 30 minute stay

        var result = AlternativeRecommendationEngine.find(params(15, 10), query(4, true), always(tables), bookings);

        assertTrue(ofKind(result, Kind.SHORTER_DURATION).isEmpty());
    }

    @Test
    @DisplayName("Suggests merging tables when combination was disabled and no single table is big enough")
    void suggestsCombinationWhenDisabled() {
        var tables = List.of(table(1, 4), table(2, 4));

        var result = AlternativeRecommendationEngine.find(params(60, 10), query(8, false), always(tables), List.of());

        assertEquals(1, result.size());
        assertEquals(Kind.TABLE_COMBINATION, result.get(0).kind());
        assertEquals(T18, result.get(0).start());
        assertEquals(2, result.get(0).option().tables().size());
        assertTrue(result.get(0).explanation().contains("2 tables"));
    }

    @Test
    @DisplayName("No combination alternative when combination was already allowed")
    void noCombinationAlternativeWhenAlreadyAllowed() {
        var tables = List.of(table(1, 4), table(2, 4));
        var bookings = List.of(booking(1, 0, 90), booking(2, 0, 90));

        var result = AlternativeRecommendationEngine.find(params(120, 10), query(8, true), always(tables), bookings);

        assertTrue(ofKind(result, Kind.TABLE_COMBINATION).isEmpty());
        assertFalse(ofKind(result, Kind.LATER_START).isEmpty());   // merged tables free up later
    }

    @Test
    @DisplayName("earliestStart forbids alternatives that start before it")
    void honoursEarliestStart() {
        var tables = List.of(table(1, 4));
        var bookings = List.of(booking(1, 0, 90));
        var p = new Params(15, 120, 10, 60, 5, T18, null);

        var result = AlternativeRecommendationEngine.find(p, query(4, true), always(tables), bookings);

        assertTrue(ofKind(result, Kind.EARLIER_START).isEmpty());
        assertFalse(ofKind(result, Kind.LATER_START).isEmpty());
    }

    @Test
    @DisplayName("latestEnd forbids alternatives that end after it")
    void honoursLatestEnd() {
        var tables = List.of(table(1, 4));
        var bookings = List.of(booking(1, 0, 90));
        var p = new Params(15, 120, 10, 60, 5, null, at(90));

        var result = AlternativeRecommendationEngine.find(p, query(4, true), always(tables), bookings);

        assertTrue(ofKind(result, Kind.LATER_START).isEmpty());
        assertFalse(ofKind(result, Kind.EARLIER_START).isEmpty());
    }

    @Test
    @DisplayName("Table eligibility is evaluated per alternative start time")
    void candidatesDependOnStartTime() {
        // table 2 is "IN_USE" (not a candidate) until 19:00, then usable again
        Function<Instant, List<TableCandidate>> candidates =
                start -> start.isBefore(at(60)) ? List.of(table(1, 4)) : List.of(table(1, 4), table(2, 4));
        var bookings = List.of(booking(1, -120, 300));          // table 1 busy the whole evening

        var result = AlternativeRecommendationEngine.find(params(120, 10), query(4, true), candidates, bookings);

        var later = ofKind(result, Kind.LATER_START);
        assertFalse(later.isEmpty());
        assertEquals(60, later.stream().mapToInt(Alternative::shiftMinutes).min().orElse(-1));
        assertEquals(2L, later.get(0).option().tables().get(0).id());
    }

    @Test
    @DisplayName("maxAlternatives limits the output")
    void respectsMaxAlternatives() {
        var tables = List.of(table(1, 4));

        var result = AlternativeRecommendationEngine.find(params(120, 2), query(4, true), always(tables), List.of());

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Empty result when nothing can seat the party at any probed time")
    void emptyWhenNothingFits() {
        var tables = List.of(table(1, 4), table(2, 4));

        var result = AlternativeRecommendationEngine.find(params(120, 10), query(20, true), always(tables), List.of());

        assertTrue(result.isEmpty());
    }

    // MORE_TABLES

    static List<TableCandidate> tables4(int count, long area) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new TableCandidate(i, i, 4, area)).toList();
    }

    @Test
    @DisplayName("MORE_TABLES: 14 guests need 4 tables, more than the usual limit of 3")
    void moreTablesForBigParty() {
        var result = AlternativeRecommendationEngine.find(params(60, 10), query(14, true),
                always(tables4(4, AREA)), List.of());

        var more = ofKind(result, Kind.MORE_TABLES);
        assertEquals(1, more.size());
        assertEquals(4, more.get(0).option().tables().size());
        assertEquals(T18, more.get(0).start());                 // same time as requested
        assertEquals(0, more.get(0).shiftMinutes());
        assertEquals(70.0, more.get(0).convenienceScore());
        assertTrue(more.get(0).explanation().contains("4 tables"));
        assertTrue(ofKind(result, Kind.TABLE_COMBINATION).isEmpty());
    }

    @Test
    @DisplayName("MORE_TABLES: also offered when combination was disabled and 3 tables are still not enough")
    void moreTablesWhenCombinationDisabled() {
        var result = AlternativeRecommendationEngine.find(params(60, 10), query(14, false),
                always(tables4(4, AREA)), List.of());

        assertEquals(1, ofKind(result, Kind.MORE_TABLES).size());
        assertTrue(ofKind(result, Kind.TABLE_COMBINATION).isEmpty());
    }

    @Test
    @DisplayName("MORE_TABLES: not offered when a combination within the usual limit exists")
    void noMoreTablesWhenUsualLimitIsEnough() {
        var result = AlternativeRecommendationEngine.find(params(60, 10), query(8, false),
                always(tables4(2, AREA)), List.of());

        assertEquals(1, result.size());
        assertEquals(Kind.TABLE_COMBINATION, result.get(0).kind());
    }

    @Test
    @DisplayName("MORE_TABLES: not offered when a single table is available")
    void noMoreTablesWhenSingleTableAvailable() {
        var tables = List.of(table(1, 8), table(2, 4), table(3, 4), table(4, 4), table(5, 4));

        var result = AlternativeRecommendationEngine.find(params(60, 10), query(6, true), always(tables), List.of());

        assertTrue(ofKind(result, Kind.MORE_TABLES).isEmpty());
    }

    @Test
    @DisplayName("MORE_TABLES: 5 tables are honoured up to the extended limit and cost more convenience")
    void moreTablesRespectsExtendedLimit() {
        var five = tables4(5, AREA);                              // 18 guests need all 5 tables (20 seats)

        var allowed = AlternativeRecommendationEngine.find(
                new Params(15, 60, 10, 60, 5, null, null), query(18, true), always(five), List.of());
        var blocked = AlternativeRecommendationEngine.find(
                new Params(15, 60, 10, 60, 4, null, null), query(18, true), always(five), List.of());

        var more = ofKind(allowed, Kind.MORE_TABLES);
        assertEquals(1, more.size());
        assertEquals(5, more.get(0).option().tables().size());
        assertEquals(60.0, more.get(0).convenienceScore());       // 80 - 10 * (5 - 3)
        assertTrue(ofKind(blocked, Kind.MORE_TABLES).isEmpty());
    }

    @Test
    @DisplayName("MORE_TABLES: disabled when the extended limit is not above the usual limit")
    void moreTablesDisabledWhenNotExtended() {
        var result = AlternativeRecommendationEngine.find(
                new Params(15, 60, 10, 60, 3, null, null), query(14, true), always(tables4(4, AREA)), List.of());

        assertTrue(ofKind(result, Kind.MORE_TABLES).isEmpty());
    }

    @Test
    @DisplayName("Tables are never merged across areas, not even by MORE_TABLES")
    void neverMergesAcrossAreas() {
        // 2 tables in area 1 + 2 tables in area 2: 16 seats in total, but only 8 within any single area
        var tables = List.of(
                new TableCandidate(1, 1, 4, 1L), new TableCandidate(2, 2, 4, 1L),
                new TableCandidate(3, 3, 4, 2L), new TableCandidate(4, 4, 4, 2L));

        var result = AlternativeRecommendationEngine.find(params(60, 10), query(14, true), always(tables), List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("MORE_TABLES: party larger than every table of the area combined gets nothing")
    void moreTablesNotEnoughSeats() {
        var result = AlternativeRecommendationEngine.find(params(60, 10), query(30, true),
                always(tables4(4, AREA)), List.of());

        assertTrue(result.isEmpty());
    }
}
