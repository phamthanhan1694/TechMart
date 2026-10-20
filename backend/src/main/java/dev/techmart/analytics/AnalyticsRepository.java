package dev.techmart.analytics;

import dev.techmart.analytics.AnalyticsDtos.*;
import dev.techmart.order.domain.RevenueStatuses;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.*;

/**
 * Truy vấn tổng hợp cho trang thống kê - toàn bộ bằng SQL thuần qua JDBC.
 *
 * <p><b>Vì sao JDBC chứ không JPA:</b> kết quả là con số và bảng phẳng, không phải entity.
 * JPQL không có {@code FILTER}, {@code generate_series}, {@code percentile_cont},
 * {@code AT TIME ZONE} hay toán tử JSONB - những thứ làm nên các báo cáo này. Đọc cột theo
 * tên với {@code ResultSet} cũng an toàn hơn {@code Object[]} theo chỉ số như DashboardRepository.
 *
 * <p><b>Tính trực tiếp trên bảng giao dịch, không có bảng tổng hợp sẵn</b> (ADR-0011). Mọi
 * truy vấn lọc theo {@code placed_at} nên dùng được {@code idx_orders_placed_at}; ở quy mô
 * vài trăm nghìn đơn mỗi truy vấn vài chục ms. Điều kiện để đổi sang bảng rollup theo ngày
 * ghi trong ADR.
 *
 * <p>Chuỗi {@code IN (...)} của trạng thái doanh thu là hằng số trong code
 * ({@link RevenueStatuses#SQL_IN}), được ghép vào SQL. Không có giá trị nào từ client đi vào
 * SQL mà không qua tham số ràng buộc hoặc whitelist.
 */
