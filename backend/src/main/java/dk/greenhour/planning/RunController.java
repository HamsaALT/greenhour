package dk.greenhour.planning;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import dk.greenhour.appliance.ApplianceKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final PlanningService service;

    public RunController(PlanningService service) {
        this.service = service;
    }

    /** Works out the best time without saving anything, so the UI can show it before the user commits. */
    @PostMapping("/preview")
    public PreviewResponse preview(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanRequest request) {
        return PreviewResponse.from(service.preview(jwt.getSubject(), request));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunResponse schedule(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlanRequest request) {
        return RunResponse.from(service.schedule(jwt.getSubject(), request));
    }

    @GetMapping
    public List<RunResponse> history(@AuthenticationPrincipal Jwt jwt) {
        return service.history(jwt.getSubject()).stream().map(RunResponse::from).toList();
    }

    @PatchMapping("/{id}")
    public RunResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody StatusChange change) {
        return RunResponse.from(service.changeStatus(jwt.getSubject(), id, change.status()));
    }

    @GetMapping("/summary")
    public RunSummary summary(@AuthenticationPrincipal Jwt jwt) {
        return service.summary(jwt.getSubject());
    }

    public record StatusChange(@NotNull RunStatus status) {
    }

    public record PreviewResponse(
        Instant start, Instant end,
        BigDecimal costDkk, BigDecimal co2Grams,
        Instant baselineStart, BigDecimal baselineCostDkk, BigDecimal baselineCo2Grams,
        BigDecimal savedDkk, BigDecimal savedCo2Grams,
        List<RunOptimizer.Candidate> options) {

        static PreviewResponse from(RunOptimizer.Plan plan) {
            return new PreviewResponse(
                plan.best().start(), plan.end(),
                plan.best().costDkk(), plan.best().co2Grams(),
                plan.baseline().start(), plan.baseline().costDkk(), plan.baseline().co2Grams(),
                plan.savedDkk(), plan.savedCo2Grams(),
                plan.candidates());
        }
    }

    public record RunResponse(
        Long id, Long applianceId, String applianceName, ApplianceKind applianceKind,
        OptimizationGoal goal, RunStatus status,
        Instant earliestStart, Instant deadline, Instant plannedStart, Instant plannedEnd,
        BigDecimal energyKwh, BigDecimal costDkk, BigDecimal co2Grams,
        BigDecimal savedDkk, BigDecimal savedCo2Grams) {

        static RunResponse from(ScheduledRun r) {
            BigDecimal savedCo2 = r.getBaselineCo2Grams() == null || r.getCo2Grams() == null
                ? null
                : r.getBaselineCo2Grams().subtract(r.getCo2Grams());
            return new RunResponse(
                r.getId(), r.getAppliance().getId(), r.getAppliance().getName(), r.getAppliance().getKind(),
                r.getGoal(), r.getStatus(),
                r.getEarliestStart(), r.getDeadline(), r.getPlannedStart(), r.getPlannedEnd(),
                r.getEnergyKwh(), r.getCostDkk(), r.getCo2Grams(),
                r.getBaselineCostDkk().subtract(r.getCostDkk()), savedCo2);
        }
    }
}
