package dev.techmart.payment.web;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.payment.domain.PaymentStatus;
import dev.techmart.payment.dto.PaymentDtos.*;
import dev.techmart.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payment", description = "Thanh toán qua cổng giả, có webhook ký HMAC")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Khởi tạo giao dịch thanh toán",
            description = """
                    **Bắt buộc** header `Idempotency-Key`. Gọi lại cùng key trả về đúng giao dịch cũ.

                    Giao dịch tạo ra ở trạng thái INITIATED. Kết quả thật đến qua webhook -
                    dùng `POST /payments/{id}/simulate-callback` để lấy sẵn body đã ký.
                    """)
    public PaymentResponse initiate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "UUID do client sinh, bắt buộc", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest req) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Thiếu header Idempotency-Key. Client phải sinh một UUID cho mỗi lần thanh toán.");
        }
        return paymentService.initiate(principal, req, idempotencyKey);
    }

    @PostMapping("/webhook")
    @SecurityRequirements
    @Operation(summary = "Callback từ cổng thanh toán",
            description = """
                    Endpoint công khai (cổng thanh toán không có token của hệ thống), nhưng
                    **bắt buộc có chữ ký HMAC hợp lệ**. Ngoài ra còn đối chiếu số tiền với
                    tổng đơn trước khi đánh dấu đã trả - không bao giờ tin số tiền webhook gửi lên.

                    Idempotent: gửi lại cùng transactionRef trả kết quả cũ, không đổi trạng thái lần hai.
                    """)
    public PaymentResponse webhook(@Valid @RequestBody WebhookRequest req) {
        return paymentService.handleWebhook(req);
    }

    @PostMapping("/{id}/simulate-callback")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "[Demo] Sinh sẵn body callback đã ký",
            description = "Chỉ để tiện demo - trả về body kèm chữ ký HMAC hợp lệ "
                    + "để dán thẳng vào POST /payments/webhook.")
    public SimulatedCallback simulate(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestParam(defaultValue = "SUCCEEDED") PaymentStatus outcome) {
        return paymentService.simulateCallback(id, outcome, principal);
    }

    @GetMapping("/by-order/{orderCode}")
    @Operation(summary = "Danh sách giao dịch của một đơn")
    public List<PaymentResponse> byOrder(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable String orderCode) {
        return paymentService.findByOrder(orderCode, principal);
    }
}
