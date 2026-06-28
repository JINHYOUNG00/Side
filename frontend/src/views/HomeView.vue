<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Card from '@/components/base/Card.vue'
import BrandLogo from '@/components/base/BrandLogo.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import ProgressBar from '@/components/base/ProgressBar.vue'
import ChecklistCard from '@/components/ChecklistCard.vue'
import SuggestionCards from '@/components/SuggestionCards.vue'
import { ApiError } from '@/api/client'
import { getWaterfall, type Waterfall } from '@/api/waterfall'
import { listEnvelopes, type Envelope } from '@/api/envelopes'
import { getCurrentCycle, type CurrentCycle } from '@/api/cycle'
import type { Category } from '@/api/budgetItems'

const router = useRouter()
const { t } = useI18n()

// SCR-03 홈 — 남는 돈 헤더 + 폭포 리스트 + 카운트업. 데이터원은 GET /me/waterfall(FLOW-02).
// overAllocated면 경고 배너 + 유연성 순 조정 후보(정렬은 프론트가 수행 — API명세 3장).
// 지급일~D+3에는 ChecklistCard(SCR-03b·CYCLE-04/06)가 최상단에 자체 판정으로 등장한다(폭포와 독립).
// 항목 편집(ITEM-07)은 백엔드 미구현이라 v1 제외.

const data = ref<Waterfall | null>(null)
const loading = ref(true)
const errorCode = ref<string | null>(null)

// 보조 요약 위젯(데스크톱 우측 단·모바일 하단) — 홈을 채우는 기존 데이터 대시보드. 비범위(지출 추적) 없음.
// 폭포 조회와 독립으로 읽어 실패해도 홈 본문을 막지 않는다.
const envelopes = ref<Envelope[]>([])
const cycle = ref<CurrentCycle | null>(null)

const hasEnvelopes = computed(() => envelopes.value.length > 0)
const envTotalSaved = computed(() => envelopes.value.reduce((s, e) => s + e.savedAmount, 0))
const envTotalTarget = computed(() => envelopes.value.reduce((s, e) => s + e.targetAmount, 0))
const envProgress = computed(() =>
  envTotalTarget.value > 0 ? Math.floor((envTotalSaved.value / envTotalTarget.value) * 100) : 0,
)
// 가장 가까운 지출 예정 봉투(서버가 KST로 계산한 dDay 최소값). 표시 전용.
const nearestEnvelope = computed(() =>
  hasEnvelopes.value ? [...envelopes.value].sort((a, b) => a.dDay - b.dDay)[0] : null,
)
// 보조 위젯이 하나라도 있을 때만 우측 단을 펼친다(없으면 본문 단일 폭 유지).
const hasAside = computed(() => hasEnvelopes.value || cycle.value !== null)

// 이체 확인 체크리스트를 지급일 구간 밖에도 열기 — "이번 사이클" 위젯 클릭 시 ChecklistCard를 강제로
// 펼치고 최상단으로 스크롤한다(지급일 구간이면 어차피 자동 노출이라 무해). 체크리스트가 닫히면 해제.
const checklistForced = ref(false)
function openChecklist() {
  checklistForced.value = true
  nextTick(() => {
    const el = document.querySelector('.checklist-card') as HTMLElement | null
    el?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
  })
}

// 다음 월급일까지 일수 — 사이클 종료(다음 지급일 전날) 다음 날이 다음 월급일. 표시용 클라 계산이라
// 날짜 문자열을 UTC 자정 기준 정수 일수로 비교해 TZ 드리프트를 피한다(ChecklistCard 윈도 계산과 동류).
function daysUntil(dateStr: string): number {
  const parts = dateStr.split('-')
  const target = Date.UTC(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
  const now = new Date()
  const today = Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())
  return Math.round((target - today) / 86_400_000)
}
const daysToPayday = computed(() => (cycle.value ? daysUntil(cycle.value.cycleEnd) + 1 : null))

// D-day 배지 라벨(i18n — 규칙 7). 양수 D-n / 0 오늘 / 음수 D+n.
function ddayLabel(d: number): string {
  if (d > 0) return t('home.ddayFuture', { n: d })
  if (d === 0) return t('home.ddayToday')
  return t('home.ddayPast', { n: -d })
}

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
  loadAside()
}

// 보조 위젯 데이터 — 봉투 요약·이번 사이클. 각각 독립으로 읽고 실패(미설정·404 등)는 조용히 빈 값으로
// 흡수해 홈 본문(폭포)에 영향을 주지 않는다. 위젯은 데이터가 있을 때만 렌더(hasEnvelopes/cycle).
async function loadAside() {
  try {
    envelopes.value = (await listEnvelopes()) ?? []
  } catch {
    envelopes.value = []
  }
  try {
    cycle.value = (await getCurrentCycle()) ?? null
  } catch {
    cycle.value = null
  }
}

onMounted(load)
onUnmounted(() => {
  if (rafId) cancelAnimationFrame(rafId)
})
</script>

