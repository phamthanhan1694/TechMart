package dev.techmart.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Cấu trúc phần trang riêng, không trả thang {@code Page} của Spring.
 *
 * <p>Lý do: Page lo nhiều field thua (pageable, sort, first/last, numberOfElements) va
 * cấu trúc đó không on dinh giữa các ban Spring - trả thang ra API là buoc client
 * phụ thuộc vào chi tiết nội bộ của framework.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext());
    }

    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }
}
