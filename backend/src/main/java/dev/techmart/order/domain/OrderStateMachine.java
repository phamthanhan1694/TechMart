package dev.techmart.order.domain;

import dev.techmart.identity.domain.Role;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static dev.techmart.order.domain.OrderStatus.*;

/**
 * Bằng chuyen trạng thái đơn hàng - nguon su thật duy nhất.
 *
 * <p>Mọi lệnh đổi trạng thái deu phải di qua đây. Không rải {@code if (status == ...)}
 * khap noi: rai ra thì mỗi lần thêm trạng thái mọi là một lan bo sot một nhanh.
 *
 * <p>7 trạng thái = 42 cấp có thể; chỉ 9 cấp hop le, 33 cấp còn lại phải bị chặn
 * bằng 409 INVALID_STATE_TRANSITION. Xem docs/02-luồng-nghiep-vu.md mục 1.
 */
public final class OrderStateMachine {

    /** Ai được phép thực hien chuyen trạng thái. SYSTEM = webhook thanh toán, job tự động. */
    public enum Actor {
        CUSTOMER, STAFF, ADMIN, SYSTEM;

        public static Actor of(Role role) {
            return switch (role) {
                case CUSTOMER -> CUSTOMER;
                case STAFF -> STAFF;
                case ADMIN -> ADMIN;
            };
        }
    }

    public record Transition(OrderStatus from, OrderStatus to) {}

    private static final Map<Transition, Set<Actor>> ALLOWED = Map.ofEntries(
            // Thanh toán thành công - chỉ webhook của cổng thanh toán được phép.
            // Khách TU DOI đơn sang PAID là lỗ hổng "mua hàng miễn phí".
            Map.entry(new Transition(PENDING_PAYMENT, PAID), EnumSet.of(Actor.SYSTEM)),

            // Khách từ huỷ, hoac job quét đơn quá hạn thanh toán
            Map.entry(new Transition(PENDING_PAYMENT, CANCELLED),
                    EnumSet.of(Actor.CUSTOMER, Actor.ADMIN, Actor.SYSTEM)),

            Map.entry(new Transition(PAID, PACKED), EnumSet.of(Actor.STAFF, Actor.ADMIN)),
            Map.entry(new Transition(PAID, CANCELLED), EnumSet.of(Actor.ADMIN)),

            Map.entry(new Transition(PACKED, SHIPPED), EnumSet.of(Actor.STAFF, Actor.ADMIN)),

            Map.entry(new Transition(SHIPPED, DELIVERED), EnumSet.of(Actor.STAFF, Actor.ADMIN)),
            Map.entry(new Transition(SHIPPED, REFUNDED), EnumSet.of(Actor.ADMIN)),

            Map.entry(new Transition(DELIVERED, REFUNDED), EnumSet.of(Actor.ADMIN))
    );

    /** Các chuyen đổi làm hàng quay lai kho. */
    private static final Set<Transition> RESTOCKING = Set.of(
            new Transition(PENDING_PAYMENT, CANCELLED),
            new Transition(PAID, CANCELLED),
            new Transition(SHIPPED, REFUNDED),
            new Transition(DELIVERED, REFUNDED));

    private OrderStateMachine() {
    }

    public static boolean isAllowed(OrderStatus from, OrderStatus to, Actor actor) {
        Set<Actor> actors = ALLOWED.get(new Transition(from, to));
        return actors != null && actors.contains(actor);
    }

    public static boolean transitionExists(OrderStatus from, OrderStatus to) {
        return ALLOWED.containsKey(new Transition(from, to));
    }

    /** Chuyen đổi này có phải hoan hàng ve kho không. */
    public static boolean restocks(OrderStatus from, OrderStatus to) {
        return RESTOCKING.contains(new Transition(from, to));
    }

    public static Set<OrderStatus> nextStatuses(OrderStatus from, Actor actor) {
        return EnumSet.allOf(OrderStatus.class).stream()
                .filter(to -> isAllowed(from, to, actor))
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(OrderStatus.class)));
    }
}
