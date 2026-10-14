-- =====================================================================
-- TechMart - schema v2 (docs/01-domain-v2.md)
-- Dung thang schema v2 ngay tu migration dau tien. Ly do: docs/00 muc 4.
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------- identity
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(150) NOT NULL,
    phone         VARCHAR(20),
    role          VARCHAR(20)  NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT users_role_chk   CHECK (role   IN ('CUSTOMER','STAFF','ADMIN')),
    CONSTRAINT users_status_chk CHECK (status IN ('ACTIVE','LOCKED'))
);

-- token luu dang HASH: DB ro ri thi token van khong dung duoc (docs/07 muc 1)
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by BIGINT      REFERENCES refresh_tokens(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

-- ---------------------------------------------------------------- catalog
CREATE TABLE brands (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE
);

CREATE TABLE categories (
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    slug      VARCHAR(120) NOT NULL UNIQUE,
    parent_id BIGINT REFERENCES categories(id)
);
CREATE INDEX idx_categories_parent ON categories(parent_id);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    category_id   BIGINT       NOT NULL REFERENCES categories(id),
    brand_id      BIGINT       NOT NULL REFERENCES brands(id),
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(280) NOT NULL UNIQUE,
    description   TEXT,
    thumbnail_url VARCHAR(500),
    spec          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    deleted_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT products_status_chk CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);
-- Chi danh index tren hang chua bi soft delete: index nho hon, query public nhanh hon
CREATE INDEX idx_products_category ON products(category_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_products_brand    ON products(brand_id)    WHERE deleted_at IS NULL;
CREATE INDEX idx_products_spec_gin ON products USING GIN (spec);
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);

