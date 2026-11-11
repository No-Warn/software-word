<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { goodsApi, tradeApi } from '../api'
import { useUserStore } from '../stores/user'
import { ConditionText } from '../constants/enums'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const goods = ref(null)
const messages = ref([])
const newMessage = ref('')
const replyTo = ref(null)
const loading = ref(false)

const isOwner = computed(() => goods.value && store.userInfo && goods.value.sellerId === store.userInfo.id)
const canBuy = computed(() => goods.value && goods.value.status === 'ON_SALE' && !isOwner.value)

async function load() {
  loading.value = true
  try {
    goods.value = await goodsApi.detail(route.params.id)
    messages.value = await goodsApi.listMessages(route.params.id)
  } finally {
    loading.value = false
  }
}

async function buy() {
  try {
    await ElMessageBox.confirm(`确认以 ¥${goods.value.price} 下单？下单后 15 分钟内需完成模拟支付`, '下单确认')
  } catch {
    return
  }
  const order = await tradeApi.createOrder(goods.value.id)
  ElMessage.success('下单成功，请 15 分钟内完成支付')
  router.push('/my/orders')
  return order
}

async function sendMessage() {
  if (!newMessage.value.trim()) return
  await goodsApi.createMessage(goods.value.id, {
    content: newMessage.value.trim(),
    parentId: replyTo.value?.id || null
  })
  newMessage.value = ''
  replyTo.value = null
  messages.value = await goodsApi.listMessages(route.params.id)
  ElMessage.success('留言已发布')
}

function reportGoods() {
  ElMessageBox.prompt('请描述违规内容', '举报该商品').then(({ value }) => {
    goodsApi.createReport({ targetType: 'GOODS', targetId: goods.value.id, reason: '用户举报', detail: value || '' })
    ElMessage.success('举报已受理，等待管理员处理')
  }).catch(() => {})
}

onMounted(load)
</script>

<template>
  <div class="detail" v-loading="loading">
    <el-row :gutter="16" v-if="goods">
      <el-col :md="12" :xs="24">
        <div class="images">
          <div v-for="(img, i) in goods.images" :key="i" class="img-item">
            <img :src="img" alt="商品图" />
          </div>
        </div>
      </el-col>
      <el-col :md="12" :xs="24">
        <h2>{{ goods.title }}</h2>
        <div class="price">¥{{ goods.price?.toFixed(2) }}</div>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="成色">{{ ConditionText[goods.condition] || goods.condition }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ goods.status }}</el-descriptions-item>
          <el-descriptions-item label="卖家">
            <router-link v-if="goods.sellerId" :to="`/user/${goods.sellerId}`">{{ goods.sellerNickname || '查看卖家' }}</router-link>
            <span v-if="goods.sellerCredit != null">（信用 {{ goods.sellerCredit }}）</span>
            <span v-else>（暂无评价）</span>
          </el-descriptions-item>
        </el-descriptions>
        <p class="desc">{{ goods.description }}</p>
        <div class="actions">
          <el-button v-if="canBuy" type="primary" size="large" @click="buy">我想要 · 下单</el-button>
          <el-button v-if="!isOwner" @click="reportGoods">举报</el-button>
          <template v-if="isOwner">
            <el-button type="warning" @click="router.push(`/publish/${goods.id}`)">编辑</el-button>
          </template>
        </div>
      </el-col>
    </el-row>

    <el-divider>留言咨询</el-divider>
    <div class="messages">
      <div v-for="m in messages" :key="m.id" class="msg">
        <span class="who">{{ m.senderNickname }}：</span>{{ m.content }}
        <el-button v-if="store.isLoggedIn && !m.parentId" link type="primary" size="small" @click="replyTo = m">回复</el-button>
      </div>
      <el-empty v-if="messages.length === 0" description="暂无留言" />
    </div>
    <div v-if="store.isLoggedIn" class="msg-input">
      <el-input v-model="newMessage" :placeholder="replyTo ? `回复 @${replyTo.senderNickname}` : '留言咨询'" maxlength="200" show-word-limit />
      <el-button type="primary" @click="sendMessage">发布</el-button>
      <el-button v-if="replyTo" @click="replyTo = null">取消回复</el-button>
    </div>
    <el-alert v-else title="登录后可留言咨询" type="info" :closable="false" style="margin-top: 12px" />
  </div>
</template>

<style scoped>
.price { color: #e6a23c; font-size: 24px; font-weight: 700; margin: 12px 0; }
.desc { white-space: pre-wrap; color: #606266; }
.img-item img { max-width: 100%; max-height: 300px; margin-bottom: 8px; border-radius: 4px; }
.messages { margin-top: 8px; }
.msg { padding: 8px 0; border-bottom: 1px dashed #ebeef5; }
.msg-input { display: flex; gap: 8px; margin-top: 12px; }
</style>