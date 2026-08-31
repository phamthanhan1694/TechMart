package dev.techmart.common.web;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Cau truc phan trang rieng, khong tra thang {@code Page} cua Spring.
 *
 * <p>Ly do: Page lo nhieu field thua (pageable, sort, first/last, numberOfElements) va
 * cau truc do khong on dinh giua cac ban Spring - tra thang ra API la buoc client
 * phu thuoc vao chi tiet noi bo cua framework.
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
