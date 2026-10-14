// ── Types cua TechMart API ────────────────────────────────────────────────
// Doi chieu 1-1 voi cac record DTO o backend (dev.techmart.*.dto.*Dtos).
// Sua DTO o backend thi sua o day - khong doan kieu tai cho su dung.

// ── Envelope chung ────────────────────────────────────────────────────────

/** Cau truc loi duy nhat cua toan bo API (common/error/ApiError.java). */
export interface ApiErrorBody {
  timestamp: string
  status: number
  /** Client hien thi theo code, khong parse message. */
  code: string
  message: string
  path: string
  traceId: string
  /** Chi co o loi validation va loi ton kho. */
  details?: unknown
}

/** Phan trang rieng cua backend, khong phai Page cua Spring. */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

// ── Identity ──────────────────────────────────────────────────────────────

export type Role = 'CUSTOMER' | 'STAFF' | 'ADMIN'

export interface UserResponse {
  id: number
  email: string
  fullName: string
  phone?: string | null
  role: Role
  createdAt: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  /** So giay con lai cua accessToken. */
  expiresIn: number
}

export interface RegisterRequest {
  email: string
  password: string
  fullName: string
  phone?: string
}

// ── Catalog ───────────────────────────────────────────────────────────────

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED'

export interface BrandResponse {
  id: number
  name: string
  slug: string
}

export interface CategoryResponse {
  id: number
  name: string
  slug: string
  parentId?: number | null
  parentName?: string | null
}

export interface VariantResponse {
  id: number
  sku: string
  name: string
  price: number
  compareAtPrice?: number | null
  /** Hang thuc su mua duoc = ton kho - dang giu. */
  availableQuantity: number
  attributes?: Record<string, unknown> | null
}

export interface ProductSummaryResponse {
  id: number
  name: string
  slug: string
  thumbnailUrl?: string | null
  brandName: string
  categoryName: string
  minPrice: number
  maxPrice: number
  availableQuantity: number
  spec?: Record<string, unknown> | null
}

export interface ProductDetailResponse {
  id: number
  name: string
  slug: string
  description?: string | null
  thumbnailUrl?: string | null
  brand: BrandResponse
  category: CategoryResponse
  spec?: Record<string, unknown> | null
  status: ProductStatus
  variants: VariantResponse[]
  images: string[]
}

/** specKeys la hop cua moi key xuat hien o cac san pham dem so sanh. */
export interface CompareResponse {
  specKeys: string[]
  products: ProductSummaryResponse[]
  specs: Array<Record<string, unknown>>
}

export interface VariantRequest {
  sku: string
  name: string
  price: number
  compareAtPrice?: number | null
  stockQuantity: number
  attributes?: Record<string, unknown>
}

export interface CreateProductRequest {
  categoryId: number
  brandId: number
  name: string
  slug: string
  description?: string
  thumbnailUrl?: string
  spec?: Record<string, unknown>
  variants: VariantRequest[]
  images?: string[]
}

export interface UpdateProductRequest {
  categoryId: number
  brandId: number
  name: string
  slug: string
  description?: string
  thumbnailUrl?: string
  spec?: Record<string, unknown>
  status: ProductStatus
}

/** Tham so tim kiem san pham. specFilter map sang query `spec.<key>=<value>`. */
export interface ProductSearchParams {
  keyword?: string
  categoryId?: number
  brandId?: number
  minPrice?: number
  maxPrice?: number
  inStock?: boolean
  specFilter?: Record<string, string>
  page?: number
  size?: number
  /** Vd 'price,asc' - backend chi cho sort theo name | price | createdAt. */
  sort?: string
}

/** Key spec duoc backend cho phep loc (ProductRepositoryImpl.allowedSpecKeys). */
export const ALLOWED_SPEC_KEYS = [
  'ram', 'storage', 'cpu', 'chipset', 'gpu', 'screen', 'os',
  'camera', 'battery', 'refreshRate', 'weight', 'releaseYear',
] as const

// ── Cart ──────────────────────────────────────────────────────────────────

/** Gioi han so luong moi dong - backend tra 422 QUANTITY_EXCEEDS_LIMIT khi vuot. */
export const MAX_QUANTITY_PER_ITEM = 10

export interface CartItemResponse {
  id: number
  variantId: number
  sku: string
  productName: string
  variantName: string
  thumbnailUrl?: string | null
  unitPrice: number
  quantity: number
  lineTotal: number
  availableQuantity: number
  /** true khi so luong trong gio vuot qua hang con lai. */
  exceedsAvailable: boolean
}

export interface CartResponse {
  id: number
  items: CartItemResponse[]
  totalItems: number
  subtotal: number
  hasStockIssue: boolean
}

