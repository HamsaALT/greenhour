package dk.greenhour.planning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import dk.greenhour.planning.RunOptimizer.MarketSlot;
import dk.greenhour.planning.RunOptimizer.Plan;
import org.junit.jupiter.api.Test;

class RunOptimizerTest {

    private static final Instant T0 = Instant.parse("2026-03-10T00:00:00Z");
    private static final BigDecimal ONE_KW = BigDecimal.ONE;

    private final RunOptimizer optimizer = new RunOptimizer();

    @Test
    void picksTheCheapestWindow() {
        // Cheapest pair of slots is at index 3-4.
        var slots = slots(new double[] {1.0, 0.9, 0.8, 0.2, 0.3, 0.9, 1.1, 1.2}, null);

        Plan plan = optimizer.optimize(slots, ONE_KW, 30, T0, T0.plus(Duration.ofHours(2)), OptimizationGoal.PRICE);

        assertThat(plan.best().start()).isEqualTo(at(3));
        assertThat(plan.end()).isEqualTo(at(5));
        // (0.2 + 0.3) DKK/kWh * 1 kW * 0.25 h
        assertThat(plan.best().costDkk()).isEqualByComparingTo("0.125");
    }

    @Test
    void comparesAgainstStartingRightAway() {
        var slots = slots(new double[] {1.0, 1.0, 0.2, 0.2}, null);

        Plan plan = optimizer.optimize(slots, new BigDecimal("2"), 30, T0, at(4), OptimizationGoal.PRICE);

        assertThat(plan.baseline().start()).isEqualTo(T0);
        assertThat(plan.baseline().costDkk()).isEqualByComparingTo("1.000");
        assertThat(plan.best().costDkk()).isEqualByComparingTo("0.200");
        assertThat(plan.savedDkk()).isEqualByComparingTo("0.800");
    }

    @Test
    void prefersTheEarliestStartWhenPricesTie() {
        var slots = slots(new double[] {0.5, 0.5, 0.5, 0.5}, null);

        Plan plan = optimizer.optimize(slots, ONE_KW, 15, T0, at(4), OptimizationGoal.PRICE);

        assertThat(plan.best().start()).isEqualTo(T0);
    }

    @Test
    void neverFinishesAfterTheDeadline() {
        // The last slot is by far the cheapest, but the run wouldn't finish in time.
        var slots = slots(new double[] {1.0, 1.0, 1.0, 0.0}, null);

        Plan plan = optimizer.optimize(slots, ONE_KW, 30, T0, at(3), OptimizationGoal.PRICE);

        assertThat(plan.end()).isBeforeOrEqualTo(at(3));
        assertThat(plan.candidates()).hasSize(2);
    }

    @Test
    void roundsAnAwkwardStartUpToTheNextSlot() {
        var slots = slots(new double[] {0.1, 0.5, 0.5, 0.5}, null);

        Plan plan = optimizer.optimize(slots, ONE_KW, 15, T0.plusSeconds(60), at(4), OptimizationGoal.PRICE);

        // Slot 0 started before we were allowed to start, so it can't be used.
        assertThat(plan.best().start()).isEqualTo(at(1));
    }

    @Test
    void optimizesForCo2WhenAsked() {
        var slots = slots(new double[] {0.1, 0.9, 0.9}, new double[] {300, 50, 60});

        Plan plan = optimizer.optimize(slots, ONE_KW, 15, T0, at(3), OptimizationGoal.CO2);

        assertThat(plan.best().start()).isEqualTo(at(1));
        assertThat(plan.best().co2Grams()).isEqualByComparingTo("12.5");
        assertThat(plan.savedCo2Grams()).isEqualByComparingTo("62.5");
    }

    @Test
    void balancedPicksTheBestCompromise() {
        // Slot 0: cheapest but dirtiest. Slot 1: cleanest but priciest. Slot 2: good at both.
        var slots = slots(new double[] {0.10, 1.00, 0.20}, new double[] {400, 40, 80});

        Plan plan = optimizer.optimize(slots, ONE_KW, 15, T0, at(3), OptimizationGoal.BALANCED);

        assertThat(plan.best().start()).isEqualTo(at(2));
    }

    @Test
    void skipsWindowsWithMissingSlots() {
        var slots = new ArrayList<>(slots(new double[] {0.1, 0.1, 0.9, 0.9, 0.9}, null));
        slots.remove(1); // gap after the first cheap slot

        Plan plan = optimizer.optimize(slots, ONE_KW, 30, T0, at(5), OptimizationGoal.PRICE);

        assertThat(plan.candidates()).extracting(RunOptimizer.Candidate::start)
            .containsExactly(at(2), at(3));
    }

    @Test
    void explainsWhenTheWindowIsTooShort() {
        var slots = slots(new double[] {0.5, 0.5}, null);

        assertThatThrownBy(() -> optimizer.optimize(slots, ONE_KW, 90, T0, at(2), OptimizationGoal.PRICE))
            .isInstanceOf(NoFeasibleWindowException.class)
            .hasMessageContaining("too short")
            .hasMessageContaining("1 h 30 min");
    }

    @Test
    void explainsWhenPricesAreNotPublishedYet() {
        assertThatThrownBy(() -> optimizer.optimize(List.of(), ONE_KW, 30, T0, at(8), OptimizationGoal.PRICE))
            .isInstanceOf(NoFeasibleWindowException.class)
            .hasMessageContaining("haven't been published");
    }

    @Test
    void explainsWhenCo2ForecastIsMissing() {
        var slots = slots(new double[] {0.5, 0.5, 0.5}, null);

        assertThatThrownBy(() -> optimizer.optimize(slots, ONE_KW, 15, T0, at(3), OptimizationGoal.CO2))
            .isInstanceOf(NoFeasibleWindowException.class)
            .hasMessageContaining("CO2 forecast");
    }

    @Test
    void reportsNoCo2SavingWhenCo2IsUnknown() {
        var slots = slots(new double[] {0.9, 0.1}, null);

        Plan plan = optimizer.optimize(slots, ONE_KW, 15, T0, at(2), OptimizationGoal.PRICE);

        assertThat(plan.best().co2Grams()).isNull();
        assertThat(plan.savedCo2Grams()).isNull();
    }

    @Test
    void rejectsRunLengthsThatDoNotFitSlots() {
        assertThatThrownBy(() -> optimizer.optimize(List.of(), ONE_KW, 20, T0, at(4), OptimizationGoal.PRICE))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundUpLeavesSlotBoundariesAlone() {
        assertThat(RunOptimizer.roundUpToSlot(at(2))).isEqualTo(at(2));
        assertThat(RunOptimizer.roundUpToSlot(at(2).plusNanos(1))).isEqualTo(at(3));
    }

    private static Instant at(int slot) {
        return T0.plus(RunOptimizer.SLOT.multipliedBy(slot));
    }

    private static List<MarketSlot> slots(double[] prices, double[] co2) {
        List<MarketSlot> result = new ArrayList<>();
        for (int i = 0; i < prices.length; i++) {
            result.add(new MarketSlot(at(i), BigDecimal.valueOf(prices[i]),
                co2 == null ? null : BigDecimal.valueOf(co2[i])));
        }
        return result;
    }
}
