package dev.techmart.admin.repository;

import dev.techmart.admin.dto.AdminDtos.RevenuePoint;
import dev.techmart.admin.dto.AdminDtos.TopProduct;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.domain.RevenueStatuses;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Truy vấn tổng hợp cho dashboard.
 *
 * <p><b>Nhóm theo {@code placed_at} chứ không phải {@code created_at}.</b> {@code created_at}
 * là cột kỹ thuật do {@code @CreationTimestamp} ghi lúc chèn dòng; {@code placed_at} mới là
 * thời điểm nghiệp vụ - lúc khách đặt hàng. Dùng nhầm thì báo cáo doanh thu của dữ liệu
 * nhập từ hệ thống cũ sẽ dồn hết vào ngày import. Triệu chứng đã gặp: 400 đơn rải 90 ngày
 * nhưng biểu đồ chỉ có đúng một cột.
 *
 * <p><b>Nguyên tắc:</b> tính ở DB rồi trả về đúng con số cần hiển thị. Không bao giờ nạp
 * hàng nghìn đơn hàng về ứng dụng rồi cộng bằng Java - cách đó chạy được với 400 đơn và
 * chết với 400.000 đơn, và là lỗi hay gặp nhất ở màn hình báo cáo.
 *
 * <p>Chỉ tính doanh thu trên các trạng thái đã thu được tiền: PAID trở đi, không tính
 * đơn CANCELLED hay chưa thanh toán.
 */
@Repository
public class DashboardRepository {

    /** Các trạng thái được tính vào doanh thu - dùng chung định nghĩa với trang thống kê. */
    private static final String REVENUE_STATUSES = RevenueStatuses.SQL_IN;

    @PersistenceContext
    private EntityManager em;

    /**
     * Gom theo ngày/tháng của {@code zone} chứ không phải UTC: đơn đặt lúc 01:30 sáng giờ
     * Việt Nam (= 18:30 UTC hôm trước) phải nằm ở cột của hôm nay (ADR-0016).
     */
    public List<RevenuePoint> revenueByPeriod(String groupBy, Instant from, Instant to, java.time.ZoneId zone) {
        // groupBy đã được whitelist ở tầng service - không bao giờ ghép thẳng chuỗi client vào SQL
        String truncUnit = "month".equals(groupBy) ? "month" : "day";

        Query query = em.createNativeQuery("""
                SELECT date_trunc(:unit, o.placed_at AT TIME ZONE :zone)::date AS period,
                       COALESCE(SUM(o.total_amount), 0)      AS revenue,
                       COUNT(*)                              AS order_count
                  FROM orders o
                 WHERE o.status IN %s
                   AND o.placed_at >= :from
                   AND o.placed_at <  :to
                 GROUP BY 1
                 ORDER BY 1
                """.formatted(REVENUE_STATUSES));
        query.setParameter("unit", truncUnit);
        query.setParameter("zone", zone.getId());
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        List<RevenuePoint> points = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            LocalDate period = ((java.sql.Date) row[0]).toLocalDate();
            BigDecimal revenue = (BigDecimal) row[1];
            long count = ((Number) row[2]).longValue();
            BigDecimal average = count == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            points.add(new RevenuePoint(period, revenue, count, average));
        }
        return points;
    }

    public List<TopProduct> topProducts(Instant from, Instant to, int limit) {
        Query query = em.createNativeQuery("""
                SELECT oi.variant_id,
                       oi.product_name,
                       oi.variant_name,
                       oi.sku,
                       SUM(oi.quantity)   AS quantity_sold,
                       SUM(oi.line_total) AS revenue
                  FROM order_items oi
                  JOIN orders o ON o.id = oi.order_id
                 WHERE o.status IN %s
                   AND o.placed_at >= :from
                   AND o.placed_at <  :to
                 GROUP BY oi.variant_id, oi.product_name, oi.variant_name, oi.sku
                 ORDER BY quantity_sold DESC
                 LIMIT :maxRows
                """.formatted(REVENUE_STATUSES));
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        query.setParameter("maxRows", limit);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(r -> new TopProduct(
                        ((Number) r[0]).longValue(), (String) r[1], (String) r[2], (String) r[3],
                        ((Number) r[4]).longValue(), (BigDecimal) r[5]))
                .toList();
    }

    public BigDecimal revenueBetween(Instant from, Instant to) {
        Query query = em.createNativeQuery("""
                SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o
                 WHERE o.status IN %s AND o.placed_at >= :from AND o.placed_at < :to
                """.formatted(REVENUE_STATUSES));
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        return (BigDecimal) query.getSingleResult();
    }

    public long countOrdersBetween(Instant from, Instant to) {
        Query query = em.createNativeQuery("""
                SELECT COUNT(*) FROM orders o
                 WHERE o.status IN %s AND o.placed_at >= :from AND o.placed_at < :to
                """.formatted(REVENUE_STATUSES));
        query.setParameter("from", Timestamp.from(from));
        query.setParameter("to", Timestamp.from(to));
        return ((Number) query.getSingleResult()).longValue();
    }

    public Map<OrderStatus, Long> countByStatus() {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT o.status, COUNT(*) FROM orders o GROUP BY o.status").getResultList();

        Map<OrderStatus, Long> result = new EnumMap<>(OrderStatus.class);
        for (Object[] row : rows) {
            result.put(OrderStatus.valueOf((String) row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }

    public long countLowStockVariants(int threshold) {
        Query query = em.createNativeQuery("""
                SELECT COUNT(*) FROM product_variants v
                 WHERE v.deleted_at IS NULL
                   AND v.stock_quantity - v.reserved_quantity <= :threshold
                """);
        query.setParameter("threshold", threshold);
        return ((Number) query.getSingleResult()).longValue();
    }

    public long countPendingOutbox() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM outbox_events WHERE status = 'PENDING'").getSingleResult()).longValue();
    }

    public long countActiveReservations() {
        return ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM inventory_reservations WHERE status = 'HELD'")
                .getSingleResult()).longValue();
    }

    public static Instant startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