<template>
  <section class="home">
    <!-- 홈 상단 헤더 — 브랜드 로고. 모바일엔 사이드바가 없어 여기서 브랜드 정체성을 준다
         (데스크톱은 좌측 사이드바가 로고를 이미 노출하므로 중복 방지로 숨김). -->
    <header class="home-top">
      <BrandLogo :size="26" />
    </header>

    <!-- 지급일~D+3 월급날 체크리스트(SCR-03b). 구간 밖·스냅샷 미생성이면 스스로 미노출.
         "이번 사이클" 위젯에서 forceOpen으로 구간 밖에도 열 수 있다(닫기는 카드가 자체 제공). -->
    <ChecklistCard :force-open="checklistForced" @close="checklistForced = false" />

    <!-- 보정/리밸런싱 제안 카드(MOD-06) — 제안이 있을 때만 노출(SCR-03 "제안 존재 시 카드 노출"). -->
    <SuggestionCards />

    <p v-if="loading" class="state">{{ $t('home.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <div v-else-if="data" class="home-grid" :class="{ 'has-aside': hasAside }">
      <div class="home-main">
      <!-- 남는 돈 헤더 + 비상금/생활비 분배 -->
      <Card class="head-card">
        <p class="eyebrow">{{ $t('home.remaining') }}</p>
        <p class="amount" :class="{ over: data.remaining < 0 }">
          <MoneyText :amount="displayRemaining" :unit="$t('common.won')" />
        </p>
        <p v-if="remainingPercent !== null" class="caption">
          {{ $t('home.incomeOf', { income: new Intl.NumberFormat('ko-KR').format(data.income), percent: remainingPercent }) }}
        </p>
        <p class="basis">{{ $t('home.planBasis') }}</p>
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

      <!-- 배분 항목이 없을 때(RPT-03 공통 빈 상태) -->
      <EmptyState v-else :title="$t('home.emptyTitle')" :body="$t('home.emptyBody')" />
      </div>

      <!-- 보조 요약 위젯 — 데스크톱 우측 단·모바일 하단. 기존 데이터(봉투·사이클)로 홈을 채운다. -->
      <aside v-if="hasAside" class="home-aside">
        <Card
          v-if="hasEnvelopes"
          class="aside-card env-summary"
          role="button"
          tabindex="0"
          @click="router.push('/envelopes')"
          @keydown.enter="router.push('/envelopes')"
        >
          <p class="aside-title">
            {{ $t('home.envelopeProgress') }}<span class="chev" aria-hidden="true">›</span>
          </p>
          <div class="env-nums">
            <span class="env-saved">
              <MoneyText :amount="envTotalSaved" />
              <span class="slash">/</span>
              <MoneyText :amount="envTotalTarget" :unit="$t('common.won')" />
            </span>
            <span class="env-pct">{{ envProgress }}%</span>
          </div>
          <ProgressBar :value="envProgress" />
          <p v-if="nearestEnvelope" class="env-near">
            <span class="env-near-name">{{ $t('home.envNearest', { name: nearestEnvelope.name }) }}</span>
            <span class="dday">{{ ddayLabel(nearestEnvelope.dDay) }}</span>
          </p>
        </Card>

        <Card
          v-if="cycle"
          class="aside-card cycle-summary"
          role="button"
          tabindex="0"
          @click="openChecklist"
          @keydown.enter="openChecklist"
        >
          <p class="aside-title">
            {{ $t('home.cycleTitle') }}<span class="chev" aria-hidden="true">›</span>
          </p>
          <p class="cycle-period">
            {{ $t('home.cyclePeriod', { start: cycle.cycleStart, end: cycle.cycleEnd }) }}
          </p>
          <p v-if="daysToPayday !== null" class="cycle-payday">
            {{ $t('home.nextPayday', { n: daysToPayday }) }}
          </p>
          <p class="cycle-transfers">
            {{ $t('home.transfers', { done: cycle.progress.done, total: cycle.progress.total }) }}
          </p>
        </Card>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.home {
  flex: 1;
}
.home-top {
  display: flex;
  align-items: center;
  padding: 2px 0 16px;
}
/* 데스크톱은 좌측 사이드바가 브랜드를 노출하므로 홈 헤더는 숨겨 중복을 피한다. */
@media (min-width: 900px) {
  .home-top {
    display: none;
  }
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
/* 데스크톱(웹 대응) — 본문(폭포)은 넓게, 보조 요약 위젯은 우측 단으로. 모바일은 단일 컬럼
   (보조 위젯이 본문 아래로 자연 스택). 위젯이 하나도 없으면 has-aside가 빠져 본문 단일 폭 유지. */
@media (min-width: 900px) {
  .home-grid.has-aside {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 300px;
    gap: 14px;
    align-items: start;
  }
}
.aside-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
  margin-bottom: 12px;
}
.aside-title .chev {
  font-size: 18px;
  color: var(--hint);
}
.env-summary,
.cycle-summary {
  cursor: pointer;
}
.env-nums {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 8px;
}
.env-saved {
  font-size: 14px;
  color: var(--ink);
}
.env-saved .slash {
  color: var(--hint);
  margin: 0 2px;
}
.env-pct {
  font-size: 13px;
  font-weight: 600;
  color: var(--purple);
}
.env-near {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
  color: var(--sub);
  margin-top: 12px;
}
.env-near-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.env-near .dday {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--purple);
  background: var(--purple-soft);
  border-radius: 999px;
  padding: 2px 8px;
}
.cycle-period {
  font-size: 13px;
  color: var(--sub);
  margin-top: 2px;
}
.cycle-payday {
  font-size: 14px;
  font-weight: 600;
  color: var(--blue);
  margin-top: 10px;
}
.cycle-transfers {
  font-size: 13px;
  color: var(--sub);
  margin-top: 6px;
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
.basis {
  font-size: 12px;
  color: var(--hint);
  margin-top: 4px;
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
</style>
