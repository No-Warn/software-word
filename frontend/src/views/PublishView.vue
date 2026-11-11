<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { goodsApi } from '../api'

const route = useRoute()
const router = useRouter()
const isEdit = computed(() => !!route.params.id)

const form = ref({
  title: '',
  description: '',
  categoryId: 1,
  condition: 'NEW',
  price: null,
  images: []
})
const loading = ref(false)

const categories = [
  { id: 1, name: '教材' },
  { id: 2, name: '数码' },
  { id: 3, name: '生活' },
  { id: 4, name: '运动' }
]

async function loadImage({ file }) {
  const fd = new FormData()
  fd.append('file', file)
  try {
    const url = await goodsApi.uploadImage(fd)
    if (form.value.images.length >= 9) {
      ElMessage.warning('最多 9 张图片')
      return
    }
    form.value.images.push(url)
  } catch (e) {
    ElMessage.error('图片上传失败')
  }
}

async function submit() {
  if (!form.value.title || form.value.price == null || form.value.images.length === 0) {
    ElMessage.warning('请完整填写标题/价格并至少上传 1 张图片')
    return
  }
  loading.value = true
  try {
    if (isEdit.value) {
      await goodsApi.edit(route.params.id, form.value)
      ElMessage.success('已保存，状态以审核结果为准')
    } else {
      await goodsApi.publish(form.value)
      ElMessage.success('发布成功，等待管理员审核')
    }
    router.push('/my/goods')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  if (isEdit.value) {
    const detail = await goodsApi.detail(route.params.id)
    form.value = {
      title: detail.title,
      description: detail.description,
      categoryId: detail.categoryId,
      condition: detail.condition,
      price: detail.price,
      images: [...detail.images]
    }
  }
})
</script>

<template>
  <div class="publish">
    <h2>{{ isEdit ? '编辑商品' : '发布闲置' }}</h2>
    <el-form :model="form" label-width="90px" style="max-width: 640px">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" maxlength="60" show-word-limit placeholder="不超过 60 字符" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="form.description" type="textarea" :rows="4" maxlength="2000" show-word-limit />
      </el-form-item>
      <el-form-item label="分类" required>
        <el-select v-model="form.categoryId">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="成色" required>
        <el-select v-model="form.condition">
          <el-option label="全新" value="NEW" />
          <el-option label="几乎全新" value="LIKE_NEW" />
          <el-option label="轻微使用" value="GOOD" />
          <el-option label="明显使用" value="WELL_USED" />
        </el-select>
      </el-form-item>
      <el-form-item label="价格（元）" required>
        <el-input-number v-model="form.price" :min="0.01" :max="99999.99" :precision="2" />
      </el-form-item>
      <el-form-item label="图片" required>
        <el-upload
          :http-request="loadImage"
          :show-file-list="false"
          accept=".jpg,.jpeg,.png,.webp"
        >
          <el-button :icon="Plus">上传图片（1-9 张，≤5MB）</el-button>
        </el-upload>
        <div class="imgs">
          <div v-for="(img, i) in form.images" :key="i" class="img-wrap">
            <img :src="img" />
            <el-button link type="danger" size="small" @click="form.images.splice(i, 1)">删除</el-button>
          </div>
        </div>
      </el-form-item>
      <el-button type="primary" :loading="loading" @click="submit">{{ isEdit ? '保存修改' : '提交审核' }}</el-button>
    </el-form>
  </div>
</template>

<style scoped>
.imgs { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
.img-wrap img { width: 90px; height: 90px; object-fit: cover; border-radius: 4px; }
</style>