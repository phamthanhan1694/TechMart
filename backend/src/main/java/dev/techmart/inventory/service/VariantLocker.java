package dev.techmart.inventory.service;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.repository.ProductVariantRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Khoá bi quan các variant trước khi đổi tồn kho.
 *
 * <p><b>Quy tắc bắt buộc với mọi nơi gọi:</b> không được nạp ProductVariant vào persistence
 * context trước khi gọi method này. Cụ thể là không {@code JOIN FETCH} sang variant ở các
 * query của luồng ghi (giỏ hàng khi checkout, reservation khi đặt hàng) - để variant ở dạng
 * proxy lazy và để chính câu query khoá nạp nó lần đầu.
 *
 * <p><b>Vì sao - lỗi thật đã gặp và đã sửa:</b> luồng giữ hàng trước đây nạp giỏ hàng bằng
 * {@code LEFT JOIN FETCH i.variant}, rồi mới chạy câu query có {@code FOR UPDATE}. Khi một
 * entity đã nằm trong persistence context với version cũ mà query có lock mode trả về dòng
 * đó với version mới hơn, Hibernate <b>ném StaleObjectStateException ngay tại câu query khoá</b>
 * (nó từ chối ghi để state đang chờ flush). Kết quả trong bài test 200 người mua đồng thời:
 * 199/200 request thất bại với ObjectOptimisticLockingFailureException. Không hề oversell -
 * nhưng cũng không bán được hàng. Khoá đúng mà vẫn hỏng.
 *
 * <p>Lưu ý thêm: gọi {@code em.refresh()} sau câu query khoá KHÔNG cứu được, vì ngoại lệ
 * đã ném ra từ chính câu query đó rồi. Thứ tự nạp mới là thứ phải sửa.
 */
@Component
public class VariantLocker {

    private final ProductVariantRepository variants;

    public VariantLocker(ProductVariantRepository variants) {
        this.variants = variants;
    }

    /**
     * {@code SELECT ... ORDER BY id FOR UPDATE} trong một câu query.
     *
     * <p>{@code ORDER BY id} chặn deadlock: đơn A mua [variant 5, variant 9] và đơn B mua
     * [variant 9, variant 5] nếu khoá theo thứ tự client gửi thì A giữ 5 chờ 9, B giữ 9 chờ 5.
     * Sắp theo id làm mọi transaction khoá cùng một thứ tự.
     */
    public List<ProductVariant> lockAndRefresh(Collection<Long> variantIds) {
        return variants.lockAllByIdOrdered(variantIds);
    }
}
