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
     * Bản dùng cho luồng GHI (đặt hàng, huỷ phiên): <b>không</b> nạp variant.
     * Variant phải ở dạng proxy cho tới khi câu query FOR UPDATE nạp nó -
     * xem {@link dev.techmart.inventory.service.VariantLocker}.
     */
    List<InventoryReservation> findBySessionId(String sessionId);

    /** Bản dùng cho luồng ĐỌC (hiển thị phiên checkout): nạp kèm variant + product. */
    @Query("""
            SELECT r FROM InventoryReservation r
              JOIN FETCH r.variant v
              JOIN FETCH v.product
             WHERE r.sessionId = :sessionId
            """)
    List<InventoryReservation> findBySessionIdWithVariant(@Param("sessionId") String sessionId);

    /**
     * Lấy một LO reservation quá hạn để nha.
     *
     * <p>Quet theo lo có LIMIT chứ không {@code UPDATE ... WHERE expires_at < now()} một phat
     * trên toan bằng: lo lon khoá lau và có thể timeout. Dùng partial index
     * {@code idx_reservations_expiry} đã tạo ở V1__init.sql.
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
