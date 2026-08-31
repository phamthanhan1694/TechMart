> ⚠️ **Tài liệu v1 — không code theo bản này.** Bản thiết kế thi hành là `01-domain-v2.md`. Giữ lại đây để tham khảo phần lý thuyết (BigDecimal, snapshot giá, N+1 query, optimistic lock).

# Sơ đồ quan hệ thực thể (ERD) — E-commerce Backend

Sơ đồ dưới viết bằng cú pháp Mermaid. Các trình xem Markdown hỗ trợ Mermaid (GitHub, GitLab, VS Code + plugin) sẽ tự render thành sơ đồ.

```mermaid
erDiagram
  USER ||--o| CART : has
  USER ||--o{ ORDER : places
  CATEGORY ||--o{ PRODUCT : groups
  CART ||--o{ CARTITEM : contains
  PRODUCT ||--o{ CARTITEM : in
  ORDER ||--o{ ORDERITEM : contains
  PRODUCT ||--o{ ORDERITEM : in

  USER {
    Long id PK
    String email UK
    String password
    String fullName
    Role role
    Instant createdAt
  }
  CATEGORY {
    Long id PK
    String name UK
    String description
  }
  PRODUCT {
    Long id PK
    String name
    String description
    BigDecimal price
    Integer stockQuantity
    String imageUrl
    Long version
    Long categoryId FK
  }
  CART {
    Long id PK
    Long userId FK
  }
  CARTITEM {
    Long id PK
    Integer quantity
    Long cartId FK
    Long productId FK
  }
  ORDER {
    Long id PK
    BigDecimal totalAmount
    OrderStatus status
    String shippingAddress
    Long userId FK
    Instant createdAt
  }
  ORDERITEM {
    Long id PK
    Integer quantity
    BigDecimal priceAtPurchase
    Long orderId FK
    Long productId FK
  }
```

## Đọc ký hiệu quan hệ (crow's foot)

- `||--o|` — một-một (one-to-one), phía `o|` là không-bắt-buộc. Vd: một `USER` có **không hoặc một** `CART`.
- `||--o{` — một-nhiều (one-to-many). Vd: một `CATEGORY` nhóm **nhiều** `PRODUCT`; một `ORDER` chứa **nhiều** `ORDERITEM`.
- `PK` = khóa chính, `FK` = khóa ngoại, `UK` = khóa duy nhất (unique).

## Tóm tắt quan hệ

- `USER` 1 — 1 `CART`, và 1 — N `ORDER`.
- `CATEGORY` 1 — N `PRODUCT`.
- `CART` 1 — N `CARTITEM`; mỗi `CARTITEM` trỏ tới 1 `PRODUCT`.
- `ORDER` 1 — N `ORDERITEM`; mỗi `ORDERITEM` trỏ tới 1 `PRODUCT` và lưu `priceAtPurchase` (giá tại lúc mua).
- `PRODUCT.version` phục vụ khóa lạc quan (optimistic lock) khi trừ tồn kho.
