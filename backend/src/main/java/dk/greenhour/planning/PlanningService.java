package dk.greenhour.planning;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import dk.greenhour.appliance.Appliance;
import dk.greenhour.appliance.ApplianceService;
import dk.greenhour.common.NotFoundException;
import dk.greenhour.market.MarketIntervalRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {

    private static final int HISTORY_LIMIT = 200;

    private final ApplianceService applianceService;
    private final MarketIntervalRepository marketRepository;
    private final ScheduledRunRepository runRepository;
    private final RunOptimizer optimizer = new RunOptimizer();
    private final Clock clock;

    public PlanningService(ApplianceService applianceService, MarketIntervalRepository marketRepository,
                           ScheduledRunRepository runRepository, Clock clock) {
        this.applianceService = applianceService;
        this.marketRepository = marketRepository;
        this.runRepository = runRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RunOptimizer.Plan preview(String ownerId, PlanRequest request) {
        return plan(applianceService.findOwned(ownerId, request.applianceId()), request);
    }

    @Transactional
    public ScheduledRun schedule(String ownerId, PlanRequest request) {
        Appliance appliance = applianceService.findOwned(ownerId, request.applianceId());
        RunOptimizer.Plan plan = plan(appliance, request);
        return runRepository.save(
            new ScheduledRun(appliance, request.goal(), effectiveStart(request), request.deadline(), plan));
    }

    @Transactional(readOnly = true)
    public List<ScheduledRun> history(String ownerId) {
        return runRepository.findForOwner(ownerId, PageRequest.of(0, HISTORY_LIMIT));
    }

    @Transactional
    public ScheduledRun changeStatus(String ownerId, Long runId, RunStatus status) {
        ScheduledRun run = runRepository.findOwned(runId, ownerId)
            .orElseThrow(() -> new NotFoundException("Run " + runId + " not found"));
        run.changeStatus(status);
        return run;
    }

    @Transactional(readOnly = true)
    public RunSummary summary(String ownerId) {
        return runRepository.summarize(ownerId, RunStatus.DONE);
    }

    private RunOptimizer.Plan plan(Appliance appliance, PlanRequest request) {
        Instant start = effectiveStart(request);
        List<RunOptimizer.MarketSlot> slots = marketRepository
            .findRange(appliance.getHousehold().getPriceArea(), start.minus(RunOptimizer.SLOT), request.deadline())
            .stream()
            .map(m -> new RunOptimizer.MarketSlot(m.getIntervalStart(), m.getPriceDkkPerKwh(), m.getCo2GramsPerKwh()))
            .toList();
        return optimizer.optimize(slots, appliance.getPowerKw(), appliance.getRunMinutes(),
            start, request.deadline(), request.goal());
    }

    /** "Earliest start" in the past just means "now". */
    private Instant effectiveStart(PlanRequest request) {
        Instant now = clock.instant();
        return request.earliestStart().isBefore(now) ? now : request.earliestStart();
    }
}
