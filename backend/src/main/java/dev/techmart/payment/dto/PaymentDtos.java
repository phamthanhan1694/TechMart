package dev.techmart.payment.dto;

import dev.techmart.payment.domain.Payment;
import dev.techmart.payment.domain.PaymentMethod;
import dev.techmart.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record InitiatePaymentRequest(
            @NotBlank @Schema(example = "TM-20260901-K7M2QX") String orderCode,
            @NotNull PaymentMethod method
    ) {}

    @Schema(description = "Callback tu cong thanh toan. Bat buoc co chu ky HMAC hop le.")
    public record WebhookRequest(
            @NotBlank String transactionRef,
            @NotNull PaymentStatus status,
            @NotNull BigDecimal amount,
            @NotBlank @Schema(description = "HMAC-SHA256 cua 'transactionRef|status|amount'") String signature
    ) {}

    public record PaymentResponse(
            Long id,
            String orderCode,
            PaymentMethod method,
            PaymentStatus status,
            BigDecimal amount,
            String transactionRef,
            String failureReason,
            Instant paidAt
    ) {
        public static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getId(), p.getOrder().getCode(), p.getMethod(), p.getStatus(),
                    p.getAmount(), p.getTransactionRef(), p.getFailureReason(), p.getPaidAt());
        }
    }

    @Schema(description = "Tien ich demo: sinh san callback da ky de khoi phai tu tinh HMAC")
    public record SimulatedCallback(
            String transactionRef,
            PaymentStatus status,
            BigDecimal amount,
            String signature,
            @Schema(description = "Dan nguyen body nay vao POST /api/v1/payments/webhook") String hint
    ) {}
}
