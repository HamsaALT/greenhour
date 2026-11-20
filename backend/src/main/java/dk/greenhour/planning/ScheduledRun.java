package dk.greenhour.planning;

import java.math.BigDecimal;
import java.time.Instant;

import dk.greenhour.appliance.Appliance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "scheduled_run")
public class ScheduledRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appliance_id")
    private Appliance appliance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OptimizationGoal goal;

    @Column(name = "earliest_start", nullable = false)
    private Instant earliestStart;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "planned_start", nullable = false)
    private Instant plannedStart;

    @Column(name = "planned_end", nullable = false)
    private Instant plannedEnd;

    @Column(name = "energy_kwh", nullable = false)
    private BigDecimal energyKwh;

    @Column(name = "cost_dkk", nullable = false)
    private BigDecimal costDkk;

    @Column(name = "co2_g")
    private BigDecimal co2Grams;

    @Column(name = "baseline_start", nullable = false)
    private Instant baselineStart;

    @Column(name = "baseline_cost_dkk", nullable = false)
    private BigDecimal baselineCostDkk;

    @Column(name = "baseline_co2_g")
    private BigDecimal baselineCo2Grams;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status = RunStatus.PLANNED;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ScheduledRun() {
    }

    public ScheduledRun(Appliance appliance, OptimizationGoal goal, Instant earliestStart, Instant deadline,
                        RunOptimizer.Plan plan) {
        this.appliance = appliance;
        this.goal = goal;
        this.earliestStart = earliestStart;
        this.deadline = deadline;
        this.plannedStart = plan.best().start();
        this.plannedEnd = plan.end();
        this.energyKwh = appliance.energyPerRunKwh();
        this.costDkk = plan.best().costDkk();
        this.co2Grams = plan.best().co2Grams();
        this.baselineStart = plan.baseline().start();
        this.baselineCostDkk = plan.baseline().costDkk();
        this.baselineCo2Grams = plan.baseline().co2Grams();
    }

    /** A run can be marked done or cancelled once. After that it's history. */
    public void changeStatus(RunStatus next) {
        if (status != RunStatus.PLANNED) {
            throw new InvalidStatusChangeException("RUN_ALREADY_CLOSED", "This run is already " + status.name().toLowerCase() + ".");
        }
        if (next == RunStatus.PLANNED) {
            throw new InvalidStatusChangeException("RUN_STATUS_INVALID", "A run can only be marked as done or cancelled.");
        }
        status = next;
    }

    public Long getId() {
        return id;
    }

    public Appliance getAppliance() {
        return appliance;
    }

    public OptimizationGoal getGoal() {
        return goal;
    }

    public Instant getEarliestStart() {
        return earliestStart;
    }

    public Instant getDeadline() {
        return deadline;
    }

    public Instant getPlannedStart() {
        return plannedStart;
    }

    public Instant getPlannedEnd() {
        return plannedEnd;
    }

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public BigDecimal getCostDkk() {
        return costDkk;
    }

    public BigDecimal getCo2Grams() {
        return co2Grams;
    }

    public BigDecimal getBaselineCostDkk() {
        return baselineCostDkk;
    }

    public BigDecimal getBaselineCo2Grams() {
        return baselineCo2Grams;
    }

    public RunStatus getStatus() {
        return status;
    }
}
