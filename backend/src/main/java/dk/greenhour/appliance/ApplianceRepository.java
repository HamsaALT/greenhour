package dk.greenhour.appliance;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplianceRepository extends JpaRepository<Appliance, Long> {

    List<Appliance> findByHouseholdOwnerIdOrderByName(String ownerId);

    Optional<Appliance> findByIdAndHouseholdOwnerId(Long id, String ownerId);
}
