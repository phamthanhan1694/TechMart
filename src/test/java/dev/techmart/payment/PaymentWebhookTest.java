package dev.techmart.payment;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.domain.User;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.inventory.service.ReservationService;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.dto.OrderDtos.PlaceOrderRequest;
import dev.techmart.order.dto.OrderDtos.ShippingAddress;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderService;
import dev.techmart.payment.domain.PaymentMethod;
import dev.techmart.payment.domain.PaymentStatus;
import dev.techmart.payment.dto.PaymentDtos.InitiatePaymentRequest;
import dev.techmart.payment.dto.PaymentDtos.WebhookRequest;
import dev.techmart.payment.service.MockPaymentGateway;
import dev.techmart.payment.service.PaymentService;
import dev.techmart.support.DatabaseCleaner;
import dev.techmart.support.IntegrationTestBase;
import dev.techmart.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ba lop bao ve cua webhook thanh toan. Thieu bat ky lop nao cung thanh lo hong
 * "mua hang mien phi" - day la nhom test dang gia nhat cua module thanh toan.
 */
@DisplayName("Webhook thanh toan")
class PaymentWebhookTest extends IntegrationTestBase {

    @Autowired PaymentService paymentService;
    @Autowired MockPaymentGateway gateway;
    @Autowired OrderService orderService;
    @Autowired ReservationService reservationService;
    @Autowired OrderRepository orders;
    @Autowired TestDataFactory fixtures;
    @Autowired DatabaseCleaner cleaner;

    private AuthPrincipal principal;
    private String orderCode;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        cleaner.clean();
        ProductVariant variant = fixtures.createVariantWithStock(10, "20000000.00");
        User buyer = fixtures.createCustomerWithCart(variant.getId(), 1);
        principal = new AuthPrincipal(buyer.getId(), buyer.getEmail(), Role.CUSTOMER);

        var session = reservationService.hold(buyer.getId());
        var order = orderService.placeOrder(buyer.getId(),
                new PlaceOrderRequest(session.sessionId(), address(), null, null),
                UUID.randomUUID().toString());
        orderCode = order.code();
        amount = order.totalAmount();
    }

    @Test
    @DisplayName("Chu ky sai -> 401, va don KHONG duoc danh dau da tra")
    void webhook_withInvalidSignature_isRejected() {
        var payment = initiate();

        assertThatThrownBy(() -> paymentService.handleWebhook(new WebhookRequest(
                payment.transactionRef(), PaymentStatus.SUCCEEDED, amount, "chu-ky-gia")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.INVALID_WEBHOOK_SIGNATURE);

        assertThat(orders.findByCode(orderCode).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("So tien khong khop -> 422, don KHONG duoc danh dau da tra")
    void webhook_withMismatchedAmount_isRejected() {
        var payment = initiate();
        BigDecimal fakeAmount = new BigDecimal("1.00");
        // Ke tan cong ky DUNG chuoi cua minh, nhung so tien khac tong don
        String signature = gateway.sign(payment.transactionRef(), "SUCCEEDED", fakeAmount);

        assertThatThrownBy(() -> paymentService.handleWebhook(new WebhookRequest(
                payment.transactionRef(), PaymentStatus.SUCCEEDED, fakeAmount, signature)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.PAYMENT_AMOUNT_MISMATCH);

        assertThat(orders.findByCode(orderCode).orElseThrow().getStatus())
                .as("Chu ky hop le nhung so tien sai thi van khong duoc tra hang")
                .isEqualTo(OrderStatus.PENDING_PAYMENT);
    }

    @Test
    @DisplayName("Webhook hop le -> don chuyen sang PAID")
    void webhook_valid_marksOrderPaid() {
        var payment = initiate();
        var result = paymentService.handleWebhook(signedWebhook(payment.transactionRef(), PaymentStatus.SUCCEEDED));

        assertThat(result.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(result.paidAt()).isNotNull();
        assertThat(orders.findByCode(orderCode).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Webhook gui trung -> idempotent, khong doi trang thai lan hai")
    void webhook_deliveredTwice_isIdempotent() {
        var payment = initiate();
        var first = paymentService.handleWebhook(signedWebhook(payment.transactionRef(), PaymentStatus.SUCCEEDED));
        var second = paymentService.handleWebhook(signedWebhook(payment.transactionRef(), PaymentStatus.SUCCEEDED));

        assertThat(second.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(second.paidAt()).isEqualTo(first.paidAt());
        assertThat(orders.findByCode(orderCode).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("So tien cung gia tri nhung khac scale -> chu ky VAN hop le")
    void webhook_amountWithDifferentScale_stillVerifies() {
        var payment = initiate();

        // Mo phong callback di qua mot lop trung gian parse JSON number thanh float:
        // 45010000.00 tro thanh 45010000.0 - cung gia tri, khac cach bieu dien.
        BigDecimal loosenedScale = new BigDecimal(amount.stripTrailingZeros().toPlainString());
        assertThat(loosenedScale.toPlainString())
                .as("Phai that su khac chuoi thi bai test moi co y nghia")
                .isNotEqualTo(amount.toPlainString());

        // Ky bang gia tri da mat scale, gui len cung bang gia tri do
        String signature = gateway.sign(payment.transactionRef(), "SUCCEEDED", loosenedScale);
        var result = paymentService.handleWebhook(new WebhookRequest(
                payment.transactionRef(), PaymentStatus.SUCCEEDED, loosenedScale, signature));

        assertThat(result.status())
                .as("Chu ky phai phu thuoc GIA TRI so tien, khong phai cach bieu dien")
                .isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(orders.findByCode(orderCode).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("Thanh toan hai lan cung Idempotency-Key -> mot giao dich duy nhat")
    void initiate_sameIdempotencyKey_returnsSamePayment() {
        String key = UUID.randomUUID().toString();
        var first = paymentService.initiate(principal,
                new InitiatePaymentRequest(orderCode, PaymentMethod.MOCK_CARD), key);
        var second = paymentService.initiate(principal,
                new InitiatePaymentRequest(orderCode, PaymentMethod.MOCK_CARD), key);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.transactionRef()).isEqualTo(first.transactionRef());
    }

    @Test
    @DisplayName("Thanh toan don da tra -> 409")
    void initiate_alreadyPaidOrder_isRejected() {
        var payment = initiate();
        paymentService.handleWebhook(signedWebhook(payment.transactionRef(), PaymentStatus.SUCCEEDED));

        assertThatThrownBy(() -> paymentService.initiate(principal,
                new InitiatePaymentRequest(orderCode, PaymentMethod.MOCK_CARD), UUID.randomUUID().toString()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).code())
                .isEqualTo(ErrorCode.ORDER_ALREADY_PAID);
    }

    private dev.techmart.payment.dto.PaymentDtos.PaymentResponse initiate() {
        return paymentService.initiate(principal,
                new InitiatePaymentRequest(orderCode, PaymentMethod.MOCK_CARD), UUID.randomUUID().toString());
    }

    private WebhookRequest signedWebhook(String transactionRef, PaymentStatus status) {
        return new WebhookRequest(transactionRef, status, amount,
                gateway.sign(transactionRef, status.name(), amount));
    }

    private ShippingAddress address() {
        return new ShippingAddress("Nguyen Van A", "0901234567", "12 Nguyen Trai",
                "Ben Thanh", "Quan 1", "TP Ho Chi Minh");
    }
}
