package dk.greenhour.planning;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class ValidPlanningWindowTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final Instant start = Instant.now().plus(Duration.ofHours(1));

    @Test
    void acceptsANormalWindow() {
        assertThat(validator.validate(request(start, start.plus(Duration.ofHours(10))))).isEmpty();
    }

    @Test
    void rejectsDeadlineBeforeStart() {
        var violations = validator.validate(request(start, start.minus(Duration.ofMinutes(30))));

        assertThat(violations).anySatisfy(v ->
            assertThat(v.getPropertyPath().toString()).isEqualTo("deadline"));
    }

    @Test
    void rejectsWindowsLongerThanTwoDays() {
        assertThat(validator.validate(request(start, start.plus(Duration.ofHours(49))))).isNotEmpty();
    }

    @Test
    void leavesMissingFieldsToNotNull() {
        var violations = validator.validate(new PlanRequest(1L, null, start, OptimizationGoal.PRICE));

        assertThat(violations).singleElement()
            .satisfies(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("earliestStart"));
    }

    private static PlanRequest request(Instant from, Instant to) {
        return new PlanRequest(1L, from, to, OptimizationGoal.PRICE);
    }
}
