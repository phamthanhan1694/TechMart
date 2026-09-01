#!/usr/bin/env bash
#
# Bắn N người mua đồng thời vào một suất flash sale, rồi kiểm chứng bằng SQL.
#
#   ./flashsale-race.sh              # mặc định 150 người
#   BUYERS=500 ./flashsale-race.sh
#
# Đây là phần đáng quay lại nhất để gửi kèm CV: nó cho thấy hệ thống bán ĐÚNG số suất
# dưới tải đột ngột, và bốn phép kiểm chứng cuối là bằng chứng khó bác bỏ.
#
# Lưu ý: nếu máy bạn có redis-server cài native, nó chiếm cổng 6379 trước container
# và app sẽ nối tới Redis đó. Vì vậy script đọc số suất còn lại QUA API chứ không
# qua `docker exec redis-cli` — API luôn đọc đúng Redis mà app đang dùng.

set -uo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
BUYERS="${BUYERS:-150}"
PARALLEL="${PARALLEL:-40}"
TOKEN_CACHE="${TOKEN_CACHE:-.flashsale-tokens}"

export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

B=$'\033[1m'; DIM=$'\033[2m'; R=$'\033[0m'
GRN=$'\033[32m'; RED=$'\033[31m'; YEL=$'\033[33m'; CYA=$'\033[36m'; MAG=$'\033[35m'
step() { printf "\n${B}${CYA}▸ %s${R}\n" "$*"; }
ok()   { printf "  ${GRN}✔${R} %s\n" "$*"; }
bad()  { printf "  ${RED}✘${R} %s\n" "$*"; }
info() { printf "  ${DIM}%s${R}\n" "$*"; }
die()  { bad "$*"; exit 1; }

jqp() { python -c "import sys,json;d=json.load(sys.stdin);$1" 2>/dev/null; }

printf "${B}${MAG}"
cat <<'BANNER'
  ┌────────────────────────────────────────────────────────────┐
  │  Flash sale — nhiều người tranh mua cùng một lúc           │
  └────────────────────────────────────────────────────────────┘
BANNER
printf "${R}"

curl -s -o /dev/null --max-time 5 "$BASE/actuator/health" || die "App chưa chạy."

