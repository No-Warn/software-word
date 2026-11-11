<script setup>
import StatusTag from './StatusTag.vue'

defineProps({
  goods: { type: Object, required: true }
})
const emit = defineEmits(['click'])
</script>

<template>
  <el-card class="goods-card" shadow="hover" @click="emit('click', goods)">
    <div class="card-body">
      <div class="title-row">
        <span class="title">{{ goods.title }}</span>
        <StatusTag :status="goods.status" />
      </div>
      <div class="price-row">
        <span class="price">¥{{ goods.price?.toFixed(2) }}</span>
        <span class="condition">{{ goods.condition }}</span>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.goods-card { cursor: pointer; margin-bottom: 12px; }
.title-row { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: 600; }
.price-row { display: flex; justify-content: space-between; margin-top: 8px; }
.price { color: #e6a23c; font-weight: 700; font-size: 16px; }
.condition { color: #909399; font-size: 12px; }
</style>