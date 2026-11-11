<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'

// 仲裁：契约 API-35 仅提供仲裁端点，订单号由纠纷双方提供，管理员在此输入并执行
const orderId = ref(null)
const action = ref('CANCEL')
const loading = ref(false)

async function arbitrate() {
  if (!orderId.value) {
    ElMessage.warning('请输入订单 ID')
    return
  }
  loading.value = true
  try {
    await adminApi.arbitrateOrder(orderId.value, { action: action.value })
    ElMessage.success(`订单 #${orderId.value} 已仲裁为「${action.value === 'COMPLETE' ? '强制完成' : '强制取消（凭证作废）'}」`)
    orderId.value = null
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="arbitrate">
    <h2>订单仲裁</h2>
    <el-alert
      type="warning"
      :closable="false"
      title="仲裁说明"
      description="强制完成仅适用于「交易中（已支付）」订单；强制取消适用于「待支付/交易中」订单，取消后模拟凭证作废、商品恢复在售。全部动作落审计日志。"
      style="max-width: 640px; margin-bottom: 16px"
    />
    <el-form inline style="max-width: 640px">
      <el-form-item label="订单 ID">
        <el-input-number v-model="orderId" :min="1" :controls="false" style="width: 160px" />
      </el-form-item>
      <el-form-item label="仲裁动作">
        <el-select v-model="action">
          <el-option label="强制完成" value="COMPLETE" />
          <el-option label="强制取消（凭证作废）" value="CANCEL" />
        </el-select>
      </el-form-item>
      <el-button type="danger" :loading="loading" @click="arbitrate">执行仲裁</el-button>
    </el-form>
  </div>
</template>