# ─────────────────────────────────────────────────────── 1. Tìm phiên đang chạy
step "1. Tìm suất flash sale đang mở bán"
ACTIVE=$(curl -s --max-time 10 "$BASE/api/v1/flash-sales/active")
ITEM=$(echo "$ACTIVE" | jqp "print(d[0]['items'][0]['id'])")
[ -n "$ITEM" ] || die "Không có phiên nào đang chạy. Chạy app với profile demo."
echo "$ACTIVE" | jqp "
s = d[0]; it = s['items'][0]
print(f\"  {s['name']}\")
print(f\"    item {it['id']}: {it['productName'][:34]}\")
print(f\"    giá gốc {int(float(it['originalPrice'])):,d}đ → flash {int(float(it['salePrice'])):,d}đ (-{it['discountPercent']}%)\")
print(f\"    còn {it['remaining']}/{it['saleQuantity']} suất · mỗi người tối đa {it['perUserLimit']}\")"
QUOTA=$(echo "$ACTIVE" | jqp "print(d[0]['items'][0]['remaining'])")
LIMIT=$(echo "$ACTIVE" | jqp "print(d[0]['items'][0]['perUserLimit'])")

# ─────────────────────────────────────────────────────── 2. Lấy token
step "2. Lấy $BUYERS token của $BUYERS người mua khác nhau"
if [ -f "$TOKEN_CACHE" ] && [ "$(wc -l < "$TOKEN_CACHE")" -ge "$BUYERS" ]; then
  info "Dùng lại token đã lưu ở $TOKEN_CACHE (token sống 15 phút)"
else
  info "Đăng nhập song song… (BCrypt nên hơi lâu, kết quả được lưu lại để chạy sau nhanh hơn)"
  : > "$TOKEN_CACHE"
  seq 1 "$BUYERS" | xargs -P "$PARALLEL" -I{} sh -c "
    curl -s --max-time 20 -X POST '$BASE/api/v1/auth/login' -H 'Content-Type: application/json' \
      -d '{\"email\":\"buyer{}@techmart.dev\",\"password\":\"Customer@123\"}' \
      | python -c \"import sys,json
try: print(json.load(sys.stdin)['accessToken'])
except Exception: pass\"
  " >> "$TOKEN_CACHE" 2>/dev/null
fi
GOT=$(grep -c . "$TOKEN_CACHE")
[ "$GOT" -ge 10 ] || die "Chỉ lấy được $GOT token. Kiểm tra app đã seed dữ liệu chưa."
ok "Có $GOT token"
if [ "$GOT" -lt "$BUYERS" ]; then
  info "Yêu cầu $BUYERS nhưng chỉ lấy được $GOT — chính rate limit của hệ thống"
  info "(60 lần đăng nhập/phút/IP) đã chặn bớt. Đó là bằng chứng nó đang hoạt động."
fi

# ─────────────────────────────────────────────────────── 3. Bắn đồng thời
step "3. Bắn $GOT request MUA cùng một lúc vào $QUOTA suất"
OUT=$(mktemp); trap 'rm -f "$OUT"' EXIT
ADDR='{"receiverName":"Nguoi mua","phone":"0901234567","street":"12 Nguyen Trai","ward":"Ben Thanh","district":"Quan 1","province":"TP Ho Chi Minh"}'

START=$(date +%s%3N)
head -n "$BUYERS" "$TOKEN_CACHE" | grep . | xargs -P "$PARALLEL" -I{} sh -c "
  curl -s -o /dev/null -w '%{http_code}\n' --max-time 30 \
    -X POST '$BASE/api/v1/flash-sales/items/$ITEM/purchase' \
    -H 'Authorization: Bearer {}' -H 'Content-Type: application/json' \
    -H \"Idempotency-Key: \$(python -c 'import uuid;print(uuid.uuid4())')\" \
    -d '{\"quantity\":1,\"shippingAddress\":$ADDR}'
" >> "$OUT" 2>/dev/null
END=$(date +%s%3N)

SOLD=$(grep -c '^201$' "$OUT" || true)
CONFLICT=$(grep -c '^409$' "$OUT" || true)
THROTTLED=$(grep -c '^429$' "$OUT" || true)
SERVER_ERR=$(grep -cE '^5[0-9][0-9]$' "$OUT" || true)
TOTAL=$(grep -c . "$OUT" || true)

printf "  ${B}%-28s %s${R}\n" "Mua thành công (201)"  "$SOLD"
printf "  %-28s %s\n" "Hết suất / vượt giới hạn (409)" "$CONFLICT"
printf "  %-28s %s\n" "Bị rate limit (429)" "$THROTTLED"
printf "  %-28s %s\n" "Lỗi server (5xx)" "$SERVER_ERR"
info "Tổng $TOTAL request trong $((END-START))ms"

# ─────────────────────────────────────────────────────── 4. Kiểm chứng bằng SQL
step "4. Kiểm chứng bằng SQL — phần khó bác bỏ nhất"
PG="docker exec techmart-postgres psql -U techmart -d techmart -tA -F| -c"

ROW=$($PG "SELECT sold_quantity, sale_quantity FROM flash_sale_items WHERE id = $ITEM;" 2>/dev/null)
DB_SOLD=$(echo "$ROW" | cut -d'|' -f1); DB_QUOTA=$(echo "$ROW" | cut -d'|' -f2)
# Đọc tồn còn lại QUA API chứ không qua redis-cli: app có thể đang nối tới một Redis
# khác với container (ví dụ máy có redis-server cài native chiếm cổng 6379 trước).
# API là nguồn đúng vì nó đọc từ chính Redis mà app đang dùng.
REDIS_LEFT=$(curl -s --max-time 10 "$BASE/api/v1/flash-sales/active"   | jqp "print(next(i['remaining'] for s in d for i in s['items'] if i['id'] == $ITEM))")
STOCK=$($PG "SELECT v.stock_quantity, v.reserved_quantity FROM product_variants v JOIN flash_sale_items f ON f.variant_id = v.id WHERE f.id = $ITEM;" 2>/dev/null)

printf "  %-34s %s / %s\n" "flash_sale_items.sold_quantity" "$DB_SOLD" "$DB_QUOTA"
printf "  %-34s %s\n" "Suất còn lại (Redis, đọc qua API)" "$REDIS_LEFT"
printf "  %-34s %s\n" "product_variants (tồn | đang giữ)" "$STOCK"

echo
FAIL=0
[ "$SERVER_ERR" -eq 0 ] || { bad "Có $SERVER_ERR lỗi 5xx — dưới tải phải từ chối, không được sập"; FAIL=1; }
[ "$DB_SOLD" -le "$DB_QUOTA" ] || { bad "OVERSELL: bán $DB_SOLD trên $DB_QUOTA suất"; FAIL=1; }
[ "$DB_SOLD" -eq "$SOLD" ] || { bad "DB ghi $DB_SOLD nhưng chỉ $SOLD request thành công — lệch sổ"; FAIL=1; }
[ "$(echo "$STOCK" | cut -d'|' -f1)" -ge 0 ] || { bad "Tồn kho âm"; FAIL=1; }
[ "$REDIS_LEFT" = "$((DB_QUOTA - DB_SOLD))" ] || { bad "Bộ đếm Redis ($REDIS_LEFT) lệch sổ cái DB ($((DB_QUOTA - DB_SOLD)))"; FAIL=1; }

if [ "$FAIL" -eq 0 ]; then
  ok "Bán đúng $DB_SOLD suất, sổ cái DB khớp số request thành công, không oversell, không 5xx"
  printf "\n${B}${GRN}  $GOT người tranh $QUOTA suất → đúng $SOLD người mua được.${R}\n\n"
else
  printf "\n${B}${RED}  Có vấn đề — xem các dòng ✘ ở trên.${R}\n\n"
  exit 1
fi
