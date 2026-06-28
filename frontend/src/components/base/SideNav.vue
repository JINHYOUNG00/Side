<script setup lang="ts">
// 좌측 사이드바 — 데스크톱(≥900px) 전용 네비. 모바일에선 숨고 BottomNav가 대신 보인다.
// BottomNav와 동일 목적지·API(current 표시 / select emit)·아이콘을 그대로 미러한다(표시 전용).
import BrandLogo from '@/components/base/BrandLogo.vue'

defineProps<{ current: string }>()
const emit = defineEmits<{ select: [key: string] }>()

const items = ['home', 'envelopes', 'report', 'all'] as const
</script>

<template>
  <nav class="side-nav">
    <BrandLogo class="side-brand" />
    <button
      v-for="key in items"
      :key="key"
      type="button"
      class="side-item"
      :class="{ on: key === current }"
      @click="emit('select', key)"
    >
      <span class="side-ic" aria-hidden="true">
        <!-- BottomNav와 동일 stroke 아이콘 — currentColor로 side-item 색을 따른다. -->
        <svg v-if="key === 'home'" viewBox="0 0 24 24" fill="none">
          <path d="M3.5 11 12 4l8.5 7" />
          <path d="M5.5 9.5V20h13V9.5" />
        </svg>
        <svg v-else-if="key === 'envelopes'" viewBox="0 0 24 24" fill="none">
          <rect x="3" y="5" width="18" height="14" rx="2.5" />
          <path d="M3.5 7.5 12 13l8.5-5.5" />
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
/* 기본(모바일)은 숨김 — 데스크톱 분기점(tokens --bp-wide=900px)에서만 노출. */
.side-nav {
  display: none;
}
@media (min-width: 900px) {
  .side-nav {
    display: flex;
    flex-direction: column;
    gap: 4px;
    width: var(--side-w);
    flex-shrink: 0;
    height: 100vh;
    position: sticky;
    top: 0;
    background: #fff;
    border-right: 1px solid var(--line-2);
    padding: 28px 16px;
  }
}
.side-brand {
  padding: 4px 12px 24px;
}
.side-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 12px;
  border-radius: var(--r);
  color: var(--sub);
  font-size: 15px;
  font-weight: 600;
  text-align: left;
  transition:
    background 0.15s ease,
    color 0.15s ease;
}
.side-item:hover {
  background: var(--line);
}
.side-item.on {
  background: var(--blue-soft);
  color: var(--blue-deep);
}
.side-ic {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
}
.side-ic svg {
  width: 22px;
  height: 22px;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
@media (prefers-reduced-motion: reduce) {
  .side-item {
    transition: none;
  }
}
</style>
