package dev.techmart.audit.service;

import dev.techmart.audit.domain.AuditAction;
import dev.techmart.audit.domain.AuditLog;
import dev.techmart.audit.repository.AuditLogRepository;
import dev.techmart.identity.security.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Ghi nhật ký hành động quản trị, trong CÙNG transaction với chính hành động đó.
 *
 * <p><b>Vì sao đồng bộ, cùng transaction</b> (ADR-0014): nhật ký tồn tại khi và chỉ khi
 * hành động đã commit. Ghi bất đồng bộ (outbox, @Async) thì có khe hở: hành động đã xảy ra
 * mà nhật ký chưa - hoặc không bao giờ - được ghi. Với audit, "thiếu một dòng" là lỗi
 * nghiêm trọng hơn "chậm thêm vài mili giây".
 *
 * <p><b>Gọi tường minh, không dùng AOP.</b> Annotation {@code @Audited} gọn hơn nhưng không
 * lấy được "đổi từ gì sang gì" - thứ làm nên giá trị của nhật ký. Đổi lại: mỗi hành động
 * mới phải nhớ gọi hàm này, và quên gọi thì không có gì báo lỗi.
 *
 * <p>Người thực hiện lấy từ SecurityContext thay vì truyền qua tham số, để không phải đổi
 * chữ ký của mọi service. Giới hạn thừa nhận: SecurityContext là thread-lọcal, nên gọi từ
 * thread khác (@Async, job định kỳ) sẽ được ghi là SYSTEM.
 */
@Service
public class AuditLogger {

    private static final String SYSTEM = "SYSTEM";

    private final AuditLogRepository repository;

    public AuditLogger(AuditLogRepository repository) {
        this.repository = repository;
    }

    /** MANDATORY: gọi ngoài transaction nghiệp vụ là lỗi lập trình, phải nổ ngay. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, String targetType, Object targetId, Map<String, Object> detail) {
        AuthPrincipal actor = currentActor();
        repository.save(new AuditLog(
                actor != null ? actor.id() : null,
                actor != null ? actor.email() : SYSTEM,
                actor != null ? actor.role().name() : SYSTEM,
                action, targetType,
                targetId != null ? String.valueOf(targetId) : null,
                detail));
    }

    private static AuthPrincipal currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }
}
