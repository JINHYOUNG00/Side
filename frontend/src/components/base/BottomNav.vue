<script setup lang="ts">
// 하단 탭 — 화면설계.html .nav 토큰. 표시 전용(라우트 연결은 App에서 select 처리).
// 라우트 실연결은 SCR-07(전체 탭 골격)에서 확장.
defineProps<{ current: string }>()
const emit = defineEmits<{ select: [key: string] }>()

const items = ['home', 'checklist', 'report', 'all'] as const
</script>

<template>
  <nav class="nav">
    <button
      v-for="key in items"
      :key="key"
      type="button"
      class="nav-item"
      :class="{ on: key === current }"
      @click="emit('select', key)"
    >
      <span class="ic" />
      {{ $t('nav.' + key) }}
    </button>
  </nav>
</template>

<style scoped>
.nav {
  display: flex;
  background: #fff;
  border-top: 1px solid var(--line-2);
  padding: 9px 0 12px;
}
.nav-item {
  flex: 1;
  text-align: center;
  font-size: 10.5px;
  color: #b0b8c1;
  font-weight: 500;
}
.nav-item.on {
  color: var(--ink);
}
.ic {
  display: block;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  background: #d8dde3;
  margin: 0 auto 3px;
}
.nav-item.on .ic {
  background: var(--ink);
}
</style>
