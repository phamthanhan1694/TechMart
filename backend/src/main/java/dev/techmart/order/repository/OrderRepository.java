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
     * Danh sách đơn của user.
     *
     * <p>{@code @EntityGraph} nạp kèm items trong cùng một query. Không có nó thì
     * 20 đơn = 21 query - đây là chỗ N+1 kinh điển nhất của mọi hệ thống e-commerce.
     */
    @EntityGraph(attributePaths = {"items"})
    Page<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByCode(String code);

    /** Đường nhanh của idempotency; chốt chặn thật sự là UNIQUE(idempotency_key) ở DB. */
    @EntityGraph(attributePaths = {"items"})
    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    @EntityGraph(attributePaths = {"history"})
    @Query("SELECT o FROM Order o WHERE o.code = :code")
    Optional<Order> findByCodeWithHistory(@Param("code") String code);

    @EntityGraph(attributePaths = {"items"})
    @Query("""
            SELECT o FROM Order o
             WHERE (:status IS NULL OR o.status = :status)
               AND (CAST(:from AS timestamp) IS NULL OR o.placedAt >= :from)
               AND (CAST(:to   AS timestamp) IS NULL OR o.placedAt <= :to)
             ORDER BY o.placedAt DESC
            """)
    Page<Order> findForAdmin(@Param("status") OrderStatus status,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);

    /** Đơn quá hạn thanh toán - job huỷ tự động nhặt lên. */
    @Query("""
            SELECT o FROM Order o
             WHERE o.status = 'PENDING_PAYMENT'
               AND o.placedAt < :threshold
            """)
    List<Order> findStalePendingPayment(@Param("threshold") Instant threshold);

    boolean existsByCode(String code);
}
