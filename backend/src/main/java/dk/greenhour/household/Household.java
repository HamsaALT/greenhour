package dk.greenhour.household;

import java.time.Instant;

import dk.greenhour.market.PriceArea;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "household")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, unique = true, updatable = false)
    private String ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_area", nullable = false)
    private PriceArea priceArea;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Household() {
    }

    public Household(String ownerId, String name, PriceArea priceArea) {
        this.ownerId = ownerId;
        this.name = name;
        this.priceArea = priceArea;
    }

    public Long getId() {
        return id;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public PriceArea getPriceArea() {
        return priceArea;
    }

    public void update(String name, PriceArea priceArea) {
        this.name = name;
        this.priceArea = priceArea;
    }
}
