package dev.techmart.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Cấu trúc phân trang riêng, không trả thẳng {@code Page} của Spring.
 *
 * <p>Lý do: Page lộ nhiều field thừa (pageable, sort, first/last, numberOfElements) và
 * cấu trúc đó không ổn định giữa các bản Spring - trả thẳng ra API là buộc client
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
