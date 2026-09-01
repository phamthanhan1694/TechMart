package dev.techmart.warranty.service;

import dev.techmart.common.error.BusinessException;
import dev.techmart.common.error.ErrorCode;
import dev.techmart.common.web.PageResponse;
import dev.techmart.identity.domain.Role;
import dev.techmart.identity.repository.UserRepository;
import dev.techmart.identity.security.AuthPrincipal;
import dev.techmart.warranty.domain.ProductSerial;
import dev.techmart.warranty.domain.WarrantyRequest;
import dev.techmart.warranty.domain.WarrantyStatus;
import dev.techmart.warranty.dto.WarrantyDtos.*;
import dev.techmart.warranty.repository.ProductSerialRepository;
import dev.techmart.warranty.repository.WarrantyRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
public class WarrantyService {

    private static final Logger log = LoggerFactory.getLogger(WarrantyService.class);
    private static final List<WarrantyStatus> OPEN_STATUSES =
            List.of(WarrantyStatus.REQUESTED, WarrantyStatus.APPROVED);

    private final WarrantyRequestRepository requests;
    private final ProductSerialRepository serials;
    private final UserRepository users;
    private final Clock clock;

    public WarrantyService(WarrantyRequestRepository requests, ProductSerialRepository serials,
                           UserRepository users, Clock clock) {
        this.requests = requests;
        this.serials = serials;
        this.users = users;
        this.clock = clock;
    }

    /**
     * Tao yeu cau bao hanh theo serial.
     *
     * <p>Kiem quyen so huu qua chuoi {@code serial -> order_item -> order -> user}: chi nguoi
     * da mua chinh don vi hang do moi yeu cau bao hanh duoc. Serial cua nguoi khac tra 404.
     */
    @Transactional
    public WarrantyRequestResponse create(AuthPrincipal principal, CreateWarrantyRequest req) {
        ProductSerial serial = serials.findBySerialNo(req.serialNo())
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.SERIAL_NOT_FOUND, req.serialNo()));

        if (serial.getOrderItem() == null) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_ALLOWED,
                    "San pham nay chua duoc ban ra, khong co bao hanh");
        }
        // Serial cua nguoi khac -> 404, khong phai 403
        if (!serial.getOrderItem().getOrder().isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.SERIAL_NOT_FOUND, req.serialNo());
        }
        if (!serial.isUnderWarranty(clock.instant())) {
            throw new BusinessException(ErrorCode.REFUND_WINDOW_EXPIRED,
                    "San pham da het han bao hanh");
        }
        if (requests.existsBySerialIdAndStatusIn(serial.getId(), OPEN_STATUSES)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS,
                    "Serial nay dang co mot yeu cau bao hanh chua xu ly xong");
        }

        WarrantyRequest request = requests.save(new WarrantyRequest(
                serial, users.getReferenceById(principal.id()), req.reason()));
        log.info("User {} tao yeu cau bao hanh cho serial {}", principal.id(), serial.getSerialNo());
        return WarrantyRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<WarrantyRequestResponse> search(AuthPrincipal principal,
                                                        WarrantyStatus status, Pageable pageable) {
        // Khach chi thay yeu cau cua minh; STAFF/ADMIN thay tat ca
        Long userFilter = principal.isStaffOrAdmin() ? null : principal.id();
        return PageResponse.from(requests.search(userFilter, status, pageable),
                WarrantyRequestResponse::from);
    }

    @Transactional(readOnly = true)
    public WarrantyRequestResponse findById(AuthPrincipal principal, Long id) {
        return WarrantyRequestResponse.from(requireVisible(principal, id));
    }

    /** STAFF/ADMIN duyet hoac tu choi. Bang chuyen trang thai nam trong entity. */
    @Transactional
    public WarrantyRequestResponse resolve(AuthPrincipal principal, Long id, ResolveWarrantyRequest req) {
        WarrantyRequest request = requests.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, id));
        try {
            request.transitionTo(req.toStatus(), req.resolution(), clock.instant());
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION, e.getMessage());
        }
        // Duyet bao hanh nghia la may co loi that -> danh dau de khong ban lai
        if (req.toStatus() == WarrantyStatus.APPROVED) {
            request.getSerial().markDefective();
        }
        log.info("User {} chuyen yeu cau bao hanh {} sang {}", principal.id(), id, req.toStatus());
        return WarrantyRequestResponse.from(request);
    }

    private WarrantyRequest requireVisible(AuthPrincipal principal, Long id) {
        WarrantyRequest request = requests.findDetailById(id)
                .orElseThrow(() -> BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, id));
        if (principal.role() == Role.CUSTOMER && !request.isOwnedBy(principal.id())) {
            throw BusinessException.notFound(ErrorCode.RESOURCE_NOT_FOUND, id);
        }
        return request;
    }
}
