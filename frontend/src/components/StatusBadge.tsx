'use client'

import type {
  FlashSaleStatus,
  OrderStatus,
  PaymentStatus,
  SerialStatus,
  WarrantyStatus,
} from '@/types'

type Tone = 'slate' | 'indigo' | 'emerald' | 'amber' | 'red' | 'sky'

interface Look {
  label: string
  tone: Tone
}

const ORDER: Record<OrderStatus, Look> = {
  PENDING_PAYMENT: { label: 'Chờ thanh toán', tone: 'amber' },
  PAID: { label: 'Đã thanh toán', tone: 'indigo' },
  PACKED: { label: 'Đã đóng gói', tone: 'sky' },
  SHIPPED: { label: 'Đang giao', tone: 'sky' },
  DELIVERED: { label: 'Đã giao', tone: 'emerald' },
  CANCELLED: { label: 'Đã huỷ', tone: 'slate' },
  REFUNDED: { label: 'Đã hoàn tiền', tone: 'red' },
}

const PAYMENT: Record<PaymentStatus, Look> = {
  INITIATED: { label: 'Đang chờ', tone: 'amber' },
  SUCCEEDED: { label: 'Thành công', tone: 'emerald' },
  FAILED: { label: 'Thất bại', tone: 'red' },
  REFUNDED: { label: 'Đã hoàn', tone: 'slate' },
}

const WARRANTY: Record<WarrantyStatus, Look> = {
  REQUESTED: { label: 'Chờ xử lý', tone: 'amber' },
  APPROVED: { label: 'Đã duyệt', tone: 'indigo' },
  REJECTED: { label: 'Từ chối', tone: 'red' },
  COMPLETED: { label: 'Hoàn tất', tone: 'emerald' },
}

const SERIAL: Record<SerialStatus, Look> = {
  IN_STOCK: { label: 'Trong kho', tone: 'slate' },
  SOLD: { label: 'Đã bán', tone: 'indigo' },
  RETURNED: { label: 'Khách trả', tone: 'amber' },
  DEFECTIVE: { label: 'Lỗi', tone: 'red' },
}

const FLASH_SALE: Record<FlashSaleStatus, Look> = {
  SCHEDULED: { label: 'Sắp diễn ra', tone: 'amber' },
  RUNNING: { label: 'Đang mở bán', tone: 'red' },
  ENDED: { label: 'Đã kết thúc', tone: 'slate' },
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = Object.fromEntries(
  Object.entries(ORDER).map(([k, v]) => [k, v.label]),
) as Record<OrderStatus, string>

type Props =
  | { kind: 'order'; status: OrderStatus }
  | { kind: 'payment'; status: PaymentStatus }
  | { kind: 'warranty'; status: WarrantyStatus }
  | { kind: 'serial'; status: SerialStatus }
  | { kind: 'flashSale'; status: FlashSaleStatus }

/** Nhãn trạng thái dùng chung — một chỗ quyết định màu, tránh mỗi màn một kiểu. */
export default function StatusBadge(props: Props) {
  const look: Look =
    props.kind === 'order'
      ? ORDER[props.status]
      : props.kind === 'payment'
        ? PAYMENT[props.status]
        : props.kind === 'warranty'
          ? WARRANTY[props.status]
          : props.kind === 'serial'
            ? SERIAL[props.status]
            : FLASH_SALE[props.status]

  // Trạng thái lạ (backend thêm enum mới) vẫn hiển thị được thay vì vỡ trang.
  if (!look) {
    return <span className="badge badge-slate">{String(props.status)}</span>
  }

  return <span className={`badge badge-${look.tone}`}>{look.label}</span>
}
