package dev.techmart.payment.service;

import dev.techmart.common.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Cong thanh toan gia.
 *
 * <p>Co y <b>khong</b> tich hop VNPay/Momo that: viec do ton thoi gian vao thu tuc dang ky
 * va doc tai lieu cua ben thu ba, khong them chieu sau ky thuat nao. Cai dang the hien la
 * <i>hinh dang</i> cua luong thanh toan that - va cong gia nay lam dung nhu that:
 * sinh transaction ref, tra ket qua qua webhook bat dong bo, va <b>ky HMAC</b> len callback.
 *
 * <p>Ky ca o cong gia vi hai ly do: (1) de phia nhan viet dung code xac thuc chu ky ngay
 * tu dau, (2) de khong tao thoi quen tin webhook khong xac thuc.
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
     * Chuoi duoc ky. Thu tu cac truong la mot phan cua giao uoc - doi thu tu la doi chu ky.
     */
    public String payloadToSign(String transactionRef, String status, BigDecimal amount) {
        return transactionRef + "|" + status + "|" + amount.toPlainString();
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
     * So sanh chu ky bang {@link MessageDigest#isEqual} chu khong phai {@code equals}:
     * so sanh chuoi thong thuong thoat som o byte dau tien khac nhau, de lo thong tin
     * qua thoi gian thuc thi (timing attack).
     */
    public boolean verify(String transactionRef, String status, BigDecimal amount, String signature) {
        if (signature == null) {
            return false;
        }
        String expected = sign(transactionRef, status, amount);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Mo phong ket qua tu cong thanh toan. Mac dinh thanh cong; goi duoc de test ca nhanh that bai. */
    public boolean authorize(String transactionRef, BigDecimal amount) {
        log.info("[MOCK GATEWAY] Uy quyen {} cho giao dich {}", amount, transactionRef);
        return true;
    }
}
