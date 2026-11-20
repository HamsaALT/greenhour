package dk.greenhour.market;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketIntervalRepository extends JpaRepository<MarketInterval, MarketInterval.Key> {

    @Query("""
        select m from MarketInterval m
        where m.priceArea = :area and m.intervalStart >= :from and m.intervalStart < :to
        order by m.intervalStart
        """)
    List<MarketInterval> findRange(PriceArea area, Instant from, Instant to);

    @Query("select max(m.intervalStart) from MarketInterval m where m.priceArea = :area")
    Optional<Instant> findLatestStart(PriceArea area);
}
