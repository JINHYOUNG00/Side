<script setup lang="ts">
import { computed } from 'vue'

// 진척 막대 — 화면설계.html .pbar 토큰. color는 토큰 키(기본 보라=봉투/제안).
const props = withDefaults(
  defineProps<{
    value: number
    color?: 'purple' | 'green' | 'blue' | 'red'
  }>(),
  { color: 'purple' },
)

const pct = computed(() => Math.min(100, Math.max(0, props.value)))
</script>

<template>
  <div class="pbar">
    <i :style="{ width: pct + '%', background: `var(--${color})` }" />
  </div>
</template>

<style scoped>
.pbar {
  height: 7px;
  background: var(--bg);
  border-radius: 4px;
  overflow: hidden;
}
.pbar i {
  display: block;
  height: 100%;
  border-radius: 4px;
}
</style>
