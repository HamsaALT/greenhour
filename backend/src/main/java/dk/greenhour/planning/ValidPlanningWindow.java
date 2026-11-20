package dk.greenhour.planning;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Deadline must come after the earliest start, and the window can't be longer
 * than 48 hours since prices are never published further ahead than that.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidPlanningWindow.Validator.class)
public @interface ValidPlanningWindow {

    Duration MAX_WINDOW = Duration.ofHours(48);

    String message() default "deadline must be after the earliest start and at most 48 hours later";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidPlanningWindow, PlanRequest> {
        @Override
        public boolean isValid(PlanRequest request, ConstraintValidatorContext context) {
            if (request == null || request.earliestStart() == null || request.deadline() == null) {
                return true; // @NotNull on the fields reports these
            }
            Duration window = Duration.between(request.earliestStart(), request.deadline());
            if (!window.isNegative() && !window.isZero() && window.compareTo(MAX_WINDOW) <= 0) {
                return true;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("deadline")
                .addConstraintViolation();
            return false;
        }
    }
}