@Repository
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AnalyticsRepository {

    private static final String R = RevenueStatuses.SQL_IN;

    private final NamedParameterJdbcTemplate jdbc;

    // =================================================================== tổng quan

    record OrderTotals(long placed, long paid, long cancelled, BigDecimal revenue, BigDecimal discount,
                       long buyers, long units) {}

    OrderTotals orderTotals(ReportRange range) {
        return jdbc.queryForObject("""
                SELECT COUNT(*)                                                   AS placed,
                       COUNT(*) FILTER (WHERE o.status IN %1$s)                   AS paid,
                       COUNT(*) FILTER (WHERE o.status = 'CANCELLED')             AS cancelled,
                       COALESCE(SUM(o.total_amount)    FILTER (WHERE o.status IN %1$s), 0) AS revenue,
                       COALESCE(SUM(o.discount_amount) FILTER (WHERE o.status IN %1$s), 0) AS discount,
                       COUNT(DISTINCT o.user_id) FILTER (WHERE o.status IN %1$s)  AS buyers,
                       COALESCE((SELECT SUM(oi.quantity)
                                   FROM order_items oi JOIN orders x ON x.id = oi.order_id
                                  WHERE x.status IN %1$s
                                    AND x.placed_at >= :start AND x.placed_at < :end), 0) AS units
                  FROM orders o
                 WHERE o.placed_at >= :start AND o.placed_at < :end
                """.formatted(R), params(range), (rs, i) -> new OrderTotals(
                rs.getLong("placed"), rs.getLong("paid"), rs.getLong("cancelled"),
                rs.getBigDecimal("revenue"), rs.getBigDecimal("discount"),
                rs.getLong("buyers"), rs.getLong("units")));
    }

    long newCustomers(ReportRange range) {
        return count("""
                SELECT COUNT(*) FROM users
                 WHERE role = 'CUSTOMER' AND created_at >= :start AND created_at < :end
                """, range);
    }

    /** Người mua trong kỳ mà đã từng có đơn được tính doanh thu TRƯỚC kỳ. */
    long returningBuyers(ReportRange range) {
        return count("""
                SELECT COUNT(DISTINCT o.user_id)
                  FROM orders o
                 WHERE o.status IN %1$s
                   AND o.placed_at >= :start AND o.placed_at < :end
                   AND EXISTS (SELECT 1 FROM orders p
                                WHERE p.user_id = o.user_id
                                  AND p.status IN %1$s
                                  AND p.placed_at < :start)
                """.formatted(R), range);
    }

    // =================================================================== chuỗi thời gian

    /**
     * Chuỗi theo ngày/tuần/tháng, <b>đủ mọi mốc kể cả mốc bằng 0</b>.
     *
     * <p>{@code generate_series} sinh khung mốc rồi LEFT JOIN số liệu vào. Chỉ GROUP BY thì ngày
     * không có đơn sẽ biến mất khỏi kết quả, và biểu đồ nối thẳng hai ngày hai bên - vẽ ra
     * một xu hướng không có thật. Đây là lỗi của bản dashboard đầu tiên.
     */
    List<SeriesPoint> series(ReportRange range, String unit) {
        MapSqlParameterSource p = params(range)
                .addValue("unit", unit)
                .addValue("step", "1 " + unit)
                .addValue("fromLocal", range.from().atStartOfDay())
                .addValue("toLocal", range.to().atStartOfDay());

        return jdbc.query("""
                WITH buckets AS (
                    SELECT generate_series(date_trunc(:unit, CAST(:fromLocal AS timestamp)),
                                           CAST(:toLocal AS timestamp),
                                           CAST(:step AS interval)) AS bucket
                ), o AS (
                    SELECT date_trunc(:unit, placed_at AT TIME ZONE :zone)                  AS bucket,
                           COALESCE(SUM(total_amount) FILTER (WHERE status IN %1$s), 0)    AS revenue,
                           COUNT(*) FILTER (WHERE status IN %1$s)                           AS paid_orders,
                           COUNT(*)                                                          AS placed_orders,
                           COUNT(*) FILTER (WHERE status = 'CANCELLED')                     AS cancelled_orders
                      FROM orders
                     WHERE placed_at >= :start AND placed_at < :end
                     GROUP BY 1
                ), u AS (
                    SELECT date_trunc(:unit, created_at AT TIME ZONE :zone) AS bucket,
                           COUNT(*)                                          AS new_customers
                      FROM users
                     WHERE role = 'CUSTOMER' AND created_at >= :start AND created_at < :end
                     GROUP BY 1
                )
                SELECT CAST(b.bucket AS date)             AS period,
                       COALESCE(o.revenue, 0)             AS revenue,
                       COALESCE(o.paid_orders, 0)         AS paid_orders,
                       COALESCE(o.placed_orders, 0)       AS placed_orders,
                       COALESCE(o.cancelled_orders, 0)    AS cancelled_orders,
                       COALESCE(u.new_customers, 0)       AS new_customers
                  FROM buckets b
                  LEFT JOIN o ON o.bucket = b.bucket
                  LEFT JOIN u ON u.bucket = b.bucket
                 ORDER BY b.bucket
                """.formatted(R), p, (rs, i) -> new SeriesPoint(
                rs.getObject("period", LocalDate.class),
                rs.getBigDecimal("revenue"),
                rs.getLong("paid_orders"),
                rs.getLong("placed_orders"),
                rs.getLong("cancelled_orders"),
                rs.getLong("new_customers")));
    }

    // =================================================================== cơ cấu

    record RawBreakdown(String key, String label, BigDecimal revenue, long orders, long units) {}

    /**
     * Doanh số theo danh mục/thương hiệu lấy từ {@code order_items.line_total}.
     *
     * <p>Đánh đổi thừa nhận: order_items chụp lại tên và giá nhưng KHÔNG chụp danh mục/thương
     * hiệu - nên ở đây phải join sang sản phẩm hiện tại. Chuyển một sản phẩm sang danh mục
     * khác thì doanh số lịch sử của nó chuyển theo. Muốn báo cáo lịch sử bất biến thì phải chụp
     * thêm {@code category_id} vào order_items lúc đặt hàng.
     */
    List<RawBreakdown> salesByCategory(ReportRange range) {
        return jdbc.query("""
                SELECT CAST(c.id AS text)                                       AS key,
                       CASE WHEN pc.name IS NULL THEN c.name ELSE pc.name || ' / ' || c.name END AS label,
                       SUM(oi.line_total)                                        AS revenue,
                       COUNT(DISTINCT o.id)                                      AS orders,
                       SUM(oi.quantity)                                          AS units
                  FROM order_items oi
                  JOIN orders o           ON o.id = oi.order_id
                  JOIN product_variants v ON v.id = oi.variant_id
                  JOIN products p         ON p.id = v.product_id
                  JOIN categories c       ON c.id = p.category_id
                  LEFT JOIN categories pc ON pc.id = c.parent_id
                 WHERE o.status IN %s
                   AND o.placed_at >= :start AND o.placed_at < :end
                 GROUP BY c.id, c.name, pc.name
                 ORDER BY revenue DESC
                """.formatted(R), params(range), AnalyticsRepository::rawBreakdown);
    }

    List<RawBreakdown> salesByBrand(ReportRange range) {
        return jdbc.query("""
                SELECT CAST(b.id AS text)     AS key,
                       b.name                 AS label,
                       SUM(oi.line_total)     AS revenue,
                       COUNT(DISTINCT o.id)   AS orders,
                       SUM(oi.quantity)       AS units
                  FROM order_items oi
                  JOIN orders o           ON o.id = oi.order_id
                  JOIN product_variants v ON v.id = oi.variant_id
                  JOIN products p         ON p.id = v.product_id
                  JOIN brands b           ON b.id = p.brand_id
                 WHERE o.status IN %s
                   AND o.placed_at >= :start AND o.placed_at < :end
                 GROUP BY b.id, b.name
                 ORDER BY revenue DESC
                """.formatted(R), params(range), AnalyticsRepository::rawBreakdown);
    }

    /** Tỉnh/thành đọc thẳng từ bản chụp địa chỉ JSONB của đơn - đúng nơi hàng đã được giao tới. */
    List<RawBreakdown> revenueByProvince(ReportRange range) {
        return jdbc.query("""
                SELECT COALESCE(NULLIF(TRIM(o.shipping_address->>'province'), ''), 'Không rõ') AS key,
                       COALESCE(NULLIF(TRIM(o.shipping_address->>'province'), ''), 'Không rõ') AS label,
                       SUM(o.total_amount)                AS revenue,
                       COUNT(*)                           AS orders,
                       COALESCE(SUM(u.units), 0)          AS units
                  FROM orders o
                  LEFT JOIN LATERAL (SELECT SUM(quantity) AS units
                                       FROM order_items WHERE order_id = o.id) u ON TRUE
                 WHERE o.status IN %s
                   AND o.placed_at >= :start AND o.placed_at < :end
                 GROUP BY 1, 2
                 ORDER BY revenue DESC
                """.formatted(R), params(range), AnalyticsRepository::rawBreakdown);
    }

    // =================================================================== phễu đơn hàng

    record RawFunnel(long placed, long paid, long packed, long shipped, long delivered,
                     long cancelled, long refunded, long pending,
                     BigDecimal medianHoursToPay, BigDecimal medianHoursToDeliver) {}

    /**
     * Phễu dựng từ {@code order_status_history}, không phải từ trạng thái hiện tại.
     *
     * <p>Trạng thái hiện tại chỉ nói đơn đang ở đâu: đơn REFUNDED có thể đã qua DELIVERED hoặc
     * chưa từng rời PAID. Lịch sử mới trả lời được "bao nhiêu đơn đã TỪNG tới bước này".
     */
    RawFunnel funnel(ReportRange range) {
        return jdbc.queryForObject("""
                WITH reached AS (
                    SELECT o.id, o.status, o.placed_at, o.delivered_at,
                           COALESCE(bool_or(h.to_status = 'PAID'), FALSE)      AS paid,
                           COALESCE(bool_or(h.to_status = 'PACKED'), FALSE)    AS packed,
                           COALESCE(bool_or(h.to_status = 'SHIPPED'), FALSE)   AS shipped,
                           COALESCE(bool_or(h.to_status = 'DELIVERED'), FALSE) AS delivered,
                           MIN(h.created_at) FILTER (WHERE h.to_status = 'PAID') AS paid_at
                      FROM orders o
                      LEFT JOIN order_status_history h ON h.order_id = o.id
                     WHERE o.placed_at >= :start AND o.placed_at < :end
                     GROUP BY o.id
                )
                SELECT COUNT(*)                                        AS placed,
                       COUNT(*) FILTER (WHERE paid)                    AS paid,
                       COUNT(*) FILTER (WHERE packed)                  AS packed,
                       COUNT(*) FILTER (WHERE shipped)                 AS shipped,
                       COUNT(*) FILTER (WHERE delivered)               AS delivered,
                       COUNT(*) FILTER (WHERE status = 'CANCELLED')    AS cancelled,
                       COUNT(*) FILTER (WHERE status = 'REFUNDED')     AS refunded,
                       COUNT(*) FILTER (WHERE status = 'PENDING_PAYMENT') AS pending,
                       percentile_cont(0.5) WITHIN GROUP
                           (ORDER BY CAST(EXTRACT(EPOCH FROM paid_at - placed_at) AS double precision) / 3600)      AS median_pay,
                       percentile_cont(0.5) WITHIN GROUP
                           (ORDER BY CAST(EXTRACT(EPOCH FROM delivered_at - placed_at) AS double precision) / 3600) AS median_deliver
                  FROM reached
                """, params(range), (rs, i) -> new RawFunnel(
                rs.getLong("placed"), rs.getLong("paid"), rs.getLong("packed"), rs.getLong("shipped"),
                rs.getLong("delivered"), rs.getLong("cancelled"), rs.getLong("refunded"), rs.getLong("pending"),
                decimalOrNull(rs, "median_pay"), decimalOrNull(rs, "median_deliver")));
    }

    // =================================================================== bản đồ nhiệt

    List<HeatCell> heatmap(ReportRange range) {
        return jdbc.query("""
                SELECT CAST(EXTRACT(ISODOW FROM placed_at AT TIME ZONE :zone) AS int) AS dow,
                       CAST(EXTRACT(HOUR   FROM placed_at AT TIME ZONE :zone) AS int) AS hr,
                       COUNT(*)            AS orders,
                       SUM(total_amount)   AS revenue
                  FROM orders
                 WHERE status IN %s
                   AND placed_at >= :start AND placed_at < :end
                 GROUP BY 1, 2
                 ORDER BY 1, 2
                """.formatted(R), params(range), (rs, i) -> new HeatCell(
                rs.getInt("dow"), rs.getInt("hr"), rs.getLong("orders"), rs.getBigDecimal("revenue")));
    }

    // =================================================================== khách hàng

    List<TopCustomer> topCustomers(ReportRange range, int limit) {
        return jdbc.query("""
                SELECT u.id, u.email, u.full_name,
                       COUNT(*)            AS orders,
                       SUM(o.total_amount) AS revenue
                  FROM orders o
                  JOIN users u ON u.id = o.user_id
                 WHERE o.status IN %s
                   AND o.placed_at >= :start AND o.placed_at < :end
                 GROUP BY u.id, u.email, u.full_name
                 ORDER BY revenue DESC, u.id
                 LIMIT :limit
                """.formatted(R), params(range).addValue("limit", limit), (rs, i) -> new TopCustomer(
                rs.getLong("id"), rs.getString("email"), rs.getString("full_name"),
                rs.getLong("orders"), rs.getBigDecimal("revenue")));
    }

    // =================================================================== thanh toán

    /**
     * Theo NGÀY TẠO giao dịch chứ không theo ngày đặt đơn: câu hỏi ở đây là "cổng thanh toán
     * hoạt động thế nào trong kỳ", kể cả lần thử thất bại của đơn đặt từ kỳ trước.
     */
    List<PaymentMethodStats> paymentStats(ReportRange range) {
        return jdbc.query("""
                SELECT p.method,
                       COUNT(*)                                                     AS attempts,
                       COUNT(*) FILTER (WHERE p.status IN ('SUCCEEDED','REFUNDED'))  AS succeeded,
                       COUNT(*) FILTER (WHERE p.status = 'FAILED')                  AS failed,
                       COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'SUCCEEDED'), 0) AS amount
                  FROM payments p
                 WHERE p.created_at >= :start AND p.created_at < :end
                 GROUP BY p.method
                 ORDER BY amount DESC
                """, params(range), (rs, i) -> {
            long attempts = rs.getLong("attempts");
            long succeeded = rs.getLong("succeeded");
            long failed = rs.getLong("failed");
            // Chia trên số lần đã có kết quả: giao dịch INITIATED chưa biết thành hay bại
            long decided = succeeded + failed;
            return new PaymentMethodStats(rs.getString("method"), attempts, succeeded, failed,
                    AnalyticsService.percent(succeeded, decided), rs.getBigDecimal("amount"));
        });
    }

    // =================================================================== mã giảm giá

    List<CouponRow> couponRows(ReportRange range) {
        return jdbc.query("""
                SELECT o.coupon_code          AS code,
                       COUNT(*)               AS orders,
                       SUM(o.discount_amount) AS discount,
                       SUM(o.total_amount)    AS revenue
                  FROM orders o
                 WHERE o.status IN %s
                   AND o.coupon_code IS NOT NULL
                   AND o.placed_at >= :start AND o.placed_at < :end
                 GROUP BY o.coupon_code
                 ORDER BY orders DESC, discount DESC
                """.formatted(R), params(range), (rs, i) -> new CouponRow(
                rs.getString("code"), rs.getLong("orders"),
                rs.getBigDecimal("discount"), rs.getBigDecimal("revenue")));
    }

    // =================================================================== tồn kho

    record StockTotals(long outOfStock, long low, long healthy, long units, long reserved, BigDecimal value) {}

    StockTotals stockTotals(int lowThreshold) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FILTER (WHERE avail <= 0)                     AS out_of_stock,
                       COUNT(*) FILTER (WHERE avail > 0 AND avail <= :low)    AS low,
                       COUNT(*) FILTER (WHERE avail > :low)                   AS healthy,
                       COALESCE(SUM(stock), 0)                                AS units,
                       COALESCE(SUM(reserved), 0)                             AS reserved,
                       COALESCE(SUM(stock * price), 0)                        AS value
                  FROM (SELECT v.stock_quantity AS stock, v.reserved_quantity AS reserved,
                               v.stock_quantity - v.reserved_quantity AS avail, v.price
                          FROM product_variants v
                          JOIN products p ON p.id = v.product_id
                         WHERE v.deleted_at IS NULL AND p.deleted_at IS NULL) x
                """, Map.of("low", lowThreshold), (rs, i) -> new StockTotals(
                rs.getLong("out_of_stock"), rs.getLong("low"), rs.getLong("healthy"),
                rs.getLong("units"), rs.getLong("reserved"), rs.getBigDecimal("value")));
    }

    List<StockByCategory> stockByCategory() {
        return jdbc.query("""
                SELECT COALESCE(pc.name, c.name)            AS label,
                       SUM(v.stock_quantity)                AS units,
                       SUM(v.stock_quantity * v.price)      AS value
                  FROM product_variants v
                  JOIN products p         ON p.id = v.product_id
                  JOIN categories c       ON c.id = p.category_id
                  LEFT JOIN categories pc ON pc.id = c.parent_id
                 WHERE v.deleted_at IS NULL AND p.deleted_at IS NULL
                 GROUP BY 1
                 ORDER BY value DESC
                """, Map.of(), (rs, i) -> new StockByCategory(
                rs.getString("label"), rs.getLong("units"), rs.getBigDecimal("value")));
    }

    /** Hàng còn tồn mà không bán được chiếc nào trong N ngày - vốn đang nằm chết trong kho. */
    List<SlowMover> slowMovers(Timestamp since, int limit) {
        return jdbc.query("""
                SELECT v.id, v.sku, p.name AS product_name, v.name AS variant_name,
                       v.stock_quantity - v.reserved_quantity AS available,
                       v.stock_quantity * v.price             AS stock_value,
                       ls.last_sold
                  FROM product_variants v
                  JOIN products p ON p.id = v.product_id
                  LEFT JOIN LATERAL (
                        SELECT MAX(o.placed_at) AS last_sold
                          FROM order_items oi JOIN orders o ON o.id = oi.order_id
                         WHERE oi.variant_id = v.id AND o.status IN %s
                       ) ls ON TRUE
                 WHERE v.deleted_at IS NULL AND p.deleted_at IS NULL
                   AND v.stock_quantity > 0
                   AND (ls.last_sold IS NULL OR ls.last_sold < :since)
                 ORDER BY stock_value DESC, v.id
                 LIMIT :limit
                """.formatted(R), new MapSqlParameterSource().addValue("since", since).addValue("limit", limit),
                (rs, i) -> {
                    Timestamp last = rs.getTimestamp("last_sold");
                    return new SlowMover(rs.getLong("id"), rs.getString("sku"), rs.getString("product_name"),
                            rs.getString("variant_name"), rs.getInt("available"), rs.getBigDecimal("stock_value"),
                            last != null ? last.toInstant() : null);
                });
    }

    // =================================================================== đánh giá

    Map<Integer, Long> ratingDistribution(ReportRange range) {
        Map<Integer, Long> result = new TreeMap<>();
        for (int star = 1; star <= 5; star++) result.put(star, 0L);   // đủ 5 mức, kể cả mức 0
        jdbc.query("""
                SELECT rating, COUNT(*) AS n FROM reviews
                 WHERE created_at >= :start AND created_at < :end
                 GROUP BY rating
                """, params(range), rs -> {
            result.put(rs.getInt("rating"), rs.getLong("n"));
        });
        return result;
    }

    List<LowRatedProduct> lowestRated(ReportRange range, int minReviews, int limit) {
        return jdbc.query("""
                SELECT p.id, p.name, p.slug,
                       ROUND(AVG(r.rating), 2) AS average,
                       COUNT(*)                AS reviews
                  FROM reviews r
                  JOIN products p ON p.id = r.product_id
                 WHERE r.created_at >= :start AND r.created_at < :end
                 GROUP BY p.id, p.name, p.slug
                HAVING COUNT(*) >= :minReviews
                 ORDER BY average ASC, reviews DESC
                 LIMIT :limit
                """, params(range).addValue("minReviews", minReviews).addValue("limit", limit),
                (rs, i) -> new LowRatedProduct(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                        rs.getBigDecimal("average"), rs.getLong("reviews")));
    }

    // =================================================================== tiện ích

    private MapSqlParameterSource params(ReportRange range) {
        return new MapSqlParameterSource()
                .addValue("start", range.start())
                .addValue("end", range.end())
                .addValue("zone", range.zone().getId());
    }

    private long count(String sql, ReportRange range) {
        Long n = jdbc.queryForObject(sql, params(range), Long.class);
        return n != null ? n : 0L;
    }

    private static RawBreakdown rawBreakdown(ResultSet rs, int i) throws SQLException {
        return new RawBreakdown(rs.getString("key"), rs.getString("label"), rs.getBigDecimal("revenue"),
                rs.getLong("orders"), rs.getLong("units"));
    }

    private static BigDecimal decimalOrNull(ResultSet rs, String column) throws SQLException {
        double v = rs.getDouble(column);
        return rs.wasNull() ? null : BigDecimal.valueOf(v).setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
