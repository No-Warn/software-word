import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

// 路由骨架：页面随 T-16~T-20 增量挂载；全局守卫（未登录跳转）在 T-03 后补充
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView }
  ]
})

export default router