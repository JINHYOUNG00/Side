<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import { updateMe, PAYDAY_ADJUSTMENTS, type PaydayAdjustment, type Me, type MeUpdate } from '@/api/me'

// SET-01 월급·월급일 수정 시트. 온보딩(OnboardingView step1) 이후 실수령액·월급일·조정 규칙을
// 다시 바꾸는 동선 — 전체 허브(SCR-07)에서 진입한다. 폼 로직은 온보딩 step1과 동일하나,
// PATCH /me 전체 갱신 시 locale(SET-03)·투자 토글(SET-02)·생활비 통장은 현재값을 그대로 보존한다.
const props = defineProps<{ open: boolean; me: Me | null }>()
const emit = defineEmits<{ close: []; saved: [Me] }>()

// 서버 검증과 동일한 상한(UserController INCOME_MAX/PAYDAY_MIN/MAX, 구현규칙 5장).
const INCOME_MAX = 1_000_000_000
const PAYDAY_MIN = 1
const PAYDAY_MAX = 31
// 흔한 월급일 프리셋(말일은 31일 — 해당 월에 없으면 말일 간주, ERD payday 1~31).
const PRESET_DAYS = [10, 21, 25, 31] as const

const income = ref('') // 천 단위 구분 표시 문자열, 제출 시 정수로 파싱
const payday = ref<number | null>(null)
const customPayday = ref(false)
const adjustment = ref<PaydayAdjustment>('PREV_BUSINESS_DAY')

const submitting = ref(false)
const errorCode = ref<string | null>(null)

// 천 단위 구분 표시. 입력은 숫자만 남긴다(OnboardingView와 동일 패턴).
const incomeDisplay = computed({
  get: () => income.value,
  set: (raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '')
    income.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
  },
})

function parsedIncome(): number {
  return Number(income.value.replace(/[^0-9]/g, ''))
}

function selectPreset(day: number) {
  customPayday.value = false
  payday.value = day
}

// 시트가 열릴 때마다 현재 설정값으로 폼을 채운다(수정 전용 — me는 온보딩 완료 후라 항상 존재).
watch(
  () => [props.open, props.me] as const,
  ([open, me]) => {
    if (!open || me === null) return
    income.value = me.baseIncome > 0 ? me.baseIncome.toLocaleString('ko-KR') : ''
    payday.value = me.payday
    customPayday.value = !PRESET_DAYS.includes(me.payday as (typeof PRESET_DAYS)[number])
    adjustment.value = me.paydayAdjustment
    errorCode.value = null
  },
  { immediate: true },
)

// 클라 검증(서버 규칙 미러). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const amt = parsedIncome()
  if (!Number.isInteger(amt) || amt < 1 || amt > INCOME_MAX) return 'VALIDATION_FAILED'
  const d = payday.value
  if (d === null || !Number.isInteger(d) || d < PAYDAY_MIN || d > PAYDAY_MAX) return 'VALIDATION_FAILED'
  return null
}

function close() {
  emit('close')
}

async function submit() {
  const current = props.me
  if (submitting.value || current === null) return
  const invalid = validate()
  if (invalid) {
    errorCode.value = invalid
    return
  }
  errorCode.value = null
  submitting.value = true
  // 전체 갱신 PATCH — 이 시트가 다루지 않는 설정(locale·투자 토글·생활비 통장)은 현재값 그대로 되돌린다.
  const payload: MeUpdate = {
    baseIncome: parsedIncome(),
    payday: payday.value as number,
    paydayAdjustment: adjustment.value,
    livingAccountId: current.livingAccountId,
    locale: current.locale,
    includeInvestmentInSavingsRate: current.includeInvestmentInSavingsRate,
  }
  try {
    const updated = await updateMe(payload)
    emit('saved', updated)
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <h3 class="title">{{ $t('menu.profileTitle') }}</h3>

    <label class="flabel" for="profile-income">{{ $t('menu.profileIncome') }}</label>
    <div class="income-wrap">
      <input
        id="profile-income"
        v-model="incomeDisplay"
        class="input big"
        type="text"
        inputmode="numeric"
        :placeholder="$t('onboarding.step1.incomePlaceholder')"
        autocomplete="off"
      />
      <span class="unit">{{ $t('common.won') }}</span>
    </div>

    <span class="flabel">{{ $t('onboarding.step1.payday') }}</span>
    <div class="chips" role="radiogroup">
      <button
        v-for="day in PRESET_DAYS"
        :key="day"
        type="button"
        class="chip"
        :class="{ on: !customPayday && payday === day }"
        :aria-pressed="!customPayday && payday === day"
        @click="selectPreset(day)"
      >
        {{ day === 31 ? $t('onboarding.step1.lastDay') : $t('onboarding.step1.dayN', { day }) }}
      </button>
      <button
        type="button"
        class="chip"
        :class="{ on: customPayday }"
        :aria-pressed="customPayday"
        @click="customPayday = true"
      >
        {{ $t('onboarding.step1.custom') }}
      </button>
    </div>
    <input
      v-if="customPayday"
      id="profile-payday"
      v-model.number="payday"
      class="input"
      type="number"
      :min="PAYDAY_MIN"
      :max="PAYDAY_MAX"
      :placeholder="$t('onboarding.step1.paydayPlaceholder')"
    />

    <span class="flabel">{{ $t('onboarding.step1.adjustment') }}</span>
    <div class="chips" role="radiogroup">
      <button
        v-for="rule in PAYDAY_ADJUSTMENTS"
        :key="rule"
        type="button"
        class="chip"
        :class="{ on: adjustment === rule }"
        :aria-pressed="adjustment === rule"
        @click="adjustment = rule"
      >
        {{ $t(`onboarding.adjustment.${rule}`) }}
      </button>
    </div>

    <p class="hint">{{ $t('menu.profileHint') }}</p>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <button class="btn" type="button" :disabled="submitting || me === null" @click="submit">
      {{ $t('menu.profileSave') }}
    </button>
  </BottomSheet>
</template>

<style scoped>
.title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 4px;
}
.flabel {
  font-size: 13px;
  color: var(--sub);
  font-weight: 500;
  margin-top: 18px;
  display: block;
}
.income-wrap {
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
  margin-top: 8px;
}
.input::placeholder {
  color: #b0b8c1;
}
.input.big {
  font-size: 22px;
  font-weight: 700;
  padding: 16px 16px;
}
.income-wrap .unit {
  position: absolute;
  right: 16px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 16px;
  font-weight: 500;
  color: var(--sub);
}
.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}
.chip {
  padding: 8px 14px;
  border-radius: 999px;
  background: var(--bg);
  color: var(--sub);
  font-size: 14px;
  font-weight: 500;
}
.chip.on {
  background: var(--blue);
  color: #fff;
}
.hint {
  font-size: 12px;
  color: var(--hint);
  margin-top: 12px;
  line-height: 1.5;
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
