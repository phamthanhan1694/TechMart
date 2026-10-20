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
 * <p>Cố ý <b>không</b> tích hợp VNPay/Momo thật: việc đó tốn thời gian vào thủ tục đăng ký
 * và đọc tài liệu của bên thứ ba, không thêm chiều sâu kỹ thuật nào. Cái đáng thể hiện là
 * <i>hình dạng</i> của luồng thanh toán thật - và cổng giả này làm đúng như thật:
 * sinh transaction ref, trả kết quả qua webhook bất đồng bộ, và <b>ký HMAC</b> lên callback.
 *
 * <p>Ký cả ở cổng giả vì hai lý do: (1) để phía nhận viết đúng code xác thực chữ ký ngay
 * từ đầu, (2) để không tạo thói quen tin webhook không xác thực.
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
     * Chuỗi được ký. Thứ tự các trường là một phần của giao ước - đổi thứ tự là đổi chữ ký.
     *
     * <p><b>Số tiền được chuẩn hoá về 2 chữ số thập phân trước khi ký.</b> Nếu ký thẳng
     * {@code amount.toPlainString()} thì chữ ký phụ thuộc vào <i>cách biểu diễn</i> chứ không
     * phải <i>giá trị</i>: cùng một số tiền, bên ký ghi {@code 45010000.00} còn bên gọi lại
     * gửi {@code 45010000.0} (rất dễ xảy ra khi callback đi qua một lớp trung gian parse
     * JSON number thành float) thì chữ ký trượt dù tiền không hề sai.
     *
     * <p>Lỗi này lộ ra khi viết script demo: body callback đi qua Python json rồi quay lại,
     * mất một chữ số 0 ở cuối, và webhook bị từ chối với INVALID_WEBHOOK_SIGNATURE.
     */
    public String payloadToSign(String transactionRef, String status, BigDecimal amount) {
        return transactionRef + "|" + status + "|" + normalize(amount);
    }

    /** Mọi số tiền trong hệ thống đều là NUMERIC(15,2), nên 2 chữ số là dạng chính tắc. */
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
            throw new IllegalStateException("Không ký được webhook", e);
        }
    }

    /**
     * So sánh chữ ký bằng {@link MessageDigest#isEqual} chứ không phải {@code equals}:
     * so sánh chuỗi thông thường thoát sớm ở byte đầu tiên khác nhau, để lộ thông tin
     * qua thời gian thực thi (timing attack).
     */
    public boolean verify(String transactionRef, String status, BigDecimal amount, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(transactionRef, status, amount);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Mô phỏng kết quả từ cổng thanh toán. Mặc định thành công; gọi được để test cả nhánh thất bại. */
    public boolean authorize(String transactionRef, BigDecimal amount) {
        log.info("[MOCK GATEWAY] Uỷ quyền {} cho giao dịch {}", amount, transactionRef);
        return true;
    }
}
