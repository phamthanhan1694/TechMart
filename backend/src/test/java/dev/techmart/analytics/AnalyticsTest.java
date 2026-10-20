package dev.techmart.analytics;

import dev.techmart.analytics.AnalyticsDtos.*;
import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.repository.AuditLogRepository;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.User;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Thống kê cho admin - kiểm những chỗ báo cáo hay sai nhất mà nhìn biểu đồ không phát hiện ra:
 * ngày trống bị mất, ranh giới ngày theo UTC, đơn huỷ lọt vào doanh thu, tỉ trọng không cộng
 * đủ 100%, và phễu đọc nhầm trạng thái hiện tại thay vì lịch sử.
 */
@DisplayName("Thống kê admin")
class AnalyticsTest extends IntegrationTestBase {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired AnalyticsService analytics;
    @Autowired OrderCsvExporter exporter;
    @Autowired AuditLogRepository auditLogs;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private ProductVariant variant;
    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        variant = fixtures.createVariantWithStock(100, "1000000.00");
        alice = fixtures.createCustomer();
        bob = fixtures.createCustomer();
    }

    @Test
    @DisplayName("Chuỗi thời gian đủ mọi ngày, ngày không có đơn = 0 chứ không biến mất")
    void series_fillsEmptyDaysWithZero() {
        order(alice, OrderStatus.PAID, at(2026, 9, 1, 10), "1000000");
        order(alice, OrderStatus.PAID, at(2026, 9, 3, 10), "3000000");

        TimeSeries series = analytics.series(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), "day");

        assertThat(series.points()).hasSize(3);
        assertThat(series.points().get(1).period()).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(series.points().get(1).revenue()).isEqualByComparingTo("0");
        assertThat(series.points().get(2).revenue()).isEqualByComparingTo("3000000");
        assertThat(series.previous())
                .as("Kỳ trước cùng độ dài để vẽ đường so sánh")
                .hasSize(3);
    }

    @Test
    @DisplayName("Ranh giới ngày theo giờ Việt Nam: 18:30 UTC là 01:30 sáng hôm sau")
    void dayBoundary_usesStoreTimeZone() {
        // 2026-09-01T18:30Z = 2026-09-02 01:30 giờ Việt Nam
        fixtures.insertOrder(alice.getId(), variant.getId(), OrderStatus.PAID,
                Instant.parse("2026-09-01T18:30:00Z"), "2000000", "Hà Nội", null, 1);

        TimeSeries series = analytics.series(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "day");
        assertThat(series.points().get(0).revenue())
                .as("Ngay 01/09 theo giờ Việt Nam không có đơn nào")
                .isEqualByComparingTo("0");
        assertThat(series.points().get(1).revenue()).isEqualByComparingTo("2000000");

        Overview sept2 = analytics.overview(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2));
        assertThat(sept2.revenue().current()).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("KPI: doanh thu bỏ đơn huỷ và chưa thanh toán, so với kỳ trước, tỉ lệ huỷ, khách quay lại")
    void overview_excludesUnpaid_comparesPreviousPeriod() {
        // Kỳ trước (31/08 - 09/09): alice mua một đơn
        order(alice, OrderStatus.DELIVERED, at(2026, 9, 5, 9), "2000000");
        // Kỳ này (10/09 - 19/09)
        order(alice, OrderStatus.PAID, at(2026, 9, 12, 9), "1000000");
        order(bob, OrderStatus.SHIPPED, at(2026, 9, 13, 9), "3000000");
        order(bob, OrderStatus.CANCELLED, at(2026, 9, 14, 9), "5000000");
        order(bob, OrderStatus.PENDING_PAYMENT, at(2026, 9, 15, 9), "7000000");

        Overview o = analytics.overview(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19));

        assertThat(o.previousRange().from()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(o.revenue().current())
                .as("Chi PAID/PACKED/SHIPPED/DELIVERED duoc tinh")
                .isEqualByComparingTo("4000000");
        assertThat(o.revenue().previous()).isEqualByComparingTo("2000000");
        assertThat(o.revenue().changePercent()).isEqualByComparingTo("100.0");
        assertThat(o.paidOrders().current()).isEqualByComparingTo("2");
        assertThat(o.averageOrderValue().current()).isEqualByComparingTo("2000000");
        assertThat(o.cancelRate().current())
                .as("1 đơn huỷ trên 4 đơn đặt")
                .isEqualByComparingTo("25.0");
        assertThat(o.returningBuyerRate().current())
                .as("alice đã mua ở kỳ trước, bob thì chưa")
                .isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("Kỳ trước bằng 0 thì changePercent = null, không bịa ra +100%")
    void overview_changePercentIsNullWhenPreviousIsZero() {
        order(alice, OrderStatus.PAID, at(2026, 9, 12, 9), "1000000");
        Overview o = analytics.overview(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19));
        assertThat(o.revenue().changePercent()).isNull();
    }

    @Test
    @DisplayName("Cơ cấu: phần đuôi gộp thành 'Khác', tỉ trọng cộng lại đủ 100%")
    void breakdown_foldsTailIntoOther() {
        // Mỗi variant từ fixtures nằm ở một danh mục riêng -> 4 danh mục
        order(alice, OrderStatus.PAID, at(2026, 9, 12, 9), "4000000");
        orderOn(fixtures.createVariantWithStock(10, "1000000.00"), "3000000");
        orderOn(fixtures.createVariantWithStock(10, "1000000.00"), "2000000");
        orderOn(fixtures.createVariantWithStock(10, "1000000.00"), "1000000");

        Breakdown b = analytics.breakdown("category", LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19), 3);

        assertThat(b.rows()).hasSize(3);
        assertThat(b.rows().get(2).key()).isEqualTo("__other__");
        assertThat(b.rows().get(2).revenue()).isEqualByComparingTo("3000000");
        BigDecimal shares = b.rows().stream().map(BreakdownRow::share).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(shares).isBetween(new BigDecimal("99.8"), new BigDecimal("100.2"));
    }

    @Test
    @DisplayName("Chiều cơ cấu là whitelist - giá trị lạ bị từ chối")
    void breakdown_rejectsUnknownDimension() {
        assertThatThrownBy(() -> analytics.breakdown("users; DROP TABLE orders", null, null, 8))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("Phễu dựng từ lịch sử: đơn REFUNDED vẫn tính là đã từng thanh toán")
    void funnel_readsHistoryNotCurrentStatus() {
        long delivered = order(alice, OrderStatus.DELIVERED, at(2026, 9, 12, 9), "1000000");
        Instant t = at(2026, 9, 12, 9);
        fixtures.insertHistory(delivered, null, OrderStatus.PENDING_PAYMENT, t);
        fixtures.insertHistory(delivered, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, t.plusSeconds(3600));
        fixtures.insertHistory(delivered, OrderStatus.PAID, OrderStatus.PACKED, t.plusSeconds(7200));
        fixtures.insertHistory(delivered, OrderStatus.PACKED, OrderStatus.SHIPPED, t.plusSeconds(10800));
        fixtures.insertHistory(delivered, OrderStatus.SHIPPED, OrderStatus.DELIVERED, t.plusSeconds(86400));

        long refunded = order(bob, OrderStatus.REFUNDED, at(2026, 9, 13, 9), "2000000");
        fixtures.insertHistory(refunded, null, OrderStatus.PENDING_PAYMENT, at(2026, 9, 13, 9));
        fixtures.insertHistory(refunded, OrderStatus.PENDING_PAYMENT, OrderStatus.PAID, at(2026, 9, 13, 10));
        fixtures.insertHistory(refunded, OrderStatus.PAID, OrderStatus.REFUNDED, at(2026, 9, 14, 10));

        Funnel f = analytics.funnel(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19));

        assertThat(f.stages()).extracting(FunnelStage::count).containsExactly(2L, 2L, 1L, 1L, 1L);
        assertThat(f.stages().get(1).percentOfPlaced()).isEqualByComparingTo("100.0");
        assertThat(f.refunded()).isEqualTo(1);
        assertThat(f.medianHoursToPay()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("Bản đồ nhiệt theo giờ địa phương")
    void heatmap_usesLocalHour() {
        // 13:00 UTC = 20:00 giờ Việt Nam
        fixtures.insertOrder(alice.getId(), variant.getId(), OrderStatus.PAID,
                Instant.parse("2026-09-15T13:00:00Z"), "1000000", "Hà Nội", null, 1);

        Heatmap h = analytics.heatmap(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19));

        assertThat(h.cells()).singleElement().satisfies(c -> {
            assertThat(c.hour()).isEqualTo(20);
            assertThat(c.dayOfWeek()).isEqualTo(LocalDate.of(2026, 9, 15).getDayOfWeek().getValue());
        });
    }

    @Test
    @DisplayName("Khoảng ngày ngược hoặc quá dài bị từ chối")
    void invalidRange_isRejected() {
        assertThatThrownBy(() -> analytics.overview(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
        assertThatThrownBy(() -> analytics.overview(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("Xuất CSV: đúng số dòng, có BOM cho Excel, và được ghi audit log")
    void csvExport_writesRowsAndIsAudited() {
        order(alice, OrderStatus.PAID, at(2026, 9, 12, 9), "1000000");
        order(bob, OrderStatus.CANCELLED, at(2026, 9, 13, 9), "2000000");

        StringWriter out = new StringWriter();
        var plan = exporter.plan(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 19), null);
        exporter.write(plan, out);

        String csv = out.toString();
        assertThat(csv).startsWith("﻿");
        assertThat(csv.lines().count()).as("1 dong tieu de + 2 don").isEqualTo(3);
        assertThat(csv).contains(alice.getEmail(), "CANCELLED", "Hà Nội");
        assertThat(auditLogs.countByAction(AuditAction.ORDERS_EXPORTED)).isEqualTo(1);
    }

    @Test
    @DisplayName("CSV injection: ô bắt đầu bằng = + - @ bị vô hiệu hoá")
    void csvCell_neutralisesFormulas() {
        assertThat(OrderCsvExporter.cell("=HYPERLINK(\"http://x\",\"bam\")"))
                .startsWith("\"'=HYPERLINK");
        assertThat(OrderCsvExporter.cell("+84901234567")).isEqualTo("'+84901234567");
        assertThat(OrderCsvExporter.cell("Quận 1, TP HCM")).isEqualTo("\"Quan 1, TP HCM\"");
        assertThat(OrderCsvExporter.cell("bình thường")).isEqualTo("bình thường");
    }

    // ------------------------------------------------------------------ tiện ích

    private long order(User user, OrderStatus status, Instant placedAt, String total) {
        return fixtures.insertOrder(user.getId(), variant.getId(), status, placedAt, total, "Hà Nội", null, 1);
    }

    private void orderOn(ProductVariant v, String total) {
        fixtures.insertOrder(alice.getId(), v.getId(), OrderStatus.PAID, at(2026, 9, 12, 9), total, "Hà Nội", null, 1);
    }

    private static Instant at(int y, int m, int d, int hour) {
        return LocalDate.of(y, m, d).atTime(hour, 0).atZone(VN).toInstant();
    }
}
