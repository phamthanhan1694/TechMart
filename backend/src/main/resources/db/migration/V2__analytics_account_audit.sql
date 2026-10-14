-- =====================================================================
-- TechMart - V2: thống kê cho admin, sổ địa chỉ, yêu thích, thông báo, audit log.
-- Mỗi bảng / index đều ghi lý do ngay cạnh nó; đánh đổi tổng hợp ở docs/13-trade-off.md.
-- =====================================================================

-- ---------------------------------------------------------------- thống kê
-- Mọi truy vấn báo cáo đều lọc theo khoảng placed_at. Thiếu index này thì mỗi lần mở
-- trang thống kê là một lần quét toàn bảng orders (ADR-0011).
-- Không dùng partial index theo status: báo cáo cần cả đơn CANCELLED để tính tỉ lệ huỷ.
CREATE INDEX idx_orders_placed_at ON orders(placed_at);

-- Chuỗi "khách hàng mới theo ngày" và bộ lọc danh sách người dùng của admin.
CREATE INDEX idx_users_created_at ON users(created_at);

-- ---------------------------------------------------------------- sổ địa chỉ
CREATE TABLE user_addresses (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label         VARCHAR(50),
    receiver_name VARCHAR(150) NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    street        VARCHAR(255) NOT NULL,
    ward          VARCHAR(100) NOT NULL,
    district      VARCHAR(100) NOT NULL,
    province      VARCHAR(100) NOT NULL,
    is_default    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user ON user_addresses(user_id);
-- Mỗi người dùng tối đa MỘT địa chỉ mặc định. Chốt ở DB bằng partial unique index chứ
-- không chỉ bằng câu if ở service: hai tab cùng bấm "đặt làm mặc định" vẫn không thể ra hai cái.
CREATE UNIQUE INDEX uq_addresses_one_default ON user_addresses(user_id) WHERE is_default;

-- ---------------------------------------------------------------- yêu thích
-- Gắn với product chứ không phải variant: khách "thích iPhone 16", chưa chọn màu / dùng lượng.
CREATE TABLE wishlist_items (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Thêm hai lần = một dòng. PUT idempotent dựa trên ràng buộc này (ON CONFLICT DO NOTHING).
    CONSTRAINT wishlist_uniq UNIQUE (user_id, product_id)
);
CREATE INDEX idx_wishlist_user ON wishlist_items(user_id, created_at DESC);

-- ---------------------------------------------------------------- thông báo
CREATE TABLE notifications (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(50)   NOT NULL,
    title           VARCHAR(200)  NOT NULL,
    body            VARCHAR(1000),
    link            VARCHAR(300),
    -- Outbox giao at-least-once. Ràng buộc này biến "giao lại" thành "không làm gì":
    -- một event chỉ sinh tối đa một thông báo cho mỗi người nhận (ADR-0013).
    source_event_id BIGINT,
    read_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT notifications_event_uniq UNIQUE (source_event_id, user_id)
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);
-- Endpoint đếm số chưa đọc bị gọi định kỳ (polling) - partial index giữ nó rẻ.
CREATE INDEX idx_notifications_unread ON notifications(user_id) WHERE read_at IS NULL;

-- ---------------------------------------------------------------- audit log
CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT       REFERENCES users(id),
    -- Snapshot email: tài khoản bị đổi email hay bị khoá thì nhật ký vẫn đọc được ai đã làm
    actor_email VARCHAR(255) NOT NULL,
    actor_role  VARCHAR(20)  NOT NULL,
    action      VARCHAR(60)  NOT NULL,
    target_type VARCHAR(40)  NOT NULL,
    target_id   VARCHAR(64),
    detail      JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_created ON audit_logs(created_at DESC);
CREATE INDEX idx_audit_target  ON audit_logs(target_type, target_id);
CREATE INDEX idx_audit_actor   ON audit_logs(actor_id, created_at DESC);

-- Nhật ký chỉ được thêm, không được sửa / xoá - kể cả từ chính ứng dụng (ADR-0014).
-- Chặn ở DB vì một bug hay một endpoint vô ý ở tầng trên không vượt qua được trigger.
-- Giới hạn: chủ sở hữu DB vẫn DROP TRIGGER được. Chống cả người có quyền đó cần
-- lưu trữ ngoài (WORM / object lock) - nằm ngoài phạm vì dự án.
CREATE FUNCTION audit_logs_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs la append-only: khong duoc %', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_immutable();
