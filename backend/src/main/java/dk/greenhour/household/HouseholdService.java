package dk.greenhour.household;

import dk.greenhour.market.PriceArea;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HouseholdService {

    private final HouseholdRepository repository;

    public HouseholdService(HouseholdRepository repository) {
        this.repository = repository;
    }

    /**
     * New users don't have to sign up separately: the first request with a valid
     * token creates their household. DK2 is the default because it covers Copenhagen.
     */
    @Transactional
    public Household currentHousehold(Jwt jwt) {
        return repository.findByOwnerId(jwt.getSubject())
            .orElseGet(() -> repository.save(new Household(jwt.getSubject(), defaultName(jwt), PriceArea.DK2)));
    }

    @Transactional
    public Household update(Jwt jwt, String name, PriceArea area) {
        Household household = currentHousehold(jwt);
        household.update(name.trim(), area);
        return household;
    }

    private static String defaultName(Jwt jwt) {
        String firstName = jwt.getClaimAsString("given_name");
        if (firstName == null) {
            firstName = jwt.getClaimAsString("preferred_username");
        }
        return firstName == null ? "My home" : firstName + "'s home";
    }
}
