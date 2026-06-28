<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import Card from '@/components/base/Card.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import ProgressBar from '@/components/base/ProgressBar.vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import ProfileEditSheet from '@/components/ProfileEditSheet.vue'
import { ApiError } from '@/api/client'
import { getMe, type Me } from '@/api/me'
import {
  getCurrentCycle,
  confirmIncome,
  changeLineStatus,
  recalibrateCurrentCycle,
  type CurrentCycle,
  type ChecklistLine,
  type PlanLineStatus,
} from '@/api/cycle'

// SCR-03b 홈 체크리스트 카드 — 지급일(cycleStart)~D+3에만 홈 최상단 등장. 비지급일 구간엔 미노출.
// 데이터원은 GET /cycles/current(CYCLE-06). 실수령액 확인(CYCLE-04, PATCH /cycles/{id}/income) →
// 통장별 이체 체크/건너뛰기(CYCLE-06, PATCH /plan-lines/{id}) 동선. 자체적으로 조회·노출 판정하므로
// HomeView는 그냥 얹기만 한다(스냅샷 미생성·구간 밖이면 아무것도 그리지 않음).

// forceOpen: 지급일 구간 밖에도 강제로 펼친다(홈 "이번 사이클" 위젯에서 이체 확인을 언제든 열기 위함).
// 구간 안에선 평소처럼 자동 노출되고, 강제로만 떠 있을 땐 닫기 버튼으로 닫을 수 있다.
const props = withDefaults(defineProps<{ forceOpen?: boolean }>(), { forceOpen: false })
const emit = defineEmits<{ close: [] }>()

const { t, locale } = useI18n()

// 카드 노출 구간 = 지급일부터 D+3까지(요구사항 2.5·화면흐름도 SCR-03). 오늘 KST가 이 범위면 노출.
const WINDOW_DAYS = 3
// 실수령액 상한(서버 CycleController INCOME_MAX, 구현규칙 5장: 1 ~ 10억).
const INCOME_MIN = 1
const INCOME_MAX = 1_000_000_000

const cycle = ref<CurrentCycle | null>(null)
const errorCode = ref<string | null>(null)
// 월급일 보정(SET-01 후속) — 현재 설정을 읽어 시트를 채우고, 저장 후 현재 사이클을 재보정한다.
const me = ref<Me | null>(null)
const profileOpen = ref(false)
const recalError = ref<string | null>(null)
const editingIncome = ref(false)
const incomeInput = ref('') // 천 단위 구분 표시 문자열, 제출 시 정수로 파싱
const incomeError = ref<string | null>(null)
const submittingIncome = ref(false)
// 동시 토글 방지 — 현재 갱신 중인 라인 id들.
const busyLines = ref<Set<number>>(new Set())

// 오늘(Asia/Seoul) YYYY-MM-DD. 브라우저 로컬 TZ와 무관하게 KST 기준으로 구간을 판정한다(규칙 3 정신).
function kstToday(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
}

// YYYY-MM-DD → UTC 자정 epoch(ms). 일수 계산·포맷에 공용으로 쓴다(로컬 TZ·DST 영향 없음).
function utcMidnight(iso: string): number {
  const parts = iso.split('-')
  return Date.UTC(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]))
}

// 두 YYYY-MM-DD 사이 일수(to − from).
function dayDiff(fromIso: string, toIso: string): number {
  return Math.round((utcMidnight(toIso) - utcMidnight(fromIso)) / 86_400_000)
}

// 노출 여부 — 사이클이 있고 오늘이 지급일~D+3 구간일 때만 카드를 그린다.
const visible = computed(() => {
  if (!cycle.value) return false
  const offset = dayDiff(cycle.value.cycleStart, kstToday())
  return offset >= 0 && offset <= WINDOW_DAYS
})

// 실제 렌더 여부 — 사이클이 있고, 지급일 구간이거나(자동) forceOpen(위젯에서 수동으로 연 경우).
const show = computed(() => cycle.value !== null && (visible.value || props.forceOpen))

