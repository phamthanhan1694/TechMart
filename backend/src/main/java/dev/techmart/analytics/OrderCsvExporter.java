package dev.techmart.analytics;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.service.AuditLogger;
import dev.techmart.common.config.AppProperties;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.order.domain.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Xuất đơn hàng ra CSV, stream từng dòng từ DB thẳng ra response.
 *
 * <p><b>Stream, không nạp hết vào bộ nhớ:</b> {@code fetchSize} khiến driver PostgreSQL dùng
 * cursor phía server và kéo từng lô 1.000 dòng - 100.000 đơn không bao giờ nằm cùng lúc trong
 * heap. (PostgreSQL chỉ dùng cursor khi autocommit tắt - tức là phải ở trong transaction.)
 *
 * <p><b>Đánh đổi:</b> connection DB bị giữ suốt thời gian client tải file. Client chậm = một
 * connection bị chiếm lâu, trong khi pool chỉ có 20. Trần {@code export-max-rows} giữ thời
 * gian đó ở mức vài giây. Khi cần xuất cả triệu dòng thì phải chuyển sang job nền ghi file
 * ra object storage rồi gửi link - thêm hạ tầng, đổi lại không giữ connection (docs/13).
 *
 * <p><b>Chống CSV injection:</b> ô bắt đầu bằng {@code = + - @} bị Excel hiểu là công thức.
 * Email hay ghi chú do khách nhập, nên kẻ xấu có thể cài {@code =HYPERLINK(...)} vào file
 * mà admin sẽ mở. Các ô đó được thêm dấu nháy đơn đứng trước.
 */
@Component
public class OrderCsvExporter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String BOM = "﻿";   // để Excel đọc đúng UTF-8 tiếng Việt

    private final NamedParameterJdbcTemplate counting;
    private final NamedParameterJdbcTemplate streaming;
    private final AnalyticsService analytics;
    private final AuditLogger audit;
    private final int maxRows;

    public OrderCsvExporter(NamedParameterJdbcTemplate jdbc, DataSource dataSource,
                            AnalyticsService analytics, AuditLogger audit, AppProperties props) {
        this.counting = jdbc;
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setFetchSize(1000);
        this.streaming = new NamedParameterJdbcTemplate(template);
        this.analytics = analytics;
        this.audit = audit;
        this.maxRows = props.reporting().exportMaxRows();
    }

    /** Kế hoạch xuất đã được kiểm tra - tách khỏi bước ghi để lỗi 422 trả được JSON sạch. */
    public record ExportPlan(ReportRange range, OrderStatus status, long rows) {}

    /**
     * Bước 1: kiểm khoảng ngày và đếm số dòng, CHƯA ghi gì ra response.
     *
     * <p>Tách riêng vì một khi controller đã đặt Content-Type CSV và header tải file thì lỗi
     * ném ra sau đó không còn trả về được dưới dạng JSON: trình duyệt sẽ tải về một "file CSV"
     * chứa JSON lỗi.
     */
    @Transactional(readOnly = true)
    public ExportPlan plan(LocalDate from, LocalDate to, OrderStatus status) {
        ReportRange range = analytics.range(from, to);
        long rows = Objects.requireNonNull(counting.queryForObject(
                "SELECT COUNT(*) FROM orders o " + WHERE, params(range, status), Long.class));
        if (rows > maxRows) {
            throw new BusinessException(ErrorCode.EXPORT_TOO_LARGE,
                    rows + " dong, toi da " + maxRows + ". Hay thu hep khoang ngay.");
        }
        return new ExportPlan(range, status, rows);
    }

    /**
     * Bước 2: stream CSV. Không readOnly: phải ghi audit log cho chính lần xuất này - dữ liệu
     * khách hàng vừa rời khỏi hệ thống, và đó đúng là loại sự kiện audit log sinh ra để ghi.
     */
    @Transactional
    public void write(ExportPlan plan, Writer out) {
        ReportRange range = plan.range();
        MapSqlParameterSource params = params(range, plan.status());

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("from", range.from().toString());
        detail.put("to", range.to().toString());
        detail.put("status", plan.status() != null ? plan.status().name() : "ALL");
        detail.put("rows", plan.rows());
        audit.record(AuditAction.ORDERS_EXPORTED, "ORDER", null, detail);

        write(out, BOM + "Mã đơn,Ngày đặt,Email khách,Trạng thái,Số sản phẩm,Tạm tính,Giảm giá,"
                + "Phí vận chuyển,Tổng tiền,Mã giảm giá,Tỉnh/Thành\r\n");

        streaming.query("""
                SELECT o.code,
                       o.placed_at AT TIME ZONE :zone AS placed_local,
                       u.email, o.status,
                       (SELECT COALESCE(SUM(quantity), 0) FROM order_items WHERE order_id = o.id) AS items,
                       o.subtotal, o.discount_amount, o.shipping_fee, o.total_amount,
                       o.coupon_code,
                       o.shipping_address->>'province' AS province
                  FROM orders o
                  JOIN users u ON u.id = o.user_id
                """ + WHERE + " ORDER BY o.placed_at, o.id", params, rs -> {
            StringBuilder line = new StringBuilder(160);
            line.append(cell(rs.getString("code"))).append(',')
                    .append(rs.getObject("placed_local", java.time.LocalDateTime.class).format(TIME)).append(',')
                    .append(cell(rs.getString("email"))).append(',')
                    .append(rs.getString("status")).append(',')
                    .append(rs.getLong("items")).append(',')
                    .append(money(rs.getBigDecimal("subtotal"))).append(',')
                    .append(money(rs.getBigDecimal("discount_amount"))).append(',')
                    .append(money(rs.getBigDecimal("shipping_fee"))).append(',')
                    .append(money(rs.getBigDecimal("total_amount"))).append(',')
                    .append(cell(rs.getString("coupon_code"))).append(',')
                    .append(cell(rs.getString("province"))).append("\r\n");
            write(out, line.toString());
        });
    }

    private static final String WHERE = """
             WHERE o.placed_at >= :start AND o.placed_at < :end
               AND (CAST(:status AS text) IS NULL OR o.status = CAST(:status AS text))
            """;

    private static MapSqlParameterSource params(ReportRange range, OrderStatus status) {
        return new MapSqlParameterSource()
                .addValue("start", range.start())
                .addValue("end", range.end())
                .addValue("zone", range.zone().getId())
                .addValue("status", status != null ? status.name() : null);
    }

    /** Thoát theo RFC 4180, cộng thêm vô hiệu hoá công thức bảng tính. */
    static String cell(String value) {
        if (value == null || value.isEmpty()) return "";
        String v = value;
        char first = v.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String money(BigDecimal value) {
        return value == null ? "" : value.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static void write(Writer out, String s) {
        try {
            out.write(s);
        } catch (IOException e) {
            throw new UncheckedIOException("Client ngat ket noi khi dang tai file", e);
        }
    }
}
