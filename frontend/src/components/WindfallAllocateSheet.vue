<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import { getCurrentCycle } from '@/api/cycle'
import { allocateSuggestion, type Suggestion } from '@/api/suggestions'

// CYCLE-05 인터랙티브 배분 시트. 여윳돈(WINDFALL)/부족(SHORTFALL) 제안의 '반영하기'에서 열린다. 이번 사이클의
// PENDING 비-LIVING 라인을 대상으로, 차액(payload.difference)까지 항목별 금액을 입력해 일괄 적용한다.
//  - WINDFALL: 고른 항목에 더함(차액에서 차감, 남으면 생활비에 잔류).
//  - SHORTFALL: 고른 항목을 줄임(부족분까지, 항목 현재액 이하).
// 실제 plan_lines 조정은 서버(POST /suggestions/{id}/allocate)가 하고, 합·상한 위반은 서버가 막는다.
const props = defineProps<{ open: boolean; suggestion: Suggestion | null }>()
const emit = defineEmits<{ close: []; applied: [id: number] }>()

interface TargetLine {
  id: number
  name: string
  plannedAmount: number
}

const lines = ref<TargetLine[]>([])
const amounts = ref<Record<number, string>>({}) // lineId → 천단위 표시 문자열
const loading = ref(false)
const submitting = ref(false)
const errorCode = ref<string | null>(null)

const type = computed<'WINDFALL' | 'SHORTFALL'>(() =>
  props.suggestion?.type === 'SHORTFALL' ? 'SHORTFALL' : 'WINDFALL',
)
const difference = computed(() => {
  const v = props.suggestion?.payload?.difference
  return typeof v === 'number' ? v : 0
})

function digits(s: string | undefined): number {
  return s ? Number(s.replace(/[^0-9]/g, '')) : 0
}
function setAmount(lineId: number, raw: string) {
  const d = raw.replace(/[^0-9]/g, '')
  amounts.value = { ...amounts.value, [lineId]: d ? Number(d).toLocaleString('ko-KR') : '' }
}

// 입력 합과 남은 한도(차액 − 합). 음수면 초과.
const allocated = computed(() => lines.value.reduce((sum, l) => sum + digits(amounts.value[l.id]), 0))
const remaining = computed(() => difference.value - allocated.value)

function fmt(n: number): string {
  return n.toLocaleString('ko-KR')
}

// 유효성: 합 1 이상·차액 이하, SHORTFALL은 각 항목이 현재액 이하(0 미만 축소 불가).
const valid = computed(() => {
  if (allocated.value <= 0 || allocated.value > difference.value) return false
  if (type.value === 'SHORTFALL') {
    return lines.value.every((l) => digits(amounts.value[l.id]) <= l.plannedAmount)
  }
  return true
})

async function load() {
  loading.value = true
  errorCode.value = null
  amounts.value = {}
  lines.value = []
  try {
    const cycle = await getCurrentCycle()
    // 통장별 묶음을 펼쳐 PENDING·비-LIVING 라인만 대상으로(LIVING은 차액 상대편이라 직접 대상 아님).
    lines.value = cycle.checklist
      .flatMap((g) => g.lines)
      .filter((l) => l.status === 'PENDING' && l.name !== 'LIVING')
      .map((l) => ({ id: l.id, name: l.name, plannedAmount: l.plannedAmount }))
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.open, props.suggestion?.id] as const,
  ([open]) => {
    if (open) void load()
  },
  { immediate: true },
)

function close() {
  emit('close')
}

async function submit() {
  if (submitting.value || !props.suggestion || !valid.value) return
  const allocations = lines.value
    .map((l) => ({ planLineId: l.id, amount: digits(amounts.value[l.id]) }))
    .filter((a) => a.amount > 0)
  if (allocations.length === 0) return
  submitting.value = true
  errorCode.value = null
  try {
    await allocateSuggestion(props.suggestion.id, allocations)
    emit('applied', props.suggestion.id)
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

// 카테고리 토큰(예: 'SAVING')은 i18n 키로, 아니면 이름 스냅샷 그대로 표시.
function lineLabel(name: string): string {
  return name
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <h3 class="title">
      {{ type === 'SHORTFALL' ? $t('suggestion.allocate.shortfallTitle') : $t('suggestion.allocate.windfallTitle') }}
    </h3>
    <p class="guide">
      {{
        type === 'SHORTFALL'
          ? $t('suggestion.allocate.shortfallGuide', { amount: fmt(difference) })
          : $t('suggestion.allocate.windfallGuide', { amount: fmt(difference) })
      }}
    </p>

    <p v-if="loading" class="state">{{ $t('suggestion.allocate.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>
    <p v-else-if="lines.length === 0" class="state">{{ $t('suggestion.allocate.empty') }}</p>

    <template v-else>
      <ul class="lines">
        <li v-for="l in lines" :key="l.id" class="line">
          <div class="line-head">
            <span class="line-name">{{ lineLabel(l.name) }}</span>
            <span class="line-current">{{ fmt(l.plannedAmount) }}{{ $t('common.won') }}</span>
          </div>
          <div class="amount-wrap">
            <input
              :id="`alloc-${l.id}`"
              class="input"
              type="text"
              inputmode="numeric"
              :value="amounts[l.id] ?? ''"
              :placeholder="$t('suggestion.allocate.amountPlaceholder')"
              autocomplete="off"
              @input="setAmount(l.id, ($event.target as HTMLInputElement).value)"
            />
            <span class="unit">{{ $t('common.won') }}</span>
          </div>
        </li>
      </ul>

      <p class="remaining" :class="{ over: remaining < 0 }">
        {{ $t('suggestion.allocate.remaining', { amount: fmt(Math.abs(remaining)) }) }}
        <span v-if="remaining < 0">{{ $t('suggestion.allocate.over') }}</span>
      </p>

      <button class="btn" type="button" :disabled="!valid || submitting" @click="submit">
        {{ $t('suggestion.allocate.submit') }}
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
.guide {
  font-size: 13px;
  color: var(--sub);
  margin-top: 6px;
  line-height: 1.5;
}
.state {
  font-size: 13px;
  color: var(--hint);
  text-align: center;
  padding: 24px 0;
}
.state.error {
  color: var(--red);
}
.lines {
  list-style: none;
  margin-top: 16px;
}
.line {
  padding: 12px 0;
  border-bottom: 1px solid var(--line-2);
}
.line-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.line-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.line-current {
  font-size: 12px;
  color: var(--hint);
}
.amount-wrap {
  position: relative;
  margin-top: 8px;
}
.amount-wrap .unit {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 14px;
  color: var(--sub);
}
.input {
  width: 100%;
  background: var(--bg);
  border: 0;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 15px;
  color: var(--ink);
}
.input::placeholder {
  color: #b0b8c1;
}
.remaining {
  font-size: 13px;
  color: var(--sub);
  margin-top: 14px;
  text-align: right;
}
.remaining.over {
  color: var(--red);
}
.btn {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
  background: var(--purple);
  color: #fff;
  margin-top: 16px;
}
.btn:disabled {
  opacity: 0.5;
}
</style>