// 모든 라인 평탄화 — 진행도 산출용.
const allLines = computed(() => cycle.value?.checklist.flatMap((g) => g.lines) ?? [])

// 진행도는 서버 progress 필드 대신 로컬 라인 상태로 재계산(토글 즉시 반영). 의미론은 동일 —
// done = 처리된(PENDING 아닌) 라인 수. done == total이면 남은 이체가 없다.
const progress = computed(() => ({
  done: allLines.value.filter((l) => l.status !== 'PENDING').length,
  total: allLines.value.length,
}))
const progressPct = computed(() =>
  progress.value.total === 0 ? 0 : Math.round((progress.value.done / progress.value.total) * 100),
)

// 지급일 표기(예: "6월 25일" / "June 25"). 사이클 라벨이 아니라 cycleStart를 현재 로케일로 포맷.
const paydayText = computed(() => {
  if (!cycle.value) return ''
  const tag = locale.value === 'en' ? 'en-US' : 'ko-KR'
  return new Intl.DateTimeFormat(tag, { month: 'long', day: 'numeric', timeZone: 'UTC' }).format(
    new Date(utcMidnight(cycle.value.cycleStart)),
  )
})

// LIVING 라인은 머신 토큰이라 i18n으로, 항목 라인은 스냅샷 이름 그대로(규칙 7).
function lineLabel(line: ChecklistLine): string {
  return line.name === 'LIVING' ? t('home.living') : line.name
}

const incomeDisplay = computed({
  get: () => incomeInput.value,
  set: (raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '')
    incomeInput.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
  },
})

async function load() {
  errorCode.value = null
  try {
    cycle.value = await getCurrentCycle()
  } catch (e) {
    // 스냅샷 미생성(404)·기타 오류는 카드를 숨긴다 — 홈의 보조 카드라 폭포 표시를 막지 않는다.
    cycle.value = null
    if (!(e instanceof ApiError) || e.code !== 'NOT_FOUND') {
      errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
    }
  }
}

// 월급일 수정 시트를 채울 현재 설정을 읽는다. 실패하면 시트 진입만 막고(버튼은 me 있을 때만), 카드 본체는 그대로.
async function loadMe() {
  try {
    me.value = await getMe()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    me.value = null
  }
}

function openProfile() {
  if (me.value === null) return
  recalError.value = null
  profileOpen.value = true
}

// 월급일 저장 후, 현재 사이클을 바뀐 월급일로 재보정한다. 이미 이체(DONE)를 시작했으면 409 — 보정 못 하고
// 다음 사이클부터 적용된다는 안내를 띄운다. 보정 성공 시 체크리스트를 다시 받아 새 경계를 반영한다.
async function onPaydaySaved(updated: Me) {
  me.value = updated
  profileOpen.value = false
  recalError.value = null
  try {
    await recalibrateCurrentCycle()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    recalError.value = e.code === 'CYCLE_LOCKED' ? 'CYCLE_LOCKED' : 'INTERNAL_ERROR'
  }
  await load()
}

function openIncomeEditor() {
  if (!cycle.value) return
  incomeInput.value = cycle.value.income.toLocaleString('ko-KR')
  incomeError.value = null
  editingIncome.value = true
}

async function submitIncome() {
  if (!cycle.value || submittingIncome.value) return
  const income = Number(incomeInput.value.replace(/[^0-9]/g, ''))
  if (!Number.isInteger(income) || income < INCOME_MIN || income > INCOME_MAX) {
    incomeError.value = 'VALIDATION_FAILED'
    return
  }
  incomeError.value = null
  submittingIncome.value = true
  try {
    await confirmIncome(cycle.value.id, income)
    // LIVING 라인이 재계산되므로 체크리스트 전체를 다시 받아온다(PATCH 응답엔 라인이 없음).
    await load()
    editingIncome.value = false
  } catch (e) {
    incomeError.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submittingIncome.value = false
  }
}

