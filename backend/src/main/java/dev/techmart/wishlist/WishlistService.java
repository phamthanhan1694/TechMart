package dev.techmart.wishlist;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.wishlist.WishlistDtos.WishlistItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Danh sách yêu thích.
 *
 * <p><b>Viết bằng JDBC, không có entity.</b> Thao tác chính là "thêm nếu chưa có" - bằng
 * JPA phải đọc-rồi-mới-ghi, hai tab bấm tim cùng lúc sẽ có một bên đụng UNIQUE và nhận
 * lỗi 409 cho một thao tác lẽ ra vô hại. {@code INSERT ... ON CONFLICT DO NOTHING} làm việc
 * đó trong một câu, không race. Đổi lại mất kiểm tra ánh xạ lúc khởi động của Hibernate
 * ({@code ddl-auto: validate}) cho bảng này - test tích hợp bù vào chỗ đó.
 *
 * <p>Sản phẩm bị ngừng bán vẫn nằm trong danh sách, gắn cờ {@code purchasable=false}: tự
 * biến mất khỏi danh sách của khách là cách tệ nhất để báo tin "hết bán".
 */
@Service
@RequiredArgsConstructor
public class WishlistService {

    public static final int MAX_ITEMS = 100;

    private final NamedParameterJdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public List<WishlistItemResponse> list(Long userId) {
        return jdbc.query("""
                SELECT p.id, p.name, p.slug, p.thumbnail_url, b.name AS brand_name,
                       (p.deleted_at IS NULL AND p.status = 'ACTIVE') AS purchasable,
                       MIN(v.price) AS min_price,
                       COALESCE(SUM(v.stock_quantity - v.reserved_quantity), 0) AS available,
                       w.created_at
                  FROM wishlist_items w
                  JOIN products p ON p.id = w.product_id
                  JOIN brands b   ON b.id = p.brand_id
                  LEFT JOIN product_variants v ON v.product_id = p.id AND v.deleted_at IS NULL
                 WHERE w.user_id = :userId
                 GROUP BY p.id, b.name, w.created_at, w.id
                 ORDER BY w.created_at DESC, w.id DESC
                """, Map.of("userId", userId), (rs, i) -> new WishlistItemResponse(
                rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getString("thumbnail_url"), rs.getString("brand_name"),
                rs.getBigDecimal("min_price"), rs.getInt("available"),
                rs.getBoolean("purchasable"), rs.getTimestamp("created_at").toInstant()));
    }

    /** Chỉ id - để trang danh sách tô tim cho đúng sản phẩm mà không phải kéo cả chi tiết. */
    @Transactional(readOnly = true)
    public List<Long> productIds(Long userId) {
        return jdbc.queryForList("SELECT product_id FROM wishlist_items WHERE user_id = :userId",
                Map.of("userId", userId), Long.class);
    }

    /** Idempotent: thêm sản phẩm đã có trong danh sách trả thành công và không làm gì. */
    @Transactional
    public void add(Long userId, Long productId) {
        Boolean active = jdbc.query("""
                SELECT (deleted_at IS NULL AND status = 'ACTIVE') FROM products WHERE id = :id
                """, Map.of("id", productId), rs -> rs.next() ? rs.getBoolean(1) : null);
        if (!Boolean.TRUE.equals(active)) {
            throw BusinessException.notFound(ErrorCode.PRODUCT_NOT_FOUND, productId);
        }

        long count = Objects.requireNonNull(jdbc.queryForObject(
                "SELECT COUNT(*) FROM wishlist_items WHERE user_id = :userId",
                Map.of("userId", userId), Long.class));
        if (count >= MAX_ITEMS) {
            // Đã có sẵn thì vẫn là "thành công" - chỉ chặn khi thật sự phải thêm dòng mới
            boolean alreadyThere = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM wishlist_items WHERE user_id = :userId AND product_id = :productId)
                    """, Map.of("userId", userId, "productId", productId), Boolean.class));
            if (!alreadyThere) {
                throw new BusinessException(ErrorCode.WISHLIST_LIMIT_EXCEEDED, "Tối đa " + MAX_ITEMS + " sản phẩm");
            }
            return;
        }

        jdbc.update("""
                INSERT INTO wishlist_items (user_id, product_id) VALUES (:userId, :productId)
                ON CONFLICT (user_id, product_id) DO NOTHING
                """, Map.of("userId", userId, "productId", productId));
    }

    /** Idempotent: xoá thứ không có trong danh sách cũng là thành công. */
    @Transactional
    public void remove(Long userId, Long productId) {
        jdbc.update("DELETE FROM wishlist_items WHERE user_id = :userId AND product_id = :productId",
                Map.of("userId", userId, "productId", productId));
    }
}
