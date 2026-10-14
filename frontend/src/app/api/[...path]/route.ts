/**
 * Proxy moi loi goi /api/... sang backend Spring Boot.
 *
 * VI SAO KHONG DUNG `rewrites()` TRONG next.config:
 * dich cua rewrites duoc CHOT LUC BUILD. Voi `output: standalone`, next.config
 * khong duoc copy vao image, nen bien BACKEND_URL dat luc chay container khong
 * co tac dung nao - Next van goi vao dia chi da nuong san luc build
 * (`http://localhost:8080`), va trong container thi cho do khong co gi.
 *
 * Route handler doc `process.env.BACKEND_URL` ngay tai thoi diem co request,
 * nen cung mot image chay duoc o may dev, trong compose, hay tren server that
 * ma khong phai build lai.
 *
 * Di kem mot loi the: dia chi backend khong bao gio lo ra trinh duyet, va vi
 * moi thu deu cung goc nen khong cham toi CORS.
 */

import { NextResponse, type NextRequest } from 'next/server'

// Luon chay dong: khong duoc phep tra ve ban da render san.
export const dynamic = 'force-dynamic'

/** Header cua ket noi (hop-by-hop) khong duoc chuyen tiep. */
const HOP_BY_HOP = new Set([
  'connection',
  'keep-alive',
  'proxy-authenticate',
  'proxy-authorization',
  'te',
  'trailer',
  'transfer-encoding',
  'upgrade',
  'host',
  'content-length',
])

function filterHeaders(source: Headers): Headers {
  const out = new Headers()
  source.forEach((value, key) => {
    if (!HOP_BY_HOP.has(key.toLowerCase())) out.set(key, value)
  })
  return out
}

async function proxy(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
): Promise<Response> {
  const backend = (process.env.BACKEND_URL ?? 'http://localhost:8080').replace(/\/$/, '')
  const { path } = await ctx.params

  const target = `${backend}/api/${path.map(encodeURIComponent).join('/')}${req.nextUrl.search}`

  // Doc het body roi gui lai: payload cua API nay deu la JSON nho, nen khong
  // can stream - doi lai tranh duoc rac roi `duplex: 'half'` cua undici.
  const hasBody = req.method !== 'GET' && req.method !== 'HEAD'
  const body = hasBody ? await req.arrayBuffer() : undefined

  let upstream: Response
  try {
    upstream = await fetch(target, {
      method: req.method,
      headers: filterHeaders(req.headers),
      body: body && body.byteLength > 0 ? body : undefined,
      redirect: 'manual',
      cache: 'no-store',
    })
  } catch {
    // Backend chua len hoac mang noi bo dut - tra dung envelope loi cua backend
    // de phia client khong phai doan mo mot cau tra loi la dang.
    return NextResponse.json(
      {
        timestamp: new Date().toISOString(),
        status: 503,
        code: 'SERVICE_UNAVAILABLE',
        message: 'Khong ket noi duoc toi backend',
        path: req.nextUrl.pathname,
        traceId: 'proxy',
      },
      { status: 503 },
    )
  }

  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: filterHeaders(upstream.headers),
  })
}

export const GET = proxy
export const POST = proxy
export const PUT = proxy
export const PATCH = proxy
export const DELETE = proxy
export const HEAD = proxy
export const OPTIONS = proxy
