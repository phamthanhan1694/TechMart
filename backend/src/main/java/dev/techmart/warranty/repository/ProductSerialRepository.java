package dev.techmart.warranty.repository;

import dev.techmart.warranty.domain.ProductSerial;
import dev.techmart.warranty.domain.SerialStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductSerialRepository extends JpaRepository<ProductSerial, Long> {

    @Query("""
            SELECT s FROM ProductSerial s
              JOIN FETCH s.variant v
              JOIN FETCH v.product
             WHERE upper(s.serialNo) = upper(:serialNo)
            """)
    Optional<ProductSerial> findBySerialNo(@Param("serialNo") String serialNo);

    boolean existsBySerialNoIgnoreCase(String serialNo);

    /**
     * Lấy và KHOÁ các serial còn trong kho để gắn cho đơn hàng.
     *
     * <p>{@code SKIP LOCKED} thay vì chờ: hai nhân viên cùng đóng gói hai đơn khác nhau
     * của cùng một sản phẩm thì người thứ hai lấy serial KHÁC chứ không xếp hàng chờ.
     * Serial nào cũng được, miễn là không ai lấy trùng - đúng kiểu bài toán mà
     * SKIP LOCKED sinh ra để giải.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM ProductSerial s
             WHERE s.variant.id = :variantId AND s.status = 'IN_STOCK'
             ORDER BY s.id
            """)
    List<ProductSerial> lockAvailableForVariant(@Param("variantId") Long variantId, Limit limit);

    long countByVariantIdAndStatus(Long variantId, SerialStatus status);

    List<ProductSerial> findByOrderItemId(Long orderItemId);
}
