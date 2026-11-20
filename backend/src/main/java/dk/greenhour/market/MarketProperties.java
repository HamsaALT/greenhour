package dk.greenhour.market;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "greenhour.market")
public record MarketProperties(String baseUrl, boolean importEnabled, String importCron, int backfillDays) {
}
