<script setup lang="ts">
import { computed } from 'vue'

// 금액 표시 전용 — 천 단위 구분, 음수는 − (U+2212), signed면 양수에 +.
// 단위(원)는 i18n 키를 caller가 prop으로 넘긴다(문구 하드코딩 금지).
const props = withDefaults(
  defineProps<{
    amount: number
    unit?: string
    signed?: boolean
  }>(),
  { unit: '', signed: false },
)

const formatted = computed(() => {
  const body = new Intl.NumberFormat('ko-KR').format(Math.abs(props.amount))
  const sign = props.amount < 0 ? '−' : props.signed ? '+' : ''
  return sign + body
})
</script>

<template>
  <span class="money"
    >{{ formatted }}<small v-if="unit" class="money-unit">{{ unit }}</small></span
  >
</template>

<style scoped>
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
}
.money-unit {
  font-size: 0.7em;
  font-weight: 500;
  color: var(--sub);
  margin-left: 2px;
}
</style>
