package dk.greenhour.market;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps market data fresh. Energinet publishes tomorrow's prices around 13:00 Danish
 * time, so an hourly refresh picks them up shortly after without hammering the API.
 */
@Component
public class MarketImportJob {

    private static final Logger log = LoggerFactory.getLogger(MarketImportJob.class);

    private final MarketDataImporter importer;
    private final MarketProperties properties;
    private final Clock clock;

    public MarketImportJob(MarketDataImporter importer, MarketProperties properties, Clock clock) {
        this.importer = importer;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        runSafely(properties.backfillDays());
    }

    @Scheduled(cron = "${greenhour.market.import-cron}", zone = "Europe/Copenhagen")
    public void refresh() {
        runSafely(1);
    }

    private void runSafely(int daysBack) {
        if (!properties.importEnabled()) {
            return;
        }
        LocalDate today = LocalDate.now(clock.withZone(MarketCalendar.ZONE));
        Instant from = MarketCalendar.startOfDay(today.minusDays(daysBack));
        Instant to = MarketCalendar.startOfDay(today.plusDays(2));
        try {
            importer.importRange(from, to);
        } catch (MarketDataUnavailableException e) {
            // The app keeps working on whatever data it already has.
            log.warn("Market import failed: {}", e.getMessage());
        }
    }
}
