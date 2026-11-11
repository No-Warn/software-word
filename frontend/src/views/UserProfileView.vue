<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '../api'
import GoodsCard from '../components/GoodsCard.vue'

const route = useRoute()
const router = useRouter()
const profile = ref(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    profile.value = await authApi.profile(route.params.id)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="user-profile" v-loading="loading">
    <template v-if="profile">
      <el-descriptions :column="2" border title="卖家主页">
        <el-descriptions-item label="昵称">{{ profile.nickname }}</el-descriptions-item>
        <el-descriptions-item label="信用分">
          {{ profile.credit != null ? profile.credit : '暂无评价' }}
        </el-descriptions-item>
        <el-descriptions-item label="简介" :span="2">{{ profile.bio || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider>在售商品（{{ profile.onSaleGoods.length }}）</el-divider>
      <el-empty v-if="profile.onSaleGoods.length === 0" description="暂无在售商品" />
      <el-row :gutter="12">
        <el-col v-for="g in profile.onSaleGoods" :key="g.id" :xs="24" :sm="12" :md="8" :lg="6">
          <GoodsCard :goods="g" @click="router.push(`/goods/${g.id}`)" />
        </el-col>
      </el-row>
    </template>
  </div>
</template>