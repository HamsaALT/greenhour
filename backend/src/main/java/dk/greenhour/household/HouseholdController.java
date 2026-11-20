package dk.greenhour.household;

import dk.greenhour.market.PriceArea;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/household")
public class HouseholdController {

    private final HouseholdService service;

    public HouseholdController(HouseholdService service) {
        this.service = service;
    }

    @GetMapping
    public HouseholdResponse get(@AuthenticationPrincipal Jwt jwt) {
        return HouseholdResponse.from(service.currentHousehold(jwt));
    }

    @PutMapping
    public HouseholdResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody HouseholdRequest request) {
        return HouseholdResponse.from(service.update(jwt, request.name(), request.priceArea()));
    }

    public record HouseholdRequest(@NotBlank @Size(max = 80) String name, @NotNull PriceArea priceArea) {
    }

    public record HouseholdResponse(Long id, String name, PriceArea priceArea) {
        static HouseholdResponse from(Household h) {
            return new HouseholdResponse(h.getId(), h.getName(), h.getPriceArea());
        }
    }
}
