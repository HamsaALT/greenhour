package dk.greenhour.market;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin client for the public Energi Data Service API (api.energidataservice.dk).
 * No API key is needed. All times are requested and returned in UTC.
 */
@Component
public class EnergiDataServiceClient {

    private static final DateTimeFormatter QUERY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    // Both areas in one request: the API rate-limits per client, so fewer calls matter.
    private static final String BOTH_AREAS = "{\"PriceArea\":[\"DK1\",\"DK2\"]}";
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(10);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final RestClient restClient;

    public EnergiDataServiceClient(RestClient.Builder builder, MarketProperties properties) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
    }

    /** Day-ahead spot prices in DKK per MWh, one record per 15 minutes and area. */
    public List<PriceRecord> fetchPrices(Instant from, Instant to) {
        return fetch("DayAheadPrices", "TimeUTC,PriceArea,DayAheadPriceDKK", from, to,
            new ParameterizedTypeReference<>() {});
    }

    /** Measured CO2 intensity, one record per 5 minutes and area. Only covers the past. */
    public List<Co2Record> fetchCo2(Instant from, Instant to) {
        return fetch("CO2Emis", "Minutes5UTC,PriceArea,CO2Emission", from, to,
            new ParameterizedTypeReference<>() {});
    }

    /** Forecast CO2 intensity. Energinet usually publishes it only to the end of the current day. */
    public List<Co2Record> fetchCo2Forecast(Instant from, Instant to) {
        return fetch("CO2EmisProg", "Minutes5UTC,PriceArea,CO2Emission", from, to,
            new ParameterizedTypeReference<>() {});
    }

    private <T> List<T> fetch(String dataset, String columns, Instant from, Instant to,
                              ParameterizedTypeReference<Response<T>> type) {
        for (int attempt = 1; ; attempt++) {
            try {
                Response<T> response = restClient.get()
                    .uri(uri -> uri.path("/dataset/{dataset}")
                        .queryParam("start", "{start}")
                        .queryParam("end", "{end}")
                        .queryParam("timezone", "UTC")
                        .queryParam("columns", "{columns}")
                        .queryParam("filter", "{filter}")
                        .queryParam("limit", 0)
                        .build(Map.of(
                            "dataset", dataset,
                            "start", QUERY_TIME.format(from.atOffset(ZoneOffset.UTC)),
                            "end", QUERY_TIME.format(to.atOffset(ZoneOffset.UTC)),
                            "columns", columns,
                            "filter", BOTH_AREAS)))
                    .retrieve()
                    .body(type);
                return response == null || response.records() == null ? List.of() : response.records();
            } catch (HttpClientErrorException.TooManyRequests e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw new MarketDataUnavailableException(
                        "Energi Data Service is rate limiting us (" + dataset + "), try again in a minute", e);
                }
                pause(retryAfter(e));
            } catch (RestClientException e) {
                throw new MarketDataUnavailableException("Could not fetch " + dataset + " from Energi Data Service", e);
            }
        }
    }

    private static Duration retryAfter(HttpClientErrorException e) {
        String header = e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After");
        try {
            Duration wait = header == null ? DEFAULT_BACKOFF : Duration.ofSeconds(Long.parseLong(header.trim()));
            return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
        } catch (NumberFormatException ignored) {
            return DEFAULT_BACKOFF;
        }
    }

    /** Overridden in tests so they don't actually wait. */
    protected void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketDataUnavailableException("Interrupted while waiting to retry", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response<T>(List<T> records) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PriceRecord(
        @JsonProperty("TimeUTC") LocalDateTime timeUtc,
        @JsonProperty("PriceArea") String priceArea,
        @JsonProperty("DayAheadPriceDKK") BigDecimal priceDkkPerMwh) {

        public Instant start() {
            return timeUtc.toInstant(ZoneOffset.UTC);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Co2Record(
        @JsonProperty("Minutes5UTC") LocalDateTime timeUtc,
        @JsonProperty("PriceArea") String priceArea,
        @JsonProperty("CO2Emission") BigDecimal gramsPerKwh) {

        public Instant start() {
            return timeUtc.toInstant(ZoneOffset.UTC);
        }
    }
}
