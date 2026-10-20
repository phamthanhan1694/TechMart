package dev.techmart.analytics;

import dev.techmart.analytics.AnalyticsDtos.Range;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Khoảng ngày báo cáo, tính cả hai đầu, theo múi giờ cửa hàng.
 *
 * <p>Chuyển sang mốc thời gian theo kiểu nửa mở {@code [start, end)}: {@code end} là 00:00
 * của ngày SAU {@code to}. Dùng {@code <= 23:59:59} thì đơn đặt lúc 23:59:59.5 rơi mất.
 */
public record ReportRange(LocalDate from, LocalDate to, ZoneId zone) {

    static ReportRange resolve(LocalDate from, LocalDate to, ZoneId zone, Clock clock, int maxDays) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(29);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "from phải trước hoặc bằng to");
        }
        // Chặn một request quét cả chục năm dữ liệu: báo cáo là thứ admin bấm liên tục (ADR-0011)
        if (ChronoUnit.DAYS.between(start, end) + 1 > maxDays) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE,
                    "Khoảng ngày tối đa " + maxDays + " ngày");
        }
        return new ReportRange(start, end, zone);
    }

    int days() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Kỳ liền trước, cùng số ngày - "30 ngày này" so với "30 ngày trước đó". */
    ReportRange previous() {
        return new ReportRange(from.minusDays(days()), from.minusDays(1), zone);
    }

    Timestamp start() {
        return Timestamp.from(from.atStartOfDay(zone).toInstant());
    }

    Timestamp end() {
        return Timestamp.from(to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    Range toDto() {
        return new Range(from, to, days());
    }
}
