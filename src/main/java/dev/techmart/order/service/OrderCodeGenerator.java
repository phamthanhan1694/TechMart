package dev.techmart.order.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Sinh ma don hang dang {@code TM-20260901-K7M2QX}.
 *
 * <p>Phan duoi la 6 ky tu ngau nhien chu khong phai so thu tu trong ngay. Danh doi:
 * mat tinh "don thu may trong ngay" (van tra cuu duoc bang created_at), doi lai ma khong
 * doan duoc va khong lo san luong don hang cua cua hang ra ben ngoai.
 *
 * <p>Va khong can bo dem dung chung -> khong co diem tranh chap khi nhieu don duoc tao
 * cung luc. Rang buoc UNIQUE(code) o DB bat truong hop trung hiem hoi.
 */
@Component
public class OrderCodeGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";   // bo 0/O/1/I de doc khong nham
    private static final int SUFFIX_LENGTH = 6;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderCodeGenerator(Clock clock) {
        this.clock = clock;
    }

    public String generate() {
        StringBuilder suffix = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            suffix.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return "TM-" + DATE.format(clock.instant()) + "-" + suffix;
    }
}
