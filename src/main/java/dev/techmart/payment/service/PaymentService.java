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
     * Khoi tao giao dich.
     *
     * <p>Idempotency giong het luong dat hang: tra cuu theo key la duong nhanh, con chot chan
     * that su la {@code UNIQUE(payments.idempotency_key)} o DB.
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
     * Xu ly callback tu cong thanh toan.
     *
     * <p>Ba lop bao ve, thieu bat ky lop nao cung thanh lo hong "mua hang mien phi":
     * <ol>
     *   <li><b>Xac thuc chu ky HMAC</b> - endpoint nay khong co token, ai cung goi duoc</li>
     *   <li><b>Doi chieu so tien</b> voi tong don - khong bao gio tin so tien trong webhook</li>
     *   <li><b>Idempotent</b> - cong that gui lai callback khi khong nhan duoc 200</li>
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

        // Gui lai callback da xu ly -> tra ket qua cu, khong doi trang thai lan hai
        if (payment.isSettled()) {
            log.info("Webhook trung cho giao dich {} (da o trang thai {})",
                    req.transactionRef(), payment.getStatus());
            return PaymentResponse.from(payment);
        }

        // Khong tin so tien webhook gui len. Thieu buoc nay, ai gui duoc webhook gia
        // voi amount=0 la mua hang mien phi.
        // compareTo bo qua scale nen 45010000.0 va 45010000.00 duoc coi la bang nhau -
        // dung o day la co y. Neu dung equals() thi hai gia tri nay khac nhau va webhook
        // hop le se bi tu choi oan.
        if (payment.getAmount().compareTo(req.amount()) != 0) {
            log.error("So tien webhook ({}) khong khop don ({}) cho giao dich {}",
                    req.amount(), payment.getAmount(), req.transactionRef());
            throw new BusinessException(ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }

        if (req.status() == PaymentStatus.SUCCEEDED) {
            payment.markSucceeded(clock.instant());
            // Chi he thong (SYSTEM) duoc chuyen don sang PAID - khach tu doi la lo hong
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

    /** Tien ich demo: sinh san body callback da ky, khoi phai tu tinh HMAC bang tay. */
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

    /** Don cua nguoi khac tra 404, khong phai 403. ADMIN/STAFF xem duoc moi don. */
    private Order requireOwnOrder(String code, AuthPrincipal principal) {
        Order order = orders.findByCode(code)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code));
        if (principal.role() == Role.CUSTOMER && !order.isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.ORDER_NOT_FOUND, code);
        }
        return order;
    }
}
