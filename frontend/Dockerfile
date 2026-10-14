# --- Giai doan tai dependency ---
# Tach rieng de Docker cache lai: doi ma nguon khong phai cai lai node_modules.
FROM node:24-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# --- Giai doan build ---
FROM node:24-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Next nhung bien NEXT_PUBLIC_* vao bundle luc BUILD chu khong phai luc chay.
# De trong: browser goi /api/... roi Next proxy sang BACKEND_URL, khong dinh CORS.
ENV NEXT_PUBLIC_API_BASE_URL=""
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

# --- Giai doan chay ---
# output: 'standalone' gom san server toi thieu + dung node_modules can thiet,
# nen anh chay khong can npm install lai.
FROM node:24-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1

# Khong chay bang root
RUN addgroup -S nodejs -g 1001 && adduser -S nextjs -u 1001 -G nodejs

COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
