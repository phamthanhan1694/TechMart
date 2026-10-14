package dev.techmart.flashsale.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Một phiên mo ban giới hạn.
 *
 * <p>Đây là noi mo hinh tai khác han phần còn lại của hệ thống: vài nghìn request/giây
 * đó vào <b>cùng một dòng</b> dữ liệu trong vai giây dau. Khoá bi quan o DB không chiu noi -
 * mọi request xếp hàng chờ cùng một row lock, connection pool căng, và các API khác chết theo.
 * Vì vậy phiên này tru ton bằng Redis Lua script (xem {@code FlashSaleStockScript}).
 */
@Entity
@Table(name = "flash_sales")
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FlashSaleStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "flashSale", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlashSaleItem> items = new ArrayList<>();

    protected FlashSale() {
    }

    public FlashSale(String name, Instant startsAt, Instant endsAt) {
        this.name = name;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = FlashSaleStatus.SCHEDULED;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public FlashSaleStatus getStatus() { return status; }
    public List<FlashSaleItem> getItems() { return items; }

    public boolean isRunningAt(Instant now) {
        return !now.isBefore(startsAt) && !now.isAfter(endsAt);
    }

    public boolean hasEndedAt(Instant now) {
        return now.isAfter(endsAt);
    }

    public void addItem(FlashSaleItem item) {
        items.add(item);
        item.attachTo(this);
    }

    public void markRunning() { this.status = FlashSaleStatus.RUNNING; }

    public void markEnded() { this.status = FlashSaleStatus.ENDED; }
}
