package dev.techmart.catalog.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Tim kiem san pham bang native SQL.
 *
 * <p><b>Vi sao native chu khong Criteria API:</b> bo loc chay tren cot JSONB
 * ({@code spec @> '{"ram":"16GB"}'}) de dung duoc GIN index. Criteria API khong dien dat
 * duoc toan tu {@code @>} ma khong dang ky ham tuy bien, va lam vong qua se mat index.
 *
 * <p><b>Chong SQL injection:</b> moi gia tri deu di qua tham so rang buoc; rieng ten cot
 * sap xep khong the tham so hoa nen phai whitelist - xem {@link #SORT_COLUMNS}.
 *
 * <p><b>Luu y:</b> {@code @SQLRestriction} cua Hibernate KHONG ap dung cho native query,
 * nen dieu kien {@code deleted_at IS NULL} phai tu viet o day.
 */
public class ProductRepositoryImpl implements ProductRepositoryCustom {

    /** Whitelist cot sap xep. Ghep thang chuoi tu client vao ORDER BY la lo hong injection. */
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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Page<Product> search(ProductSearchCriteria c, Pageable pageable) {
        StringBuilder where = new StringBuilder();
        Map<String, Object> params = new HashMap<>();

        if (c.keyword() != null && !c.keyword().isBlank()) {
            // ILIKE %kw% dung duoc GIN trigram index da tao o V1__init.sql
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
                // Bo qua truong khong nam trong whitelist thay vi nem loi:
                // client gui sort la thu khong nen lam sap request.
                continue;
            }
            clauses.add(column + (order.isAscending() ? " ASC" : " DESC"));
        }
        return clauses.isEmpty() ? DEFAULT_ORDER : String.join(", ", clauses);
    }

    private String toJson(Map<String, String> spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Khong the chuyen bo loc spec sang JSON", e);
        }
    }

    /** Cac key spec duoc phep loc - kiem o tang web truoc khi vao day. */
    public static Set<String> allowedSpecKeys() {
        return Set.of("ram", "storage", "cpu", "chipset", "gpu", "screen", "os",
                "camera", "battery", "refreshRate", "weight", "releaseYear");
    }
}