CREATE TABLE product_variants (
    id                BIGSERIAL PRIMARY KEY,
    product_id        BIGINT        NOT NULL REFERENCES products(id),
    sku               VARCHAR(64)   NOT NULL UNIQUE,
    name              VARCHAR(150)  NOT NULL,
    price             NUMERIC(15,2) NOT NULL,
    compare_at_price  NUMERIC(15,2),
    stock_quantity    INTEGER       NOT NULL DEFAULT 0,
    reserved_quantity INTEGER       NOT NULL DEFAULT 0,
    attributes        JSONB         NOT NULL DEFAULT '{}'::jsonb,
    version           BIGINT        NOT NULL DEFAULT 0,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- Chot chan cuoi cung cua oversell. Neu logic khoa sai, DB nem loi
    -- thay vi de ton kho am. Xem docs/01-domain-v2.md muc 6.
    CONSTRAINT variants_stock_nonneg     CHECK (stock_quantity    >= 0),
    CONSTRAINT variants_reserved_nonneg  CHECK (reserved_quantity >= 0),
    CONSTRAINT variants_reserved_lte_stock CHECK (reserved_quantity <= stock_quantity),
    CONSTRAINT variants_price_positive   CHECK (price > 0)
);
CREATE INDEX idx_variants_product ON product_variants(product_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_variants_price   ON product_variants(price)      WHERE deleted_at IS NULL;

CREATE TABLE product_images (
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    url        VARCHAR(500) NOT NULL,
    sort_order INTEGER      NOT NULL DEFAULT 0
);
CREATE INDEX idx_images_product ON product_images(product_id);

-- ---------------------------------------------------------------- cart
CREATE TABLE carts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE cart_items (
    id         BIGSERIAL PRIMARY KEY,
    cart_id    BIGINT      NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    variant_id BIGINT      NOT NULL REFERENCES product_variants(id),
    quantity   INTEGER     NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT cart_items_qty_chk CHECK (quantity >= 1),
    CONSTRAINT cart_items_uniq UNIQUE (cart_id, variant_id)
);

-- ---------------------------------------------------------------- inventory
CREATE TABLE inventory_reservations (
    id         BIGSERIAL PRIMARY KEY,
    variant_id BIGINT      NOT NULL REFERENCES product_variants(id),
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    order_id   BIGINT,
    session_id VARCHAR(64) NOT NULL,
    quantity   INTEGER     NOT NULL,
    status     VARCHAR(20) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reservations_qty_chk    CHECK (quantity >= 1),
    CONSTRAINT reservations_status_chk CHECK (status IN ('HELD','COMMITTED','RELEASED','EXPIRED'))
);
-- Partial index cho job quet het han. Thieu index nay job se full-scan moi 30 giay.
CREATE INDEX idx_reservations_expiry ON inventory_reservations(expires_at) WHERE status = 'HELD';
CREATE INDEX idx_reservations_session ON inventory_reservations(session_id);
CREATE INDEX idx_reservations_variant ON inventory_reservations(variant_id);

-- ---------------------------------------------------------------- promotion
CREATE TABLE coupons (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(50)   NOT NULL UNIQUE,
    type             VARCHAR(20)   NOT NULL,
    value            NUMERIC(15,2) NOT NULL,
    min_order_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    max_discount     NUMERIC(15,2),
    usage_limit      INTEGER,
    used_count       INTEGER       NOT NULL DEFAULT 0,
    per_user_limit   INTEGER       NOT NULL DEFAULT 1,
    starts_at        TIMESTAMPTZ   NOT NULL,
    ends_at          TIMESTAMPTZ   NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT coupons_type_chk  CHECK (type IN ('PERCENT','FIXED')),
    CONSTRAINT coupons_used_chk  CHECK (used_count >= 0),
    CONSTRAINT coupons_range_chk CHECK (ends_at > starts_at)
);

-- ---------------------------------------------------------------- order
CREATE TABLE orders (
    id               BIGSERIAL PRIMARY KEY,
    code             VARCHAR(32)   NOT NULL UNIQUE,
    user_id          BIGINT        NOT NULL REFERENCES users(id),
    status           VARCHAR(30)   NOT NULL,
    subtotal         NUMERIC(15,2) NOT NULL,
    discount_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
    shipping_fee     NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount     NUMERIC(15,2) NOT NULL,
    coupon_code      VARCHAR(50),
    shipping_address JSONB         NOT NULL,
    note             VARCHAR(500),
    idempotency_key  VARCHAR(100)  NOT NULL UNIQUE,
    version          BIGINT        NOT NULL DEFAULT 0,
    placed_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    delivered_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT orders_status_chk CHECK (status IN
        ('PENDING_PAYMENT','PAID','PACKED','SHIPPED','DELIVERED','CANCELLED','REFUNDED')),
    CONSTRAINT orders_amount_chk CHECK (total_amount >= 0 AND subtotal >= 0 AND discount_amount >= 0)
);
CREATE INDEX idx_orders_user   ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_status ON orders(status, created_at DESC);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    variant_id   BIGINT        NOT NULL REFERENCES product_variants(id),
    -- Snapshot: don cu phai in duoc hoa don ma khong can join sang products
    product_name VARCHAR(255)  NOT NULL,
    variant_name VARCHAR(150)  NOT NULL,
    sku          VARCHAR(64)   NOT NULL,
    unit_price   NUMERIC(15,2) NOT NULL,
    quantity     INTEGER       NOT NULL,
    line_total   NUMERIC(15,2) NOT NULL,
    CONSTRAINT order_items_qty_chk CHECK (quantity >= 1)
);
CREATE INDEX idx_order_items_order   ON order_items(order_id);
CREATE INDEX idx_order_items_variant ON order_items(variant_id);

