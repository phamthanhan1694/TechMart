package dev.techmart.cart.domain;

import dev.techmart.catalog.domain.ProductVariant;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cart_items")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Column(nullable = false)
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CartItem() {
    }

    CartItem(Cart cart, ProductVariant variant, int quantity) {
        this.cart = cart;
        this.variant = variant;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Cart getCart() { return cart; }
    public ProductVariant getVariant() { return variant; }
    public Integer getQuantity() { return quantity; }

    /** Giá hiện tại x số lượng. Không phải giá đã chốt - việc chốt xảy ra ở Order. */
    public BigDecimal lineTotal() {
        return variant.getPrice().multiply(BigDecimal.valueOf(quantity));
    }

    void increase(int amount) {
        this.quantity += amount;
    }

    public void changeQuantity(int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("Số lượng phải >= 1");
        }
        this.quantity = quantity;
    }
}
