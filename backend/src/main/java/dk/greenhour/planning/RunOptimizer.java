package dk.greenhour.planning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Picks the best start time for a run inside a time window.
 *
 * Every 15-minute slot boundary in the window is a possible start. For each one
 * we add up the price (and CO2) of the slots the run would cover. Windows are
 * at most 48 hours and runs at most 12, so trying every start is a few thousand
 * additions, which is simpler and easier to trust than anything clever.
 */
public class RunOptimizer {

    static final Duration SLOT = Duration.ofMinutes(15);
    private static final BigDecimal SLOT_HOURS = new BigDecimal("0.25");

    public Plan optimize(List<MarketSlot> slots, BigDecimal powerKw, int runMinutes,
                         Instant earliestStart, Instant deadline, OptimizationGoal goal) {
        if (runMinutes <= 0 || runMinutes % 15 != 0) {
            throw new IllegalArgumentException("Run length must be a positive multiple of 15 minutes");
        }
        Instant firstPossibleStart = roundUpToSlot(earliestStart);
        Duration runLength = Duration.ofMinutes(runMinutes);
        if (firstPossibleStart.plus(runLength).isAfter(deadline)) {
            throw new NoFeasibleWindowException(NoFeasibleWindowException.Reason.WINDOW_TOO_SHORT,
                "The window is too short for a run of " + formatDuration(runLength) + ".",
                Map.of("runMinutes", runMinutes));
        }

        List<MarketSlot> usable = slots.stream()
            .filter(s -> !s.start().isBefore(firstPossibleStart) && s.start().isBefore(deadline))
            .sorted(Comparator.comparing(MarketSlot::start))
            .toList();

        int slotsNeeded = runMinutes / 15;
        List<Candidate> candidates = new ArrayList<>();
        boolean anyPriceOnlyCandidate = false;

        for (int i = 0; i + slotsNeeded <= usable.size(); i++) {
            List<MarketSlot> window = usable.subList(i, i + slotsNeeded);
            if (!isContiguous(window)) {
                continue;
            }
            Candidate candidate = evaluate(window, powerKw);
            if (goal.needsCo2() && candidate.co2Grams() == null) {
                anyPriceOnlyCandidate = true;
                continue;
            }
            candidates.add(candidate);
        }

        if (candidates.isEmpty()) {
            throw anyPriceOnlyCandidate
                ? new NoFeasibleWindowException(NoFeasibleWindowException.Reason.CO2_FORECAST_MISSING,
                    "There is no CO2 forecast for this window yet. Energinet usually only forecasts the current day, so try optimizing for price instead.",
                    Map.of())
                : new NoFeasibleWindowException(NoFeasibleWindowException.Reason.PRICES_NOT_PUBLISHED,
                    "Prices for this window haven't been published yet. Tomorrow's prices usually arrive around 13:00.",
                    Map.of());
        }

        // The baseline is what you'd do without the app: start as soon as you're allowed to.
        Candidate baseline = candidates.getFirst();
        Candidate best = pickBest(candidates, goal);
        return new Plan(best, best.start().plus(runLength), baseline, candidates);
    }

    private static Candidate evaluate(List<MarketSlot> window, BigDecimal powerKw) {
        BigDecimal priceSum = BigDecimal.ZERO;
        BigDecimal co2Sum = BigDecimal.ZERO;
        for (MarketSlot slot : window) {
            priceSum = priceSum.add(slot.priceDkkPerKwh());
            co2Sum = slot.co2GramsPerKwh() == null || co2Sum == null ? null : co2Sum.add(slot.co2GramsPerKwh());
        }
        BigDecimal energyPerSlot = powerKw.multiply(SLOT_HOURS);
        BigDecimal cost = priceSum.multiply(energyPerSlot).setScale(3, RoundingMode.HALF_UP);
        BigDecimal co2 = co2Sum == null ? null : co2Sum.multiply(energyPerSlot).setScale(1, RoundingMode.HALF_UP);
        return new Candidate(window.getFirst().start(), cost, co2);
    }

    private static Candidate pickBest(List<Candidate> candidates, OptimizationGoal goal) {
        // Ties go to the earliest start: no reason to wait longer for the same result.
        Comparator<Candidate> byScore = switch (goal) {
            case PRICE -> Comparator.comparing(Candidate::costDkk);
            case CO2 -> Comparator.comparing(Candidate::co2Grams);
            case BALANCED -> Comparator.comparingDouble(balancedScore(candidates));
        };
        return candidates.stream()
            .min(byScore.thenComparing(Candidate::start))
            .orElseThrow();
    }

    /**
     * Price and CO2 have different units, so each is scaled to 0..1 across the
     * candidates (0 = best option available, 1 = worst) and the two are averaged.
     */
    private static ToDoubleFunction<Candidate> balancedScore(List<Candidate> candidates) {
        double minCost = candidates.stream().mapToDouble(c -> c.costDkk().doubleValue()).min().orElse(0);
        double maxCost = candidates.stream().mapToDouble(c -> c.costDkk().doubleValue()).max().orElse(0);
        double minCo2 = candidates.stream().mapToDouble(c -> c.co2Grams().doubleValue()).min().orElse(0);
        double maxCo2 = candidates.stream().mapToDouble(c -> c.co2Grams().doubleValue()).max().orElse(0);
        return c -> 0.5 * scale(c.costDkk().doubleValue(), minCost, maxCost)
                  + 0.5 * scale(c.co2Grams().doubleValue(), minCo2, maxCo2);
    }

    private static double scale(double value, double min, double max) {
        return max == min ? 0 : (value - min) / (max - min);
    }

    private static boolean isContiguous(List<MarketSlot> window) {
        Instant expectedLast = window.getFirst().start().plus(SLOT.multipliedBy(window.size() - 1L));
        return window.getLast().start().equals(expectedLast);
    }

    static Instant roundUpToSlot(Instant instant) {
        long slotSeconds = SLOT.toSeconds();
        long seconds = instant.getEpochSecond() + (instant.getNano() > 0 ? 1 : 0);
        return Instant.ofEpochSecond(Math.ceilDiv(seconds, slotSeconds) * slotSeconds);
    }

    private static String formatDuration(Duration d) {
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        if (hours == 0) {
            return minutes + " min";
        }
        return minutes == 0 ? hours + " h" : hours + " h " + minutes + " min";
    }

    public record MarketSlot(Instant start, BigDecimal priceDkkPerKwh, BigDecimal co2GramsPerKwh) {
    }

    public record Candidate(Instant start, BigDecimal costDkk, BigDecimal co2Grams) {
    }

    public record Plan(Candidate best, Instant end, Candidate baseline, List<Candidate> candidates) {

        public BigDecimal savedDkk() {
            return baseline.costDkk().subtract(best.costDkk());
        }

        public BigDecimal savedCo2Grams() {
            if (baseline.co2Grams() == null || best.co2Grams() == null) {
                return null;
            }
            return baseline.co2Grams().subtract(best.co2Grams());
        }
    }
}
