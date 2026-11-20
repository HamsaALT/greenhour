package dk.greenhour.appliance;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/appliances")
public class ApplianceController {

    private final ApplianceService service;

    public ApplianceController(ApplianceService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplianceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(jwt.getSubject()).stream().map(ApplianceResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplianceResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApplianceRequest request) {
        return ApplianceResponse.from(
            service.create(jwt, request.name(), request.kind(), request.powerKw(), request.runMinutes()));
    }

    @PutMapping("/{id}")
    public ApplianceResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                    @Valid @RequestBody ApplianceRequest request) {
        return ApplianceResponse.from(service.update(jwt.getSubject(), id,
            request.name(), request.kind(), request.powerKw(), request.runMinutes()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(jwt.getSubject(), id);
    }

    public record ApplianceRequest(
        @NotBlank @Size(max = 60) String name,
        @NotNull ApplianceKind kind,
        @NotNull @DecimalMin("0.05") @DecimalMax("22.0") BigDecimal powerKw,
        @NotNull @Min(15) @Max(720) @WholeQuarterHours Integer runMinutes) {
    }

    public record ApplianceResponse(Long id, String name, ApplianceKind kind, BigDecimal powerKw,
                                    int runMinutes, BigDecimal energyPerRunKwh) {
        static ApplianceResponse from(Appliance a) {
            return new ApplianceResponse(a.getId(), a.getName(), a.getKind(), a.getPowerKw(),
                a.getRunMinutes(), a.energyPerRunKwh());
        }
    }
}