// ── Checkout / giu hang ───────────────────────────────────────────────────

/** Mot variant khong du hang - nam trong `details` cua loi 409 INSUFFICIENT_STOCK. */
export interface StockShortage {
  variantId: number
  sku: string
  productName: string
  requested: number
  available: number
}

export interface ReservedLine {
  variantId: number
  sku: string
  productName: string
  variantName: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface CheckoutSessionResponse {
  sessionId: string
  expiresAt: string
  secondsRemaining: number
  items: ReservedLine[]
  subtotal: number
}

// ── Order ─────────────────────────────────────────────────────────────────

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'PACKED'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED'

export interface ShippingAddress {
  receiverName: string
  phone: string
  street: string
  ward: string
  district: string
  province: string
}

export interface PlaceOrderRequest {
  sessionId: string
  shippingAddress: ShippingAddress
  note?: string
  couponCode?: string
}

export interface OrderItemResponse {
  id: number
  variantId: number
  productName: string
  variantName: string
  sku: string
  unitPrice: number
  quantity: number
  lineTotal: number
}

export interface OrderResponse {
  id: number
  code: string
  status: OrderStatus
  subtotal: number
  discountAmount: number
  shippingFee: number
  totalAmount: number
  couponCode?: string | null
  shippingAddress: Record<string, string>
  note?: string | null
  items: OrderItemResponse[]
  placedAt: string
  deliveredAt?: string | null
}

export interface OrderSummaryResponse {
  id: number
  code: string
  status: OrderStatus
  totalAmount: number
  totalItems: number
  placedAt: string
}

export interface StatusHistoryResponse {
  fromStatus?: OrderStatus | null
  toStatus: OrderStatus
  changedBy?: number | null
  note?: string | null
  createdAt: string
}

// ── Payment ───────────────────────────────────────────────────────────────

export type PaymentMethod = 'MOCK_CARD' | 'COD'
export type PaymentStatus = 'INITIATED' | 'SUCCEEDED' | 'FAILED' | 'REFUNDED'

export interface PaymentResponse {
  id: number
  orderCode: string
  method: PaymentMethod
  status: PaymentStatus
  amount: number
  transactionRef: string
  failureReason?: string | null
  paidAt?: string | null
}

/** Body callback da ky san - dan thang vao POST /payments/webhook. */
export interface SimulatedCallback {
  transactionRef: string
  status: PaymentStatus
  amount: number
  signature: string
  hint: string
}

export interface WebhookRequest {
  transactionRef: string
  status: PaymentStatus
  amount: number
  signature: string
}

// ── Promotion ─────────────────────────────────────────────────────────────

export type DiscountType = 'PERCENT' | 'FIXED'

/** Chi de hien thi truoc - KHONG giu cho. Luot that su tru khi dat hang. */
export interface ValidateCouponResponse {
  valid: boolean
  discountAmount: number
  reason?: string | null
  message: string
}

export interface CouponResponse {
  id: number
  code: string
  type: DiscountType
  value: number
  minOrderAmount?: number | null
  maxDiscount?: number | null
  usageLimit?: number | null
  usedCount: number
  perUserLimit?: number | null
  startsAt: string
  endsAt: string
  active: boolean
}

export interface CreateCouponRequest {
  code: string
  type: DiscountType
  value: number
  minOrderAmount?: number
  maxDiscount?: number
  usageLimit?: number
  perUserLimit?: number
  startsAt: string
  endsAt: string
}

// ── Flash sale ────────────────────────────────────────────────────────────

export type FlashSaleStatus = 'SCHEDULED' | 'RUNNING' | 'ENDED'

export interface FlashSaleItemResponse {
  id: number
  variantId: number
  productName: string
  variantName: string
  thumbnailUrl?: string | null
  originalPrice: number
  salePrice: number
  discountPercent: number
  saleQuantity: number
  /** Doc tu Redis khi phien dang chay, tu DB khi da ket thuc. */
  remaining: number
  perUserLimit: number
}

export interface FlashSaleResponse {
  id: number
  name: string
  startsAt: string
  endsAt: string
  status: FlashSaleStatus
  secondsUntilStart: number
  items: FlashSaleItemResponse[]
}

export interface FlashSalePurchaseRequest {
  quantity: number
  shippingAddress: ShippingAddress
}

export interface CreateFlashSaleRequest {
  name: string
  startsAt: string
  endsAt: string
  items: Array<{
    variantId: number
    salePrice: number
    saleQuantity: number
    perUserLimit?: number
  }>
}

// ── Review ────────────────────────────────────────────────────────────────

export interface ReviewResponse {
  id: number
  productId: number
  reviewerName: string
  rating: number
  comment?: string | null
  verifiedPurchase: boolean
  createdAt: string
}

export interface RatingSummary {
  average: number
  total: number
  /** So luot theo tung muc sao, vd {"5": 12, "4": 3}. */
  distribution: Record<string, number>
}

export interface CreateReviewRequest {
  orderItemId: number
  rating: number
  comment?: string
}

// ── Warranty ──────────────────────────────────────────────────────────────

export type SerialStatus = 'IN_STOCK' | 'SOLD' | 'RETURNED' | 'DEFECTIVE'
export type WarrantyStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'COMPLETED'

export interface WarrantyLookupResponse {
  serialNo: string
  productName: string
  variantName: string
  status: SerialStatus
  /** null neu hang chua xuat kho. */
  activatedAt?: string | null
  expiresAt?: string | null
  underWarranty: boolean
  warrantyMonths: number
  message: string
}

export interface SerialResponse {
  id: number
  serialNo: string
  status: SerialStatus
  activatedAt?: string | null
}

export interface BulkSerialResponse {
  imported: number
  skippedDuplicates: number
  duplicates: string[]
}

export interface WarrantyRequestResponse {
  id: number
  serialNo: string
  productName: string
  status: WarrantyStatus
  reason: string
  resolution?: string | null
  createdAt: string
  resolvedAt?: string | null
}

// ── Admin ─────────────────────────────────────────────────────────────────

export interface DashboardSummary {
  revenueToday: number
  revenueThisMonth: number
  ordersToday: number
  ordersThisMonth: number
  averageOrderValue: number
  ordersByStatus: Partial<Record<OrderStatus, number>>
  lowStockVariants: number
  pendingOutboxEvents: number
  activeReservations: number
}

export interface RevenuePoint {
  period: string
  revenue: number
  orderCount: number
  averageOrderValue: number
}

export interface RevenueReport {
  groupBy: string
  from?: string | null
  to?: string | null
  totalRevenue: number
  totalOrders: number
  points: RevenuePoint[]
}

export interface TopProduct {
  variantId: number
  productName: string
  variantName: string
  sku: string
  quantitySold: number
  revenue: number
}

export interface LowStockItem {
  variantId: number
  sku: string
  productName: string
  variantName: string
  stockQuantity: number
  reservedQuantity: number
  availableQuantity: number
}

export interface AdminOrderRow {
  code: string
  customerEmail: string
  status: OrderStatus
  totalAmount: number
  itemCount: number
  placedAt: string
}

// ── Thống kê (analytics/AnalyticsDtos) ────────────────────────────────────

export interface ReportRange {
  from: string
  to: string
  days: number
}

/** Một chỉ số kèm kỳ trước. changePercent = null khi kỳ trước bằng 0. */
export interface Kpi {
  current: number
  previous: number
  changePercent: number | null
}

export interface AnalyticsOverview {
  range: ReportRange
  previousRange: ReportRange
  revenue: Kpi
  paidOrders: Kpi
  averageOrderValue: Kpi
  unitsSold: Kpi
  placedOrders: Kpi
  /** Đơn vị % */
  cancelRate: Kpi
  discountTotal: Kpi
  newCustomers: Kpi
  buyers: Kpi
  /** Đơn vị % */
  returningBuyerRate: Kpi
}

export type SeriesUnit = 'day' | 'week' | 'month'

export interface SeriesPoint {
  period: string
  revenue: number
  paidOrders: number
  placedOrders: number
  cancelledOrders: number
  newCustomers: number
}

export interface TimeSeries {
  groupBy: SeriesUnit
  range: ReportRange
  points: SeriesPoint[]
  previous: SeriesPoint[]
}

export type BreakdownDimension = 'category' | 'brand' | 'province'

export interface BreakdownRow {
  key: string
  label: string
  revenue: number
  orders: number
  units: number
  /** Đơn vị % */
  share: number
}

export interface Breakdown {
  dimension: BreakdownDimension
  measure: 'LINE_TOTAL' | 'ORDER_TOTAL'
  total: number
  rows: BreakdownRow[]
}

export interface FunnelStage {
  stage: 'PLACED' | 'PAID' | 'PACKED' | 'SHIPPED' | 'DELIVERED'
  count: number
  percentOfPlaced: number | null
}

export interface Funnel {
  range: ReportRange
  stages: FunnelStage[]
  cancelled: number
  refunded: number
  pendingPayment: number
  medianHoursToPay: number | null
  medianHoursToDeliver: number | null
}

export interface HeatCell {
  /** 1 = Thứ Hai … 7 = Chủ Nhật */
  dayOfWeek: number
  hour: number
  orders: number
  revenue: number
}

export interface Heatmap {
  range: ReportRange
  zone: string
  cells: HeatCell[]
}

export interface TopCustomer {
  userId: number
  email: string
  fullName: string
  orders: number
  revenue: number
}

export interface CustomerStats {
  range: ReportRange
  newCustomers: number
  buyers: number
  returningBuyers: number
  returningBuyerRate: number | null
  topCustomers: TopCustomer[]
}

export interface PaymentMethodStats {
  method: PaymentMethod
  attempts: number
  succeeded: number
  failed: number
  successRate: number | null
  amount: number
}

export interface CouponRow {
  code: string
  orders: number
  discount: number
  revenue: number
}

export interface CouponStats {
  range: ReportRange
  paidOrders: number
  ordersWithCoupon: number
  couponOrderShare: number | null
  discountTotal: number
  rows: CouponRow[]
}

export interface StockByCategory {
  label: string
  units: number
  value: number
}

export interface SlowMover {
  variantId: number
  sku: string
  productName: string
  variantName: string
  availableQuantity: number
  stockValue: number
  lastSoldAt?: string | null
}

export interface InventoryStats {
  outOfStock: number
  lowStock: number
  healthy: number
  totalUnits: number
  reservedUnits: number
  stockValue: number
  lowThreshold: number
  slowDays: number
  byCategory: StockByCategory[]
  slowMovers: SlowMover[]
}

export interface LowRatedProduct {
  productId: number
  name: string
  slug: string
  average: number
  reviews: number
}

export interface ReviewStats {
  range: ReportRange
  total: number
  average: number | null
  distribution: Record<string, number>
  lowestRated: LowRatedProduct[]
}

// ── Quản lý người dùng (identity/dto/AccountDtos) ─────────────────────────

export type UserStatus = 'ACTIVE' | 'LOCKED'

export interface AdminUserRow {
  id: number
  email: string
  fullName: string
  phone?: string | null
  role: Role
  status: UserStatus
  createdAt: string
  paidOrders: number
  totalSpent: number
  lastOrderAt?: string | null
}

export interface AdminUserDetail {
  user: AdminUserRow
  /** Số giây còn bị khoá đăng nhập tạm thời; 0 = không bị khoá. */
  loginLockedSeconds: number
  recentOrders: OrderSummaryResponse[]
}

export interface CreateUserRequest {
  email: string
  password: string
  fullName: string
  phone?: string
  role: Role
}

// ── Tài khoản cá nhân ─────────────────────────────────────────────────────

export interface AddressRequest {
  label?: string
  receiverName: string
  phone: string
  street: string
  ward: string
  district: string
  province: string
  makeDefault: boolean
}

export interface AddressResponse {
  id: number
  label?: string | null
  receiverName: string
  phone: string
  street: string
  ward: string
  district: string
  province: string
  isDefault: boolean
  createdAt: string
}

// ── Yêu thích ─────────────────────────────────────────────────────────────

export interface WishlistItem {
  productId: number
  name: string
  slug: string
  thumbnailUrl?: string | null
  brandName: string
  minPrice?: number | null
  availableQuantity: number
  /** false khi sản phẩm đã ngừng bán. */
  purchasable: boolean
  addedAt: string
}

// ── Thông báo ─────────────────────────────────────────────────────────────

export interface NotificationItem {
  id: number
  type: string
  title: string
  body?: string | null
  link?: string | null
  read: boolean
  createdAt: string
}

// ── Audit log ─────────────────────────────────────────────────────────────

export type AuditAction =
  | 'ORDER_STATUS_CHANGED'
  | 'PRODUCT_CREATED'
  | 'PRODUCT_UPDATED'
  | 'PRODUCT_DELETED'
  | 'VARIANT_CREATED'
  | 'VARIANT_UPDATED'
  | 'STOCK_ADJUSTED'
  | 'COUPON_CREATED'
  | 'COUPON_DEACTIVATED'
  | 'FLASH_SALE_CREATED'
  | 'WARRANTY_RESOLVED'
  | 'USER_CREATED'
  | 'USER_LOCKED'
  | 'USER_UNLOCKED'
  | 'USER_ROLE_CHANGED'
  | 'LOGIN_LOCKOUT_CLEARED'
  | 'ORDERS_EXPORTED'

export interface AuditLogEntry {
  id: number
  actorId?: number | null
  actorEmail: string
  actorRole: string
  action: AuditAction
  targetType: string
  targetId?: string | null
  detail: Record<string, unknown>
  createdAt: string
}
