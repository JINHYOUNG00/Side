<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '@/components/base/BottomNav.vue'

const route = useRoute()
const router = useRouter()

// 라우트 → 하단 탭 키 매핑. 전체 허브와 그 하위(통장·항목)는 '전체' 탭을 활성 유지.
const TAB_BY_ROUTE: Record<string, string> = {
  home: 'home',
  menu: 'all',
  accounts: 'all',
  items: 'all',
  envelopes: 'all',
  archive: 'all',
}
const current = computed(() => {
  const name = route.name as string | undefined
  return (name && TAB_BY_ROUTE[name]) ?? 'home'
})
// 로그인·콜백 등 chrome:false 라우트는 하단 네비를 숨긴다.
const showChrome = computed(() => route.meta.chrome !== false)

function onSelect(key: string) {
  if (key === 'home') {
    router.push('/')
  } else if (key === 'all') {
    router.push('/menu')
  }
  // checklist(봉투·SCR-04)·report(SCR-06) 탭은 해당 화면 도입(Phase 3/5) 시 연결
}
</script>

<template>
  <div class="app-shell">
    <main class="app-body">
      <RouterView />
    </main>
    <BottomNav v-if="showChrome" :current="current" @select="onSelect" />
  </div>
</template>

<style scoped>
.app-shell {
  max-width: 430px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
}
.app-body {
  flex: 1;
  padding: 20px 20px 0;
  display: flex;
  flex-direction: column;
}
</style>
