package dk.greenhour.planning;

import java.math.BigDecimal;

public record RunSummary(long completedRuns, BigDecimal savedDkk, BigDecimal savedCo2Grams) {
}
