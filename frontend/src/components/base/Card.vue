<script setup lang="ts">
// 토스류 흰 카드 — 화면설계.html .card 토큰과 1:1.
</script>

<template>
  <div class="card">
    <slot />
  </div>
</template>

<style scoped>
.card {
  background: var(--surface);
  border-radius: var(--r-lg);
  padding: 20px;
  margin-bottom: 14px;
}
</style>
