// 枚举镜像（03§3.5：前端唯一来源，字段值与后端 enums 包一致，口径 01§1.7.2）
export const GoodsStatus = {
  DRAFT: 'DRAFT',
  PENDING_AUDIT: 'PENDING_AUDIT',
  REJECTED: 'REJECTED',
  ON_SALE: 'ON_SALE',
  LOCKED: 'LOCKED',
  SOLD: 'SOLD',
  OFF_SHELF: 'OFF_SHELF'
}

export const OrderStatus = {
  PENDING_PAY: 'PENDING_PAY',
  PAID: 'PAID',
  COMPLETED: 'COMPLETED',
  CANCELLED: 'CANCELLED',
  CLOSED: 'CLOSED'
}

export const Condition = {
  NEW: 'NEW',
  LIKE_NEW: 'LIKE_NEW',
  GOOD: 'GOOD',
  WELL_USED: 'WELL_USED'
}

export const ConditionText = {
  NEW: '全新',
  LIKE_NEW: '几乎全新',
  GOOD: '轻微使用',
  WELL_USED: '明显使用'
}

export const SortType = { LATEST: 'LATEST', PRICE_ASC: 'PRICE_ASC', PRICE_DESC: 'PRICE_DESC' }