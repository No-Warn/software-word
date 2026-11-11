import axios from 'axios'
import { ElMessage } from 'element-plus'

// 统一请求封装（03§3.3 utils/request）：Bearer 注入 + Result.code 判定 + 错误码文案
const request = axios.create({ baseURL: '/api/v1', timeout: 10000 })

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  (resp) => {
    const body = resp.data
    if (body && body.code === '00000') return body.data
    ElMessage.error(body?.message || '请求失败')
    return Promise.reject(body)
  },
  (err) => {
    const body = err.response?.data
    ElMessage.error(body?.message || '网络异常，请稍后重试')
    return Promise.reject(err)
  }
)

export default request