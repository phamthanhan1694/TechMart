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
     * Khoa bi quan cac variant truoc khi tru ton kho.
     *
     * <p><b>ORDER BY v.id la chi tiet quan trong nhat cua ca phuong thuc nay.</b>
     * Don A mua [variant 5, variant 9], don B mua [variant 9, variant 5]. Neu khoa theo
     * thu tu client gui thi A giu 5 cho 9 con B giu 9 cho 5 -> deadlock. Sap xep theo id
     * lam moi transaction khoa cung mot thu tu, deadlock bien mat.
     *
     * <p>Xem docs/02-luong-nghiep-vu.md muc 3 va ADR-0003.
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
     * Gom khoang gia + hang con ban duoc cho CA trang san pham trong mot query.
     * Day la cach chong N+1 o endpoint danh sach - xem docs/01-domain-v2.md muc 7.
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
