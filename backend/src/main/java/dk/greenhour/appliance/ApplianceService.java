package dk.greenhour.appliance;

import java.math.BigDecimal;
import java.util.List;

import dk.greenhour.common.NotFoundException;
import dk.greenhour.household.HouseholdService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplianceService {

    private final ApplianceRepository repository;
    private final HouseholdService householdService;

    public ApplianceService(ApplianceRepository repository, HouseholdService householdService) {
        this.repository = repository;
        this.householdService = householdService;
    }

    @Transactional(readOnly = true)
    public List<Appliance> list(String ownerId) {
        return repository.findByHouseholdOwnerIdOrderByName(ownerId);
    }

    @Transactional
    public Appliance create(Jwt jwt, String name, ApplianceKind kind, BigDecimal powerKw, int runMinutes) {
        var household = householdService.currentHousehold(jwt);
        // Flush now so a duplicate name fails here, inside the request, as a clean 409.
        return repository.saveAndFlush(new Appliance(household, name, kind, powerKw, runMinutes));
    }

    @Transactional
    public Appliance update(String ownerId, Long id, String name, ApplianceKind kind, BigDecimal powerKw, int runMinutes) {
        var appliance = findOwned(ownerId, id);
        appliance.update(name, kind, powerKw, runMinutes);
        return repository.saveAndFlush(appliance);
    }

    @Transactional
    public void delete(String ownerId, Long id) {
        repository.delete(findOwned(ownerId, id));
    }

    /** Someone else's appliance gives the same 404 as a missing one, so ids can't be probed. */
    @Transactional(readOnly = true)
    public Appliance findOwned(String ownerId, Long id) {
        return repository.findByIdAndHouseholdOwnerId(id, ownerId)
            .orElseThrow(() -> new NotFoundException("Appliance " + id + " not found"));
    }
}
