package dev.techmart.address.service;

import dev.techmart.address.domain.UserAddress;
import dev.techmart.address.dto.AddressDtos.AddressRequest;
import dev.techmart.address.dto.AddressDtos.AddressResponse;
import dev.techmart.address.repository.UserAddressRepository;
import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sổ địa chỉ giao hàng.
 *
 * <p>Bất biến: người có ít nhất một địa chỉ thì có ĐÚNG một địa chỉ mặc định. Service giữ
 * phần "ít nhất một" (địa chỉ đầu tiên tự thành mặc định, xoá mặc định thì đẩy cái mới
 * nhất lên); DB giữ phần "không quá một" bằng partial unique index.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    /** Đủ cho nhà riêng, công ty, nhà bố mẹ... Không giới hạn thì một script ghi được triệu dòng. */
    public static final int MAX_ADDRESSES = 10;

    private final UserAddressRepository addresses;

    @Transactional(readOnly = true)
    public List<AddressResponse> list(Long userId) {
        return addresses.findByUser(userId).stream().map(AddressResponse::from).toList();
    }

    /**
     * Thêm địa chỉ. Đếm-rồi-thêm không khoá nên hai request song song có thể vượt trần một
     * chút - chấp nhận: trần ở đây để chặn lạm dụng, không phải ràng buộc nghiệp vụ chính xác.
     */
    @Transactional
    public AddressResponse create(Long userId, AddressRequest req) {
        long existing = addresses.countByUserId(userId);
        if (existing >= MAX_ADDRESSES) {
            throw new BusinessException(ErrorCode.ADDRESS_LIMIT_EXCEEDED,
                    "Tối đa " + MAX_ADDRESSES + " địa chỉ");
        }
        boolean makeDefault = existing == 0 || req.makeDefault();
        if (makeDefault) {
            addresses.clearDefault(userId);
        }
        UserAddress address = new UserAddress(userId);
        apply(address, req);
        if (makeDefault) {
            address.markDefault();
        }
        return AddressResponse.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public AddressResponse update(Long userId, Long id, AddressRequest req) {
        if (req.makeDefault()) {
            requireOwned(userId, id);        // kiểm quyền TRƯỚC khi đụng vào địa chỉ khác
            addresses.clearDefault(userId);
        }
        UserAddress address = requireOwned(userId, id);
        apply(address, req);
        if (req.makeDefault()) {
            address.markDefault();
        }
        return AddressResponse.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public AddressResponse setDefault(Long userId, Long id) {
        requireOwned(userId, id);
        addresses.clearDefault(userId);
        UserAddress address = requireOwned(userId, id);   // nạp lại: clearDefault đã xoá persistence context
        address.markDefault();
        return AddressResponse.from(addresses.saveAndFlush(address));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        UserAddress address = requireOwned(userId, id);
        boolean wasDefault = address.isDefault();
        addresses.delete(address);
        addresses.flush();

        if (wasDefault) {
            // Còn địa chỉ nào thì địa chỉ mới nhất lên làm mặc định - giữ bất biến "đúng một"
            addresses.findByUser(userId).stream().findFirst().ifPresent(UserAddress::markDefault);
        }
    }

    /** Địa chỉ của người khác trả 404, không phải 403 - như mọi tài nguyên sở hữu khác. */
    private UserAddress requireOwned(Long userId, Long id) {
        return addresses.findById(id)
                .filter(a -> a.isOwnedBy(userId))
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.ADDRESS_NOT_FOUND, id));
    }

    private static void apply(UserAddress address, AddressRequest req) {
        address.update(blankToNull(req.label()), req.receiverName().trim(), req.phone().trim(),
                req.street().trim(), req.ward().trim(), req.district().trim(), req.province().trim());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
