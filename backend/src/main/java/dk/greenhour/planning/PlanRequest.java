package dk.greenhour.planning;

import java.time.Instant;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

@ValidPlanningWindow
public record PlanRequest(
    @NotNull Long applianceId,
    @NotNull Instant earliestStart,
    @NotNull @Future Instant deadline,
    @NotNull OptimizationGoal goal) {
}
