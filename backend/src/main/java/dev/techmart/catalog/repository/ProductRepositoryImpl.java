package dev.techmart.catalog.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import dev.techmart.catalog.domain.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tìm kiếm sản phẩm bằng native SQL.
 *
 * <p><b>Vì sao native chứ không Criteria API:</b> bộ lọc chạy trên cột JSONB
 * ({@code spec @> '{"ram":"16GB"}'}) để dùng được GIN index. Criteria API không diễn đạt
 * được toán tử {@code @>} mà không dạng ký hàm tuỳ biến, và làm vòng qua sẽ mất index.
 *
 * <p><b>Chống SQL injection:</b> mọi giá trị đều đi qua tham số ràng buộc; riêng tên cột
 * sắp xếp không thể tham số hoá nên phải whitelist - xem {@link #SORT_COLUMNS}.
 *
 * <p><b>Lưu ý:</b> {@code @SQLRestriction} của Hibernate KHÔNG áp đúng chỗ native query,
 * nên điều kiện {@code deleted_at IS NULL} phải tự viết ở đây.
 */
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    /** Whitelist cột sắp xếp. Ghép thẳng chuỗi từ client vào ORDER BY là lỗ hổng injection. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "name", "p.name",
            "createdAt", "p.created_at",
            "price", "(SELECT MIN(v.price) FROM product_variants v "
                    + "WHERE v.product_id = p.id AND v.deleted_at IS NULL)");

    private static final String DEFAULT_ORDER = "p.created_at DESC";

    private static final String BASE_FROM = """
            FROM products p
            WHERE p.deleted_at IS NULL
              AND p.status = 'ACTIVE'
            """;

    @PersistenceContext
    private EntityManager em;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Override
    public Page<Product> search(ProductSearchCriteria c, Pageable pageable) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        if (c.keyword() != null && !c.keyword().isBlank()) {
            // ILIKE %kw% dùng được GIN trigram index đã tạo ở V1__init.sql
            where.append(" AND p.name ILIKE :keyword");
            params.put("keyword", "%" + c.keyword().trim() + "%");
        }
        if (c.categoryId() != null) {
            where.append(" AND p.category_id = :categoryId");
            params.put("categoryId", c.categoryId());
        }
        if (c.brandId() != null) {
            where.append(" AND p.brand_id = :brandId");
            params.put("brandId", c.brandId());
        }
        if (c.hasSpecFilter()) {
            where.append(" AND p.spec @> CAST(:specFilter AS jsonb)");
            params.put("specFilter", toJson(c.spec()));
        }
        if (c.minPrice() != null || c.maxPrice() != null || Boolean.TRUE.equals(c.inStock())) {
            where.append(" AND EXISTS (SELECT 1 FROM product_variants v"
                    + " WHERE v.product_id = p.id AND v.deleted_at IS NULL");
            if (c.minPrice() != null) {
                where.append(" AND v.price >= :minPrice");
                params.put("minPrice", c.minPrice());
            }
            if (c.maxPrice() != null) {
                where.append(" AND v.price <= :maxPrice");
                params.put("maxPrice", c.maxPrice());
            }
            if (Boolean.TRUE.equals(c.inStock())) {
                where.append(" AND v.stock_quantity - v.reserved_quantity > 0");
            }
            where.append(")");
        }

        String sql = "SELECT p.* " + BASE_FROM + where + " ORDER BY " + orderBy(pageable.getSort());
        Query query = em.createNativeQuery(sql, Product.class);
        params.forEach(query::setParameter);
        query.setFirstResult((int) pageable.getOffset());
        query.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Product> content = query.getResultList();

        Query countQuery = em.createNativeQuery("SELECT count(*) " + BASE_FROM + where);
        params.forEach(countQuery::setParameter);
        long total = ((Number) countQuery.getSingleResult()).longValue();

        return new PageImpl<>(content, pageable, total);
    }

    private String orderBy(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return DEFAULT_ORDER;
        }
        List<String> clauses = new ArrayList<>();
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                // Bỏ qua trường không nằm trong whitelist thay vì ném lỗi:
                // client gửi sort là thứ không nên làm sập request.
                continue;
            }
            clauses.add(column + (order.isAscending() ? " ASC" : " DESC"));
        }
        return clauses.isEmpty() ? DEFAULT_ORDER : String.join(", ", clauses);
    }

    private String toJson(Map<String, String> spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (JacksonException e) {   // Jackson 3: ngoại lệ không còn là checked exception
            throw new IllegalArgumentException("Khong the chuyen bo loc spec sang JSON", e);
        }
    }

    /** Các key spec được phép lọc - kiểm ở tầng web trước khi vào đây. */
    public static Set<String> allowedSpecKeys() {
        return Set.of("ram", "storage", "cpu", "chipset", "gpu", "screen", "os",
                "camera", "battery", "refreshRate", "weight", "releaseYear");
    }
}
