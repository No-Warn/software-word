<script setup>
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from './api'
import { useUserStore } from './stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()

const menus = [
  { path: '/', label: '首页' },
  { path: '/publish', label: '发布', auth: true },
  { path: '/my/goods', label: '我的商品', auth: true },
  { path: '/my/orders', label: '我的订单', auth: true },
  { path: '/my/messages', label: '我的留言', auth: true },
  { path: '/my/reports', label: '我的举报', auth: true }
]

const adminMenus = [
  { path: '/admin', label: '看板' },
  { path: '/admin/goods', label: '商品审核' },
  { path: '/admin/reports', label: '举报处理' },
  { path: '/admin/users', label: '用户管理' },
  { path: '/admin/orders', label: '订单仲裁' }
]

function isActive(path) {
  return route.path === path
}

async function logout() {
  try {
    await authApi.logout()
  } catch {
    // 无状态登出：前端清除即可
  }
  store.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <div class="brand" @click="router.push('/')">拾光集市 · 校园二手</div>
      <el-menu mode="horizontal" :default-active="route.path" router class="nav" :ellipsis="false">
        <el-menu-item v-for="m in menus" :key="m.path" :index="m.path" v-show="!m.auth || store.isLoggedIn">
          {{ m.label }}
        </el-menu-item>
        <template v-if="store.isAdmin">
          <el-sub-menu index="admin">
            <template #title>管理后台</template>
            <el-menu-item v-for="m in adminMenus" :key="m.path" :index="m.path">{{ m.label }}</el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
      <div class="user-area">
        <template v-if="store.isLoggedIn">
          <span class="who">{{ store.userInfo?.nickname }}</span>
          <el-button link @click="router.push('/my/profile')">资料</el-button>
          <el-button link type="danger" @click="logout">退出</el-button>
        </template>
        <template v-else>
          <el-button link type="primary" @click="router.push('/login')">登录</el-button>
          <el-button link type="primary" @click="router.push('/register')">注册</el-button>
        </template>
      </div>
    </el-header>
    <el-main class="main">
      <router-view />
    </el-main>
    <el-footer class="footer">拾光集市 · 校园二手交易平台（模拟支付 · 单机部署）</el-footer>
  </el-container>
</template>

<style scoped>
.layout { min-height: 100vh; }
.header {
  display: flex; align-items: center; gap: 16px;
  border-bottom: 1px solid #ebeef5;
}
.brand { font-weight: 700; cursor: pointer; white-space: nowrap; color: #409eff; }
.nav { flex: 1; border-bottom: none; }
.user-area { white-space: nowrap; }
.who { margin-right: 8px; color: #606266; }
.main { max-width: 1200px; margin: 0 auto; width: 100%; }
.footer { text-align: center; color: #c0c4cc; font-size: 12px; }
</style>