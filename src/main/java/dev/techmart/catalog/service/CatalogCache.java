package dev.techmart.catalog.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p><b>Bai toan kho nhat cua cache khong phai doc nhanh, ma la xoa dung luc.</b>
 * Trang danh sach san pham co vo so bien the khoa (tu khoa, bo loc spec, khoang gia,
 * sap xep, so trang) - admin sua mot san pham thi khong the biet phai xoa nhung khoa nao.
 *
 * <p>Cach giai o day la <b>bo dem the he (generation counter)</b>: moi thay doi catalog
 * tang {@code catalog:gen} len mot. Khoa cache danh sach co chua so the he, nen toan bo
 * cache cu tro thanh <i>khong the cham toi</i> ngay lap tuc, khong can SCAN hay xoa hang loat.
 * Cac khoa cu tu het han theo TTL roi bien mat.
 *
 * <p>Trang chi tiet thi don gian hon: khoa la slug, xoa truc tiep duoc.
 *
 * <p><b>Fail-open:</b> Redis loi thi tinh lai tu DB. Cache la thu tang toc, khong phai
 * nguon su that - cung triet ly voi reservation (ADR-0004).
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
     * Doc tu cache, khong co thi goi {@code loader} va ghi lai.
     *
     * <p>Day la toan bo pattern cache-aside: ung dung tu quan ly cache, DB khong biet
     * cache ton tai. Khac voi read-through (cache tu goi DB) - cache-aside don gian hon
     * va khong phu thuoc thu vien cache nao.
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
     * Goi sau MOI thay doi catalog. Tang the he lam moi khoa danh sach cu tro thanh
     * khong the cham toi, va xoa thang khoa chi tiet cua san pham vua doi.
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
            // Redis chet -> the he 0, va read() ben duoi cung se fail-open
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
            // Doi dinh dang DTO lam ban ghi cu khong doc duoc - bo qua, coi nhu cache miss
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

    /** Bam khoa de do dai co dinh: chuoi truy van co the rat dai va Redis khong thich khoa dai. */
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
