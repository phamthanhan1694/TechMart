package dev.techmart.admin.repository;

import dev.techmart.admin.dto.AdminDtos.RevenuePoint;
import dev.techmart.admin.dto.AdminDtos.TopProduct;
import dev.techmart.order.domain.OrderStatus;
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
 * Truy van tong hop cho dashboard.
 *
 * <p><b>Nhom theo {@code placed_at} chu khong phai {@code created_at}.</b> {@code created_at}
 * la cot ky thuat do {@code @CreationTimestamp} ghi luc chen dong; {@code placed_at} moi la
 * thoi diem nghiep vu - luc khach dat hang. Dung nham thi bao cao doanh thu cua du lieu
 * nhap tu he thong cu se dồn het vao ngay import. Trieu chung da gap: 400 don rai 90 ngay
 * nhung bieu do chi co dung mot cot.
 *
 * <p><b>Nguyen tac:</b> tinh o DB roi tra ve dung con so can hien thi. Khong bao gio nap
 * hang nghin don hang ve ung dung roi cong bang Java - cach do chay duoc voi 400 don va
 * chet voi 400.000 don, va la loi hay gap nhat o man hinh bao cao.
 *
 * <p>Chi tinh doanh thu tren cac trang thai da thu duoc tien: PAID tro di, khong tinh
 * don CANCELLED hay chua thanh toan.
 */
@Repository
public class DashboardRepository {

    /** Cac trang thai duoc tinh vao doanh thu. */
    private static final String REVENUE_STATUSES = "('PAID','PACKED','SHIPPED','DELIVERED')";

    @PersistenceContext
    private EntityManager em;

    public List<RevenuePoint> revenueByPeriod(String groupBy, Instant from, Instant to) {
        // groupBy da duoc whitelist o tang service - khong bao gio ghep thang chuoi client vao SQL
        String truncUnit = "month".equals(groupBy) ? "month" : "day";

        Query query = em.createNativeQuery("""
                SELECT date_trunc(:unit, o.placed_at)::date AS period,
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
