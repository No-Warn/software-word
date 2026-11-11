<script setup>
import { ref, onMounted } from 'vue'
import { messageApi } from '../api'

// messageApi 未单独建文件：复用 goodsApi 中的 myMessages
import { goodsApi as api } from '../api'
const msgApi = api

const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await msgApi.myMessages({ page: page.value, size: size.value })
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div class="my-messages" v-loading="loading">
    <h2>我的留言</h2>
    <el-table :data="list">
      <el-table-column prop="goodsId" label="商品 ID" width="100" />
      <el-table-column prop="senderNickname" label="发送者" width="120" />
      <el-table-column prop="content" label="内容" min-width="240" />
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="回复" width="90">
        <template #default="{ row }">{{ row.parentId ? '是' : '否' }}</template>
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