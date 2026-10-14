package dev.techmart.payment.service;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.repository.OrderRepository;
import dev.techmart.order.service.OrderService;
import dev.techmart.payment.domain.Payment;
import dev.techmart.payment.domain.PaymentStatus;
import dev.techmart.payment.dto.PaymentDtos.*;
import dev.techmart.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final MockPaymentGateway gateway;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, OrderRepository orders, OrderService orderService,
                          MockPaymentGateway gateway, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.orderService = orderService;
        this.gateway = gateway;
        this.clock = clock;
    }

    /**
     * Khoi tạo giao dịch.
     *
     * <p>Idempotency giống hệt luồng đặt hàng: tra cứu theo key là đường nhanh, con chốt chặn
     * thật su là {@code UNIQUE(payments.idempotency_key)} o DB.
     */
    @Transactional
    public PaymentResponse initiate(AuthPrincipal principal, InitiatePaymentRequest req, String idempotencyKey) {
        var replay = payments.findByIdempotencyKey(idempotencyKey);
        if (replay.isPresent()) {
            return PaymentResponse.from(replay.get());
        }

        Order order = requireOwnOrder(req.orderCode(), principal);

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(order.getStatus() == OrderStatus.PENDING_PAYMENT
                    ? ErrorCode.INVALID_STATE_TRANSITION : ErrorCode.ORDER_ALREADY_PAID,
                    "Don o trang thai " + order.getStatus() + ", khong the thanh toan");
        }
        if (payments.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCEEDED)) {
            throw new BusinessException(ErrorCode.ORDER_ALREADY_PAID);
        }

        String transactionRef = gateway.createTransactionRef();
        Payment payment = new Payment(order, req.method(), order.getTotalAmount(),
                transactionRef, idempotencyKey);

        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            return payments.findByIdempotencyKey(idempotencyKey)
                    .map(PaymentResponse::from)
                    .orElseThrow(() -> new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT));
        }

        gateway.authorize(transactionRef, order.getTotalAmount());
        log.info("Da khoi tao giao dich {} cho don {} so tien {}",
                transactionRef, order.getCode(), order.getTotalAmount());
        return PaymentResponse.from(payment);
    }

    /**
     * Xu ly callback từ cổng thanh toán.
     *
     * <p>Ba lớp bảo vệ, thieu bất kỳ lop nao cũng thành lỗ hổng "mua hàng miễn phí":
     * <ol>
     *   <li><b>Xác thực chu ky HMAC</b> - endpoint này không có token, ai cũng gọi được</li>
     *   <li><b>Đổi chieu số tiền</b> với tổng đơn - không bao giờ tin số tiền trong webhook</li>
     *   <li><b>Idempotent</b> - cong thật gửi lai callback khi không nhận được 200</li>
     * </ol>
     */
    @Transactional
    public PaymentResponse handleWebhook(WebhookRequest req) {
        if (!gateway.verify(req.transactionRef(), req.status().name(), req.amount(), req.signature())) {
            log.warn("Webhook co chu ky khong hop le cho giao dich {}", req.transactionRef());
            throw new BusinessException(ErrorCode.INVALID_WEBHOOK_SIGNATURE);
        }

        Payment payment = payments.findByTransactionRef(req.transactionRef())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PAYMENT_NOT_FOUND, req.transactionRef()));

        // Gửi lai callback da xu ly -> trả kết quả cũ, không đổi trạng thái lan hai
        if (payment.isSettled()) {
            log.info("Webhook trung cho giao dich {} (da o trang thai {})",
                    req.transactionRef(), payment.getStatus());
            return PaymentResponse.from(payment);
        }

        // Không tin số tiền webhook gửi lên. Thieu buoc nay, ai gửi được webhook giả
        // với âmount=0 là mua hàng miễn phí.
        // compareTo bỏ qua scale nên 45010000.0 và 45010000.00 được coi là bằng nhau -
        // dùng ở đây là có y. Nếu dùng equals() thì hai giá trị này khác nhau và webhook
        // hop le sẽ bị từ chối oan.
        if (payment.getAmount().compareTo(req.amount()) != 0) {
            log.error("So tien webhook ({}) khong khop don ({}) cho giao dich {}",
                    req.amount(), payment.getAmount(), req.transactionRef());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (req.status() == PaymentStatus.SUCCEEDED) {
            payment.markSucceeded(clock.instant());
            // Chi hệ thống (SYSTEM) được chuyen đơn sang PAID - khách từ đổi là lỗ hổng
            orderService.transitionBySystem(payment.getOrder(), OrderStatus.PAID,
                    "Thanh toan thanh cong: " + req.transactionRef());
            log.info("Don {} da thanh toan qua giao dich {}",
                    payment.getOrder().getCode(), req.transactionRef());
        } else {
            payment.markFailed("Cong thanh toan tu choi");
            log.info("Giao dich {} that bai", req.transactionRef());
        }
        return PaymentResponse.from(payment);
    }

    /** Tien ich demo: sinh san body callback da ky, khoi phải từ tinh HMAC bằng tay. */
    @Transactional(readOnly = true)
    public SimulatedCallback simulateCallback(Long paymentId, PaymentStatus outcome, AuthPrincipal principal) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.PAYMENT_NOT_FOUND, paymentId));
        if (principal.role() == Role.CUSTOMER && !payment.getOrder().isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.PAYMENT_NOT_FOUND, paymentId);
        }
        BigDecimal amount = payment.getAmount();
        String signature = gateway.sign(payment.getTransactionRef(), outcome.name(), amount);
        return new SimulatedCallback(payment.getTransactionRef(), outcome, amount, signature,
                "POST /api/v1/payments/webhook voi dung body nay");
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findByOrder(String orderCode, AuthPrincipal principal) {
        Order order = requireOwnOrder(orderCode, principal);
        return payments.findByOrderIdOrderByCreatedAtDesc(order.getId()).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /** Đơn của người khác trả 404, không phải 403. ADMIN/STAFF xem được mọi đơn. */
    private Order requireOwnOrder(String code, AuthPrincipal principal) {
        Order order = orders.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
        if (principal.role() == Role.CUSTOMER && !order.isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code);
        }
        return order;
    }
}
