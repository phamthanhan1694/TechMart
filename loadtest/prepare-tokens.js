/**
 * Sinh loadtest/tokens.json - danh sach accessToken cua cac user da seed.
 *
 * Vi sao can nhieu token khac nhau: flash sale co per_user_limit. Neu ban 500 request
 * bang mot token thi 499 cai bi tu choi vi vuot gioi han moi nguoi, va bai load test
 * do nham "gioi han moi nguoi" thanh "het suat".
 *
 * Chay:  node loadtest/prepare-tokens.js
 *        BASE_URL=https://... node loadtest/prepare-tokens.js
 */

const fs = require('fs');
const path = require('path');

const BASE = process.env.BASE_URL || 'http://localhost:8080';
const COUNT = Number(process.env.COUNT || 500);
const PASSWORD = 'Customer@123';
const CONCURRENCY = 25;

async function login(email) {
  const res = await fetch(`${BASE}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  if (!res.ok) {
    throw new Error(`Dang nhap that bai cho ${email}: HTTP ${res.status}`);
  }
  return (await res.json()).accessToken;
}

async function main() {
  const emails = Array.from({ length: COUNT }, (_, i) => `buyer${i + 1}@techmart.dev`);
  const tokens = [];

  for (let i = 0; i < emails.length; i += CONCURRENCY) {
    const batch = emails.slice(i, i + CONCURRENCY);
    const results = await Promise.allSettled(batch.map(login));
    for (const r of results) {
      if (r.status === 'fulfilled') tokens.push(r.value);
    }
    process.stdout.write(`\rDa lay ${tokens.length}/${COUNT} token`);
  }

  const out = path.join(__dirname, 'tokens.json');
  fs.writeFileSync(out, JSON.stringify(tokens, null, 0));
  console.log(`\nDa ghi ${tokens.length} token vao ${out}`);

  if (tokens.length < COUNT) {
    console.warn(`Canh bao: chi lay duoc ${tokens.length}/${COUNT}. `
      + `Kiem tra app da chay voi profile demo va da seed du lieu chua.`);
  }
}

main().catch((e) => {
  console.error('Loi:', e.message);
  process.exit(1);
});
