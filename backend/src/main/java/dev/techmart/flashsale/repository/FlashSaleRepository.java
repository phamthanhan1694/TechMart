package dev.techmart.flashsale.repository;

import dev.techmart.flashsale.domain.FlashSale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FlashSaleRepository extends JpaRepository<FlashSale, Long> {

    @Query("""
            SELECT DISTINCT s FROM FlashSale s
              LEFT JOIN FETCH s.items i
              LEFT JOIN FETCH i.variant v
              LEFT JOIN FETCH v.product
             WHERE s.startsAt <= :now AND s.endsAt >= :now
             ORDER BY s.startsAt
            """)
    List<FlashSale> findActive(@Param("now") Instant now);

    @Query("""
            SELECT DISTINCT s FROM FlashSale s
              LEFT JOIN FETCH s.items i
              LEFT JOIN FETCH i.variant v
              LEFT JOIN FETCH v.product
             WHERE s.startsAt > :now
             ORDER BY s.startsAt
            """)
    List<FlashSale> findUpcoming(@Param("now") Instant now);

    @Query("""
            SELECT DISTINCT s FROM FlashSale s
              LEFT JOIN FETCH s.items i
              LEFT JOIN FETCH i.variant
             WHERE s.id = :id
            """)
    Optional<FlashSale> findByIdWithItems(@Param("id") Long id);
}
