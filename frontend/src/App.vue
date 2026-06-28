<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '@/components/base/BottomNav.vue'
import SideNav from '@/components/base/SideNav.vue'

const route = useRoute()
const router = useRouter()

// 라우트 → 하단 탭 키 매핑. 전체 허브와 그 하위(통장·항목)는 '전체' 탭을 활성 유지.
const TAB_BY_ROUTE: Record<string, string> = {
  home: 'home',
  reports: 'report',
  envelopes: 'envelopes',
  menu: 'all',
  accounts: 'all',
  items: 'all',
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
  } else if (key === 'envelopes') {
    router.push('/envelopes')
  } else if (key === 'report') {
    router.push('/reports')
  } else if (key === 'all') {
    router.push('/menu')
  }
}
</script>

<template>
  <div class="app-shell">
    <SideNav v-if="showChrome" :current="current" @select="onSelect" />
    <main class="app-body" :class="{ 'with-nav': showChrome }">
      <div class="content">
        <RouterView />
      </div>
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
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
}
/* 모바일에서 하단 탭은 fixed라 흐름에서 빠진다 — 콘텐츠가 탭바 뒤로 가려지지 않게
   탭바 높이(+안전영역)만큼 하단 여백을 둔다. 탭바 없는 라우트(로그인 등)·데스크톱은 제외. */
@media (max-width: 899px) {
  .app-body.with-nav {
    padding-bottom: calc(72px + env(safe-area-inset-bottom, 0px));
  }
}
/* 데스크톱(웹 대응) — 좌측 사이드바 + 가운데 정렬된 본문. 모바일 레이아웃은 위 기본값 그대로.
   분기점은 tokens --bp-wide(900px)와 동기화. */
@media (min-width: 900px) {
  .app-shell {
    max-width: none;
    flex-direction: row;
    align-items: flex-start;
  }
  .app-body {
    height: 100vh;
    overflow-y: auto;
    padding: 40px;
    align-items: center;
  }
  .content {
    width: 100%;
    max-width: var(--content-w);
  }
}
</style>
