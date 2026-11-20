package dk.greenhour.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import dk.greenhour.market.EnergiDataServiceClient.Co2Record;
import dk.greenhour.market.MarketDataImporter.SlotKey;
import org.junit.jupiter.api.Test;

class MarketDataImporterTest {

    @Test
    void averagesFiveMinuteCo2IntoQuarterHourSlots() {
        var records = List.of(
            co2("DK1", "2026-11-18T12:00:00", "100"),
            co2("DK1", "2026-11-18T12:05:00", "110"),
            co2("DK1", "2026-11-18T12:10:00", "120"),
            co2("DK1", "2026-11-18T12:15:00", "200"));

        var averages = MarketDataImporter.averagePerSlot(records);

        assertThat(averages).hasSize(2);
        assertThat(averages.get(key("DK1", "2026-11-18T12:00:00Z"))).isEqualByComparingTo("110");
        assertThat(averages.get(key("DK1", "2026-11-18T12:15:00Z"))).isEqualByComparingTo("200");
    }

    @Test
    void keepsAreasApart() {
        var averages = MarketDataImporter.averagePerSlot(List.of(
            co2("DK1", "2026-11-18T12:00:00", "100"),
            co2("DK2", "2026-11-18T12:00:00", "300")));

        assertThat(averages.get(key("DK1", "2026-11-18T12:00:00Z"))).isEqualByComparingTo("100");
        assertThat(averages.get(key("DK2", "2026-11-18T12:00:00Z"))).isEqualByComparingTo("300");
    }

    @Test
    void skipsRecordsWithoutAValue() {
        var averages = MarketDataImporter.averagePerSlot(List.of(
            co2("DK1", "2026-11-18T12:00:00", null),
            co2("DK1", "2026-11-18T12:05:00", "90")));

        assertThat(averages.get(key("DK1", "2026-11-18T12:00:00Z"))).isEqualByComparingTo("90");
    }

    private static SlotKey key(String area, String instant) {
        return new SlotKey(area, Instant.parse(instant));
    }

    private static Co2Record co2(String area, String utc, String value) {
        return new Co2Record(LocalDateTime.parse(utc), area, value == null ? null : new BigDecimal(value));
    }
}
