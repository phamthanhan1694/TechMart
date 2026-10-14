package dev.techmart.cart.repository;

import dev.techmart.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Nạp giỏ + item + variant + product trong MỘT query.
     * Không có JOIN FETCH này thì một giỏ 5 món = 1 + 5 + 5 query (N+1 hai cấp).
     */
    @Query("""
            SELECT c FROM Cart c
              LEFT JOIN FETCH c.items i
              LEFT JOIN FETCH i.variant v
              LEFT JOIN FETCH v.product
             WHERE c.user.id = :userId
            """)
    Optional<Cart> findByUserIdWithItems(@Param("userId") Long userId);

    /**
     * Bản dùng cho luồng GHI (giữ hàng khi checkout): nạp item nhưng <b>không</b> nạp variant.
     *
     * <p>Variant phải ở dạng proxy lazy cho tới khi câu query {@code FOR UPDATE} nạp nó lần đầu.
     * Nếu nạp trước bằng JOIN FETCH thì câu query khoá sẽ ném StaleObjectStateException -
     * xem {@link dev.techmart.inventory.service.VariantLocker}.
     */
    @Query("""
            SELECT c FROM Cart c
              LEFT JOIN FETCH c.items
             WHERE c.user.id = :userId
            """)
    Optional<Cart> findByUserIdForCheckout(@Param("userId") Long userId);

    Optional<Cart> findByUserId(Long userId);
}
