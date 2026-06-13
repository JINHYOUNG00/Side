<script setup lang="ts">
import { useRouter } from 'vue-router'
import Card from '@/components/base/Card.vue'
import { useAuthStore } from '@/stores/auth'

// SCR-07 전체 — 허브 화면. 통장·항목 관리 진입 + 로그아웃.
// 보관함(SCR-08·P4)·가져오기/내보내기(DATA-01/02·P4+)·설정 상세(SET-02 투자포함 P5,
// SET-01 월급일/생활비통장 onboarding, SET-03 언어 P7)·탈퇴는 백엔드·후속 Phase 의존이라
// 골격에서는 미연결(고아 링크 금지). 라우트가 생기면 links에 추가만 하면 된다.
const router = useRouter()
const auth = useAuthStore()

const links = [
  { key: 'accounts', to: '/accounts' },
  { key: 'items', to: '/items' },
] as const

function go(to: string) {
  router.push(to)
}

function logout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <section class="menu">
    <header class="head">
      <h1 class="title">{{ $t('menu.title') }}</h1>
    </header>

    <Card class="list">
      <button v-for="link in links" :key="link.key" type="button" class="row" @click="go(link.to)">
        <span class="nm">{{ $t(`menu.${link.key}`) }}</span>
        <span class="chev">›</span>
      </button>
    </Card>

    <button class="logout" type="button" @click="logout">{{ $t('menu.logout') }}</button>
  </section>
</template>

<style scoped>
.menu {
  flex: 1;
}
.head {
  padding: 4px 0 18px;
}
.title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
.list {
  padding: 4px 20px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  text-align: left;
  padding: 15px 0;
  border-bottom: 1px solid var(--line-2);
}
.row:last-child {
  border-bottom: 0;
}
.nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.chev {
  font-size: 18px;
  color: var(--hint);
}
.logout {
  display: block;
  width: 100%;
  text-align: center;
  font-size: 13px;
  color: var(--hint);
  padding: 16px 0;
}
</style>
