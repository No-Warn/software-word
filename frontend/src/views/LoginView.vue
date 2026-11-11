<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const store = useUserStore()
const form = ref({ email: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.value.email || !form.value.password) {
    ElMessage.warning('请输入邮箱与密码')
    return
  }
  loading.value = true
  try {
    const data = await authApi.login(form.value)
    store.setLogin(data.token, data.userInfo)
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2>登录 · 拾光集市</h2>
      <el-form :model="form" label-width="70px" @submit.prevent>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="校园邮箱" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">登录</el-button>
        <div class="links">
          <router-link to="/register">没有账号？去注册</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.auth-page { display: flex; justify-content: center; padding: 60px 0; }
.auth-card { width: 420px; }
.links { margin-top: 12px; text-align: center; }
</style>