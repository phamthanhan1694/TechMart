package dev.techmart.inventory.service;

import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.repository.ProductVariantRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Khoa bi quan cac variant truoc khi doi ton kho.
 *
 * <p><b>Quy tac bat buoc voi moi noi goi:</b> khong duoc nap ProductVariant vao persistence
 * context truoc khi goi method nay. Cu the la khong {@code JOIN FETCH} sang variant o cac
 * query cua luong ghi (gio hang khi checkout, reservation khi dat hang) - de variant o dang
 * proxy lazy va de chinh cau query khoa nap no lan dau.
 *
 * <p><b>Vi sao - loi that da gap va da sua:</b> luong giu hang truoc day nap gio hang bang
 * {@code LEFT JOIN FETCH i.variant}, roi moi chay cau query co {@code FOR UPDATE}. Khi mot
 * entity da nam trong persistence context voi version cu ma query co lock mode tra ve dong
 * do voi version moi hon, Hibernate <b>nem StaleObjectStateException ngay tai cau query khoa</b>
 * (no tu choi ghi de state dang cho flush). Ket qua trong bai test 200 nguoi mua dong thoi:
 * 199/200 request that bai voi ObjectOptimisticLockingFailureException. Khong he oversell -
 * nhung cung khong ban duoc hang. Khoa dung ma van hong.
 *
 * <p>Luu y them: goi {@code em.refresh()} sau cau query khoa KHONG cuu duoc, vi ngoai le
 * da nem ra tu chinh cau query do roi. Thu tu nap moi la thu phai sua.
 */
@Component
public class VariantLocker {

    private final ProductVariantRepository variants;

    public VariantLocker(ProductVariantRepository variants) {
        this.variants = variants;
    }

    /**
     * {@code SELECT ... ORDER BY id FOR UPDATE} trong mot cau query.
     *
     * <p>{@code ORDER BY id} chan deadlock: don A mua [variant 5, variant 9] va don B mua
     * [variant 9, variant 5] neu khoa theo thu tu client gui thi A giu 5 cho 9, B giu 9 cho 5.
     * Sap theo id lam moi transaction khoa cung mot thu tu.
     */
    public List<ProductVariant> lockAndRefresh(Collection<Long> variantIds) {
        return variants.lockAllByIdOrdered(variantIds);
    }
}
