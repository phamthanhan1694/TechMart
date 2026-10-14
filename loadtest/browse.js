// Kich ban duyet san pham - do p95 cua endpoint tim kiem.
//
// Chay:  k6 run loadtest/browse.js
//        k6 run -e BASE_URL=https://... loadtest/browse.js
//
// Luu y: keyword va spec duoc chon NGAU NHIEN. Ban mai mot truy van giong het nhau thi
// cache hit 100% va con so dep mot cach gia tao.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const listLatency = new Trend('list_latency', true);
const detailLatency = new Trend('detail_latency', true);

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 50 },
    { duration: '1m', target: 200 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_failed': ['rate<0.01'],
    'list_latency': ['p(95)<300'],
  },
};

const KEYWORDS = ['iphone', 'macbook', 'galaxy', 'thinkpad', 'xiaomi', 'pixel', 'rog', 'inspiron'];
const RAM = ['8GB', '12GB', '16GB', '32GB'];
const SORTS = ['price,asc', 'price,desc', 'name,asc', 'createdAt,desc'];

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export default function () {
  const url = `${BASE}/api/v1/products`
    + `?keyword=${pick(KEYWORDS)}`
    + `&spec.ram=${pick(RAM)}`
    + `&sort=${pick(SORTS)}`
    + `&page=0&size=20`;

  const res = http.get(url, { tags: { name: 'list' } });
  listLatency.add(res.timings.duration);
  check(res, { 'list 200': (r) => r.status === 200 });

  // Mo chi tiet mot san pham trong ket qua - mo phong hanh vi that
  if (res.status === 200) {
    const body = res.json();
    if (body.content && body.content.length > 0) {
      const slug = body.content[Math.floor(Math.random() * body.content.length)].slug;
      const detail = http.get(`${BASE}/api/v1/products/${slug}`, { tags: { name: 'detail' } });
      detailLatency.add(detail.timings.duration);
      check(detail, { 'detail 200': (r) => r.status === 200 });
    }
  }

  sleep(Math.random() * 2);
}
