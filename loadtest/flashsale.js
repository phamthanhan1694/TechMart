// Kich ban flash sale - 500 nguoi tranh mua cung mot luc.
//
// Chuan bi:
//   1. Chay app voi profile demo (da seed san mot phien dang chay)
//   2. node loadtest/prepare-tokens.js     -> sinh loadtest/tokens.json
//   3. Lay ITEM_ID tu GET /api/v1/flash-sales/active
//   4. k6 run -e ITEM_ID=<id> loadtest/flashsale.js
//
// Sau khi chay, KIEM CHUNG BANG SQL - day moi la phan thuyet phuc:
//   SELECT sold_quantity, sale_quantity FROM flash_sale_items WHERE id = <id>;
//   -- sold_quantity phai DUNG BANG so 'sold' cua k6, khong hon mot don vi
//   SELECT stock_quantity FROM product_variants WHERE id = ...;  -- khong duoc am

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const sold = new Counter('sold');
const soldOut = new Counter('sold_out');
const throttled = new Counter('throttled');
const serverError = new Counter('server_error');

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const ITEM_ID = __ENV.ITEM_ID;
const TOKENS = JSON.parse(open('./tokens.json'));

export const options = {
  scenarios: {
    thundering_herd: {
      executor: 'shared-iterations',
      vus: 500,
      iterations: 500,
      maxDuration: '2m',
    },
  },
  thresholds: {
    // Khong duoc co 5xx: duoi tai cuc dai, thu duoc bao ve la TINH DUNG DAN,
    // chu khong phai toc do. Tu choi ban la dung; sap la sai.
    'server_error': ['count==0'],
  },
};

export default function () {
  if (!ITEM_ID) {
    throw new Error('Thieu ITEM_ID. Chay: k6 run -e ITEM_ID=<id> loadtest/flashsale.js');
  }
  const token = TOKENS[exec.vu.idInTest % TOKENS.length];

  const res = http.post(
    `${BASE}/api/v1/flash-sales/items/${ITEM_ID}/purchase`,
    JSON.stringify({
      quantity: 1,
      shippingAddress: {
        receiverName: 'Nguoi mua k6',
        phone: '0901234567',
        street: '12 Nguyen Trai',
        ward: 'Ben Thanh',
        district: 'Quan 1',
        province: 'TP Ho Chi Minh',
      },
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Idempotency-Key': `${exec.vu.idInTest}-${exec.scenario.iterationInTest}`,
      },
    }
  );

  if (res.status === 201) sold.add(1);
  else if (res.status === 409) soldOut.add(1);
  else if (res.status === 429) throttled.add(1);
  else if (res.status >= 500) serverError.add(1);

  check(res, { 'khong 5xx': (r) => r.status < 500 });
}
