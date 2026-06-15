<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import { ApiError } from '@/api/client'
import {
  spendEnvelope,
  SHORTFALL_SOURCES,
  type Envelope,
  type SpendInput,
  type ShortfallSource,
} from '@/api/envelopes'

// MOD-04 봉투 지출 처리(ENV-04~05). 실제 낸 금액을 받아 적립액과의 차액을 자동 분류한다:
//  - 부족(actual>saved): 충당 출처(생활비/비상금) 선택
//  - 잉여(actual<saved): 이월(다음 사이클로) / 회수 선택
//  - 정확: 부가 선택 없음
// 처리 후 서버 응답의 status로 일회성 종료(CLOSED)·반복형 갱신(next_due 이동)을 안내한다(ENV-05).
const props = defineProps<{ open: boolean; envelope: Envelope | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const AMOUNT_MIN = 1
const AMOUNT_MAX = 1_000_000_000

const actual = ref('') // 천 단위 구분 표시 문자열
const shortfallSource = ref<ShortfallSource | null>(null)
const carryOver = ref<boolean | null>(null)
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const result = ref<Envelope | null>(null) // 처리 성공 후 갱신된 봉투(종료/갱신 안내용)

const saved = computed(() => props.envelope?.savedAmount ?? 0)
const isRecurring = computed(() => props.envelope?.cycleMonths != null)

const actualDisplay = computed({
  get: () => actual.value,
  set: (raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '')
    actual.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
  },
})

const amount = computed(() => Number(actual.value.replace(/[^0-9]/g, '')))
// 차액 분기는 유효한 금액(≥1)일 때만 노출 — 빈 입력/0에서는 충당·이월 UI를 띄우지 않는다.
const isShortfall = computed(() => amount.value >= AMOUNT_MIN && amount.value > saved.value)
const isSurplus = computed(() => amount.value >= AMOUNT_MIN && amount.value < saved.value)
const isExact = computed(() => amount.value >= AMOUNT_MIN && amount.value === saved.value)
const shortfallText = computed(() => (amount.value - saved.value).toLocaleString('ko-KR'))
const surplusText = computed(() => (saved.value - amount.value).toLocaleString('ko-KR'))

// 갱신된 다음 지출일을 로케일 표기로(목록은 dDay라 ISO 날짜가 노출되는 유일한 지점 — MoneyText의 ko-KR 포맷 관용구와 동일).
const renewedDate = computed(() => {
  if (!result.value) return ''
  const d = new Date(`${result.value.nextDueDate}T00:00:00+09:00`)
  return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'long', timeZone: 'Asia/Seoul' }).format(d)
})

// 시트가 열릴 때마다 초기화(입력 단계로 복귀).
watch(
  () => [props.open, props.envelope] as const,
  ([open]) => {
    if (!open) return
    actual.value = ''
    shortfallSource.value = null
    carryOver.value = null
    errorCode.value = null
    result.value = null
  },
  { immediate: true },
)

// 클라 검증(서버 차액 일관성 규칙 미러링). 통과하면 null.
function validate(): string | null {
  if (!Number.isInteger(amount.value) || amount.value < AMOUNT_MIN || amount.value > AMOUNT_MAX) {
    return 'VALIDATION_FAILED'
  }
  if (isShortfall.value && shortfallSource.value === null) return 'VALIDATION_FAILED'
  if (isSurplus.value && carryOver.value === null) return 'VALIDATION_FAILED'
  return null
}

function close() {
  emit('close')
}

