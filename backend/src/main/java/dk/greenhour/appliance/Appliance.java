package dk.greenhour.appliance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import dk.greenhour.household.Household;
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
@Table(name = "appliance")
public class Appliance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "household_id")
    private Household household;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplianceKind kind;

    @Column(name = "power_kw", nullable = false)
    private BigDecimal powerKw;

    @Column(name = "run_minutes", nullable = false)
    private int runMinutes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Appliance() {
    }

    public Appliance(Household household, String name, ApplianceKind kind, BigDecimal powerKw, int runMinutes) {
        this.household = household;
        update(name, kind, powerKw, runMinutes);
    }

    public void update(String name, ApplianceKind kind, BigDecimal powerKw, int runMinutes) {
        this.name = name.trim();
        this.kind = kind;
        this.powerKw = powerKw;
        this.runMinutes = runMinutes;
    }

    /** Assumes a flat power draw, which is close enough for planning purposes. */
    public BigDecimal energyPerRunKwh() {
        return powerKw.multiply(BigDecimal.valueOf(runMinutes))
            .divide(BigDecimal.valueOf(60), 3, RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public Household getHousehold() {
        return household;
    }

    public String getName() {
        return name;
    }

    public ApplianceKind getKind() {
        return kind;
    }

    public BigDecimal getPowerKw() {
        return powerKw;
    }

    public int getRunMinutes() {
        return runMinutes;
    }
}
