package dk.greenhour.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import dk.greenhour.PostgresTestConfiguration;
import dk.greenhour.market.EnergiDataServiceClient.Co2Record;
import dk.greenhour.market.EnergiDataServiceClient.PriceRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Checks the upsert SQL against real PostgreSQL, using a stubbed API client. */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class MarketImportIT {

    // Far in the future so it can't clash with the demo data.
    private static final Instant SLOT = Instant.parse("2031-06-01T10:00:00Z");

    @Autowired
    MarketDataImporter importer;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    EnergiDataServiceClient client;

    @BeforeEach
    void noDataByDefault() {
        when(client.fetchPrices(any(), any())).thenReturn(List.of());
        when(client.fetchCo2(any(), any())).thenReturn(List.of());
        when(client.fetchCo2Forecast(any(), any())).thenReturn(List.of());
    }

    @Test
    void storesPricesInKwhAndPrefersMeasuredCo2() {
        when(client.fetchPrices(any(), any()))
            .thenReturn(List.of(price("DK1", SLOT, "1500"), price("DK1", SLOT.plusSeconds(900), "1200")));
        when(client.fetchCo2(any(), any())).thenReturn(List.of(co2("DK1", SLOT, "80")));
        when(client.fetchCo2Forecast(any(), any()))
            .thenReturn(List.of(co2("DK1", SLOT, "999"), co2("DK1", SLOT.plusSeconds(900), "150")));

        var result = importer.importRange(SLOT, SLOT.plusSeconds(1800));

        assertThat(result.slotsStored()).isEqualTo(2);
        Map<String, Object> first = row("DK1", SLOT);
        assertThat((BigDecimal) first.get("price_dkk_per_kwh")).isEqualByComparingTo("1.5");
        assertThat((BigDecimal) first.get("co2_g_per_kwh")).isEqualByComparingTo("80");
        assertThat(first.get("co2_is_forecast")).isEqualTo(false);
        assertThat(row("DK1", SLOT.plusSeconds(900)).get("co2_is_forecast")).isEqualTo(true);
        assertThat(importer.lastStatus()).hasValueSatisfying(s -> assertThat(s.error()).isNull());
    }

    @Test
    void matchesCo2ToTheRightArea() {
        Instant slot = SLOT.plusSeconds(3600);
        when(client.fetchPrices(any(), any()))
            .thenReturn(List.of(price("DK1", slot, "1000"), price("DK2", slot, "1100")));
        when(client.fetchCo2(any(), any()))
            .thenReturn(List.of(co2("DK1", slot, "50"), co2("DK2", slot, "250")));

        importer.importRange(slot, slot.plusSeconds(900));

        assertThat((BigDecimal) row("DK1", slot).get("co2_g_per_kwh")).isEqualByComparingTo("50");
        assertThat((BigDecimal) row("DK2", slot).get("co2_g_per_kwh")).isEqualByComparingTo("250");
    }

    @Test
    void laterImportWithoutCo2KeepsTheCo2WeAlreadyHave() {
        Instant slot = SLOT.plusSeconds(86_400);
        when(client.fetchPrices(any(), any())).thenReturn(List.of(price("DK2", slot, "900")));
        when(client.fetchCo2(any(), any())).thenReturn(List.of(co2("DK2", slot, "60")));
        importer.importRange(slot, slot.plusSeconds(900));

        when(client.fetchCo2(any(), any())).thenReturn(List.of());
        when(client.fetchPrices(any(), any())).thenReturn(List.of(price("DK2", slot, "950")));
        importer.importRange(slot, slot.plusSeconds(900));

        Map<String, Object> stored = row("DK2", slot);
        assertThat((BigDecimal) stored.get("price_dkk_per_kwh")).isEqualByComparingTo("0.95");
        assertThat((BigDecimal) stored.get("co2_g_per_kwh")).isEqualByComparingTo("60");
    }

    @Test
    void realDataReplacesDemoData() {
        Instant demoSlot = jdbc.queryForObject(
            "select min(interval_start) from market_interval where source = 'DEMO' and price_area = 'DK1'",
            Timestamp.class).toInstant();
        when(client.fetchPrices(any(), any())).thenReturn(List.of(price("DK1", demoSlot, "700")));

        importer.importRange(demoSlot, demoSlot.plusSeconds(900));

        Map<String, Object> row = row("DK1", demoSlot);
        assertThat(row.get("source")).isEqualTo("ENERGINET");
        // Demo CO2 must not survive next to a real price.
        assertThat(row.get("co2_g_per_kwh")).isNull();
    }

    @Test
    void recordsFailedImports() {
        when(client.fetchCo2(any(), any()))
            .thenThrow(new MarketDataUnavailableException("Could not fetch CO2Emis", null));

        assertThatThrownBy(() -> importer.importRange(SLOT, SLOT.plusSeconds(900)))
            .isInstanceOf(MarketDataUnavailableException.class);
        assertThat(importer.lastStatus()).hasValueSatisfying(s ->
            assertThat(s.error()).contains("CO2Emis"));
    }

    private Map<String, Object> row(String area, Instant slot) {
        return jdbc.queryForMap("select * from market_interval where price_area = ? and interval_start = ?",
            area, Timestamp.from(slot));
    }

    private static PriceRecord price(String area, Instant start, String dkkPerMwh) {
        return new PriceRecord(LocalDateTime.ofInstant(start, ZoneOffset.UTC), area, new BigDecimal(dkkPerMwh));
    }

    private static Co2Record co2(String area, Instant start, String grams) {
        return new Co2Record(LocalDateTime.ofInstant(start, ZoneOffset.UTC), area, new BigDecimal(grams));
    }
}
