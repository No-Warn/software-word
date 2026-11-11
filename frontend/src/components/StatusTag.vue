<script setup>
import { GoodsStatus } from '../constants/enums'

defineProps({
  status: { type: String, required: false, default: '' }
})

// 商品状态 → 展示文案/颜色（枚举口径 01§1.7.2）
const map = {
  DRAFT: { text: '草稿', type: 'info' },
  PENDING_AUDIT: { text: '待审核', type: 'warning' },
  REJECTED: { text: '审核不通过', type: 'danger' },
  ON_SALE: { text: '在售', type: 'success' },
  LOCKED: { text: '交易中', type: 'primary' },
  SOLD: { text: '已售出', type: 'info' },
  OFF_SHELF: { text: '已下架', type: 'info' }
}
</script>

<template>
  <el-tag v-if="map[status]" :type="map[status].type" size="small">{{ map[status].text }}</el-tag>
</template>