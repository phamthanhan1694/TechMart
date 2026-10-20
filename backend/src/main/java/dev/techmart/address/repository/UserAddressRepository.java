package dev.techmart.address.repository;

import dev.techmart.address.domain.UserAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    @Query("SELECT a FROM UserAddress a WHERE a.userId = :userId ORDER BY a.isDefault DESC, a.createdAt DESC, a.id DESC")
    List<UserAddress> findByUser(@Param("userId") Long userId);

    long countByUserId(Long userId);

    /**
     * Bỏ cờ mặc định của mọi địa chỉ của người dùng - chạy NGAY, không đợi flush.
     *
     * <p>Phải là câu UPDATE hàng loạt chạy trước khi bật cờ cho địa chỉ mới. Nếu chỉ sửa hai
     * entity trong bộ nhớ, Hibernate có thể phát UPDATE bật cờ mới trước UPDATE tắt cờ cũ -
     * và partial unique index (không thể DEFERRABLE) sẽ báo vi phạm ngay ở câu đầu tiên.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserAddress a SET a.isDefault = false WHERE a.userId = :userId AND a.isDefault = true")
    int clearDefault(@Param("userId") Long userId);
}
