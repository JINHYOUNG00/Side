import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      // SCR-01 로그인. public: 비로그인 접근 허용.
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, chrome: false },
    },
    {
      // OAuth 공급자 redirect 착지점 — code를 받아 서버와 교환(AuthCallbackView).
      path: '/login/callback/:provider',
      name: 'auth-callback',
      component: () => import('@/views/AuthCallbackView.vue'),
      meta: { public: true, chrome: false },
    },
    {
      // MOD-03 통장 관리(목록 + 추가·수정·삭제 폼). SCR-07 전체 탭에서 진입.
      path: '/accounts',
      name: 'accounts',
      component: () => import('@/views/AccountsView.vue'),
    },
  ],
})

// 가드 라우팅: 비로그인은 보호 라우트 차단, 로그인 상태로 /login 진입 시 홈으로.
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.isAuthenticated) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'home' }
  }
  return true
})

export default router
