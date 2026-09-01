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
     * Khoa dong coupon truoc khi tru luot.
     *
     * <p>Giong het luong ton kho: <b>khong duoc nap coupon vao persistence context truoc</b>
     * khi goi method nay, neu khong Hibernate se nem StaleObjectStateException ngay tai
     * cau query khoa (xem {@link dev.techmart.inventory.service.VariantLocker}).
     * Vi vay luong dat hang chi truyen xuong day chuoi ma, khong truyen entity.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Coupon c WHERE upper(c.code) = upper(:code)")
    Optional<Coupon> lockByCode(@Param("code") String code);
}
