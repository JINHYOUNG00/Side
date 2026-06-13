<script setup lang="ts">
import Card from '@/components/base/Card.vue'
</script>

<template>
  <section class="home">
    <header class="home-head">
      <p class="eyebrow">{{ $t('home.remaining') }}</p>
      <p class="amount muted">—</p>
    </header>

    <Card>
      <div class="empty">
        <p class="empty-title">{{ $t('home.emptyTitle') }}</p>
        <p class="empty-body">{{ $t('home.emptyBody') }}</p>
      </div>
    </Card>
  </section>
</template>

<style scoped>
.home-head {
  padding: 8px 0 18px;
}
.eyebrow {
  font-size: 13px;
  color: var(--sub);
  font-weight: 500;
}
.amount {
  font-size: 30px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin-top: 4px;
}
.muted {
  color: var(--hint);
}
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
