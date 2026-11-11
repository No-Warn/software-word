<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()
const form = ref({ email: '', nickname: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.value.email || !form.value.nickname || !form.value.password) {
    ElMessage.warning('请完整填写注册信息')
    return
  }
  loading.value = true
  try {
    const data = await authApi.register(form.value)
    store.setLogin(data.token, data.userInfo)
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card">
      <h2>注册 · 拾光集市</h2>
      <el-form :model="form" label-width="70px" @submit.prevent>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="仅支持校园邮箱（BR-01）" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="2-20 字符" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password placeholder="8-64 字符" />
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">注册</el-button>
        <div class="links">
          <router-link to="/login">已有账号？去登录</router-link>
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