CREATE TABLE order_status_history (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT      NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status   VARCHAR(30) NOT NULL,
    changed_by  BIGINT      REFERENCES users(id),
    note        VARCHAR(500),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_history_order ON order_status_history(order_id, created_at);

CREATE TABLE coupon_redemptions (
    id         BIGSERIAL PRIMARY KEY,
    coupon_id  BIGINT      NOT NULL REFERENCES coupons(id),
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    order_id   BIGINT      NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT coupon_redemptions_uniq UNIQUE (coupon_id, user_id, order_id)
);
CREATE INDEX idx_redemptions_coupon_user ON coupon_redemptions(coupon_id, user_id);

-- ---------------------------------------------------------------- payment
CREATE TABLE payments (
    id              BIGSERIAL PRIMARY KEY,
    order_id        BIGINT        NOT NULL REFERENCES orders(id),
    method          VARCHAR(20)   NOT NULL,
    status          VARCHAR(20)   NOT NULL,
    amount          NUMERIC(15,2) NOT NULL,
    transaction_ref VARCHAR(100)  UNIQUE,
    idempotency_key VARCHAR(100)  NOT NULL UNIQUE,
    failure_reason  VARCHAR(255),
    paid_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT payments_method_chk CHECK (method IN ('MOCK_CARD','COD')),
    CONSTRAINT payments_status_chk CHECK (status IN ('INITIATED','SUCCEEDED','FAILED','REFUNDED'))
);
CREATE INDEX idx_payments_order ON payments(order_id);

-- ---------------------------------------------------------------- flash sale
CREATE TABLE flash_sales (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    starts_at  TIMESTAMPTZ  NOT NULL,
    ends_at    TIMESTAMPTZ  NOT NULL,
    status     VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT flash_sales_status_chk CHECK (status IN ('SCHEDULED','RUNNING','ENDED')),
    CONSTRAINT flash_sales_range_chk  CHECK (ends_at > starts_at)
);

CREATE TABLE flash_sale_items (
    id             BIGSERIAL PRIMARY KEY,
    flash_sale_id  BIGINT        NOT NULL REFERENCES flash_sales(id) ON DELETE CASCADE,
    variant_id     BIGINT        NOT NULL REFERENCES product_variants(id),
    sale_price     NUMERIC(15,2) NOT NULL,
    sale_quantity  INTEGER       NOT NULL,
    sold_quantity  INTEGER       NOT NULL DEFAULT 0,
    per_user_limit INTEGER       NOT NULL DEFAULT 1,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT flash_items_sold_chk CHECK (sold_quantity <= sale_quantity),
    CONSTRAINT flash_items_uniq UNIQUE (flash_sale_id, variant_id)
);

-- ---------------------------------------------------------------- review
CREATE TABLE reviews (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT      NOT NULL REFERENCES products(id),
    user_id       BIGINT      NOT NULL REFERENCES users(id),
    order_item_id BIGINT      NOT NULL UNIQUE REFERENCES order_items(id),
    rating        INTEGER     NOT NULL,
    comment       VARCHAR(2000),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT reviews_rating_chk CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_reviews_product ON reviews(product_id, created_at DESC);

-- ---------------------------------------------------------------- serial / bao hanh
CREATE TABLE product_serials (
    id              BIGSERIAL PRIMARY KEY,
    variant_id      BIGINT      NOT NULL REFERENCES product_variants(id),
    serial_no       VARCHAR(64) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'IN_STOCK',
    order_item_id   BIGINT      REFERENCES order_items(id),
    warranty_months INTEGER     NOT NULL DEFAULT 12,
    activated_at    TIMESTAMPTZ,
    CONSTRAINT serials_status_chk CHECK (status IN ('IN_STOCK','SOLD','RETURNED','DEFECTIVE'))
);
CREATE INDEX idx_serials_variant ON product_serials(variant_id) WHERE status = 'IN_STOCK';

CREATE TABLE warranty_requests (
    id          BIGSERIAL PRIMARY KEY,
    serial_id   BIGINT      NOT NULL REFERENCES product_serials(id),
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    status      VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    reason      VARCHAR(1000) NOT NULL,
    resolution  VARCHAR(1000),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ,
    CONSTRAINT warranty_status_chk CHECK (status IN ('REQUESTED','APPROVED','REJECTED','COMPLETED'))
);

-- ---------------------------------------------------------------- outbox
CREATE TABLE outbox_events (
    id             BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,
    aggregate_id   BIGINT       NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    payload        JSONB        NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    last_error     VARCHAR(1000),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    processed_at   TIMESTAMPTZ,
    CONSTRAINT outbox_status_chk CHECK (status IN ('PENDING','PROCESSED','FAILED'))
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE status = 'PENDING';

-- ---------------------------------------------------------------- idempotency
-- Chot idempotency o DB, khong chot o Redis: Redis mat du lieu thi van dung.
CREATE TABLE idempotency_records (
    id            BIGSERIAL PRIMARY KEY,
    key_value     VARCHAR(100) NOT NULL,
    endpoint      VARCHAR(120) NOT NULL,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    request_hash  VARCHAR(64)  NOT NULL,
    response_body TEXT,
    status_code   INTEGER,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT idempotency_uniq UNIQUE (key_value, endpoint)
);
