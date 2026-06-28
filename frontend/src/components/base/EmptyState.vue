<script setup lang="ts">
// 신규 사용자 빈 상태(RPT-03) — 주요 화면 공통. 제목·본문은 호출 측 i18n 키로 주입(규칙 7, 문장 미생성).
import Card from '@/components/base/Card.vue'

defineProps<{ title: string; body: string }>()
</script>

<template>
  <Card class="empty">
    <p class="empty-title">{{ title }}</p>
    <p class="empty-body">{{ body }}</p>
  </Card>
</template>

<style scoped>
.empty {
  text-align: center;
  padding: 28px 12px;
}
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.empty-body {
  font-size: 13px;
  color: var(--hint);
  margin-top: 6px;
  line-height: 1.6;
}
</style>
