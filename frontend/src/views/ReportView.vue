<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Card from '@/components/base/Card.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import CheckInSheet from '@/components/CheckInSheet.vue'
import SuggestionCards from '@/components/SuggestionCards.vue'
import { ApiError } from '@/api/client'
import { getTrend, getSummary, type TrendPoint, type ReportSummary } from '@/api/reports'
import { getCurrentCycle, type CurrentCycle } from '@/api/cycle'

// SCR-06 리포트(RPT-02/03). 저축률·만기·봉투 집행 요약 메트릭 + 사이클별 계획 vs 실제 추이 차트(결측 구분) +
// 월말 체크인 진입(MOD-05). 데이터원은 GET /reports/summary·/reports/trend(조회 전용). 추이가 비면 RPT-03
// 빈 상태로 "첫 체크인 후 추이가 쌓입니다"를 예고한다. 색: 저축률·달성=초록, 계획/배분=파랑.

const summary = ref<ReportSummary | null>(null)
const trend = ref<TrendPoint[]>([])
const currentCycle = ref<CurrentCycle | null>(null) // 체크인 대상(없으면 진입 숨김)
const loading = ref(true)
const errorCode = ref<string | null>(null)

const sheetOpen = ref(false)

const hasTrend = computed(() => trend.value.length > 0)

// 저축률 표시 — 서버가 소수 첫째 자리로 반올림한 값을 그대로 1자리로 포맷(홈과 동일 관용구).
function formatRate(value: number): string {
  return value.toFixed(1)
}

// 차트 스케일 기준 — 계획·실제 통틀어 최댓값(0이면 1로 보정해 0 나눗셈 회피).
const chartMax = computed(() => {
  let max = 0
  for (const p of trend.value) {
    max = Math.max(max, p.planned, p.actual ?? 0)
  }
  return max > 0 ? max : 1
})

function heightPct(value: number): string {
  return `${Math.round((value / chartMax.value) * 100)}%`
}

// 차트 라벨은 사이클 라벨("YYYY-MM")의 월 부분만 — 6개 컬럼이 좁은 폭에 들어가도록.
function shortLabel(label: string): string {
  return label.split('-')[1] ?? label
}

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    const [summaryData, trendData] = await Promise.all([getSummary(), getTrend()])
    summary.value = summaryData
    trend.value = trendData
    // 현재 사이클은 스냅샷 미생성 시 404 — 체크인 진입을 숨길 뿐 리포트 조회 실패는 아니다.
    try {
      currentCycle.value = await getCurrentCycle()
    } catch {
      currentCycle.value = null
    }
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

function openCheckIn() {
  sheetOpen.value = true
}
function closeSheet() {
  sheetOpen.value = false
}
// 체크인을 기록하면 시트를 닫고 추이를 다시 읽어 새 점을 반영한다.
async function onSaved() {
  sheetOpen.value = false
  await load()
}

onMounted(load)
</script>

