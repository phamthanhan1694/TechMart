package dev.techmart.order.domain;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Định nghĩa DUY NHẤT của "đơn được tính vào doanh thu": các trạng thái đã thu được tiền.
 *
 * <p>Dashboard, trang thống kê, thống kê khách hàng và file xuất đều dùng hằng này. Mỗi nơi
 * tự viết danh sách riêng thì sớm muộn sẽ có một nơi quên thêm trạng thái mới, và hai màn
 * hình báo hai con số doanh thu khác nhau - lỗi mất niềm tin nhanh nhất của mọi báo cáo.
 *
 * <p>Đánh đổi thừa nhận (ADR-0011): doanh thu tính theo <b>trạng thái hiện tại</b> và gom theo
 * <b>ngày đặt</b>. Đơn đặt tháng trước, tháng này bị hoàn tiền sẽ làm doanh thu tháng trước
 * giảm đi - số liệu quá khứ không đóng băng. Kế toán theo dòng tiền cần bảng bút toán riêng.
 */
public final class RevenueStatuses {

    public static final Set<OrderStatus> SET = EnumSet.of(
            OrderStatus.PAID, OrderStatus.PACKED, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    /** Dạng {@code ('PAID','PACKED',...)} để ghép vào SQL - hằng số của code, không phải dữ liệu client. */
    public static final String SQL_IN = SET.stream()
            .map(s -> "'" + s.name() + "'")
            .collect(Collectors.joining(",", "(", ")"));

    private RevenueStatuses() {
    }
}
