package dev.techmart.payment.service;

import dev.techmart.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cổng thanh toán giả.
 *
 * <p>Có y <b>không</b> tich hop VNPay/Momo thật: viec đó ton thoi gian vào thu tuc dạng ky
 * và doc tai lieu của ben thu ba, không thêm chieu sau kỹ thuật nao. Cai dạng the hien la
 * <i>hinh dạng</i> của luồng thanh toán thật - và cổng giả này làm dùng nhu thật:
 * sinh transaction ref, trả kết quả qua webhook bat dòng bo, và <b>ky HMAC</b> len callback.
 *
 * <p>Ký cả ở cổng giả vì hai lý do: (1) để phía nhận viết đúng code xác thực chữ ký ngay
 * từ dau, (2) để không tạo thoi quen tin webhook không xac thuc.
 */
@Component
public class MockPaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final AppProperties props;

    public MockPaymentGateway(AppProperties props) {
        this.props = props;
    }

    public String createTransactionRef() {
        return "MOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    /**
     * Chuoi được ky. Thứ tự các truong là một phần của giao uoc - đổi thứ tự là đổi chu ky.
     *
     * <p><b>Số tiền được chuan hoa về 2 chữ số thap phần trước khi ky.</b> Nếu ky thang
     * {@code âmount.toPlainString()} thì chu ky phụ thuộc vào <i>cach bieu dien</i> chứ không
     * phải <i>giá trị</i>: cùng một số tiền, ben ky ghi {@code 45010000.00} con ben gọi lai
     * gửi {@code 45010000.0} (rat để xay ra khi callback đi qua một lớp trung gian parse
     * JSON number thanh float) thì chu ky truot du tien không hề sai.
     *
     * <p>Lỗi này lo ra khi viet script demo: body callback di qua Python json rồi quay lai,
     * mat một chu so 0 o cuoi, và webhook bị từ chối với INVALID_WEBHOOK_SIGNATURE.
     */
    public String payloadToSign(String transactionRef, String status, BigDecimal amount) {
        return transactionRef + "|" + status + "|" + normalize(amount);
    }

    /** Mọi số tiền trong hệ thống deu là NUMERIC(15,2), nên 2 chu so là dạng chính tac. */
    private static String normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public String sign(String transactionRef, String status, BigDecimal amount) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    props.payment().webhookSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(
                    payloadToSign(transactionRef, status, amount).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Khong ky duoc webhook", e);
        }
    }

    /**
     * So sanh chu ky bằng {@link MessageDigest#isEqual} chứ không phải {@code equals}:
     * so sanh chuỗi thong thuong thoat som o byte dau tien khác nhau, để lo thong tin
     * qua thoi gian thực thì (timing attack).
     */
    public boolean verify(String transactionRef, String status, BigDecimal amount, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(transactionRef, status, amount);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Mô phỏng kết quả từ cổng thanh toán. Mac dinh thành công; gọi được để test ca nhanh thất bại. */
    public boolean authorize(String transactionRef, BigDecimal amount) {
        log.info("[MOCK GATEWAY] Uy quyen {} cho giao dich {}", amount, transactionRef);
        return true;
    }
}
