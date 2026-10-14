package dev.techmart.analytics;

import dev.techmart.analytics.AnalyticsDtos.*;
import dev.techmart.analytics.AnalyticsRepository.OrderTotals;
import dev.techmart.analytics.AnalyticsRepository.RawBreakdown;
import dev.techmart.analytics.AnalyticsRepository.RawFunnel;
import dev.techmart.analytics.AnalyticsRepository.StockTotals;
import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Thống kê cho ADMIN.
 *
 * <p>Không cache (ADR-0011). Người xem trang này là vài quản trị viên, không phải hàng
 * nghìn khách - tải thấp, còn cache thì làm "doanh thu hôm nay" đứng yên vài phút ngay sau
 * khi có đơn mới, đúng lúc người ta mở trang để xem. Khi trang này chậm, bước tiếp theo là
 * bảng rollup theo ngày, không phải cache.
 */
@Service
public class AnalyticsService {

    /** Whitelist: giá trị từ client không bao giờ được ghép thẳng vào date_trunc. */
    private static final Set<String> UNITS = Set.of("day", "week", "month");
    private static final Set<String> DIMENSIONS = Set.of("category", "brand", "province");
    private static final String OTHER_KEY = "__other__";

    private final AnalyticsRepository repo;
    private final Clock clock;
    private final ZoneId zone;
    private final int maxRangeDays;

    public AnalyticsService(AnalyticsRepository repo, Clock clock, AppProperties props) {
        this.repo = repo;
        this.clock = clock;
        this.zone = props.reporting().zone();
        this.maxRangeDays = props.reporting().maxRangeDays();
    }

    /** Public vì được gọi qua proxy của Spring từ OrderCsvExporter. */
    public ReportRange range(LocalDate from, LocalDate to) {
        return ReportRange.resolve(from, to, zone, clock, maxRangeDays);
    }

    // =================================================================== tổng quan

    @Transactional(readOnly = true)
    public Overview overview(LocalDate from, LocalDate to) {
        ReportRange cur = range(from, to);
        ReportRange prev = cur.previous();

        OrderTotals c = repo.orderTotals(cur);
        OrderTotals p = repo.orderTotals(prev);
        long returningCur = repo.returningBuyers(cur);
        long returningPrev = repo.returningBuyers(prev);

        return new Overview(cur.toDto(), prev.toDto(),
                kpi(c.revenue(), p.revenue()),
                kpi(c.paid(), p.paid()),
                kpi(average(c.revenue(), c.paid()), average(p.revenue(), p.paid())),
                kpi(c.units(), p.units()),
                kpi(c.placed(), p.placed()),
                kpi(percent(c.cancelled(), c.placed()), percent(p.cancelled(), p.placed())),
                kpi(c.discount(), p.discount()),
                kpi(repo.newCustomers(cur), repo.newCustomers(prev)),
                kpi(c.buyers(), p.buyers()),
                kpi(percent(returningCur, c.buyers()), percent(returningPrev, p.buyers())));
    }

    // =================================================================== chuỗi thời gian

    @Transactional(readOnly = true)
    public TimeSeries series(LocalDate from, LocalDate to, String groupBy) {
        String unit = UNITS.contains(groupBy) ? groupBy : "day";
        ReportRange cur = range(from, to);
        return new TimeSeries(unit, cur.toDto(), repo.series(cur, unit), repo.series(cur.previous(), unit));
    }

    // =================================================================== cơ cấu

