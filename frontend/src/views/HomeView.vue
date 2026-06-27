<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import Card from '@/components/base/Card.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import ChecklistCard from '@/components/ChecklistCard.vue'
import { ApiError } from '@/api/client'
import { getWaterfall, type Waterfall } from '@/api/waterfall'
import type { Category } from '@/api/budgetItems'

// SCR-03 홈 — 남는 돈 헤더 + 폭포 리스트 + 카운트업. 데이터원은 GET /me/waterfall(FLOW-02).
// overAllocated면 경고 배너 + 유연성 순 조정 후보(정렬은 프론트가 수행 — API명세 3장).
// 지급일~D+3에는 ChecklistCard(SCR-03b·CYCLE-04/06)가 최상단에 자체 판정으로 등장한다(폭포와 독립).
// 항목 편집(ITEM-07)은 백엔드 미구현이라 v1 제외.

const data = ref<Waterfall | null>(null)
const loading = ref(true)
const errorCode = ref<string | null>(null)

// 카운트업으로 그릴 "남는 돈" 표시값(애니메이션 중에는 target까지 증가).
const displayRemaining = ref(0)
let rafId = 0

// 폭포 스택 바 색 — 시각 정본 화면설계.html SCR-03 평상시의 카테고리 색을 따른다(문구 아님).
const CATEGORY_COLOR: Record<Category, string> = {
  SAVING: '#3182f6',
  INVESTMENT: '#79aef9',
  FIXED: '#b5d2fb',
  INSURANCE: '#1b64da',
  SUBSCRIPTION: '#dce9fd',
  EMERGENCY: '#7048e8', // groups에는 안 나오지만 완전성 위해 매핑
}

// 유연성 순위(작을수록 유연 = 먼저 줄일 후보). API명세: LIVING·EMERGENCY > INVESTMENT > 나머지.
const FLEX_RANK: Record<string, number> = {
  LIVING: 0,
  EMERGENCY: 0,
  INVESTMENT: 1,
  SAVING: 2,
  FIXED: 2,
  INSURANCE: 2,
  SUBSCRIPTION: 2,
}

const hasPlan = computed(() => (data.value?.groups.length ?? 0) > 0)

// 헤더 캡션의 비율(남는 돈 / 월급). 저축률(savingsRate)과 다른 단순 표시 비율이다 — 저축률은 서버가
// SET-02 토글을 반영해 산정한 값(data.savingsRate)을 그대로 쓴다.
const remainingPercent = computed(() => {
  const d = data.value
  if (!d || d.income <= 0) return null
  return Math.round((d.remaining / d.income) * 100)
})

// 저축률(SET-02) 표시 — 서버가 소수 첫째 자리로 반올림한 비율을 그대로 1자리로 포맷(60 → "60.0").
function formatRate(value: number): string {
  return value.toFixed(1)
}

// 스택 바 세그먼트 — 각 그룹 소계 + 남는 돈(양수일 때). flex는 width 비율(income 기준).
const barSegments = computed(() => {
  const d = data.value
  if (!d || d.income <= 0) return []
  const segs = d.groups.map((g) => ({
    key: g.category as string,
    flex: g.subtotal / d.income,
    color: CATEGORY_COLOR[g.category],
  }))
  if (d.envelopeContribution > 0) {
    segs.push({ key: 'ENVELOPE', flex: d.envelopeContribution / d.income, color: '#7048e8' })
  }
  if (d.remaining > 0) {
    segs.push({ key: 'REMAINING', flex: d.remaining / d.income, color: '#e5e8eb' })
  }
  return segs
})

// 과배분 시 유연성 순 조정 후보(프론트 책임). 줄일 수 있는(양수) 버킷만, 유연→고정·금액 큰 순.
const adjustCandidates = computed(() => {
  const d = data.value
  if (!d || !d.overAllocated) return []
  const buckets: { key: string; amount: number }[] = []
  if (d.split.living > 0) buckets.push({ key: 'LIVING', amount: d.split.living })
  if (d.split.emergency > 0) buckets.push({ key: 'EMERGENCY', amount: d.split.emergency })
  for (const g of d.groups) {
    if (g.subtotal > 0) buckets.push({ key: g.category, amount: g.subtotal })
  }
  return buckets
    .map((b) => ({ ...b, flex: FLEX_RANK[b.key] ?? 2 }))
    .sort((a, b) => a.flex - b.flex || b.amount - a.amount)
})

