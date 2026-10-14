import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  // Gom san server toi thieu + dung node_modules can thiet vao .next/standalone,
  // de anh Docker chay khong phai cai lai dependency.
  output: 'standalone',

  // Khong dung rewrites() de proxy sang backend: dich cua rewrites bi chot luc
  // build, nen bien BACKEND_URL dat luc chay container se khong co tac dung.
  // Viec proxy do route handler src/app/api/[...path]/route.ts lo, no doc bien
  // moi truong ngay tai thoi diem co request.
  images: {
    // Anh san pham trong seed tro ve CDN ngoai - cho phep moi host https.
    remotePatterns: [{ protocol: 'https', hostname: '**' }],
  },
}

export default nextConfig
