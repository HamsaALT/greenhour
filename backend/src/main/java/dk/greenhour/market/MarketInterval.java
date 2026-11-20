package dk.greenhour.market;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One 15-minute slot of the day-ahead market. Written by {@link MarketDataImporter}, read-only elsewhere. */
@Entity
@Table(name = "market_interval")
@IdClass(MarketInterval.Key.class)
public class MarketInterval {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "price_area")
    private PriceArea priceArea;

    @Id
    @Column(name = "interval_start")
    private Instant intervalStart;

    @Column(name = "price_dkk_per_kwh", nullable = false)
    private BigDecimal priceDkkPerKwh;

    @Column(name = "co2_g_per_kwh")
    private BigDecimal co2GramsPerKwh;

    @Column(name = "co2_is_forecast", nullable = false)
    private boolean co2Forecast;

    @Column(name = "source", nullable = false)
    private String source;

    protected MarketInterval() {
    }

    public MarketInterval(PriceArea priceArea, Instant intervalStart, BigDecimal priceDkkPerKwh,
                          BigDecimal co2GramsPerKwh, boolean co2Forecast) {
        this.priceArea = priceArea;
        this.intervalStart = intervalStart;
        this.priceDkkPerKwh = priceDkkPerKwh;
        this.co2GramsPerKwh = co2GramsPerKwh;
        this.co2Forecast = co2Forecast;
        this.source = "ENERGINET";
    }

    public boolean isDemoData() {
        return "DEMO".equals(source);
    }

    public PriceArea getPriceArea() {
        return priceArea;
    }

    public Instant getIntervalStart() {
        return intervalStart;
    }

    public BigDecimal getPriceDkkPerKwh() {
        return priceDkkPerKwh;
    }

    public BigDecimal getCo2GramsPerKwh() {
        return co2GramsPerKwh;
    }

    public boolean isCo2Forecast() {
        return co2Forecast;
    }

    public record Key(PriceArea priceArea, Instant intervalStart) implements Serializable {
        public Key() {
            this(null, null);
        }
    }
}
