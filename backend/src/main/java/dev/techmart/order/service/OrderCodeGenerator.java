package dev.techmart.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sinh mã đơn hàng dạng {@code TM-20260901-K7M2QX}.
 *
 * <p>Phần dưới là 6 ký tự ngẫu nhiên chứ không phải số thứ tự trong ngày. Đánh đổi:
 * mất tính "đơn thứ mấy trong ngày" (vẫn tra cứu được bằng created_at), đổi lại mà không
 * đoán được và không lộ sản lượng đơn hàng của cửa hàng ra bên ngoài.
 *
 * <p>Và không cần bộ đếm dùng chung -> không có điểm tranh chấp khi nhiều đơn được tạo
 * cùng lúc. Ràng buộc UNIQUE(code) ở DB bắt trường hợp trùng hiếm hoi.
 */
@Component
@RequiredArgsConstructor
public class OrderCodeGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";   // bỏ 0/O/1/I để đọc không nhầm
    private static final int SUFFIX_LENGTH = 6;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public String generate() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "TM-" + DATE.format(clock.instant()) + "-" + suffix;
    }
}
