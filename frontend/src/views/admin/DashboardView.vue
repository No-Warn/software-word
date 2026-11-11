<script setup>
import { ref, onMounted } from 'vue'
import { adminApi } from '../../api'

const data = ref(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    data.value = await adminApi.dashboard()
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="dashboard" v-loading="loading">
    <h2>统计看板</h2>
    <el-row :gutter="12" v-if="data">
      <el-col :span="8">
        <el-card><div class="kpi">{{ data.userTotal }}</div><div class="kpi-label">注册用户总数</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><div class="kpi">{{ data.userTodayNew }}</div><div class="kpi-label">今日新增用户</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><div class="kpi">{{ data.goodsTotal }}</div><div class="kpi-label">商品总数</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><div class="kpi warn">{{ data.goodsPendingAudit }}</div><div class="kpi-label">待审核商品</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><div class="kpi">{{ data.orderTotal }}</div><div class="kpi-label">订单总数</div></el-card>
      </el-col>
      <el-col :span="8">
        <el-card><div class="kpi money">¥{{ data.todayAmount?.toFixed(2) }}</div><div class="kpi-label">今日成交额（模拟）</div></el-card>
      </el-col>
    </el-row>

    <el-divider>近 7 日订单趋势</el-divider>
    <el-table v-if="data" :data="data.orderTrend7d" style="max-width: 480px">
      <el-table-column prop="date" label="日期" />
      <el-table-column label="订单数">
        <template #default="{ row }">
          <el-progress :percentage="Math.min(100, row.count * 10)" :format="() => row.count" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.kpi { font-size: 28px; font-weight: 700; }
.kpi.warn { color: #e6a23c; }
.kpi.money { color: #67c23a; }
.kpi-label { color: #909399; margin-top: 4px; }
.el-card { margin-bottom: 12px; }
</style>