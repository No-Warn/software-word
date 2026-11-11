<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { goodsApi } from '../api'
import GoodsCard from '../components/GoodsCard.vue'

const router = useRouter()
const keyword = ref('')
const categoryId = ref(null)
const sort = ref('LATEST')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await goodsApi.list({
      keyword: keyword.value || undefined,
      categoryId: categoryId.value || undefined,
      sort: sort.value,
      page: page.value,
      size: size.value
    })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}
function openDetail(g) {
  router.push(`/goods/${g.id}`)
}
onMounted(load)
</script>

<template>
  <div class="home" v-loading="loading">
    <div class="search-bar">
      <el-input v-model="keyword" placeholder="搜索闲置宝贝" clearable style="max-width: 320px" @keyup.enter="search" />
      <el-select v-model="categoryId" placeholder="分类" clearable style="width: 130px" @change="search">
        <el-option label="教材" :value="1" />
        <el-option label="数码" :value="2" />
        <el-option label="生活" :value="3" />
        <el-option label="运动" :value="4" />
      </el-select>
      <el-select v-model="sort" style="width: 130px" @change="search">
        <el-option label="最新发布" value="LATEST" />
        <el-option label="价格从低到高" value="PRICE_ASC" />
        <el-option label="价格从高到低" value="PRICE_DESC" />
      </el-select>
      <el-button type="primary" @click="search">搜索</el-button>
    </div>

    <el-empty v-if="!loading && list.length === 0" description="没有找到相关商品" />
    <el-row :gutter="12">
      <el-col v-for="g in list" :key="g.id" :xs="24" :sm="12" :md="8" :lg="6">
        <GoodsCard :goods="g" @click="openDetail" />
      </el-col>
    </el-row>
    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
      style="margin-top: 16px; justify-content: center"
    />
  </div>
</template>

<style scoped>
.search-bar { display: flex; gap: 8px; margin-bottom: 16px; flex-wrap: wrap; }
</style>