<template>
  <section class="report">
    <header class="head">
      <h1 class="title">{{ $t('report.title') }}</h1>
    </header>

    <p v-if="loading" class="state">{{ $t('report.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else-if="summary">
      <!-- 보정/리밸런싱 제안 카드(MOD-06) — 제안이 있을 때만 노출 -->
      <SuggestionCards />

      <!-- 요약 메트릭: 저축률 + 만기 수령 누적 + 봉투 집행 -->
      <Card class="metrics">
        <div class="metric">
          <p class="m-label">{{ $t('report.savingsRate') }}</p>
          <p class="m-rate">{{ formatRate(summary.savingsRate.value) }}%</p>
          <p class="m-note">
            {{
              $t(summary.savingsRate.includesInvestment ? 'report.withInvestment' : 'report.withoutInvestment')
            }}
          </p>
        </div>
        <div class="metric-row">
          <div class="metric half">
            <p class="m-label">{{ $t('report.maturityReceived') }}</p>
            <MoneyText class="m-amt received" :amount="summary.maturity.totalReceivedAmount" :unit="$t('common.won')" />
            <p class="m-note">
              {{ $t('report.maturityCounts', { archived: summary.maturity.archivedCount, recorded: summary.maturity.recordedCount }) }}
            </p>
          </div>
          <div class="metric half">
            <p class="m-label">{{ $t('report.envelopeSpent') }}</p>
            <MoneyText class="m-amt" :amount="summary.envelopeSpentTotal" :unit="$t('common.won')" />
          </div>
        </div>
      </Card>

      <!-- 계획 vs 실제 추이 차트 (RPT-02) -->
      <Card v-if="hasTrend" class="trend">
        <p class="t-title">{{ $t('report.trendTitle') }}</p>
        <div class="legend">
          <span class="lg"><i class="sw planned"></i>{{ $t('report.planned') }}</span>
          <span class="lg"><i class="sw actual"></i>{{ $t('report.actual') }}</span>
          <span class="lg"><i class="sw missing"></i>{{ $t('report.missing') }}</span>
        </div>
        <div class="chart">
          <div v-for="p in trend" :key="p.label" class="col" :class="{ dim: !p.checkedIn }">
            <div class="bars">
              <i class="bar planned" :style="{ height: heightPct(p.planned) }"></i>
              <i
                v-if="p.checkedIn && p.actual !== null"
                class="bar actual"
                :style="{ height: heightPct(p.actual) }"
              ></i>
              <i v-else class="bar missing" :aria-label="$t('report.missing')"></i>
            </div>
            <span class="lbl">{{ shortLabel(p.label) }}</span>
          </div>
        </div>
      </Card>

      <!-- 빈 상태(RPT-03): 첫 체크인 후 추이가 쌓임을 예고 -->
      <EmptyState v-else :title="$t('report.emptyTitle')" :body="$t('report.emptyBody')" />

      <!-- 월말 체크인 진입(MOD-05) — 현재 사이클이 있을 때만 -->
      <button v-if="currentCycle" type="button" class="checkin-btn" @click="openCheckIn">
        {{ $t('report.checkInCta') }}
      </button>
    </template>

    <CheckInSheet :open="sheetOpen" :cycle="currentCycle" @close="closeSheet" @saved="onSaved" />
  </section>
</template>

<style scoped>
.report {
  flex: 1;
}
.head {
  padding: 4px 0 18px;
}
.title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
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
.metrics {
  padding: 20px;
  margin-bottom: 14px;
}
.metric-row {
  display: flex;
  gap: 10px;
  margin-top: 18px;
}
.metric.half {
  flex: 1;
}
.m-label {
  font-size: 13px;
  color: var(--sub);
}
.m-rate {
  font-size: 28px;
  font-weight: 700;
  color: var(--green);
  margin-top: 6px;
  letter-spacing: -0.6px;
}
.m-amt {
  display: block;
  font-size: 18px;
  margin-top: 6px;
  color: var(--ink);
}
.m-amt.received {
  color: var(--green);
}
.m-note {
  font-size: 12px;
  color: var(--hint);
  margin-top: 6px;
}
.trend {
  padding: 20px;
  margin-bottom: 14px;
}
.t-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.legend {
  display: flex;
  gap: 14px;
  margin-top: 10px;
}
.lg {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: var(--sub);
}
.sw {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  display: inline-block;
}
.sw.planned {
  background: var(--blue-soft);
}
.sw.actual {
  background: var(--blue);
}
.sw.missing {
  border: 1px dashed var(--line);
  background: transparent;
}
.chart {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  height: 132px;
  margin-top: 16px;
}
.col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.col.dim {
  opacity: 0.85;
}
.bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 3px;
  width: 100%;
}
.bar {
  width: 12px;
  border-radius: 4px 4px 0 0;
  min-height: 2px;
}
.bar.planned {
  background: var(--blue-soft);
}
.bar.actual {
  background: var(--blue);
}
.bar.missing {
  width: 12px;
  height: 100%;
  border: 1px dashed var(--line);
  border-bottom: 0;
  border-radius: 4px 4px 0 0;
  background: transparent;
}
.lbl {
  font-size: 11px;
  color: var(--hint);
  margin-top: 8px;
}
.checkin-btn {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
  background: var(--blue);
  color: #fff;
}
</style>
