import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import HomeView from '../views/HomeView.vue'

// 路由（需求 1.9.1 页面清单）：懒加载优化 chunk；meta.auth 未登录跳转（带回跳）；meta.admin 双保险
const routes = [
  { path: '/', name: 'home', component: HomeView },
  { path: '/goods/:id', name: 'goodsDetail', component: () => import('../views/GoodsDetailView.vue') },
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { guest: true } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { guest: true } },
  { path: '/publish', name: 'publish', component: () => import('../views/PublishView.vue'), meta: { auth: true } },
  { path: '/publish/:id', name: 'publishEdit', component: () => import('../views/PublishView.vue'), meta: { auth: true } },
  { path: '/my/goods', name: 'myGoods', component: () => import('../views/MyGoodsView.vue'), meta: { auth: true } },
  { path: '/my/orders', name: 'myOrders', component: () => import('../views/MyOrdersView.vue'), meta: { auth: true } },
  { path: '/my/messages', name: 'myMessages', component: () => import('../views/MyMessagesView.vue'), meta: { auth: true } },
  { path: '/my/reports', name: 'myReports', component: () => import('../views/MyReportsView.vue'), meta: { auth: true } },
  { path: '/my/profile', name: 'myProfile', component: () => import('../views/MyProfileView.vue'), meta: { auth: true } },
  { path: '/user/:id', name: 'userProfile', component: () => import('../views/UserProfileView.vue') },
  { path: '/admin', name: 'adminDashboard', component: () => import('../views/admin/DashboardView.vue'), meta: { auth: true, admin: true } },
  { path: '/admin/goods', name: 'adminGoods', component: () => import('../views/admin/GoodsAuditView.vue'), meta: { auth: true, admin: true } },
  { path: '/admin/reports', name: 'adminReports', component: () => import('../views/admin/ReportsView.vue'), meta: { auth: true, admin: true } },
  { path: '/admin/users', name: 'adminUsers', component: () => import('../views/admin/UsersView.vue'), meta: { auth: true, admin: true } },
  { path: '/admin/orders', name: 'adminOrders', component: () => import('../views/admin/OrdersView.vue'), meta: { auth: true, admin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.auth && !store.isLoggedIn) {
    ElMessage.warning('请先登录')
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.admin && !store.isAdmin) {
    ElMessage.error('无管理员权限')
    return { name: 'home' }
  }
  if (to.meta.guest && store.isLoggedIn) {
    return { name: 'home' }
  }
  return true
})

export default router