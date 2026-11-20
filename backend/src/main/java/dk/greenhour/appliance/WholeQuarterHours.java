package dk.greenhour.appliance;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Run time must line up with the 15-minute market slots. */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WholeQuarterHours.Validator.class)
public @interface WholeQuarterHours {

    String message() default "must be a multiple of 15 minutes";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<WholeQuarterHours, Integer> {
        @Override
        public boolean isValid(Integer minutes, ConstraintValidatorContext context) {
            return minutes == null || minutes % 15 == 0;
        }
    }
}
