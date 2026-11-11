<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const list = ref([])
const loading = ref(false)

const statusText = { ACTIVE: '正常', BANNED: '已封禁' }

async function load() {
  loading.value = true
  try {
    const data = await adminApi.users({
      keyword: keyword.value || undefined,
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

async function toggle(row) {
  const action = row.status === 'ACTIVE' ? '封禁' : '解封'
  await ElMessageBox.confirm(`确认${action}用户「${row.nickname}」？`, `${action}确认`)
  await adminApi.banUser(row.id, row.status === 'ACTIVE')
  ElMessage.success(`已${action}`)
  load()
}

onMounted(load)
</script>

<template>
  <div class="users" v-loading="loading">
    <h2>用户管理</h2>
    <div class="bar">
      <el-input v-model="keyword" placeholder="昵称/邮箱检索" clearable style="max-width: 300px" @keyup.enter="search" />
      <el-button type="primary" @click="search">检索</el-button>
    </div>
    <el-table :data="list">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="nickname" label="昵称" min-width="140" />
      <el-table-column prop="email" label="邮箱" min-width="200" />
      <el-table-column prop="role" label="角色" width="90" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">{{ statusText[row.status] || row.status }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.role === 'USER'"
            size="small"
            :type="row.status === 'ACTIVE' ? 'danger' : 'success'"
            @click="toggle(row)"
          >{{ row.status === 'ACTIVE' ? '封禁' : '解封' }}</el-button>
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

<style scoped>
.bar { display: flex; gap: 8px; margin-bottom: 12px; }
</style>