<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import { recordCheckIn, type CheckInResult } from '@/api/checkin'
import type { CurrentCycle } from '@/api/cycle'

// MOD-05 월말 체크인(RPT-01). 사이클 종료 전일에 생활비 통장 잔액을 입력받아 계획 대비 실제를 기록한다.
// 질문 1개(생활비 잔액) + 선택 입력(이번 달 충당해 넣은 돈, 기본 0). 기록 즉시 결과(달성/초과/잉여)를 피드백한다.
// 초과액 overspend = toppedUp − livingRemaining은 서버가 계산·저장하므로 클라는 응답의 부호로 문구만 고른다(규칙 7).
const props = defineProps<{ open: boolean; cycle: CurrentCycle | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const AMOUNT_MIN = 0 // 잔액·투입액은 0(전액 소진·미투입) 허용 — 봉투 금액(≥1)과 다르다.
const AMOUNT_MAX = 1_000_000_000

const living = ref('') // 생활비 통장 잔액(천 단위 구분 표시 문자열)
const topped = ref('') // 충당 투입액(선택)
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const result = ref<CheckInResult | null>(null) // 기록 성공 후 결과(달성/초과 피드백용)

function moneyModel(store: typeof living) {
  return computed({
    get: () => store.value,
    set: (raw: string) => {
      const digits = raw.replace(/[^0-9]/g, '')
      store.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
    },
  })
}
const livingDisplay = moneyModel(living)
const toppedDisplay = moneyModel(topped)

function digits(s: string): number {
  return Number(s.replace(/[^0-9]/g, ''))
}
const livingAmount = computed(() => digits(living.value))
const toppedAmount = computed(() => (topped.value ? digits(topped.value) : 0))

// 결과 피드백 — overspend 부호로 초과/정확/잉여를 가른다. 절댓값을 천 단위 문구로.
const overspend = computed(() => result.value?.overspend ?? 0)
const overspendAbs = computed(() => Math.abs(overspend.value).toLocaleString('ko-KR'))
const outcome = computed<'over' | 'exact' | 'surplus'>(() => {
  if (overspend.value > 0) return 'over'
  if (overspend.value < 0) return 'surplus'
  return 'exact'
})

// 시트가 열릴 때마다 입력 단계로 초기화.
watch(
  () => [props.open, props.cycle] as const,
  ([open]) => {
    if (!open) return
    living.value = ''
    topped.value = ''
    errorCode.value = null
    result.value = null
  },
  { immediate: true },
)

function inRange(n: number): boolean {
  return Number.isInteger(n) && n >= AMOUNT_MIN && n <= AMOUNT_MAX
}

function validate(): string | null {
  if (!living.value || !inRange(livingAmount.value)) return 'VALIDATION_FAILED'
  if (topped.value && !inRange(toppedAmount.value)) return 'VALIDATION_FAILED'
  return null
}

function close() {
  emit('close')
}

async function submit() {
  if (submitting.value || !props.cycle) return
  const invalid = validate()
  if (invalid) {
    errorCode.value = invalid
    return
  }
  errorCode.value = null
  submitting.value = true
  try {
    result.value = await recordCheckIn({
      cycleId: props.cycle.id,
      livingRemaining: livingAmount.value,
      toppedUp: toppedAmount.value,
    })
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

// 결과 확인 → 부모가 시트를 닫고 추이를 재조회한다.
function done() {
  emit('saved')
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <!-- 결과 단계: overspend 부호로 달성/초과/잉여 피드백 -->
    <template v-if="result">
      <h3 class="title">{{ $t('checkin.doneTitle') }}</h3>
      <p class="result" :class="outcome" role="status">
        <template v-if="outcome === 'over'">{{ $t('checkin.over', { amount: overspendAbs }) }}</template>
        <template v-else-if="outcome === 'surplus'">{{ $t('checkin.surplus', { amount: overspendAbs }) }}</template>
        <template v-else>{{ $t('checkin.exact') }}</template>
      </p>
      <button class="btn" type="button" @click="done">{{ $t('checkin.confirm') }}</button>
    </template>

    <!-- 입력 단계 -->
    <template v-else>
      <h3 class="title">{{ $t('checkin.title') }}</h3>
      <p v-if="cycle" class="sub">{{ $t('checkin.cycle', { label: cycle.label }) }}</p>

      <label class="flabel" for="checkin-living">{{ $t('checkin.living') }}</label>
      <div class="amount-wrap">
        <input
          id="checkin-living"
          v-model="livingDisplay"
          class="input"
          type="text"
          inputmode="numeric"
          :placeholder="$t('checkin.livingPlaceholder')"
          autocomplete="off"
        />
        <span class="unit">{{ $t('common.won') }}</span>
      </div>

      <label class="flabel" for="checkin-topped">{{ $t('checkin.toppedUp') }}</label>
      <p class="hint">{{ $t('checkin.toppedUpHint') }}</p>
      <div class="amount-wrap">
        <input
          id="checkin-topped"
          v-model="toppedDisplay"
          class="input"
          type="text"
          inputmode="numeric"
          :placeholder="$t('checkin.toppedUpPlaceholder')"
          autocomplete="off"
        />
        <span class="unit">{{ $t('common.won') }}</span>
      </div>

      <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ $t('checkin.submit') }}
      </button>
    </template>
  </BottomSheet>
</template>

<style scoped>
.title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 4px;
}
.sub {
  font-size: 13px;
  color: var(--sub);
  margin-top: 6px;
}
.flabel {
  font-size: 13px;
  color: var(--sub);
  font-weight: 500;
  margin-top: 18px;
  display: block;
}
.hint {
  font-size: 12px;
  color: var(--hint);
  margin-top: 4px;
  line-height: 1.5;
}
.input {
  width: 100%;
  background: var(--bg);
  border: 0;
  border-radius: 12px;
  padding: 14px 16px;
  font-size: 15px;
  color: var(--ink);
  margin-top: 8px;
}
.input::placeholder {
  color: #b0b8c1;
}
.amount-wrap {
  position: relative;
}
.amount-wrap .unit {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  margin-top: 4px;
  font-size: 14px;
  color: var(--sub);
}
.result {
  font-size: 14px;
  border-radius: var(--r);
  padding: 14px 16px;
  margin-top: 14px;
  line-height: 1.6;
}
.result.over {
  color: var(--red);
  background: var(--red-soft);
}
.result.surplus,
.result.exact {
  color: var(--green);
  background: var(--green-soft);
}
.error {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  margin-top: 16px;
  line-height: 1.5;
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
