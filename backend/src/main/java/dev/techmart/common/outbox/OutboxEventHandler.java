package dev.techmart.common.outbox;

/**
 * Nơi xử lý một loại event outbox. Module nào cần phản ứng với event thì khai báo một bean
 * hiện thực interface này - {@code common} không phải biết tới module đó (hướng phụ thuộc
 * vẫn là module nghiệp vụ -> common, không bao giờ ngược lại).
 *
 * <p>Hợp đồng bắt buộc (ADR-0013):
 * <ul>
 *   <li><b>Idempotent</b> - outbox giao at-least-once, cùng một event có thể tới hai lần;</li>
 *   <li><b>Không ném lỗi vì dữ liệu</b> - handler chạy chung transaction với cả lô event.
 *       Một lỗi SQL làm transaction bị đánh dấu rollback-only và kéo theo cả lô: không event
 *       nào được đánh dấu xong, kể cả bộ đếm retry. Dữ liệu thiếu thì ghi log và bỏ qua.</li>
 * </ul>
 */
public interface OutboxEventHandler {

    boolean supports(OutboxEvent event);

    void handle(OutboxEvent event);
}
