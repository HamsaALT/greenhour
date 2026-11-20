package dk.greenhour.market;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * "Today" always means today in Denmark, regardless of where the server runs.
 * On DST change days a Danish day is 23 or 25 hours long, which is why day
 * boundaries are computed through the zone instead of adding 24 hours.
 */
public final class MarketCalendar {

    public static final ZoneId ZONE = ZoneId.of("Europe/Copenhagen");

    private MarketCalendar() {
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    public static Instant endOfDay(LocalDate date) {
        return startOfDay(date.plusDays(1));
    }
}
