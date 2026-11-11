import request from '../utils/request'
import { useUserStore } from '../stores/user'

// 认证与用户 API（契约 02§2.5.2 API-01~07/28）
export const authApi = {
  register: (data) => request.post('/auth/register', data),
  login: (data) => request.post('/auth/login', data),
  logout: () => request.post('/auth/logout'),
  me: () => request.get('/users/me'),
  updateMe: (data) => request.put('/users/me', data),
  changePassword: (data) => request.put('/users/me/password', data),
  profile: (userId) => request.get(`/users/${userId}/profile`),
  myReports: (params) => request.get('/users/me/reports', { params })
}

// 商品与交互 API（契约 API-10~16/22~26/27）
export const goodsApi = {
  list: (params) => request.get('/goods', { params }),
  detail: (id) => request.get(`/goods/${id}`),
  publish: (data) => request.post('/goods', data),
  edit: (id, data) => request.put(`/goods/${id}`, data),
  shelf: (id, onShelf) => request.post(`/goods/${id}/shelf`, null, { params: { onShelf } }),
  remove: (id) => request.delete(`/goods/${id}`),
  myGoods: () => request.get('/users/me/goods'),
  uploadImage: (formData) => request.post('/files/image', formData),
  listMessages: (goodsId) => request.get(`/goods/${goodsId}/messages`),
  createMessage: (goodsId, data) => request.post(`/goods/${goodsId}/messages`, data),
  myMessages: (params) => request.get('/users/me/messages', { params }),
  createReview: (data) => request.post('/reviews', data),
  listOrderReviews: (orderId) => request.get(`/orders/${orderId}/reviews`),
  createReport: (data) => request.post('/reports', data)
}

// 交易 API（契约 API-17~21）
export const tradeApi = {
  createOrder: (goodsId) => request.post('/orders', { goodsId }),
  pay: (orderId) => request.post(`/orders/${orderId}/pay`),
  cancel: (orderId) => request.post(`/orders/${orderId}/cancel`),
  complete: (orderId) => request.post(`/orders/${orderId}/complete`),
  myOrders: (role, params) => request.get('/users/me/orders', { params: { role, ...params } })
}

// 管理 API（契约 API-29~36）：路由层已拦截非 ADMIN，此处双保险
export const adminApi = {
  pendingGoods: (params) => request.get('/admin/goods/pending', { params }),
  auditGoods: (id, data) => request.post(`/admin/goods/${id}/audit`, data),
  reports: (params) => request.get('/admin/reports', { params }),
  handleReport: (id, data) => request.post(`/admin/reports/${id}/handle`, data),
  users: (params) => request.get('/admin/users', { params }),
  banUser: (id, banned) => request.post(`/admin/users/${id}/ban`, null, { params: { banned } }),
  arbitrateOrder: (id, data) => request.post(`/admin/orders/${id}/arbitrate`, data),
  dashboard: () => request.get('/admin/dashboard')
}

export function ensureGuestRedirect() {
  return useUserStore()
}