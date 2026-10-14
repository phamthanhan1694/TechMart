package dev.techmart.catalog.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import dev.techmart.common.config.AppProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Cache-aside cho catalog.
 *
 * <p><b>Bài toán kho nhất của cache không phải đọc nhanh, mà là xoá đúng lúc.</b>
 * Trang danh sách sản phẩm có vô số biến thể khoá (từ khoá, bộ lọc spec, khoảng giá,
 * sắp xếp, số trang) - admin sửa một sản phẩm thì không thể biết phải xoá những khoá nào.
 *
 * <p>Cách giải ở đây là <b>bộ đếm thế hệ (generation counter)</b>: mọi thay đổi catalog
 * tầng {@code catalog:gen} lên một. Khoá cache danh sách có chứa số thế hệ, nên toàn bộ
 * cache cũ trở thành <i>không thể chạm tới</i> ngay lập tức, không cần SCAN hay xoá hàng loạt.
 * Các khoá cũ từ hết hạn theo TTL rồi biến mat.
 *
 * <p>Trang chi tiết thì đơn gian hơn: khoá là slug, xoá truc tiep được.
 *
 * <p><b>Fail-open:</b> Redis lỗi thì tính lại từ DB. Cache là thứ tăng tốc, không phải
 * nguon su thật - cũng triet ly với reservation (ADR-0004).
 */
@Component
public class CatalogCache {

    private static final Logger log = LoggerFactory.getLogger(CatalogCache.class);

    private static final String GENERATION_KEY = "catalog:gen";
    private static final String LIST_PREFIX = "catalog:list:";
    private static final String DETAIL_PREFIX = "catalog:detail:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties props;
    private final Counter hits;
    private final Counter misses;

    public CatalogCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                        AppProperties props, MeterRegistry registry) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
        this.hits = Counter.builder("techmart.catalog.cache")
                .tag("result", "hit").description("So lan doc duoc tu cache").register(registry);
        this.misses = Counter.builder("techmart.catalog.cache")
                .tag("result", "miss").description("So lan phai truy van DB").register(registry);
    }

    public boolean isEnabled() {
        return props.cache().productsEnabled();
    }

    /**
     * Doc từ cache, không có thì gọi {@code loader} và ghi lại.
     *
     * <p>Đây là toàn bộ pattern cache-aside: ứng dụng từ quản lý cache, DB không biet
     * cache tồn tại. Khac với read-through (cache từ gọi DB) - cache-aside đơn gian hơn
     * và không phụ thuộc thư viện cache nao.
     */
    public <T> T listOrLoad(String rawKey, TypeReference<T> type, Supplier<T> loader) {
        if (!isEnabled()) {
            return loader.get();
        }
        String key = LIST_PREFIX + generation() + ":" + hash(rawKey);
        Optional<T> cached = read(key, type);
        if (cached.isPresent()) {
            hits.increment();
            return cached.get();
        }
        misses.increment();
        T value = loader.get();
        write(key, value, props.cache().listTtl());
        return value;
    }

    public <T> T detailOrLoad(String slug, TypeReference<T> type, Supplier<T> loader) {
        if (!isEnabled()) {
            return loader.get();
        }
        String key = DETAIL_PREFIX + slug;
        Optional<T> cached = read(key, type);
        if (cached.isPresent()) {
            hits.increment();
            return cached.get();
        }
        misses.increment();
        T value = loader.get();
        write(key, value, props.cache().detailTtl());
        return value;
    }

    /**
     * Gọi sau MỖI thay đổi catalog. Tầng thế hệ làm mọi khoá danh sách cũ trở thành
     * không thể chạm tới, và xoá thẳng khoá chi tiết của sản phẩm vừa đổi.
     */
    public void invalidate(String slug) {
        if (!isEnabled()) {
            return;
        }
        try {
            redis.opsForValue().increment(GENERATION_KEY);
            if (slug != null) {
                redis.delete(DETAIL_PREFIX + slug);
            }
        } catch (DataAccessException e) {
            log.warn("Khong xoa duoc cache catalog: {}", e.getMessage());
        }
    }

    private long generation() {
        try {
            String value = redis.opsForValue().get(GENERATION_KEY);
            return value == null ? 0L : Long.parseLong(value);
        } catch (DataAccessException | NumberFormatException e) {
            // Redis chết -> thế hệ 0, và read() bên dưới cũng sẽ fail-open
            return 0L;
        }
    }

    private <T> Optional<T> read(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, type));
        } catch (DataAccessException e) {
            log.warn("Redis khong kha dung, bo qua cache: {}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            // Đổi định dạng DTO làm bản ghi cũ không đọc được - bỏ qua, coi như cache miss
            log.warn("Khong doc duoc cache {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void write(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (DataAccessException e) {
            log.warn("Redis khong kha dung, khong ghi duoc cache: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Khong ghi duoc cache {}: {}", key, e.getMessage());
        }
    }

    /** Băm khoá để độ dài cố định: chuỗi truy vấn có thể rất dài và Redis không thích khoá dài. */
    private static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (Exception e) {
            return String.valueOf(raw.hashCode());
        }
    }
}
