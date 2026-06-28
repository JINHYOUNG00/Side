<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError } from '@/api/client'
import { getMe, updateMe, PAYDAY_ADJUSTMENTS, type PaydayAdjustment, type MeUpdate } from '@/api/me'

// SCR-02 온보딩 스텝 1 — 실수령액·월급일·지급일 조정 규칙(SET-01).
// 스텝 2(첫 항목·통장)는 MOD-01/03, 스텝 3(폭포 미리보기)은 FLOW-02 의존이라 현재는 step 1만.
// 짝 백엔드: GET /me 로 현재값을 읽어 채우고, PATCH /me 로 전체 설정을 갱신한다.
const router = useRouter()

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
// step 1에서 다루지 않는 생활비 통장 지정은 GET 값을 그대로 되돌려 보존(전체 갱신 PATCH).
const livingAccountId = ref<number | null>(null)

const loading = ref(true)
const submitting = ref(false)
const errorCode = ref<string | null>(null)

// 천 단위 구분 표시. 입력은 숫자만 남긴다(ItemFormSheet와 동일 패턴).
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

function enableCustom() {
  customPayday.value = true
}

// 현재 설정을 읽어 폼을 채운다. 신규 사용자는 baseIncome=0 플레이스홀더(payday 1·NONE도 임의값)라
// 아직 등록 전으로 보고 빈 폼으로 둔다 — 월급일·조정 규칙을 직접 고르게 한다. 재진입(등록 후)만 prefill.
onMounted(async () => {
  try {
    const me = await getMe()
    livingAccountId.value = me.livingAccountId
    if (me.baseIncome > 0) {
      income.value = me.baseIncome.toLocaleString('ko-KR')
      payday.value = me.payday
      customPayday.value = !PRESET_DAYS.includes(me.payday as (typeof PRESET_DAYS)[number])
      adjustment.value = me.paydayAdjustment
    }
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
})

// 클라 검증(서버 규칙 미러). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const amt = parsedIncome()
  if (!Number.isInteger(amt) || amt < 1 || amt > INCOME_MAX) return 'VALIDATION_FAILED'
  const d = payday.value
  if (d === null || !Number.isInteger(d) || d < PAYDAY_MIN || d > PAYDAY_MAX) return 'VALIDATION_FAILED'
  return null
}

async function submit() {
  if (submitting.value) return
  const invalid = validate()
  if (invalid) {
    errorCode.value = invalid
    return
  }
  errorCode.value = null
  submitting.value = true
  const payload: MeUpdate = {
    baseIncome: parsedIncome(),
    payday: payday.value as number,
    paydayAdjustment: adjustment.value,
    livingAccountId: livingAccountId.value,
  }
  try {
    await updateMe(payload)
    // step 1 완료 → 홈으로(step 2 항목/통장·step 3 폭포 미리보기는 후속 Phase 도입 시 연결).
    await router.replace({ name: 'home' })
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <section class="onboarding">
    <p v-if="loading" class="state">{{ $t('onboarding.loading') }}</p>

    <template v-else>
      <div class="dots" aria-hidden="true"><i class="on"></i><i></i><i></i></div>
      <h1 class="title">{{ $t('onboarding.step1.title') }}</h1>

      <div class="income-wrap">
        <input
          id="onboarding-income"
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
          @click="enableCustom"
        >
          {{ $t('onboarding.step1.custom') }}
        </button>
      </div>
      <input
        v-if="customPayday"
        id="onboarding-payday"
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

      <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

      <button class="btn" type="button" :disabled="submitting" @click="submit">
        {{ $t('onboarding.step1.next') }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.onboarding {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding-bottom: 24px;
}
/* 데스크톱(웹 대응) — 입력이 풀폭으로 늘어지지 않게 좁은 중앙 컬럼. 높이 채움(flex:1) 대신
   자연 높이로 두고 버튼은 폼 바로 뒤에(모바일은 화면 하단 고정 유지). */
@media (min-width: 900px) {
  .onboarding {
    flex: none;
    max-width: 440px;
    width: 100%;
    margin: 40px auto 0;
  }
  .btn {
    margin-top: 32px;
  }
}
.state {
  font-size: 14px;
  color: var(--hint);
  text-align: center;
  padding: 32px 0;
}
.dots {
  display: flex;
  gap: 6px;
  margin: 4px 0 24px;
}
.dots i {
  width: 18px;
  height: 4px;
  border-radius: 2px;
  background: var(--line-2);
}
.dots i.on {
  background: var(--blue);
}
.title {
  font-size: 21px;
  font-weight: 700;
  line-height: 1.4;
  letter-spacing: -0.4px;
  white-space: pre-line;
}
.income-wrap {
  position: relative;
  margin-top: 24px;
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
  font-size: 24px;
  font-weight: 700;
  padding: 18px 16px;
  margin-top: 0;
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
.flabel {
  font-size: 13px;
  color: var(--sub);
  font-weight: 500;
  margin-top: 22px;
  display: block;
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
  margin-top: auto;
}
.btn:disabled {
  opacity: 0.5;
}
</style>
