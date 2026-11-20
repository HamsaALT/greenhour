package dk.greenhour.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class MarketDataImporter {

    private static final Logger log = LoggerFactory.getLogger(MarketDataImporter.class);
    private static final long SLOT_SECONDS = 15 * 60;
    private static final BigDecimal KWH_PER_MWH = BigDecimal.valueOf(1000);

    // Measured CO2 always wins over a forecast. A null in the incoming row means
    // "no CO2 figure yet", which must not wipe out one we already have.
    private static final String UPSERT = """
        insert into market_interval (price_area, interval_start, price_dkk_per_kwh, co2_g_per_kwh, co2_is_forecast, source, updated_at)
        values (?, ?, ?, ?, ?, 'ENERGINET', now())
        on conflict (price_area, interval_start) do update set
            price_dkk_per_kwh = excluded.price_dkk_per_kwh,
            co2_g_per_kwh = case when market_interval.source = 'DEMO'
                                 then excluded.co2_g_per_kwh
                                 else coalesce(excluded.co2_g_per_kwh, market_interval.co2_g_per_kwh) end,
            co2_is_forecast = case when excluded.co2_g_per_kwh is null and market_interval.source <> 'DEMO'
                                   then market_interval.co2_is_forecast
                                   else excluded.co2_is_forecast end,
            source = 'ENERGINET',
            updated_at = now()
        """;

    private final EnergiDataServiceClient client;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final AtomicReference<ImportStatus> lastStatus = new AtomicReference<>();

    public MarketDataImporter(EnergiDataServiceClient client, JdbcTemplate jdbc,
                              PlatformTransactionManager transactionManager, Clock clock) {
        this.client = client;
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Fetches first, then writes everything in one short transaction. The HTTP calls
     * can take a while (retries on rate limiting), and shouldn't hold a DB connection.
     */
    public ImportResult importRange(Instant from, Instant to) {
        try {
            List<Object[]> rows = buildRows(
                client.fetchPrices(from, to),
                averagePerSlot(client.fetchCo2(from, to)),
                averagePerSlot(client.fetchCo2Forecast(from, to)));
            transaction.executeWithoutResult(status -> jdbc.batchUpdate(UPSERT, rows));

            int withCo2 = (int) rows.stream().filter(r -> r[3] != null).count();
            var result = new ImportResult(from, to, rows.size(), withCo2);
            lastStatus.set(new ImportStatus(clock.instant(), result, null));
            log.info("Imported {} market slots ({} with CO2) for {} to {}", rows.size(), withCo2, from, to);
            return result;
        } catch (MarketDataUnavailableException e) {
            lastStatus.set(new ImportStatus(clock.instant(), null, e.getMessage()));
            throw e;
        }
    }

    public Optional<ImportStatus> lastStatus() {
        return Optional.ofNullable(lastStatus.get());
    }

    private static List<Object[]> buildRows(List<EnergiDataServiceClient.PriceRecord> prices,
                                            Map<SlotKey, BigDecimal> measured,
                                            Map<SlotKey, BigDecimal> forecast) {
        List<Object[]> rows = new ArrayList<>();
        for (var price : prices) {
            if (price.priceDkkPerMwh() == null || !isKnownArea(price.priceArea())) {
                continue;
            }
            var key = new SlotKey(price.priceArea(), price.start());
            BigDecimal co2 = measured.get(key);
            boolean isForecast = false;
            if (co2 == null) {
                co2 = forecast.get(key);
                isForecast = co2 != null;
            }
            rows.add(new Object[] {
                price.priceArea(),
                Timestamp.from(price.start()),
                price.priceDkkPerMwh().divide(KWH_PER_MWH, 5, RoundingMode.HALF_UP),
                co2,
                isForecast
            });
        }
        return rows;
    }

    private static boolean isKnownArea(String area) {
        return "DK1".equals(area) || "DK2".equals(area);
    }

    /** CO2 comes in 5-minute steps, prices in 15-minute steps. Average the CO2 into price slots. */
    static Map<SlotKey, BigDecimal> averagePerSlot(Collection<EnergiDataServiceClient.Co2Record> records) {
        Map<SlotKey, List<BigDecimal>> bySlot = records.stream()
            .filter(r -> r.gramsPerKwh() != null)
            .collect(Collectors.groupingBy(
                r -> new SlotKey(r.priceArea(),
                    Instant.ofEpochSecond(Math.floorDiv(r.start().getEpochSecond(), SLOT_SECONDS) * SLOT_SECONDS)),
                Collectors.mapping(EnergiDataServiceClient.Co2Record::gramsPerKwh, Collectors.toList())));

        Map<SlotKey, BigDecimal> averages = new HashMap<>();
        bySlot.forEach((slot, values) -> averages.put(slot,
            values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP)));
        return averages;
    }

    record SlotKey(String area, Instant start) {
    }

    public record ImportResult(Instant from, Instant to, int slotsStored, int slotsWithCo2) {
    }

    public record ImportStatus(Instant ranAt, ImportResult result, String error) {
    }
}
