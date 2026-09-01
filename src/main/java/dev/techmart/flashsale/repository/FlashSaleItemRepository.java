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
     * Khoa dong suat ban truoc khi ghi so da ban.
     *
     * <p>Chi nhung nguoi <b>da thang</b> o cua Redis moi den duoc day, nen so transaction
     * cham vao dong nay bi chan tren boi {@code sale_quantity} - vai chuc, khong phai vai nghin.
     * Redis la <i>cua</i>, DB van la <i>so cai</i>.
     *
     * <p>Khong fetch variant o day: de no o dang proxy cho VariantLocker nap - xem
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
