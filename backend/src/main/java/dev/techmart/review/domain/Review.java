package dev.techmart.review.domain;

import dev.techmart.catalog.domain.Product;
import dev.techmart.identity.domain.User;
import dev.techmart.order.domain.OrderItem;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Đánh giá sản phẩm, gắn với một dòng đơn hàng cụ thể.
 *
 * <p><b>Gắn với {@code order_item} chứ không phải {@code product}</b> - đây là điểm thiết kế
 * chính. Ràng buộc {@code UNIQUE(order_item_id)} ở DB biến "chỉ đánh giá được sản phẩm
 * đã mua, và mỗi lần mua chỉ đánh giá một lần" thành một bất biến của dữ liệu, không phải
 * một điều kiện if trong code có thể quên.
 *
 * <p>Mua lại lần hai thì được đánh giá lại - dùng, vì đó là một trải nghiệm mua hàng khác.
 */
@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false, unique = true)
    private OrderItem orderItem;

    @Column(nullable = false)
    private Integer rating;

    @Column(length = 2000)
    private String comment;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(Product product, User user, OrderItem orderItem, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5");
        }
        this.product = product;
        this.user = user;
        this.orderItem = orderItem;
        this.rating = rating;
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Product getProduct() { return product; }
    public User getUser() { return user; }
    public OrderItem getOrderItem() { return orderItem; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public void edit(int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Điểm đánh giá phải từ 1 đến 5");
        }
        this.rating = rating;
        this.comment = comment;
    }
}
