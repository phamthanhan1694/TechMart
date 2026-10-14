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
@Tag(name = "Payment", description = "Thanh toan qua cong gia, co webhook ky HMAC")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Khoi tao giao dich thanh toan",
            description = """
                    **Bat buoc** header `Idempotency-Key`. Goi lai cung key tra ve dung giao dich cu.

                    Giao dich tao ra o trang thai INITIATED. Ket qua that den qua webhook -
                    dung `POST /payments/{id}/simulate-callback` de lay san body da ky.
                    """)
    public PaymentResponse initiate(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Parameter(description = "UUID do client sinh, bat buoc", required = true)
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody InitiatePaymentRequest req) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                    "Thieu header Idempotency-Key. Client phai sinh mot UUID cho moi lan thanh toan.");
        }
        return paymentService.initiate(principal, req, idempotencyKey);
    }

    @PostMapping("/webhook")
    @SecurityRequirements
    @Operation(summary = "Callback tu cong thanh toan",
            description = """
                    Endpoint cong khai (cong thanh toan khong co token cua he thong), nhung
                    **bat buoc co chu ky HMAC hop le**. Ngoai ra con doi chieu so tien voi
                    tong don truoc khi danh dau da tra - khong bao gio tin so tien webhook gui len.

                    Idempotent: gui lai cung transactionRef tra ket qua cu, khong doi trang thai lan hai.
                    """)
    public PaymentResponse webhook(@Valid @RequestBody WebhookRequest req) {
        return paymentService.handleWebhook(req);
    }

    @PostMapping("/{id}/simulate-callback")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    @Operation(summary = "[Demo] Sinh san body callback da ky",
            description = "Chi de tien demo - tra ve body kem chu ky HMAC hop le "
                    + "de dan thang vao POST /payments/webhook.")
    public SimulatedCallback simulate(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable Long id,
                                      @RequestParam(defaultValue = "SUCCEEDED") PaymentStatus outcome) {
        return paymentService.simulateCallback(id, outcome, principal);
    }

    @GetMapping("/by-order/{orderCode}")
    @Operation(summary = "Danh sach giao dich cua mot don")
    public List<PaymentResponse> byOrder(@AuthenticationPrincipal AuthPrincipal principal,
                                         @PathVariable String orderCode) {
        return paymentService.findByOrder(orderCode, principal);
    }
}
