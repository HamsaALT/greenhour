package dk.greenhour.admin;

import java.time.Clock;
import java.time.LocalDate;

import dk.greenhour.market.MarketCalendar;
import dk.greenhour.market.MarketDataImporter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Access is restricted to ROLE_ADMIN in SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MarketDataImporter importer;
    private final Clock clock;

    public AdminController(MarketDataImporter importer, Clock clock) {
        this.importer = importer;
        this.clock = clock;
    }

    @PostMapping("/market-import")
    public MarketDataImporter.ImportResult runImport(@RequestParam(defaultValue = "1") @Min(0) @Max(31) int daysBack) {
        LocalDate today = LocalDate.now(clock.withZone(MarketCalendar.ZONE));
        return importer.importRange(
            MarketCalendar.startOfDay(today.minusDays(daysBack)),
            MarketCalendar.startOfDay(today.plusDays(2)));
    }

    @GetMapping("/market-import")
    public ResponseEntity<MarketDataImporter.ImportStatus> lastImport() {
        return ResponseEntity.of(importer.lastStatus());
    }
}
