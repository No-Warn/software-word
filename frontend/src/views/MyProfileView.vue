<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { useUserStore } from '../stores/user'

const store = useUserStore()
const profileForm = ref({ nickname: '', avatarUrl: '', bio: '' })
const passwordForm = ref({ oldPassword: '', newPassword: '' })
const loading = ref(false)

onMounted(async () => {
  const me = await authApi.me()
  profileForm.value = { nickname: me.nickname, avatarUrl: me.avatarUrl || '', bio: me.bio || '' }
})

async function saveProfile() {
  loading.value = true
  try {
    const vo = await authApi.updateMe(profileForm.value)
    store.updateUserInfo(vo)
    ElMessage.success('资料已保存')
  } finally {
    loading.value = false
  }
}

async function changePassword() {
  await authApi.changePassword(passwordForm.value)
  ElMessage.success('密码已修改，请重新登录')
  store.logout()
  location.href = '/login'
}
</script>

<template>
  <div class="profile">
    <h2>个人资料</h2>
    <el-form :model="profileForm" label-width="80px" style="max-width: 480px">
      <el-form-item label="昵称">
        <el-input v-model="profileForm.nickname" maxlength="20" />
      </el-form-item>
      <el-form-item label="头像地址">
        <el-input v-model="profileForm.avatarUrl" placeholder="图片 URL（可留空）" />
      </el-form-item>
      <el-form-item label="简介">
        <el-input v-model="profileForm.bio" type="textarea" :rows="2" maxlength="200" />
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="saveProfile">保存资料</el-button>
    </el-form>

    <el-divider>修改密码</el-divider>
    <el-form :model="passwordForm" label-width="80px" style="max-width: 480px">
      <el-form-item label="旧密码">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="8-64 字符" />
      </el-form-item>
      <el-button type="warning" @click="changePassword">修改密码（旧会话全部失效）</el-button>
    </el-form>
  </div>
</template>