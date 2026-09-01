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
     * Lay va KHOA cac serial con trong kho de gan cho don hang.
     *
     * <p>{@code SKIP LOCKED} thay vi cho: hai nhan vien cung dong goi hai don khac nhau
     * cua cung mot san pham thi nguoi thu hai lay serial KHAC chu khong xep hang cho.
     * Serial nao cung duoc, mien la khong ai lay trung - dung kieu bai toan ma
     * SKIP LOCKED sinh ra de giai.
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
