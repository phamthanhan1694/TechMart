package dev.techmart.cart.repository;

import dev.techmart.cart.domain.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    /**
     * Nap gio + item + variant + product trong MOT query.
     * Khong co JOIN FETCH nay thi mot gio 5 mon = 1 + 5 + 5 query (N+1 hai cap).
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
     * Ban dung cho luong GHI (giu hang khi checkout): nap item nhung <b>khong</b> nap variant.
     *
     * <p>Variant phai o dang proxy lazy cho toi khi cau query {@code FOR UPDATE} nap no lan dau.
     * Neu nap truoc bang JOIN FETCH thi cau query khoa se nem StaleObjectStateException -
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
