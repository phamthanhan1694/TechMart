package dev.techmart.promotion.repository;

import dev.techmart.promotion.domain.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    /**
     * Khoá dòng coupon trước khi tru luot.
     *
     * <p>Giong het luồng tồn kho: <b>không được nạp coupon vào persistence context trước</b>
     * khi gọi method nay, nếu không Hibernate sẽ ném StaleObjectStateException ngay tai
     * câu query khoá (xem {@link dev.techmart.inventory.service.VariantLocker}).
     * Vì vậy luồng đặt hàng chỉ truyen xuong đây chuỗi ma, không truyen entity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE upper(c.code) = upper(:code)")
    Optional<Coupon> lockByCode(@Param("code") String code);
}
