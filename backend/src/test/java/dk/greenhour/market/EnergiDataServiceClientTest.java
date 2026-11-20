package dk.greenhour.market;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class EnergiDataServiceClientTest {

    private static final Instant FROM = Instant.parse("2026-11-18T23:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-19T00:00:00Z");

    private MockRestServiceServer server;
    private EnergiDataServiceClient client;
    private final List<Duration> pauses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new EnergiDataServiceClient(builder,
            new MarketProperties("https://api.example.test", true, "0 0 * * * *", 7)) {
            @Override
            protected void pause(Duration duration) {
                pauses.add(duration);
            }
        };
    }

    @Test
    void requestsPricesForBothAreasInUtc() {
        server.expect(requestTo(startsWith("https://api.example.test/dataset/DayAheadPrices")))
            .andExpect(queryParam("start", "2026-11-18T23%3A00"))
            .andExpect(queryParam("end", "2026-11-19T00%3A00"))
            .andExpect(queryParam("timezone", "UTC"))
            .andExpect(queryParam("filter", "%7B%22PriceArea%22%3A%5B%22DK1%22%2C%22DK2%22%5D%7D"))
            .andRespond(withSuccess("""
                {"total":2,"records":[
                  {"TimeUTC":"2026-11-18T23:00:00","PriceArea":"DK2","DayAheadPriceDKK":1248.981011},
                  {"TimeUTC":"2026-11-18T23:00:00","PriceArea":"DK1","DayAheadPriceDKK":1256.381207}
                ]}""", MediaType.APPLICATION_JSON));

        var prices = client.fetchPrices(FROM, TO);

        assertThat(prices).hasSize(2);
        assertThat(prices.getFirst().start()).isEqualTo(FROM);
        assertThat(prices.getFirst().priceArea()).isEqualTo("DK2");
        assertThat(prices.getFirst().priceDkkPerMwh()).isEqualByComparingTo("1248.981011");
        server.verify();
    }

    @Test
    void readsCo2Records() {
        server.expect(requestTo(startsWith("https://api.example.test/dataset/CO2EmisProg")))
            .andRespond(withSuccess("""
                {"records":[{"Minutes5UTC":"2026-11-18T23:05:00","PriceArea":"DK1","CO2Emission":203.0}]}""",
                MediaType.APPLICATION_JSON));

        var co2 = client.fetchCo2Forecast(FROM, TO);

        assertThat(co2).singleElement().satisfies(r -> {
            assertThat(r.start()).isEqualTo(Instant.parse("2026-11-18T23:05:00Z"));
            assertThat(r.gramsPerKwh()).isEqualByComparingTo("203");
        });
    }

    @Test
    void treatsEmptyBodyAsNoData() {
        server.expect(requestTo(startsWith("https://api.example.test/dataset/CO2Emis")))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCo2(FROM, TO)).isEmpty();
    }

    @Test
    void backsOffAndRetriesWhenRateLimited() {
        server.expect(requestTo(startsWith("https://api.example.test/dataset/CO2Emis")))
            .andRespond(withTooManyRequests(24));
        server.expect(requestTo(startsWith("https://api.example.test/dataset/CO2Emis")))
            .andRespond(withSuccess("""
                {"records":[{"Minutes5UTC":"2026-11-18T23:00:00","PriceArea":"DK1","CO2Emission":90}]}""",
                MediaType.APPLICATION_JSON));

        assertThat(client.fetchCo2(FROM, TO)).hasSize(1);
        assertThat(pauses).containsExactly(Duration.ofSeconds(24));
    }

    @Test
    void givesUpAfterThreeRateLimitedAttempts() {
        server.expect(times(3), requestTo(startsWith("https://api.example.test/dataset/DayAheadPrices")))
            .andRespond(withTooManyRequests());

        assertThatThrownBy(() -> client.fetchPrices(FROM, TO))
            .isInstanceOf(MarketDataUnavailableException.class)
            .hasMessageContaining("rate limiting");
        assertThat(pauses).hasSize(2);
    }

    @Test
    void wrapsUpstreamFailures() {
        server.expect(requestTo(startsWith("https://api.example.test/dataset/DayAheadPrices")))
            .andRespond(withServerError());

        assertThatThrownBy(() -> client.fetchPrices(FROM, TO))
            .isInstanceOf(MarketDataUnavailableException.class)
            .hasMessageContaining("DayAheadPrices");
    }
}
