package dev.techmart.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Ep gioi han kich thuoc trang.
 *
 * <p>Khong co gioi han thi mot request {@code ?size=1000000} du de ha service -
 * DB tra ve hang trieu dong va app dung het bo nho khi map sang DTO.
 */
public final class PageableSanitizer {

    public static final int MAX_PAGE_SIZE = 100;

    private PageableSanitizer() {
    }

    public static Pageable sanitize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }
}
