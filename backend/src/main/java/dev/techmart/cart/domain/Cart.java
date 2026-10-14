package dev.techmart.cart.domain;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.identity.domain.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Giỏ hàng của một user.
 *
 * <p>Tong tien KHÔNG luu trong DB - tinh dòng mỗi lần doc. Lý do: giá variant có thể đổi,
 * giỏ hàng phải phần anh giá hiện tại. Gia chỉ được "chot" khi tạo đơn hàng.
 *
 * <p>Thêm vào giỏ KHÔNG giữ hàng. Giữ hàng chỉ xay ra ở bước checkout - nếu giữ từ lúc
 * thêm giỏ thì nhưng giỏ bi bo quen hàng tuan sẽ khoá tồn kho vo ich.
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "cart", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
    }

    public Cart(User user) {
        this.user = user;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public List<CartItem> getItems() { return items; }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public BigDecimal subtotal() {
        return items.stream()
                .map(CartItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Optional<CartItem> findItemByVariant(Long variantId) {
        return items.stream()
                .filter(i -> i.getVariant().getId().equals(variantId))
                .findFirst();
    }

    public Optional<CartItem> findItem(Long itemId) {
        return items.stream().filter(i -> i.getId().equals(itemId)).findFirst();
    }

    /** Thêm variant da có trong giỏ thì cong đơn số lượng, không tạo dòng thứ hai. */
    public CartItem addOrIncrease(ProductVariant variant, int quantity) {
        return findItemByVariant(variant.getId())
                .map(existing -> {
                    existing.increase(quantity);
                    return existing;
                })
                .orElseGet(() -> {
                    CartItem item = new CartItem(this, variant, quantity);
                    items.add(item);
                    return item;
                });
    }

    public void removeItem(CartItem item) {
        items.remove(item);   // orphanRemoval = true -> bi xoá khoi DB
    }

    public void clear() {
        items.clear();
    }
}
