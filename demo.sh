#!/usr/bin/env bash
#
# Demo trọn luồng TechMart — chạy được nhiều lần, mỗi lần tự tạo tài khoản mới.
#
#   ./demo.sh
#   BASE_URL=http://localhost:8080 ./demo.sh
#
# Yêu cầu: app đang chạy với profile demo (đã seed dữ liệu), có curl và python.
# Quay lại bằng asciinema hoặc bất kỳ công cụ ghi màn hình nào để gửi kèm CV.

set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"

# BAT BUOC tren Windows: python doc tham so -c theo ANSI codepage, nen moi doan code
# co dau tieng Viet se chet am tham voi UnicodeDecodeError. Bat che do UTF-8 tu goc.
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

# --- màu ---
B=$'\033[1m'; DIM=$'\033[2m'; R=$'\033[0m'
GRN=$'\033[32m'; RED=$'\033[31m'; YEL=$'\033[33m'; CYA=$'\033[36m'; MAG=$'\033[35m'

step()  { printf "\n${B}${CYA}▸ %s${R}\n" "$*"; }
ok()    { printf "  ${GRN}✔${R} %s\n" "$*"; }
bad()   { printf "  ${RED}✘${R} %s\n" "$*"; }
info()  { printf "  ${DIM}%s${R}\n" "$*"; }
hl()    { printf "  ${B}${YEL}%s${R}\n" "$*"; }
pause() { sleep "${DEMO_PAUSE:-0.6}"; }

jqp() { python -c "import sys,json;d=json.load(sys.stdin);$1" 2>/dev/null; }

die() { bad "$*"; exit 1; }

printf "${B}${MAG}"
cat <<'BANNER'
  ┌────────────────────────────────────────────────────────────┐
  │  TechMart — demo luồng đặt hàng chống bán quá tồn kho       │
  └────────────────────────────────────────────────────────────┘
BANNER
printf "${R}"
info "Máy chủ: $BASE"

curl -s -o /dev/null --max-time 5 "$BASE/actuator/health" || die "App chưa chạy. Khởi động bằng: cd backend && SPRING_PROFILES_ACTIVE=demo sh ./mvnw spring-boot:run"

# ─────────────────────────────────────────────────────────── 1. Đăng ký + đăng nhập
step "1. Đăng ký tài khoản mới rồi đăng nhập"
EMAIL="demo-$(date +%s)@techmart.dev"
curl -s --max-time 10 -X POST "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Demo@12345\",\"fullName\":\"Khach demo\",\"phone\":\"0901234567\"}" >/dev/null
ok "Đã đăng ký $EMAIL"

TOKENS=$(curl -s --max-time 10 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Demo@12345\"}")
TOKEN=$(echo "$TOKENS" | jqp "print(d['accessToken'])")
[ -n "$TOKEN" ] || die "Đăng nhập thất bại"
AUTH="Authorization: Bearer $TOKEN"
ok "accessToken: ${TOKEN:0:38}…  ${DIM}(sống 15 phút)${R}"
pause

