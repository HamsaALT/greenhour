package dk.greenhour.planning;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScheduledRunRepository extends JpaRepository<ScheduledRun, Long> {

    @Query("""
        select r from ScheduledRun r join fetch r.appliance a
        where a.household.ownerId = :ownerId
        order by r.plannedStart desc
        """)
    List<ScheduledRun> findForOwner(String ownerId, Pageable page);

    @Query("""
        select r from ScheduledRun r join fetch r.appliance a
        where r.id = :id and a.household.ownerId = :ownerId
        """)
    Optional<ScheduledRun> findOwned(Long id, String ownerId);

    @Query("""
        select new dk.greenhour.planning.RunSummary(
            count(r),
            coalesce(sum(r.baselineCostDkk - r.costDkk), 0),
            coalesce(sum(r.baselineCo2Grams - r.co2Grams), 0))
        from ScheduledRun r
        where r.appliance.household.ownerId = :ownerId and r.status = :status
        """)
    RunSummary summarize(String ownerId, RunStatus status);
}