// 라인 상태 전이 후 로컬 라인을 갱신(진행도·표시 즉시 반영). 응답이 갱신된 라인 단건.
async function setStatus(line: ChecklistLine, status: PlanLineStatus) {
  if (busyLines.value.has(line.id)) return
  errorCode.value = null
  busyLines.value = new Set(busyLines.value).add(line.id)
  try {
    const updated = await changeLineStatus(line.id, status)
    line.status = updated.status
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    const next = new Set(busyLines.value)
    next.delete(line.id)
    busyLines.value = next
  }
}

// 체크 토글: 완료면 대기로 되돌리고, 아니면(대기·건너뜀) 완료로.
function toggleDone(line: ChecklistLine) {
  setStatus(line, line.status === 'DONE' ? 'PENDING' : 'DONE')
}

onMounted(() => {
  load()
  loadMe()
})

defineExpose({ load })
</script>

<template>
  <Card v-if="show && cycle" class="checklist-card">
    <!-- 강제로 열린 경우(지급일 구간 밖)엔 닫기 제공 — 구간 안 자동 노출 땐 숨김. -->
    <div v-if="forceOpen && !visible" class="forced-head">
      <button class="close-forced" type="button" @click="emit('close')">{{ $t('checklist.close') }}</button>
    </div>
    <!-- 헤더: 월급날 + 지급일 + 실수령액 확인 -->
    <p class="eyebrow">{{ $t('checklist.eyebrow', { date: paydayText }) }}</p>
    <div class="income-row">
      <span class="income-label">{{ $t('checklist.income') }}</span>
      <MoneyText class="income-amt" :amount="cycle.income" :unit="$t('common.won')" />
      <span v-if="cycle.incomeConfirmed" class="confirmed" aria-hidden="true">✓</span>
      <button class="edit" type="button" @click="openIncomeEditor">{{ $t('checklist.edit') }}</button>
    </div>

    <!-- 진행도 -->
    <ProgressBar class="pbar" :value="progressPct" color="green" />
    <p class="progress-caption">
      {{ $t('checklist.progress', { done: progress.done, total: progress.total }) }}
    </p>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <!-- 통장별 체크리스트 -->
    <div v-for="g in cycle.checklist" :key="g.accountId" class="group">
      <div class="group-head">
        <span class="group-name">{{ g.accountName }}</span>
        <MoneyText class="group-total" :amount="g.total" />
      </div>
      <div v-for="line in g.lines" :key="line.id" class="line" :class="{ done: line.status === 'DONE', skipped: line.status === 'SKIPPED' }">
        <button
          class="check"
          type="button"
          role="checkbox"
          :aria-checked="line.status === 'DONE'"
          :aria-label="line.status === 'DONE' ? $t('checklist.markPending') : $t('checklist.markDone')"
          :disabled="busyLines.has(line.id)"
          @click="toggleDone(line)"
        >
          <span v-if="line.status === 'DONE'" class="tick" aria-hidden="true">✓</span>
        </button>
        <span class="line-name">{{ lineLabel(line) }}</span>
        <MoneyText class="line-amt" :amount="line.plannedAmount" />
        <button
          v-if="line.status === 'SKIPPED'"
          class="line-action"
          type="button"
          :disabled="busyLines.has(line.id)"
          @click="setStatus(line, 'PENDING')"
        >
          {{ $t('checklist.revert') }}
        </button>
        <button
          v-else-if="line.status === 'PENDING'"
          class="line-action"
          type="button"
          :disabled="busyLines.has(line.id)"
          @click="setStatus(line, 'SKIPPED')"
        >
          {{ $t('checklist.skip') }}
        </button>
        <span v-else class="line-spacer" aria-hidden="true"></span>
      </div>
    </div>

    <p class="hint">{{ $t('checklist.hint') }}</p>

    <!-- 월급일 보정 동선: 표시된 월급날이 틀렸을 때 월급일을 고쳐 이번 사이클을 다시 만든다(SET-01 후속) -->
    <button v-if="me" class="wrong-payday" type="button" @click="openProfile">
      {{ $t('checklist.wrongPayday') }}
    </button>
    <p v-if="recalError" class="error" role="alert">
      {{ recalError === 'CYCLE_LOCKED' ? $t('checklist.recalLocked') : $t(`errors.${recalError}`) }}
    </p>

    <!-- 월급일 수정 시트(저장 시 현재 사이클 재보정) -->
    <ProfileEditSheet :open="profileOpen" :me="me" @close="profileOpen = false" @saved="onPaydaySaved" />

    <!-- 실수령액 수정 시트 -->
    <BottomSheet :open="editingIncome" @close="editingIncome = false">
      <h3 class="sheet-title">{{ $t('checklist.editTitle') }}</h3>
      <div class="amount-wrap">
        <input
          v-model="incomeDisplay"
          class="input"
          type="text"
          inputmode="numeric"
          :placeholder="$t('onboarding.step1.incomePlaceholder')"
          autocomplete="off"
          aria-label="income"
        />
        <span class="unit">{{ $t('common.won') }}</span>
      </div>
      <p v-if="incomeError" class="error" role="alert">{{ $t(`errors.${incomeError}`) }}</p>
      <button class="btn" type="button" :disabled="submittingIncome" @click="submitIncome">
        {{ $t('checklist.save') }}
      </button>
    </BottomSheet>
  </Card>
