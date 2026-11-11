<script setup>
import { ref, onMounted } from 'vue'
import { authApi } from '../api'

const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

const statusText = { PENDING: '待处理', RESOLVED: '属实已处理', DISMISSED: '不属实' }

async function load() {
  loading.value = true
  try {
    const data = await authApi.myReports({ page: page.value, size: size.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="my-reports" v-loading="loading">
    <h2>我的举报</h2>
    <el-table :data="list">
      <el-table-column label="目标" width="120">
        <template #default="{ row }">{{ row.targetType === 'GOODS' ? '商品' : '用户' }} #{{ row.targetId }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="120" />
      <el-table-column prop="detail" label="说明" min-width="180" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">{{ statusText[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column prop="handleNote" label="处理说明" min-width="160" />
      <el-table-column label="处理时间" width="180">
        <template #default="{ row }">{{ row.handledAt ? new Date(row.handledAt).toLocaleString() : '-' }}</template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
      style="margin-top: 12px"
    />
  </div>
</template>