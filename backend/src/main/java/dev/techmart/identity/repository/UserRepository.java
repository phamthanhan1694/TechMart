package dev.techmart.identity.repository;

import dev.techmart.identity.domain.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    /**
     * Khoá mọi ADMIN đang hoạt động trước khi đếm.
     *
     * <p>Không khoá thì hai admin cùng hạ quyền nhau một lúc: mỗi bên đếm thấy còn 2 admin,
     * cả hai cùng hợp lệ, và hệ thống còn 0 admin - không ai vào được khu quản trị để sửa.
     * Cùng một kiểu lỗi "đọc rồi mới ghi" với oversell, và cùng cách chữa: khoá dòng.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT u FROM User u
             WHERE u.role = dev.techmart.identity.domain.Role.ADMIN
               AND u.status = dev.techmart.identity.domain.UserStatus.ACTIVE
             ORDER BY u.id
            """)
    List<User> lockActiveAdmins();
}