    /**
     * Cơ cấu doanh số theo một chiều.
     *
     * <p>Quá {@code limit} dòng thì gộp phần đuôi thành "Khác" chứ không cắt bỏ: cắt bỏ làm
     * tổng tỉ trọng không còn là 100%, và biểu đồ ngầm nói dối về phần còn lại. Cũng không
     * sinh thêm màu cho dòng thứ 9, 10... - quá tám màu thì mắt không phân biệt được nữa.
     */
    @Transactional(readOnly = true)
    public Breakdown breakdown(String dimension, LocalDate from, LocalDate to, int limit) {
        if (!DIMENSIONS.contains(dimension)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "dimension phai la mot trong " + DIMENSIONS);
        }
        ReportRange range = range(from, to);
        List<RawBreakdown> raw = switch (dimension) {
            case "brand" -> repo.salesByBrand(range);
            case "province" -> repo.revenueByProvince(range);
            default -> repo.salesByCategory(range);
        };
        int cap = Math.min(Math.max(limit, 3), 20);
        BigDecimal total = raw.stream().map(RawBreakdown::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<BreakdownRow> rows = new ArrayList<>();
        int keep = raw.size() > cap ? cap - 1 : raw.size();
        for (int i = 0; i < keep; i++) {
            RawBreakdown r = raw.get(i);
            rows.add(new BreakdownRow(r.key(), r.label(), r.revenue(), r.orders(), r.units(), share(r.revenue(), total)));
        }
        if (raw.size() > keep) {
            List<RawBreakdown> tail = raw.subList(keep, raw.size());
            BigDecimal tailRevenue = tail.stream().map(RawBreakdown::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            // Số đơn của nhóm gộp là cộng dồn: một đơn có hàng thuộc hai nhóm đuôi bị đếm hai lần
            rows.add(new BreakdownRow(OTHER_KEY, "Khác (" + tail.size() + ")", tailRevenue,
                    tail.stream().mapToLong(RawBreakdown::orders).sum(),
                    tail.stream().mapToLong(RawBreakdown::units).sum(),
                    share(tailRevenue, total)));
        }
        String measure = "province".equals(dimension) ? "ORDER_TOTAL" : "LINE_TOTAL";
        return new Breakdown(dimension, measure, total, rows);
    }

    // =================================================================== phễu, nhiệt, khách

    @Transactional(readOnly = true)
    public Funnel funnel(LocalDate from, LocalDate to) {
        ReportRange range = range(from, to);
        RawFunnel f = repo.funnel(range);
        List<FunnelStage> stages = List.of(
                new FunnelStage("PLACED", f.placed(), percent(f.placed(), f.placed())),
                new FunnelStage("PAID", f.paid(), percent(f.paid(), f.placed())),
                new FunnelStage("PACKED", f.packed(), percent(f.packed(), f.placed())),
                new FunnelStage("SHIPPED", f.shipped(), percent(f.shipped(), f.placed())),
                new FunnelStage("DELIVERED", f.delivered(), percent(f.delivered(), f.placed())));
        return new Funnel(range.toDto(), stages, f.cancelled(), f.refunded(), f.pending(),
                f.medianHoursToPay(), f.medianHoursToDeliver());
    }

    @Transactional(readOnly = true)
    public Heatmap heatmap(LocalDate from, LocalDate to) {
        ReportRange range = range(from, to);
        return new Heatmap(range.toDto(), zone.getId(), repo.heatmap(range));
    }

    @Transactional(readOnly = true)
    public CustomerStats customers(LocalDate from, LocalDate to, int limit) {
        ReportRange range = range(from, to);
        OrderTotals totals = repo.orderTotals(range);
        long returning = repo.returningBuyers(range);
        return new CustomerStats(range.toDto(), repo.newCustomers(range), totals.buyers(), returning,
                percent(returning, totals.buyers()),
                repo.topCustomers(range, Math.min(Math.max(limit, 1), 50)));
    }

    @Transactional(readOnly = true)
    public List<PaymentMethodStats> payments(LocalDate from, LocalDate to) {
        return repo.paymentStats(range(from, to));
    }

    @Transactional(readOnly = true)
    public CouponStats coupons(LocalDate from, LocalDate to) {
        ReportRange range = range(from, to);
        List<CouponRow> rows = repo.couponRows(range);
        long paid = repo.orderTotals(range).paid();
        long withCoupon = rows.stream().mapToLong(CouponRow::orders).sum();
        BigDecimal discount = rows.stream().map(CouponRow::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CouponStats(range.toDto(), paid, withCoupon, percent(withCoupon, paid), discount, rows);
    }

    /** Ảnh chụp tồn kho tại thời điểm gọi - không có khoảng ngày vì kho không có lịch sử số dư. */
    @Transactional(readOnly = true)
    public InventoryStats inventory(int lowThreshold, int slowDays) {
        int low = Math.min(Math.max(lowThreshold, 0), 1000);
        int days = Math.min(Math.max(slowDays, 7), 365);
        StockTotals t = repo.stockTotals(low);
        Timestamp since = Timestamp.from(clock.instant().minus(days, ChronoUnit.DAYS));
        return new InventoryStats(t.outOfStock(), t.low(), t.healthy(), t.units(), t.reserved(), t.value(),
                low, days, repo.stockByCategory(), repo.slowMovers(since, 10));
    }

    @Transactional(readOnly = true)
    public ReviewStats reviews(LocalDate from, LocalDate to) {
        ReportRange range = range(from, to);
        Map<Integer, Long> distribution = repo.ratingDistribution(range);
        long total = distribution.values().stream().mapToLong(Long::longValue).sum();
        long weighted = distribution.entrySet().stream().mapToLong(e -> e.getKey() * e.getValue()).sum();
        BigDecimal average = total == 0 ? null
                : BigDecimal.valueOf(weighted).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
        return new ReviewStats(range.toDto(), total, average, distribution, repo.lowestRated(range, 3, 5));
    }

    // =================================================================== phép tính

    /** Phần trăm, 1 chữ số thập phân. Mẫu bằng 0 trả null - "0%" sẽ là một con số bịa. */
    static BigDecimal percent(long part, long whole) {
        if (whole == 0) return null;
        return BigDecimal.valueOf(part * 100.0 / whole).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal share(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) return BigDecimal.ZERO;
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal average(BigDecimal sum, long count) {
        return count == 0 ? BigDecimal.ZERO : sum.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
    }

    private static Kpi kpi(long current, long previous) {
        return kpi(BigDecimal.valueOf(current), BigDecimal.valueOf(previous));
    }

    private static Kpi kpi(BigDecimal current, BigDecimal previous) {
        BigDecimal change = null;
        if (current != null && previous != null && previous.signum() != 0) {
            change = current.subtract(previous)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(previous.abs(), 1, RoundingMode.HALF_UP);
        }
        return new Kpi(current, previous, change);
    }
}
