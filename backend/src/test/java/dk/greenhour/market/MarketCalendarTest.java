package dk.greenhour.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class MarketCalendarTest {

    @Test
    void normalDayStartsAtLocalMidnight() {
        assertThat(MarketCalendar.startOfDay(LocalDate.of(2026, 1, 15)))
            .isEqualTo(Instant.parse("2026-01-14T23:00:00Z"));
    }

    @Test
    void springForwardDayIsTwentyThreeHours() {
        LocalDate day = LocalDate.of(2026, 3, 29);

        assertThat(lengthOf(day)).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void fallBackDayIsTwentyFiveHours() {
        LocalDate day = LocalDate.of(2026, 10, 25);

        assertThat(lengthOf(day)).isEqualTo(Duration.ofHours(25));
    }

    private static Duration lengthOf(LocalDate day) {
        return Duration.between(MarketCalendar.startOfDay(day), MarketCalendar.endOfDay(day));
    }
}
