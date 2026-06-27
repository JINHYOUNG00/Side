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
      // SCR-02 온보딩 스텝1 — 첫 가입 직후 기본 정보 등록(실수령액·월급일·조정 규칙). 인증 필수.
      path: '/onboarding',
      name: 'onboarding',
      component: () => import('@/views/OnboardingView.vue'),
      meta: { chrome: false },
    },
    {
      // SCR-07 전체 — 허브. 하단 탭 '전체'에서 진입, 통장·항목 관리로 분기.
      path: '/menu',
      name: 'menu',
      component: () => import('@/views/MenuView.vue'),
    },
    {
      // MOD-03 통장 관리(목록 + 추가·수정·삭제 폼). SCR-07 전체 허브에서 진입.
      path: '/accounts',
      name: 'accounts',
      component: () => import('@/views/AccountsView.vue'),
    },
    {
      // MOD-01 항목 전체 목록(추가·삭제 폼). SCR-07 전체 허브에서 진입.
      path: '/items',
      name: 'items',
      component: () => import('@/views/ItemsView.vue'),
    },
    {
      // SCR-04 봉투 목록(진행률·D-day + MOD-02 폼·MOD-04 지출 처리). ENV-01~05. 전체 허브에서 진입.
      path: '/envelopes',
      name: 'envelopes',
      component: () => import('@/views/EnvelopesView.vue'),
    },
    {
      // SCR-08 보관함(ARCHIVED 항목·만기 수령 누적 통계·실수령액 기록). ITEM-08. 전체 허브에서 진입.
      path: '/archive',
      name: 'archive',
      component: () => import('@/views/ArchiveView.vue'),
    },
    {
      // SCR-06 리포트(계획 vs 실제 추이·요약 메트릭·결측 구분 + MOD-05 체크인). RPT-02/03. 하단 탭 '리포트'.
      path: '/reports',
      name: 'reports',
      component: () => import('@/views/ReportView.vue'),
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
