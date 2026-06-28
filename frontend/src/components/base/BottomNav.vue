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
      <span class="ic" aria-hidden="true">
        <!-- stroke 아이콘 — currentColor로 nav-item 색(off #b0b8c1 / on ink)을 그대로 따른다. -->
        <svg v-if="key === 'home'" viewBox="0 0 24 24" fill="none">
          <path d="M3.5 11 12 4l8.5 7" />
          <path d="M5.5 9.5V20h13V9.5" />
        </svg>
        <svg v-else-if="key === 'checklist'" viewBox="0 0 24 24" fill="none">
          <path d="M10 7h10M10 12h10M10 17h7" />
          <path d="M4 7l1.3 1.3L7.5 6M4 12.5l1.3 1.3L7.5 11.5" />
        </svg>
        <svg v-else-if="key === 'report'" viewBox="0 0 24 24" fill="none">
          <path d="M4 20h16" />
          <path d="M7 20v-6M12 20V5M17 20v-9" />
        </svg>
        <svg v-else viewBox="0 0 24 24" fill="none">
          <rect x="4" y="4" width="7" height="7" rx="1.8" />
          <rect x="13" y="4" width="7" height="7" rx="1.8" />
          <rect x="4" y="13" width="7" height="7" rx="1.8" />
          <rect x="13" y="13" width="7" height="7" rx="1.8" />
        </svg>
      </span>
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
  transition: color 0.15s ease;
}
.nav-item.on {
  color: var(--ink);
}
.nav-item:active .ic {
  transform: scale(0.9);
}
.ic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin: 0 auto 3px;
  transition: transform 0.1s ease;
}
.ic svg {
  width: 24px;
  height: 24px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
@media (prefers-reduced-motion: reduce) {
  .nav-item,
  .ic,
  .nav-item:active .ic {
    transition: none;
    transform: none;
  }
}
</style>
