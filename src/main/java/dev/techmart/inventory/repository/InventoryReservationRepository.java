package dev.techmart.inventory.repository;

import dev.techmart.inventory.domain.InventoryReservation;
import dev.techmart.inventory.domain.ReservationStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {

    /**
     * Ban dung cho luong GHI (dat hang, huy phien): <b>khong</b> nap variant.
     * Variant phai o dang proxy cho toi khi cau query FOR UPDATE nap no -
     * xem {@link dev.techmart.inventory.service.VariantLocker}.
     */
    List<InventoryReservation> findBySessionId(String sessionId);

    /** Ban dung cho luong DOC (hien thi phien checkout): nap kem variant + product. */
    @Query("""
            SELECT r FROM InventoryReservation r
              JOIN FETCH r.variant v
              JOIN FETCH v.product
             WHERE r.sessionId = :sessionId
            """)
    List<InventoryReservation> findBySessionIdWithVariant(@Param("sessionId") String sessionId);

    /**
     * Lay mot LO reservation qua han de nha.
     *
     * <p>Quet theo lo co LIMIT chu khong {@code UPDATE ... WHERE expires_at < now()} mot phat
     * tren toan bang: lo lon khoa lau va co the timeout. Dung partial index
     * {@code idx_reservations_expiry} da tao o V1__init.sql.
     */
    @Query("""
            SELECT r FROM InventoryReservation r
             WHERE r.status = 'HELD'
               AND r.expiresAt < :now
             ORDER BY r.expiresAt ASC
            """)
    List<InventoryReservation> findExpiredBatch(@Param("now") Instant now, Limit limit);

    long countByStatus(ReservationStatus status);

    @Query("""
            SELECT COALESCE(SUM(r.quantity), 0) FROM InventoryReservation r
             WHERE r.variant.id = :variantId AND r.status = 'HELD'
            """)
    long sumHeldQuantity(@Param("variantId") Long variantId);
}
