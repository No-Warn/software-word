<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const status = ref('PENDING')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

const statusText = { PENDING: '待处理', RESOLVED: '属实已处理', DISMISSED: '不属实' }

async function load() {
  loading.value = true
  try {
    const data = await adminApi.reports({
      status: status.value || undefined,
      page: page.value,
      size: size.value
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function switchStatus(s) {
  status.value = s
  page.value = 1
  load()
}

async function handle(row, result, action, tip) {
  await ElMessageBox.confirm(`确认${tip}？`, '处理确认')
  await adminApi.handleReport(row.id, { result, action: action || null, note: tip })
  ElMessage.success('已处理并记录审计日志')
  load()
}

onMounted(load)
</script>

<template>
  <div class="reports" v-loading="loading">
    <h2>举报处理</h2>
    <el-radio-group v-model="status" style="margin-bottom: 12px" @change="switchStatus">
      <el-radio-button value="PENDING">待处理</el-radio-button>
      <el-radio-button value="RESOLVED">属实已处理</el-radio-button>
      <el-radio-button value="DISMISSED">不属实</el-radio-button>
    </el-radio-group>

    <el-empty v-if="list.length === 0" description="没有举报单" />
    <el-table v-else :data="list">
      <el-table-column label="目标" width="120">
        <template #default="{ row }">{{ row.targetType === 'GOODS' ? '商品' : '用户' }} #{{ row.targetId }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="120" />
      <el-table-column prop="detail" label="说明" min-width="180" />
      <el-table-column prop="handleNote" label="处理说明" min-width="140" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 'PENDING' && row.targetType === 'GOODS'">
            <el-button size="small" type="danger" @click="handle(row, 'RESOLVED', 'OFF_SHELF_GOODS', '属实并下架该商品')">属实·下架</el-button>
            <el-button size="small" @click="handle(row, 'DISMISSED', null, '不属实，关闭举报')">不属实</el-button>
          </template>
          <template v-else-if="row.status === 'PENDING' && row.targetType === 'USER'">
            <el-button size="small" type="danger" @click="handle(row, 'RESOLVED', 'BAN_USER', '属实并封禁该用户')">属实·封禁</el-button>
            <el-button size="small" @click="handle(row, 'DISMISSED', null, '不属实，关闭举报')">不属实</el-button>
          </template>
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