package dev.techmart.promotion.repository;

import dev.techmart.promotion.domain.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    @Query("""
            SELECT COUNT(r) FROM CouponRedemption r
             WHERE r.coupon.id = :couponId AND r.user.id = :userId
            """)
    long countByCouponAndUser(@Param("couponId") Long couponId, @Param("userId") Long userId);

    List<CouponRedemption> findByOrderId(Long orderId);
}
