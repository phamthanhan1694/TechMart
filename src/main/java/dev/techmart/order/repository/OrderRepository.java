package dev.techmart.order.repository;

import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Danh sach don cua user.
     *
     * <p>{@code @EntityGraph} nap kem items trong cung mot query. Khong co no thi
     * 20 don = 21 query - day la cho N+1 kinh dien nhat cua moi he thong e-commerce.
     */
    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByCode(String code);

    /** Duong nhanh cua idempotency; chot chan that su la UNIQUE(idempotency_key) o DB. */
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"history"})
    @Query("SELECT o FROM Order o WHERE o.code = :code")
    Optional<Order> findByCodeWithHistory(@Param("code") String code);

    @EntityGraph(attributePaths = {"items"})
    @Query("""
            SELECT o FROM Order o
             WHERE (:status IS NULL OR o.status = :status)
               AND (CAST(:from AS timestamp) IS NULL OR o.createdAt >= :from)
               AND (CAST(:to   AS timestamp) IS NULL OR o.createdAt <= :to)
             ORDER BY o.createdAt DESC
            """)
    Page<Order> findForAdmin(@Param("status") OrderStatus status,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);

    /** Don qua han thanh toan - job huy tu dong nhat len. */
    @Query("""
            SELECT o FROM Order o
             WHERE o.status = 'PENDING_PAYMENT'
               AND o.placedAt < :threshold
            """)
    List<Order> findStalePendingPayment(@Param("threshold") Instant threshold);

    boolean existsByCode(String code);
}
