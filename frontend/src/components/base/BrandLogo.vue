<script setup lang="ts">
// 브랜드 로고 — '흐르는 물방울' 마크(폭포: 위에서 아래로 흐르는 물줄기 + 물방울) + 워드마크.
// 마크는 장식이라 aria-hidden, 접근성 이름은 워드마크 텍스트가 제공한다. 워드마크 문구는
// i18n app.name(규칙 7 — 문구 하드코딩 금지). 색은 토큰(파랑=주요)으로, 크기는 size prop에
// 비례(워드마크·간격까지 함께 스케일)해 사이드바(작게)·로그인(크게)에 같은 로고를 쓴다.
import { computed } from 'vue'

const props = withDefaults(defineProps<{ size?: number }>(), { size: 32 })
const wordSize = computed(() => Math.round(props.size * 0.6))
const gap = computed(() => Math.round(props.size * 0.34))
</script>

<template>
  <span class="brand" :style="{ gap: gap + 'px' }">
    <svg class="brand-mark" :width="size" :height="size" viewBox="0 0 32 32" aria-hidden="true">
      <rect class="tile" width="32" height="32" rx="9" />
      <path class="flow" d="M10 8 q5 0 5 5 q0 5 5 5" />
      <path class="flow faded" d="M10 15 q5 0 5 5 q0 5 5 5" />
      <circle class="drop" cx="21.5" cy="9.5" r="1.8" />
    </svg>
    <span class="brand-word" :style="{ fontSize: wordSize + 'px' }">{{ $t('app.name') }}</span>
  </span>
</template>

<style scoped>
.brand {
  display: inline-flex;
  align-items: center;
}
.brand-mark {
  flex-shrink: 0;
  display: block;
}
.tile {
  fill: var(--blue);
}
.flow {
  fill: none;
  stroke: #fff;
  stroke-width: 2.6;
  stroke-linecap: round;
}
.faded {
  opacity: 0.6;
}
.drop {
  fill: #fff;
}
.brand-word {
  font-weight: 800;
  color: var(--ink);
  letter-spacing: -0.01em;
}
</style>
