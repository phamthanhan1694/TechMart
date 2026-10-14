package dev.techmart.common.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Ép giới hạn kich thuộc trang.
 *
 * <p>Không có giới hạn thì một request {@code ?size=1000000} du để ha service -
 * DB trả về hàng trieu dòng và app dùng het bộ nhớ khi map sang DTO.
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