// 과배분 부족액(생활비 음수의 절댓값 = 비상금 포함 배분이 월급을 넘은 크기).
const shortfall = computed(() => {
  const living = data.value?.split.living ?? 0
  return living < 0 ? -living : 0
})

// 버킷 키 → 표시 라벨 i18n 키.
function bucketLabelKey(key: string): string {
  if (key === 'LIVING') return 'home.living'
  if (key === 'EMERGENCY') return 'home.emergency'
  return `items.category.${key}`
}

// 카운트업: 0 → target(easeOutCubic). prefers-reduced-motion이면 애니메이션 생략(접근성·테스트 안정).
function countUp(target: number) {
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches
  if (reduce || typeof requestAnimationFrame === 'undefined') {
    displayRemaining.value = target
    return
  }
  const duration = 600
  const start = performance.now()
  const tick = (now: number) => {
    const p = Math.min(1, (now - start) / duration)
    const eased = 1 - Math.pow(1 - p, 3)
    displayRemaining.value = Math.round(target * eased)
    if (p < 1) rafId = requestAnimationFrame(tick)
    else displayRemaining.value = target
  }
  rafId = requestAnimationFrame(tick)
}

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    const w = await getWaterfall()
    data.value = w
    countUp(w.remaining)
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

onMounted(load)
onUnmounted(() => {
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<template>
  <section class="home">
    <!-- 지급일~D+3 월급날 체크리스트(SCR-03b). 구간 밖·스냅샷 미생성이면 스스로 미노출. -->
    <ChecklistCard />

    <p v-if="loading" class="state">{{ $t('home.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else-if="data">
      <!-- 남는 돈 헤더 + 비상금/생활비 분배 -->
      <Card class="head-card">
        <p class="eyebrow">{{ $t('home.remaining') }}</p>
        <p class="amount" :class="{ over: data.remaining < 0 }">
          <MoneyText :amount="displayRemaining" :unit="$t('common.won')" />
        </p>
        <p v-if="remainingPercent !== null" class="caption">
          {{ $t('home.incomeOf', { income: new Intl.NumberFormat('ko-KR').format(data.income), percent: remainingPercent }) }}
        </p>
        <p v-if="data.income > 0" class="savings">
          <span class="savings-rate">{{ $t('home.savingsRate', { percent: formatRate(data.savingsRate.value) }) }}</span>
          <span class="savings-note">{{
            $t(data.savingsRate.includesInvestment ? 'home.savingsWithInvestment' : 'home.savingsWithoutInvestment')
          }}</span>
        </p>
        <div class="split">
          <div class="split-cell">
            <p class="split-label">{{ $t('home.emergency') }}</p>
            <MoneyText class="split-amt" :amount="data.split.emergency" />
          </div>
          <div class="split-cell">
            <p class="split-label">{{ $t('home.living') }}</p>
            <MoneyText class="split-amt" :class="{ over: data.split.living < 0 }" :amount="data.split.living" />
          </div>
        </div>
      </Card>

      <!-- 과배분 경고 + 유연성 순 조정 후보(FLOW-02) -->
      <Card v-if="data.overAllocated" class="warn-card">
        <p class="warn-banner" role="alert">
          {{ $t('home.overAllocated.banner', { amount: new Intl.NumberFormat('ko-KR').format(shortfall) }) }}
        </p>
        <ul class="candidates">
          <li v-for="c in adjustCandidates" :key="c.key" class="candidate">
            <span class="cand-name">{{ $t(bucketLabelKey(c.key)) }}</span>
            <span class="flex-tag" :data-flex="c.flex">{{ $t(`home.flex.${c.flex}`) }}</span>
            <MoneyText class="cand-amt" :amount="c.amount" />
          </li>
        </ul>
      </Card>

      <!-- 폭포 리스트 -->
      <Card v-if="hasPlan" class="waterfall">
        <div class="stack">
          <i
            v-for="s in barSegments"
            :key="s.key"
            :style="{ flex: s.flex, background: s.color }"
          ></i>
        </div>
        <div v-for="g in data.groups" :key="g.category" class="row">
          <span class="dot" :style="{ background: CATEGORY_COLOR[g.category] }"></span>
          <span class="nm">
            {{ $t(`items.category.${g.category}`) }}
            <small v-if="g.items.length > 1">{{ $t('home.count', { n: g.items.length }) }}</small>
          </span>
          <MoneyText class="amt" :amount="g.subtotal" />
        </div>
        <div v-if="data.envelopeContribution > 0" class="row">
          <span class="dot" style="background: #7048e8"></span>
          <span class="nm">{{ $t('home.envelope') }}</span>
          <MoneyText class="amt" :amount="data.envelopeContribution" />
        </div>
        <div class="row total">
          <span class="dot" style="background: #e5e8eb"></span>
          <span class="nm">{{ $t('home.remaining') }}</span>
          <MoneyText class="amt" :class="data.remaining < 0 ? 'over' : 'good'" :amount="data.remaining" />
        </div>
      </Card>

      <!-- 배분 항목이 없을 때 -->
      <Card v-else class="empty">
        <p class="empty-title">{{ $t('home.emptyTitle') }}</p>
        <p class="empty-body">{{ $t('home.emptyBody') }}</p>
      </Card>
    </template>
  </section>
</template>

<style scoped>
.home {
  flex: 1;
}
.state {
  font-size: 14px;
  color: var(--hint);
  text-align: center;
  padding: 40px 0;
}
.state.error {
  color: var(--red);
}
.head-card {
  margin-bottom: 14px;
}
.eyebrow {
  font-size: 13px;
  color: var(--sub);
  font-weight: 500;
}
.amount {
  font-size: 34px;
  font-weight: 700;
  letter-spacing: -0.8px;
  margin-top: 4px;
  color: var(--ink);
}
.amount.over,
.split-amt.over,
.amt.over {
  color: var(--red);
}
.amt.good {
  color: var(--green);
}
.caption {
  font-size: 13px;
  color: var(--hint);
  margin-top: 6px;
}
.savings {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-top: 10px;
}
.savings-rate {
  font-size: 14px;
  font-weight: 600;
  color: var(--green);
}
.savings-note {
  font-size: 12px;
  color: var(--hint);
}
.split {
  display: flex;
  gap: 10px;
  margin-top: 16px;
}
.split-cell {
  flex: 1;
  background: var(--bg);
  border-radius: 12px;
  padding: 12px 14px;
}
.split-label {
  font-size: 12px;
  color: var(--sub);
}
.split-amt {
  font-size: 16px;
  margin-top: 4px;
  display: inline-block;
}
.warn-card {
  margin-bottom: 14px;
}
.warn-banner {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  line-height: 1.5;
}
.candidates {
  list-style: none;
  margin-top: 6px;
}
.candidate {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line-2);
}
.candidate:last-child {
  border-bottom: 0;
}
.cand-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.flex-tag {
  font-size: 11px;
  color: var(--sub);
  background: var(--bg);
  border-radius: 999px;
  padding: 2px 8px;
}
.flex-tag[data-flex='0'] {
  color: var(--blue);
  background: var(--blue-soft);
}
.cand-amt {
  margin-left: auto;
  font-size: 14px;
  color: var(--sub);
}
.waterfall {
  padding-top: 18px;
}
.stack {
  display: flex;
  height: 10px;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 16px;
}
.stack i {
  display: block;
}
.row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid var(--line-2);
}
.row:last-child {
  border-bottom: 0;
}
.row.total {
  border-top: 1px solid var(--line-2);
  border-bottom: 0;
  margin-top: 2px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  flex-shrink: 0;
}
.nm {
  font-size: 14px;
  color: var(--ink);
}
.nm small {
  font-size: 12px;
  color: var(--hint);
  margin-left: 4px;
}
.row .amt {
  margin-left: auto;
  font-size: 14px;
  color: var(--ink);
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
