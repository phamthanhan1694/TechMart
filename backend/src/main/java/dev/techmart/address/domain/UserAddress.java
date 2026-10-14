package dev.techmart.address.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * Một địa chỉ trong sổ địa chỉ của khách.
 *
 * <p>Đơn hàng KHÔNG tham chiếu tới bảng này: {@code orders.shipping_address} vẫn là bản
 * chụp JSONB tại lúc đặt. Khách sửa hay xoá địa chỉ trong sổ thì đơn cũ vẫn phải in ra
 * đúng địa chỉ đã giao - cùng lý do {@code order_items} chụp lại tên và giá sản phẩm.
 */
@Entity
@Table(name = "user_addresses")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(length = 50)
    private String label;

    @Column(name = "receiver_name", nullable = false, length = 150)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false)
    private String street;

    @Column(nullable = false, length = 100)
    private String ward;

    @Column(nullable = false, length = 100)
    private String district;

    @Column(nullable = false, length = 100)
    private String province;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAddress() {
    }

    public UserAddress(Long userId) {
        this.userId = userId;
    }

    public void update(String label, String receiverName, String phone, String street,
                       String ward, String district, String province) {
        this.label = label;
        this.receiverName = receiverName;
        this.phone = phone;
        this.street = street;
        this.ward = ward;
        this.district = district;
        this.province = province;
    }

    public void markDefault() { this.isDefault = true; }

    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getLabel() { return label; }
    public String getReceiverName() { return receiverName; }
    public String getPhone() { return phone; }
    public String getStreet() { return street; }
    public String getWard() { return ward; }
    public String getDistrict() { return district; }
    public String getProvince() { return province; }
    public boolean isDefault() { return isDefault; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserAddress other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass());
    }
}
