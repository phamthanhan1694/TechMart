package dev.techmart.order.domain;

import dev.techmart.identity.domain.Role;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static dev.techmart.order.domain.OrderStatus.*;

/**
 * Bang chuyen trang thai don hang - nguon su that duy nhat.
 *
 * <p>Moi lenh doi trang thai deu phai di qua day. Khong rai {@code if (status == ...)}
 * khap noi: rai ra thi moi lan them trang thai moi la mot lan bo sot mot nhanh.
 *
 * <p>7 trang thai = 42 cap co the; chi 9 cap hop le, 33 cap con lai phai bi chan
 * bang 409 INVALID_STATE_TRANSITION. Xem docs/02-luong-nghiep-vu.md muc 1.
 */
public final class OrderStateMachine {

    /** Ai duoc phep thuc hien chuyen trang thai. SYSTEM = webhook thanh toan, job tu dong. */
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
            // Thanh toan thanh cong - chi webhook cua cong thanh toan duoc phep.
            // Khach TU DOI don sang PAID la lo hong "mua hang mien phi".
            Map.entry(new Transition(PENDING_PAYMENT, PAID), EnumSet.of(Actor.SYSTEM)),

            // Khach tu huy, hoac job quet don qua han thanh toan
            Map.entry(new Transition(PENDING_PAYMENT, CANCELLED),
                    EnumSet.of(Actor.CUSTOMER, Actor.ADMIN, Actor.SYSTEM)),

            Map.entry(new Transition(PAID, PACKED), EnumSet.of(Actor.STAFF, Actor.ADMIN)),
            Map.entry(new Transition(PAID, CANCELLED), EnumSet.of(Actor.ADMIN)),

            Map.entry(new Transition(PACKED, SHIPPED), EnumSet.of(Actor.STAFF, Actor.ADMIN)),

            Map.entry(new Transition(SHIPPED, DELIVERED), EnumSet.of(Actor.STAFF, Actor.ADMIN)),
            Map.entry(new Transition(SHIPPED, REFUNDED), EnumSet.of(Actor.ADMIN)),

            Map.entry(new Transition(DELIVERED, REFUNDED), EnumSet.of(Actor.ADMIN))
    );

    /** Cac chuyen doi lam hang quay lai kho. */
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

    /** Chuyen doi nay co phai hoan hang ve kho khong. */
    public static boolean restocks(OrderStatus from, OrderStatus to) {
        return RESTOCKING.contains(new Transition(from, to));
    }

    public static Set<OrderStatus> nextStatuses(OrderStatus from, Actor actor) {
        return EnumSet.allOf(OrderStatus.class).stream()
                .filter(to -> isAllowed(from, to, actor))
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(OrderStatus.class)));
    }
}