</template>

<style scoped>
.checklist-card {
  margin-bottom: 14px;
  border: 1.5px solid var(--blue-soft);
}
.forced-head {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
}
.close-forced {
  font-size: 13px;
  font-weight: 600;
  color: var(--sub);
}
.eyebrow {
  font-size: 13px;
  color: var(--blue);
  font-weight: 600;
}
.income-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.income-label {
  font-size: 14px;
  color: var(--ink);
}
.income-amt {
  margin-left: auto;
  font-size: 15px;
  color: var(--ink);
}
.confirmed {
  color: var(--green);
  font-size: 13px;
  font-weight: 700;
}
.edit {
  font-size: 13px;
  color: var(--blue);
  font-weight: 600;
}
.pbar {
  margin: 14px 0 6px;
}
.progress-caption {
  font-size: 12px;
  color: var(--sub);
}
.error {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  margin-top: 12px;
  line-height: 1.5;
}
.group {
  margin-top: 16px;
}
.group-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 4px;
}
.group-name {
  font-size: 12px;
  color: var(--sub);
  font-weight: 600;
}
.group-total {
  margin-left: auto;
  font-size: 12px;
  color: var(--hint);
}
.line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-bottom: 1px solid var(--line-2);
}
.line:last-child {
  border-bottom: 0;
}
.check {
  width: 22px;
  height: 22px;
  border-radius: 999px;
  border: 1.5px solid var(--line-2);
  background: var(--surface);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.line.done .check {
  background: var(--green);
  border-color: var(--green);
}
.tick {
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  line-height: 1;
}
.line-name {
  font-size: 14px;
  color: var(--ink);
}
.line.done .line-name {
  color: var(--hint);
  text-decoration: line-through;
}
.line.skipped .line-name {
  color: var(--hint);
}
.line-amt {
  margin-left: auto;
  font-size: 14px;
  color: var(--ink);
}
.line.done .line-amt,
.line.skipped .line-amt {
  color: var(--hint);
}
.line-action {
  font-size: 12px;
  color: var(--sub);
  font-weight: 600;
  flex-shrink: 0;
}
.line.skipped .line-action {
  color: var(--blue);
}
.line-spacer {
  width: 0;
}
.hint {
  font-size: 12px;
  color: var(--hint);
  margin-top: 12px;
  line-height: 1.5;
}
.wrong-payday {
  display: block;
  margin-top: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--blue);
}
.sheet-title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 14px;
}
.amount-wrap {
  position: relative;
}
.input {
  width: 100%;
  background: var(--bg);
  border: 0;
  border-radius: 12px;
  padding: 14px 16px;
  font-size: 15px;
  color: var(--ink);
}
.input::placeholder {
  color: #b0b8c1;
}
.amount-wrap .unit {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: var(--sub);
}
.btn {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
  background: var(--blue);
  color: #fff;
  margin-top: 16px;
}
.btn:disabled {
  opacity: 0.5;
}
</style>