async function submit() {
  if (submitting.value || !props.envelope) return
  const invalid = validate()
  if (invalid) {
    errorCode.value = invalid
    return
  }
  errorCode.value = null
  submitting.value = true
  const payload: SpendInput = {
    actualAmount: amount.value,
    shortfallSource: isShortfall.value ? shortfallSource.value : null,
    carryOver: isSurplus.value ? carryOver.value : null,
  }
  try {
    result.value = await spendEnvelope(props.envelope.id, payload)
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

// 처리 결과 확인 → 목록 갱신(부모가 시트를 닫고 재조회).
function done() {
  emit('saved')
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <!-- 처리 결과 단계: status로 종료/갱신 안내(ENV-05) -->
    <template v-if="result">
      <h3 class="title">{{ $t('envelopes.spendSheet.doneTitle') }}</h3>
      <p v-if="result.status === 'CLOSED'" class="result" role="status">
        {{ $t('envelopes.spendSheet.closed', { name: result.name }) }}
      </p>
      <p v-else class="result" role="status">
        {{ $t('envelopes.spendSheet.renewed', { date: renewedDate }) }}
      </p>
      <button class="btn" type="button" @click="done">
        {{ $t('envelopes.spendSheet.confirm') }}
      </button>
    </template>

    <!-- 입력 단계 -->
    <template v-else>
      <h3 class="title">{{ $t('envelopes.spendSheet.title', { name: envelope?.name }) }}</h3>
      <p class="sub">
        {{ $t('envelopes.spendSheet.saved') }}
        <MoneyText :amount="saved" :unit="$t('common.won')" />
      </p>

      <label class="flabel" for="envelope-actual">{{ $t('envelopes.spendSheet.actual') }}</label>
      <div class="amount-wrap">
        <input
          id="envelope-actual"
          v-model="actualDisplay"
          class="input"
          type="text"
          inputmode="numeric"
          :placeholder="$t('envelopes.spendSheet.amountPlaceholder')"
          autocomplete="off"
        />
        <span class="unit">{{ $t('common.won') }}</span>
      </div>

      <!-- 부족: 충당 출처 선택 -->
      <template v-if="isShortfall">
        <p class="diff short">{{ $t('envelopes.spendSheet.shortfall', { amount: shortfallText }) }}</p>
        <div class="chips" role="radiogroup">
          <button
            v-for="s in SHORTFALL_SOURCES"
            :key="s"
            type="button"
            class="chip"
            :class="{ on: shortfallSource === s }"
            :aria-pressed="shortfallSource === s"
            @click="shortfallSource = s"
          >
            {{ $t(`envelopes.spendSheet.source.${s}`) }}
          </button>
        </div>
      </template>

      <!-- 잉여: 이월 / 회수 선택 -->
      <template v-else-if="isSurplus">
        <p class="diff surplus">{{ $t('envelopes.spendSheet.surplus', { amount: surplusText }) }}</p>
        <div class="chips" role="radiogroup">
          <button
            type="button"
            class="chip"
            :class="{ on: carryOver === true }"
            :aria-pressed="carryOver === true"
            @click="carryOver = true"
          >
            {{ $t('envelopes.spendSheet.carryOver') }}
          </button>
          <button
            type="button"
            class="chip"
            :class="{ on: carryOver === false }"
            :aria-pressed="carryOver === false"
            @click="carryOver = false"
          >
            {{ $t('envelopes.spendSheet.recover') }}
          </button>
        </div>
      </template>

      <p v-else-if="isExact" class="diff exact">{{ $t('envelopes.spendSheet.exact') }}</p>

      <p class="next-note">
        {{ isRecurring ? $t('envelopes.spendSheet.willRenew') : $t('envelopes.spendSheet.willClose') }}
      </p>

      <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ $t('envelopes.spendSheet.submit') }}
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
.diff {
  font-size: 13px;
  border-radius: var(--r);
  padding: 12px 14px;
  margin-top: 14px;
  line-height: 1.5;
}
.diff.short {
  color: var(--red);
  background: var(--red-soft);
}
.diff.surplus {
  color: var(--green);
  background: var(--green-soft);
}
.diff.exact {
  color: var(--sub);
  background: var(--bg);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.chip {
  padding: 9px 14px;
  border-radius: 999px;
  background: var(--bg);
  color: var(--sub);
  font-size: 14px;
  font-weight: 500;
}
.chip.on {
  background: var(--purple);
  color: #fff;
}
.next-note {
  font-size: 12px;
  color: var(--hint);
  margin-top: 14px;
  line-height: 1.5;
}
.result {
  font-size: 14px;
  color: var(--ink);
  background: var(--purple-soft);
  border-radius: var(--r);
  padding: 14px 16px;
  margin-top: 14px;
  line-height: 1.6;
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
  background: var(--purple);
  color: #fff;
  margin-top: 16px;
}
.btn:disabled {
  opacity: 0.5;
}
</style>
