<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { goodsApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()
const groups = ref({})
const counts = ref({})
const activeTab = ref('ON_SALE')
const loading = ref(false)

const tabLabel = {
  PENDING_AUDIT: '待审核',
  ON_SALE: '在售',
  REJECTED: '审核不通过',
  OFF_SHELF: '已下架',
  LOCKED: '交易中',
  SOLD: '已售出',
  DRAFT: '草稿'
}

async function load() {
  loading.value = true
  try {
    const data = await goodsApi.myGoods()
    groups.value = data.groups
    counts.value = data.counts
  } finally {
    loading.value = false
  }
}

async function shelf(g, onShelf) {
  await goodsApi.shelf(g.id, onShelf)
  ElMessage.success(onShelf ? '已提交重新上架审核' : '已下架')
  load()
}

async function remove(g) {
  await ElMessageBox.confirm('确认删除该商品？', '删除确认')
  await goodsApi.remove(g.id)
  ElMessage.success('已删除')
  load()
}

onMounted(load)
</script>

<template>
  <div class="my-goods" v-loading="loading">
    <h2>我的商品</h2>
    <el-tabs v-model="activeTab">
      <el-tab-pane
        v-for="(label, key) in tabLabel"
        :key="key"
        :name="key"
        :label="`${tabLabel[key]}（${counts[key] || 0}）`"
      >
        <el-empty v-if="!(groups[key] || []).length" description="暂无商品" />
        <el-table v-else :data="groups[key]">
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column label="价格" width="100">
            <template #default="{ row }">¥{{ row.price?.toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="rejectReason" label="驳回原因" min-width="140" />
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ON_SALE'" size="small" @click="shelf(row, false)">下架</el-button>
              <el-button v-if="row.status === 'OFF_SHELF'" size="small" type="primary" @click="shelf(row, true)">重新上架</el-button>
              <el-button
                v-if="['OFF_SHELF', 'REJECTED'].includes(row.status)"
                size="small"
                @click="router.push(`/publish/${row.id}`)"
              >编辑</el-button>
              <el-button
                v-if="['DRAFT', 'OFF_SHELF', 'REJECTED'].includes(row.status)"
                size="small"
                type="danger"
                @click="remove(row)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>