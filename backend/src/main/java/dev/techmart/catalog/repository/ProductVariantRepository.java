package dev.techmart.catalog.repository;

import dev.techmart.catalog.domain.ProductVariant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    boolean existsBySku(String sku);

    List<ProductVariant> findAllByProductId(Long productId);

    /**
     * Khoá bi quan các variant trước khi trừ tồn kho.
     *
     * <p><b>ORDER BY v.id là chi tiết quan trọng nhất của cả phương thức này.</b>
     * Đơn A mua [variant 5, variant 9], đơn B mua [variant 9, variant 5]. Nếu khoá theo
     * thứ tự client gửi thì A giữ 5 chờ 9 còn B giữ 9 chờ 5 -> deadlock. Sắp xếp theo id
     * làm mọi transaction khoá cùng một thứ tự, deadlock biến mất.
     *
     * <p>Xem docs/02-luồng-nghiep-vu.md mục 3 và ADR-0003.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM ProductVariant v WHERE v.id IN :ids ORDER BY v.id")
    List<ProductVariant> lockAllByIdOrdered(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT v FROM ProductVariant v
              JOIN FETCH v.product p
              JOIN FETCH p.brand
             WHERE v.id IN :ids
            """)
    List<ProductVariant> findAllWithProduct(@Param("ids") Collection<Long> ids);

    @Query("""
            SELECT v FROM ProductVariant v
             WHERE v.stockQuantity - v.reservedQuantity <= :threshold
             ORDER BY v.stockQuantity - v.reservedQuantity ASC
            """)
    List<ProductVariant> findLowStock(@Param("threshold") int threshold);

    /**
     * Gom khoảng giá + hàng còn bán được cho CẢ trang sản phẩm trong một query.
     * Đây là cách chống N+1 ở endpoint danh sách - xem docs/01-domain-v2.md mục 7.
     */
    @Query("""
            SELECT new dev.techmart.catalog.repository.VariantAggregate(
                       v.product.id, MIN(v.price), MAX(v.price),
                       SUM(v.stockQuantity - v.reservedQuantity))
              FROM ProductVariant v
             WHERE v.product.id IN :productIds
             GROUP BY v.product.id
            """)
    List<VariantAggregate> aggregateByProductIds(@Param("productIds") Collection<Long> productIds);
}
