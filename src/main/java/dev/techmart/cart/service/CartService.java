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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static dev.techmart.cart.dto.CartDtos.MAX_QUANTITY_PER_ITEM;

/**
 * Gio hang.
 *
 * <p>Moi thao tac deu nhan {@code userId} tu SecurityContext o controller,
 * <b>khong bao gio</b> tu request body. Va {@code itemId} luon duoc kiem co thuoc gio
 * cua user hien tai khong - neu khong thi tra 404 (khong phai 403, vi 403 la gian tiep
 * xac nhan id do ton tai).
 */
@Service
public class CartService {

    private final CartRepository carts;
    private final ProductVariantRepository variants;
    private final UserRepository users;

    public CartService(CartRepository carts, ProductVariantRepository variants, UserRepository users) {
        this.carts = carts;
        this.variants = variants;
        this.users = users;
    }

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
                    "Toi da " + MAX_QUANTITY_PER_ITEM + " san pham moi dong");
        }
        // Kiem tra o day chi de bao som cho khach. Rang buoc that su duoc ap luc giu hang
        // va luc dat hang - giua hai thoi diem do ton kho co the doi.
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

    /** Dung boi OrderService sau khi dat hang thanh cong. */
    @Transactional
    public void clearAfterOrder(Long userId) {
        carts.findByUserIdWithItems(userId).ifPresent(Cart::clear);
    }

    private Cart loadOrCreate(Long userId) {
        return carts.findByUserIdWithItems(userId)
                .orElseGet(() -> carts.save(new Cart(users.getReferenceById(userId))));
    }

    /**
     * Tra 404 chu khong 403 khi item thuoc gio cua nguoi khac.
     * Xem docs/07-security-checklist.md muc 2.
     */
    private CartItem requireOwnItem(Cart cart, Long itemId) {
        return cart.findItem(itemId)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.CART_ITEM_NOT_FOUND, itemId));
    }
}
