package dk.greenhour.market;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market")
public class MarketController {

    private final MarketIntervalRepository repository;
    private final Clock clock;

    public MarketController(MarketIntervalRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** All slots for one Danish calendar day. Defaults to today. */
    @GetMapping
    public MarketDay day(@RequestParam PriceArea area,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(MarketCalendar.ZONE));
        List<Slot> slots = repository
            .findRange(area, MarketCalendar.startOfDay(day), MarketCalendar.endOfDay(day))
            .stream()
            .map(Slot::from)
            .toList();
        Instant availableUntil = repository.findLatestStart(area).map(s -> s.plusSeconds(15 * 60)).orElse(null);
        return new MarketDay(area, day, availableUntil, slots);
    }

    public record MarketDay(PriceArea area, LocalDate date, Instant dataAvailableUntil, List<Slot> slots) {
    }

    public record Slot(Instant start, BigDecimal priceDkkPerKwh, BigDecimal co2GramsPerKwh,
                       boolean co2Forecast, boolean demoData) {
        static Slot from(MarketInterval m) {
            return new Slot(m.getIntervalStart(), m.getPriceDkkPerKwh(), m.getCo2GramsPerKwh(),
                m.isCo2Forecast(), m.isDemoData());
        }
    }
}
