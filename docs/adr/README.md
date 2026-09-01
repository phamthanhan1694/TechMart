# Architecture Decision Records

Mỗi file ghi lại **một** quyết định kỹ thuật: bối cảnh, các phương án đã cân nhắc, quyết định, đánh đổi chấp nhận, và điều kiện để đổi ý.

Viết ADR **ngay lúc ra quyết định**, không dồn cuối dự án. ADR viết dồn đọc ra là biết — nó chỉ mô tả cái đã làm chứ không ghi lại được lý do thật và những phương án đã bị loại.

Mục "Khi nào nên đổi ý" là mục ăn điểm nhất khi phỏng vấn: nó chứng minh bạn biết quyết định của mình đúng trong **điều kiện nào**, chứ không coi nó là chân lý.

## Danh sách

| # | Quyết định | Trạng thái | Tham chiếu |
|---|---|---|---|
| [0001](0001-monolith-modular.md) | Monolith module hóa thay vì microservices | ☐ chưa viết | `00-pham-vi-va-ke-hoach.md` §2 |
| [0002](0002-jsonb-cho-product-spec.md) | JSONB cho product spec thay vì EAV | ☐ chưa viết | `01-domain-v2.md` §5.2 |
| [0003](0003-chien-luoc-khoa-ton-kho.md) | Pessimistic lock cho đơn thường, Redis atomic cho flash sale | ✅ | `02-luong-nghiep-vu.md` §3, §4 |
| [0004](0004-reservation-nguon-su-that.md) | Reservation: DB là nguồn sự thật, Redis là đường nhanh | ✅ | `02-luong-nghiep-vu.md` §2 |
| [0005](0005-outbox-thay-message-broker.md) | Outbox pattern thay message broker | ☐ chưa viết | `02-luong-nghiep-vu.md` §7 |
| [0006](0006-refresh-token-rotation.md) | Refresh token có rotation + revoke, lưu hash | ☐ chưa viết | `07-security-checklist.md` §1 |
| [0007](0007-soft-delete.md) | Soft delete cho product/variant | ☐ chưa viết | `01-domain-v2.md` §1 |
| [0008](0008-idempotency-key.md) | `Idempotency-Key` ở tầng API | ☐ chưa viết | `03-api-contract.md` §1 |
| [0009](0009-rate-limit-tu-viet-bang-lua.md) | Rate limit tự viết bằng Lua thay vì Bucket4j | ☐ chưa viết | `README.md` — Quyết định đáng nói |
| [0010](0010-flashsale-redis-la-cua-db-la-so-cai.md) | Flash sale: Redis là cửa, DB là sổ cái | ☐ chưa viết | `README.md` §3 |

Dùng [0000-template.md](0000-template.md) làm khuôn. Độ dài mục tiêu: **15–25 dòng**. ADR dài hơn thế thì không ai đọc, kể cả bạn.
