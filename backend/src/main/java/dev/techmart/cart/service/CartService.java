package dev.techmart.cart.service;

import dev.techmart.cart.domain.Cart;
import dev.techmart.cart.domain.CartItem;
import dev.techmart.cart.dto.CartDtos.*;
import dev.techmart.cart.repository.CartRepository;
import dev.techmart.catalog.domain.ProductVariant;
import dev.techmart.catalog.repository.ProductVariantRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static dev.techmart.cart.dto.CartDtos.MAX_QUANTITY_PER_ITEM;

/**
 * Giỏ hàng.
 *
 * <p>Mọi thao tác đều nhận {@code userId} từ SecurityContext ở controller,
 * <b>không bao giờ</b> từ request body. Và {@code itemId} luôn được kiểm có thuộc giỏ
 * của user hiện tại không - nếu không thì trả 404 (không phải 403, vì 403 là gián tiếp
 * xác nhận id đó tồn tại).
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository carts;
    private final ProductVariantRepository variants;
    private final UserRepository users;

    @Transactional
    public CartResponse getCart(Long userId) {
        return CartResponse.from(loadOrCreate(userId));
    }

    @Transactional
    public CartResponse addItem(Long userId, AddItemRequest req) {
        Cart cart = loadOrCreate(userId);
        ProductVariant variant = variants.findById(req.variantId())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.VARIANT_NOT_FOUND, req.variantId()));

        CartItem item = cart.addOrIncrease(variant, req.quantity());

        if (item.getQuantity() > MAX_QUANTITY_PER_ITEM) {
            throw new BusinessException(ErrorCode.QUANTITY_EXCEEDS_LIMIT,
                    "Tối đa " + MAX_QUANTITY_PER_ITEM + " sản phẩm mỗi dòng");
        }
        // Kiểm tra ở đây chỉ để báo sớm cho khách. Ràng buộc thật sự được áp ở lúc giữ hàng
        // và lúc đặt hàng - giữa hai thời điểm đó tồn kho có thể đổi.
        if (item.getQuantity() > variant.availableQuantity()) {
            throw BusinessException.insufficientStock(Map.of(
                    "variantId", variant.getId(),
                    "sku", variant.getSku(),
                    "requested", item.getQuantity(),
                    "available", variant.availableQuantity()));
        }
        carts.save(cart);
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse updateItem(Long userId, Long itemId, UpdateItemRequest req) {
        Cart cart = loadOrCreate(userId);
        CartItem item = requireOwnItem(cart, itemId);

        if (req.quantity() > item.getVariant().availableQuantity()) {
            throw BusinessException.insufficientStock(Map.of(
                    "variantId", item.getVariant().getId(),
                    "sku", item.getVariant().getSku(),
                    "requested", req.quantity(),
                    "available", item.getVariant().availableQuantity()));
        }
        item.changeQuantity(req.quantity());
        return CartResponse.from(cart);
    }

    @Transactional
    public CartResponse removeItem(Long userId, Long itemId) {
        Cart cart = loadOrCreate(userId);
        cart.removeItem(requireOwnItem(cart, itemId));
        return CartResponse.from(cart);
    }

    @Transactional
    public void clear(Long userId) {
        loadOrCreate(userId).clear();
    }

    /** Dùng bởi OrderService sau khi đặt hàng thành công. */
    @Transactional
    public void clearAfterOrder(Long userId) {
        carts.findByUserIdWithItems(userId).ifPresent(Cart::clear);
    }

    private Cart loadOrCreate(Long userId) {
        return carts.findByUserIdWithItems(userId)
                .orElseGet(() -> carts.save(new Cart(users.getReferenceById(userId))));
    }

    /**
     * Trả 404 chứ không 403 khi item thuộc giỏ của người khác.
     * Xem docs/07-security-checklist.md mục 2.
     */
    private CartItem requireOwnItem(Cart cart, Long itemId) {
        return cart.findItem(itemId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CART_ITEM_NOT_FOUND, itemId));
    }
}
