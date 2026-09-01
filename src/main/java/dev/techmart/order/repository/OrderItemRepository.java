package dev.techmart.order.repository;

import dev.techmart.order.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /**
     * Nap kem don + variant + product de kiem dieu kien danh gia trong mot query:
     * dong nay co thuoc user do khong, don da DELIVERED chua, san pham nao.
     */
    @Query("""
            SELECT i FROM OrderItem i
              JOIN FETCH i.order o
              JOIN FETCH i.variant v
              JOIN FETCH v.product
             WHERE i.id = :id
            """)
    Optional<OrderItem> findDetailById(@Param("id") Long id);

    List<OrderItem> findByOrderId(Long orderId);
}