# ─────────────────────────────────────────────────────────── 2. Tìm kiếm JSONB
step "2. Tìm kiếm — lọc theo thông số kỹ thuật trên cột JSONB"
info "GET /api/v1/products?keyword=iPhone&spec.ram=8GB&sort=price,asc"
SEARCH=$(curl -s --max-time 10 "$BASE/api/v1/products?keyword=iPhone&spec.ram=8GB&sort=price,asc&size=3")
echo "$SEARCH" | jqp "
print('  Tìm thấy', d['totalElements'], 'sản phẩm')
for p in d['content']:
    print(f\"    · {p['name'][:34]:36s} {int(float(p['minPrice'])):>12,d}đ  còn {p['availableQuantity']:>3d}  ram={p['spec'].get('ram')}\")"
SLUG=$(echo "$SEARCH" | jqp "print(d['content'][0]['slug'])")
pause

# ─────────────────────────────────────────────────────────── 3. Chi tiết + tồn kho
step "3. Chi tiết sản phẩm — chú ý availableQuantity"
DETAIL=$(curl -s --max-time 10 "$BASE/api/v1/products/$SLUG")
VID=$(echo "$DETAIL" | jqp "print(d['variants'][0]['id'])")
BEFORE=$(echo "$DETAIL" | jqp "print(d['variants'][0]['availableQuantity'])")
echo "$DETAIL" | jqp "
print('  ', d['name'])
v = d['variants'][0]
print(f\"    variant {v['id']}: {v['name']:26s} {int(float(v['price'])):>12,d}đ\")"
hl "availableQuantity trước khi giữ hàng: $BEFORE"
pause

# ─────────────────────────────────────────────────────────── 4. Giỏ hàng
step "4. Thêm 2 sản phẩm vào giỏ — thêm vào giỏ KHÔNG giữ hàng"
curl -s --max-time 10 -X POST "$BASE/api/v1/cart/items" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"variantId\":$VID,\"quantity\":2}" | jqp "
print(f\"  Giỏ: {d['totalItems']} món, tạm tính {int(float(d['subtotal'])):,d}đ\")"
MID=$(curl -s --max-time 10 "$BASE/api/v1/products/$SLUG" | jqp "print(d['variants'][0]['availableQuantity'])")
info "availableQuantity vẫn là $MID — đúng như thiết kế, giỏ hàng không khoá tồn kho"
pause

# ─────────────────────────────────────────────────────────── 5. Giữ hàng
step "5. Vào checkout — GIỮ HÀNG 10 phút"
SESSION=$(curl -s --max-time 10 -X POST "$BASE/api/v1/checkout/sessions" -H "$AUTH")
SID=$(echo "$SESSION" | jqp "print(d['sessionId'])")
echo "$SESSION" | jqp "print(f\"  Phiên {d['sessionId'][:8]}… hết hạn sau {d['secondsRemaining']}s, tạm tính {int(float(d['subtotal'])):,d}đ\")"
HELD=$(curl -s --max-time 10 "$BASE/api/v1/products/$SLUG" | jqp "print(d['variants'][0]['availableQuantity'])")
hl "availableQuantity: $BEFORE → $HELD   (giảm $((BEFORE-HELD)))"
info "stockQuantity CHƯA đổi — hàng mới bị giữ, chưa bị trừ khỏi kho"
pause

# ─────────────────────────────────────────────────────────── 6. Đặt hàng idempotent
step "6. Đặt hàng — gọi HAI LẦN với cùng Idempotency-Key"
ADDR='{"receiverName":"Nguyen Van A","phone":"0901234567","street":"12 Nguyen Trai","ward":"Ben Thanh","district":"Quan 1","province":"TP Ho Chi Minh"}'
KEY=$(python -c 'import uuid;print(uuid.uuid4())')
BODY="{\"sessionId\":\"$SID\",\"shippingAddress\":$ADDR,\"note\":\"Giao gio hanh chinh\"}"

R1=$(curl -s --max-time 10 -X POST "$BASE/api/v1/orders" -H "$AUTH" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" -d "$BODY")
CODE=$(echo "$R1" | jqp "print(d['code'])")
echo "$R1" | jqp "print(f\"  Lần 1 → {d['code']}  tổng {int(float(d['totalAmount'])):,d}đ  [{d['status']}]\")"

R2=$(curl -s --max-time 10 -X POST "$BASE/api/v1/orders" -H "$AUTH" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" -d "$BODY")
CODE2=$(echo "$R2" | jqp "print(d['code'])")
echo "$R2" | jqp "print(f\"  Lần 2 → {d['code']}  [{d['status']}]\")"

if [ "$CODE" = "$CODE2" ]; then
  ok "Cùng một đơn — idempotent đúng. Chốt chặn là UNIQUE(orders.idempotency_key) ở DB"
else
  bad "Tạo hai đơn khác nhau — SAI"
fi
info "Thiếu header Idempotency-Key thì server từ chối:"
curl -s --max-time 10 -X POST "$BASE/api/v1/orders" -H "$AUTH" -H 'Content-Type: application/json' -d "$BODY" \
  | jqp "print(f\"    HTTP {d['status']} {d['code']}\")"
pause

# ─────────────────────────────────────────────────────────── 7. Thanh toán
step "7. Thanh toán — webhook ký HMAC, ba lớp bảo vệ"
PAY=$(curl -s --max-time 10 -X POST "$BASE/api/v1/payments" -H "$AUTH" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(python -c 'import uuid;print(uuid.uuid4())')" \
  -d "{\"orderCode\":\"$CODE\",\"method\":\"MOCK_CARD\"}")
PID=$(echo "$PAY" | jqp "print(d['id'])")
echo "$PAY" | jqp "print(f\"  Giao dịch {d['transactionRef']}  [{d['status']}]\")"

info "Thử webhook với chữ ký giả:"
curl -s --max-time 10 -X POST "$BASE/api/v1/payments/webhook" -H 'Content-Type: application/json' \
  -d "$(echo "$PAY" | jqp "print(json.dumps({'transactionRef':d['transactionRef'],'status':'SUCCEEDED','amount':d['amount'],'signature':'chu-ky-gia'}))")" \
  | jqp "print(f\"    HTTP {d['status']} {d['code']} — bị từ chối\")"

CB=$(curl -s --max-time 10 -X POST "$BASE/api/v1/payments/$PID/simulate-callback?outcome=SUCCEEDED" -H "$AUTH")
# Chú ý: body đi qua json.dumps của Python nên 45010000.00 bị rút thành 45010000.0.
# Rất nhiều client thật cũng làm đúng như vậy (parse JSON number thành float).
# Webhook vẫn xác thực được vì server chuẩn hoá scale trước khi ký — xem MockPaymentGateway.
curl -s --max-time 10 -X POST "$BASE/api/v1/payments/webhook" -H 'Content-Type: application/json' \
  -d "$(echo "$CB" | jqp "print(json.dumps({'transactionRef':d['transactionRef'],'status':d['status'],'amount':d['amount'],'signature':d['signature']}))")" \
  | jqp "print(f\"  Webhook ký đúng → giao dịch {d['status']}\")"
curl -s --max-time 10 "$BASE/api/v1/orders/$CODE" -H "$AUTH" | jqp "print(f\"  Đơn {d['code']} → [{d['status']}]\")"
pause

# ─────────────────────────────────────────────────────────── 8. Admin
step "8. Admin đổi trạng thái — đi qua bảng chuyển trạng thái"
ADMIN=$(curl -s --max-time 10 -X POST "$BASE/api/v1/auth/login" -H 'Content-Type: application/json' \
  -d '{"email":"admin@techmart.dev","password":"Admin@123"}' | jqp "print(d['accessToken'])")
AADMIN="Authorization: Bearer $ADMIN"

for TO in PACKED SHIPPED DELIVERED; do
  curl -s --max-time 10 -X PATCH "$BASE/api/v1/admin/orders/$CODE/status" -H "$AADMIN" -H 'Content-Type: application/json' \
    -d "{\"toStatus\":\"$TO\",\"note\":\"demo\"}" | jqp "print(f\"  → {d['status']}\")"
done

info "Thử chuyển ngược DELIVERED → PAID (không có trong bảng):"
curl -s --max-time 10 -X PATCH "$BASE/api/v1/admin/orders/$CODE/status" -H "$AADMIN" -H 'Content-Type: application/json' \
  -d '{"toStatus":"PAID"}' | jqp "print(f\"    HTTP {d['status']} {d['code']}\")"

info "Lịch sử trạng thái của đơn:"
# Dùng .get() chứ không phải [] — API cấu hình non_null nên fromStatus VẮNG MẶT
# ở bản ghi đầu tiên (đơn mới tạo thì không có trạng thái trước đó).
curl -s --max-time 10 "$BASE/api/v1/orders/$CODE/history" -H "$AUTH" | jqp "
for h in d:
    print(f\"    {h['createdAt'][:19]}  {str(h.get('fromStatus') or '—'):>16s} → {h['toStatus']}\")"
pause

# ─────────────────────────────────────────────────────────── 9. Hủy đơn idempotent
step "9. Hủy đơn hai lần — chỉ hoàn kho MỘT lần"
KEY2=$(python -c 'import uuid;print(uuid.uuid4())')
curl -s --max-time 10 -X POST "$BASE/api/v1/cart/items" -H "$AUTH" -H 'Content-Type: application/json' \
  -d "{\"variantId\":$VID,\"quantity\":1}" >/dev/null
SID2=$(curl -s --max-time 10 -X POST "$BASE/api/v1/checkout/sessions" -H "$AUTH" | jqp "print(d['sessionId'])")
CODE3=$(curl -s --max-time 10 -X POST "$BASE/api/v1/orders" -H "$AUTH" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY2" -d "{\"sessionId\":\"$SID2\",\"shippingAddress\":$ADDR}" | jqp "print(d['code'])")
S1=$(curl -s --max-time 10 "$BASE/api/v1/products/$SLUG" | jqp "print(d['variants'][0]['availableQuantity'])")
info "Đã tạo đơn $CODE3 (1 sản phẩm), tồn khả dụng còn $S1"

for i in 1 2; do
  curl -s --max-time 10 -X POST "$BASE/api/v1/orders/$CODE3/cancel" -H "$AUTH" -H 'Content-Type: application/json' \
    -d '{"reason":"Doi y"}' | jqp "print(f\"  Hủy lần $i → [{d['status']}]\")"
done
S2=$(curl -s --max-time 10 "$BASE/api/v1/products/$SLUG" | jqp "print(d['variants'][0]['availableQuantity'])")
if [ "$S2" -eq "$((S1+1))" ]; then
  ok "Tồn khả dụng $S1 → $S2 — hoàn kho đúng một lần"
else
  bad "Tồn khả dụng $S1 → $S2 — SAI, hoàn kho nhiều lần"
fi

# ─────────────────────────────────────────────────────────── 10. Tổng kết
step "10. Trạng thái tồn kho cuối cùng"
curl -s --max-time 10 "$BASE/api/v1/products/$SLUG" | jqp "
v = d['variants'][0]
print(f\"  {d['name']}\")
print(f\"    stockQuantity     = {v['availableQuantity']}\")"
info "Ban đầu $BEFORE · đã bán 2 (đơn đã giao) · đã hoàn 1 (đơn đã hủy)"

printf "\n${B}${GRN}  Xong. Chạy ./flashsale-race.sh để xem phần chống bán quá tồn dưới tải.${R}\n\n"
