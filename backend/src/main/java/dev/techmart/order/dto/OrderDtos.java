package dev.techmart.order.dto;

import dev.techmart.order.domain.Order;
import dev.techmart.order.domain.OrderItem;
import dev.techmart.order.domain.OrderStatus;
import dev.techmart.order.domain.OrderStatusHistory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ShippingAddress(
            @NotBlank @Size(max = 150) String receiverName,
            @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "So dien thoai phai co 10 chu so") String phone,
            @NotBlank @Size(max = 255) String street,
            @NotBlank @Size(max = 100) String ward,
            @NotBlank @Size(max = 100) String district,
            @NotBlank @Size(max = 100) String province
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("receiverName", receiverName);
            map.put("phone", phone);
            map.put("street", street);
            map.put("ward", ward);
            map.put("district", district);
            map.put("province", province);
            return map;
        }
    }

    public record PlaceOrderRequest(
            @NotBlank @Schema(description = "sessionId lay tu POST /api/v1/checkout/sessions")
            String sessionId,
            @NotNull @Valid ShippingAddress shippingAddress,
            @Size(max = 500) String note,
            @Schema(description = "Ma giam gia, khong bat buoc") String couponCode
    ) {}

    public record CancelOrderRequest(@Size(max = 500) String reason) {}

    public record UpdateStatusRequest(
            @NotNull OrderStatus toStatus,
            @Size(max = 500) String note
    ) {}

    public record OrderItemResponse(
            Long id,
            Long variantId,
            String productName,
            String variantName,
            String sku,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal
    ) {
        public static OrderItemResponse from(OrderItem i) {
            return new OrderItemResponse(i.getId(), i.getVariant().getId(), i.getProductName(),
                    i.getVariantName(), i.getSku(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal());
        }
    }

    public record OrderResponse(
            Long id,
            @Schema(example = "TM-20260901-K7M2QX") String code,
            OrderStatus status,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal shippingFee,
            BigDecimal totalAmount,
            String couponCode,
            Map<String, Object> shippingAddress,
            String note,
            List<OrderItemResponse> items,
            Instant placedAt,
            Instant deliveredAt
    ) {
        public static OrderResponse from(Order o) {
            return new OrderResponse(o.getId(), o.getCode(), o.getStatus(), o.getSubtotal(),
                    o.getDiscountAmount(), o.getShippingFee(), o.getTotalAmount(), o.getCouponCode(),
                    o.getShippingAddress(), o.getNote(),
                    o.getItems().stream().map(OrderItemResponse::from).toList(),
                    o.getPlacedAt(), o.getDeliveredAt());
        }
    }

    public record OrderSummaryResponse(
            Long id,
            String code,
            OrderStatus status,
            BigDecimal totalAmount,
            int totalItems,
            Instant placedAt
    ) {
        public static OrderSummaryResponse from(Order o) {
            return new OrderSummaryResponse(o.getId(), o.getCode(), o.getStatus(), o.getTotalAmount(),
                    o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(), o.getPlacedAt());
        }
    }

    public record StatusHistoryResponse(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            Long changedBy,
            String note,
            Instant createdAt
    ) {
        public static StatusHistoryResponse from(OrderStatusHistory h) {
            return new StatusHistoryResponse(h.getFromStatus(), h.getToStatus(),
                    h.getChangedBy(), h.getNote(), h.getCreatedAt());
        }
    }
}
