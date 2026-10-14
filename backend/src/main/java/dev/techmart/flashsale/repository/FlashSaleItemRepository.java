package dev.techmart.flashsale.repository;

import dev.techmart.flashsale.domain.FlashSaleItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, Long> {

    /**
     * Khoá dòng suất bán trước khi ghi so đã bán.
     *
     * <p>Chi nhưng người <b>đã thắng</b> o cửa Redis mọi den được đây, nên so transaction
     * cham vào dòng này bị chặn trên boi {@code sale_quantity} - vai chuc, không phải vài nghìn.
     * Redis là <i>cua</i>, DB vẫn là <i>so cai</i>.
     *
     * <p>Không fetch variant ở đây: để nó ở dạng proxy cho VariantLocker nạp - xem
     * {@link dev.techmart.inventory.service.VariantLocker}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM FlashSaleItem i WHERE i.id = :id")
    Optional<FlashSaleItem> lockById(@Param("id") Long id);

    @Query("""
            SELECT i FROM FlashSaleItem i
              JOIN FETCH i.flashSale
              JOIN FETCH i.variant v
              JOIN FETCH v.product
             WHERE i.id = :id
            """)
    Optional<FlashSaleItem> findDetailById(@Param("id") Long id);

    List<FlashSaleItem> findByFlashSaleId(Long flashSaleId);
}
