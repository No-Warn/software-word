<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await adminApi.pendingGoods({ page: page.value, size: size.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

async function approve(row) {
  await ElMessageBox.confirm(`通过「${row.title}」的审核？通过后首页可见`, '审核确认')
  await adminApi.auditGoods(row.id, { action: 'APPROVE' })
  ElMessage.success('已通过')
  load()
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('请输入驳回原因（必填）', '驳回', { inputValidator: (v) => !!v || '驳回原因必填' })
  await adminApi.auditGoods(row.id, { action: 'REJECT', reason: value })
  ElMessage.success('已驳回')
  load()
}

onMounted(load)
</script>

<template>
  <div class="audit" v-loading="loading">
    <h2>商品审核</h2>
    <el-empty v-if="list.length === 0" description="没有待审核商品" />
    <el-table v-else :data="list">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column label="价格" width="100">
        <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="success" @click="approve(row)">通过</el-button>
          <el-button size="small" type="danger" @click="reject(row)">驳回</el-button>
        </template>
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