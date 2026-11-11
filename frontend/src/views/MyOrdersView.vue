<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tradeApi, goodsApi } from '../api'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const role = ref('BUYER')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

// 倒计时：全局 1 秒 tick，剩余 ms 由 timeoutAt 计算
const nowTick = ref(Date.now())
let timer
onMounted(() => {
  load()
  timer = setInterval(() => { nowTick.value = Date.now() }, 1000)
})
onUnmounted(() => clearInterval(timer))

function remainMs(row) {
  return Math.max(0, (row.timeoutAt || 0) - nowTick.value)
}
function remainText(row) {
  if (row.status !== 'PENDING_PAY') return '-'
  const ms = remainMs(row)
  if (ms <= 0) return '即将超时'
  const m = Math.floor(ms / 60000)
  const s = Math.floor((ms % 60000) / 1000)
  return `${m}分${s}秒`
}

async function load() {
  loading.value = true
  try {
    const data = await tradeApi.myOrders(role.value, { page: page.value, size: size.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
function switchRole(r) {
  role.value = r
  page.value = 1
  load()
}

async function pay(row) {
  const order = await tradeApi.pay(row.id)
  ElMessage.success(`支付成功，凭证号：${order.payVoucherNo}（模拟支付）`)
  load()
}
async function cancel(row) {
  await ElMessageBox.confirm('确认取消该订单？', '取消确认')
  await tradeApi.cancel(row.id)
  ElMessage.success('已取消，商品恢复在售')
  load()
}
async function complete(row) {
  await ElMessageBox.confirm('面交完成？确认后订单终结并开放互评', '确认完成')
  await tradeApi.complete(row.id)
  ElMessage.success('订单已完成')
  load()
}

const reviewDialog = ref({ visible: false, orderId: null, rating: 5, content: '' })
function openReview(row) {
  reviewDialog.value = { visible: true, orderId: row.id, rating: 5, content: '' }
}
async function submitReview() {
  await goodsApi.createReview({
    orderId: reviewDialog.value.orderId,
    rating: reviewDialog.value.rating,
    content: reviewDialog.value.content
  })
  reviewDialog.value.visible = false
  ElMessage.success('评价成功')
}

const statusText = {
  PENDING_PAY: '待支付',
  PAID: '交易中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  CLOSED: '超时关闭'
}
const canPay = computed(() => (row) => row.status === 'PENDING_PAY' && role.value === 'BUYER')
</script>

<template>
  <div class="my-orders" v-loading="loading">
    <h2>我的订单</h2>
    <el-radio-group v-model="role" style="margin-bottom: 12px" @change="switchRole">
      <el-radio-button value="BUYER">我买到的</el-radio-button>
      <el-radio-button value="SELLER">我卖出的</el-radio-button>
    </el-radio-group>

    <el-table :data="list">
      <el-table-column prop="orderNo" label="订单号" width="220" />
      <el-table-column prop="goodsTitle" label="商品" min-width="140" />
      <el-table-column label="金额" width="90">
        <template #default="{ row }">¥{{ row.amount?.toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="对方" width="110">
        <template #default="{ row }">{{ row.counterpartNickname || '-' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">{{ statusText[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column label="剩余支付时间" width="130">
        <template #default="{ row }">{{ remainText(row) }}</template>
      </el-table-column>
      <el-table-column prop="payVoucherNo" label="支付凭证" min-width="180" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="role === 'BUYER'">
            <el-button v-if="canPay(row)" size="small" type="primary" @click="pay(row)">模拟支付</el-button>
            <el-button v-if="canPay(row)" size="small" @click="cancel(row)">取消</el-button>
            <el-button v-if="row.status === 'PAID'" size="small" type="success" @click="complete(row)">确认完成</el-button>
            <el-button v-if="row.status === 'COMPLETED'" size="small" type="warning" @click="openReview(row)">评价</el-button>
          </template>
          <span v-else style="color: #909399; font-size: 12px">等待买家操作</span>
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

    <el-dialog v-model="reviewDialog.visible" title="交易评价">
      <el-form label-width="70px">
        <el-form-item label="评分">
          <el-rate v-model="reviewDialog.rating" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="reviewDialog.content" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitReview">提交评价</el-button>
      </template>
    </el-dialog>
  </div>
</template>