<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import BottomNav from '@/components/base/BottomNav.vue'

const route = useRoute()
const router = useRouter()

const current = computed(() => (route.name as string | undefined) ?? 'home')
// 로그인·콜백 등 chrome:false 라우트는 하단 네비를 숨긴다.
const showChrome = computed(() => route.meta.chrome !== false)

function onSelect(key: string) {
  if (key === 'home') {
    router.push('/')
  }
  // 그 외 탭(checklist/report/all)은 SCR-07에서 라우트 연결
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
