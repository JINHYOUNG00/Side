<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import {
  createBudgetItem,
  updateBudgetItem,
  deleteBudgetItem,
  previewMaturity,
  previewFx,
  CATEGORIES,
  TAX_TYPES,
  FX_FREQUENCIES,
  type BudgetItem,
  type BudgetItemInput,
  type BudgetItemUpdateInput,
  type Category,
  type TaxType,
  type MaturityPreview,
  type FxFrequency,
  type FxPreview,
} from '@/api/budgetItems'
import type { Account } from '@/api/accounts'

// MOD-01 항목 폼. item=null이면 추가, 있으면 수정(전체 교체 PATCH) + soft delete. 저축(SAVING) 선택 시
// 조건부 필드(만기일·이율·세금유형·예상 만기금액 미리보기 ITEM-05, 특수 상품 수동 입력 ITEM-06)가 펼쳐진다.
// 수정 기본은 다음 사이클부터 적용 — '이번 달 반영' 토글을 켜야 현재 사이클 미완료 라인 재계산(ITEM-07, 구현규칙 4장).
const props = defineProps<{ open: boolean; item: BudgetItem | null; accounts: Account[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()

// 서버 검증과 동일한 상한(BudgetItemController NAME_MAX/AMOUNT_MIN/MAX/RATE/MONTHS, 구현규칙 5장).
const NAME_MAX = 50
const AMOUNT_MIN = 1
const AMOUNT_MAX = 1_000_000_000
const RATE_MAX = 100
// 외화 도우미(ITEM-04) 입력 상한 — 서버 BudgetItemController FX_UNIT_MAX/FX_RATE_MAX와 동일.
const FX_UNIT_MAX = 1_000_000
const FX_RATE_MAX = 100_000

const category = ref<Category>('SAVING')
const name = ref('')
const amount = ref('') // 숫자 문자열(천 단위 구분 표시), 제출 시 정수로 파싱
const accountId = ref<number | null>(null)
const startDate = ref('')
const applyToCurrentCycle = ref(false)
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const confirmingDelete = ref(false)

// 저축 조건부 필드(ITEM-05/06). 만기일·이율·세금유형은 공식 계산용, 수동 입력 모드는 특수 상품용.
const endDate = ref('')
const interestRate = ref('') // % 문자열
const taxType = ref<TaxType>('NORMAL_15_4')
const manualMaturity = ref(false) // ITEM-06: 표준 공식이 아닌 상품 → 예상 만기금액 직접 입력
const expectedMaturity = ref('') // 수동 입력 금액 문자열(천 단위 표시)
const maturityPreview = ref<MaturityPreview | null>(null)

// 외화 적립 도우미(ITEM-04). 투자 항목에서 켜면 일/회 외화 금액·빈도·환율로 권장 월 이체액을 계산해
// 금액 입력에 채운다(저장은 원화 월액으로만 — 화면흐름도 "투자+매일 선택 시").
const fxHelper = ref(false)
const fxCurrency = ref('USD')
const fxUnitAmount = ref('') // 외화 일/회 금액(소수 가능)
const fxFrequency = ref<FxFrequency>('BUSINESS_DAYS')
const fxRate = ref('') // 기준 환율(외화 1단위당 원)
const fxPreview = ref<FxPreview | null>(null)

const isManage = computed(() => props.item !== null)
const hasAccounts = computed(() => props.accounts.length > 0)
const isSaving = computed(() => category.value === 'SAVING')
const isInvestment = computed(() => category.value === 'INVESTMENT')

// 천 단위 구분 표시용. 입력은 숫자만 남긴다.
const amountDisplay = computed({
  get: () => amount.value,
  set: (raw: string) => {
    amount.value = formatThousands(raw)
  },
})
const expectedMaturityDisplay = computed({
  get: () => expectedMaturity.value,
  set: (raw: string) => {
    expectedMaturity.value = formatThousands(raw)
  },
})

function formatThousands(raw: string): string {
  const digits = raw.replace(/[^0-9]/g, '')
  return digits ? Number(digits).toLocaleString('ko-KR') : ''
}

// 시트가 열릴 때마다 폼을 초기화. 추가 모드는 빈 값 + 시작일 오늘 기본, 수정 모드는 기존 항목 값으로 프리필.
// 예상 만기금액 수동값이 있던 항목은 수동 입력 모드로, 없으면 이율·세금유형으로 프리필한다(ITEM-05/06).
watch(
  () => [props.open, props.item] as const,
  ([open, item]) => {
    if (!open) return
    category.value = item ? item.category : 'SAVING'
    name.value = item ? item.name : ''
    amount.value = item ? item.amount.toLocaleString('ko-KR') : ''
    accountId.value = item ? item.accountId : null
    startDate.value = item ? item.startDate : today()
    endDate.value = item?.endDate ?? ''
    manualMaturity.value = item?.expectedMaturityAmount != null
    expectedMaturity.value = item?.expectedMaturityAmount != null
      ? item.expectedMaturityAmount.toLocaleString('ko-KR')
      : ''
    interestRate.value = item?.interestRate != null ? String(item.interestRate) : ''
    taxType.value = item?.taxType ?? 'NORMAL_15_4'
    applyToCurrentCycle.value = false
    errorCode.value = null
    confirmingDelete.value = false
    maturityPreview.value = null
    // 외화 도우미는 항상 꺼진 채로 시작 — FX 원본 입력은 저장하지 않으므로(원화 월액만 저장) 프리필 없음.
    fxHelper.value = false
    fxCurrency.value = 'USD'
    fxUnitAmount.value = ''
    fxFrequency.value = 'BUSINESS_DAYS'
    fxRate.value = ''
    fxPreview.value = null
  },
  { immediate: true },
)

// 오늘 날짜(YYYY-MM-DD). 시작일 기본값 — 사용자가 바꿀 수 있다.
function today(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function parsedAmount(): number {
  return Number(amount.value.replace(/[^0-9]/g, ''))
}

function parsedExpectedMaturity(): number {
  return Number(expectedMaturity.value.replace(/[^0-9]/g, ''))
}

function parsedRate(): number {
  return Number(interestRate.value)
}

// 시작일~만기일 납입 개월 수(end-inclusive) — 서버 ExpectedMaturity와 동일 규칙(만기일 다음 날까지의 완전 월 수).
function monthsBetweenInclusive(start: string, end: string): number {
  const s = new Date(start)
  const e = new Date(end)
  e.setDate(e.getDate() + 1)
  let m = (e.getFullYear() - s.getFullYear()) * 12 + (e.getMonth() - s.getMonth())
  if (e.getDate() < s.getDate()) m -= 1
  return m
}

// 이율·세금·만기일이 갖춰진 저축 항목(수동 모드 아님)일 때 서버로 예상 만기금액을 미리 계산해 표시(ITEM-05).
// 입력이 부족하거나 계산 불가(개월<1)면 배너를 숨긴다. 마지막 요청만 반영(경합 가드).
let previewSeq = 0
async function refreshPreview() {
  const seq = ++previewSeq
  const amt = parsedAmount()
  const rate = parsedRate()
  if (
    !isSaving.value ||
    manualMaturity.value ||
    !startDate.value ||
    !endDate.value ||
    !Number.isInteger(amt) ||
    amt < AMOUNT_MIN ||
    amt > AMOUNT_MAX ||
    !interestRate.value ||
    Number.isNaN(rate) ||
    rate < 0 ||
    rate > RATE_MAX
  ) {
    maturityPreview.value = null
    return
  }
  const months = monthsBetweenInclusive(startDate.value, endDate.value)
  if (months < 1) {
    maturityPreview.value = null
    return
  }
  try {
    const result = await previewMaturity({ monthlyAmount: amt, months, interestRate: rate, taxType: taxType.value })
    if (seq === previewSeq) maturityPreview.value = result
  } catch {
    if (seq === previewSeq) maturityPreview.value = null
  }
}

watch(
  () => [
    isSaving.value,
    manualMaturity.value,
    amount.value,
    startDate.value,
    endDate.value,
    interestRate.value,
    taxType.value,
  ],
  () => {
    void refreshPreview()
  },
)

function parsedFxUnitAmount(): number {
  return Number(fxUnitAmount.value)
}

function parsedFxRate(): number {
  return Number(fxRate.value)
}

// 투자 항목에서 외화 도우미가 켜지고 일/회 금액·환율이 갖춰지면 서버로 권장 월 이체액을 미리 계산한다(ITEM-04).
// 입력이 부족하거나 범위를 벗어나면 배너를 숨긴다. 마지막 요청만 반영(경합 가드 — 만기 미리보기와 동일).
let fxSeq = 0
async function refreshFxPreview() {
  const seq = ++fxSeq
  const unit = parsedFxUnitAmount()
  const rate = parsedFxRate()
  if (
    !isInvestment.value ||
    !fxHelper.value ||
    !fxUnitAmount.value ||
    Number.isNaN(unit) ||
    unit <= 0 ||
    unit > FX_UNIT_MAX ||
    !fxRate.value ||
    Number.isNaN(rate) ||
    rate <= 0 ||
    rate > FX_RATE_MAX
  ) {
    fxPreview.value = null
    return
  }
  try {
    const result = await previewFx({
      currency: fxCurrency.value.trim() || 'USD',
      unitAmount: unit,
      frequency: fxFrequency.value,
      fxRate: rate,
    })
    if (seq === fxSeq) fxPreview.value = result
  } catch {
    if (seq === fxSeq) fxPreview.value = null
  }
}

watch(
  () => [isInvestment.value, fxHelper.value, fxUnitAmount.value, fxFrequency.value, fxRate.value, fxCurrency.value],
  () => {
    void refreshFxPreview()
  },
)

// 권장 월 이체액을 금액 입력에 채운다(저장은 원화 월액 — 요구사항정의서 ITEM-04). 사용자가 명시적으로 적용한다.
function applyFxRecommendation() {
  if (!fxPreview.value) return
  amount.value = fxPreview.value.recommendedMonthlyKrw.toLocaleString('ko-KR')
}

// 클라 검증(서버 규칙 미러링). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const n = name.value.trim()
  if (n.length === 0 || n.length > NAME_MAX) return 'VALIDATION_FAILED'
  const amt = parsedAmount()
  if (!Number.isInteger(amt) || amt < AMOUNT_MIN || amt > AMOUNT_MAX) return 'VALIDATION_FAILED'
  if (accountId.value === null) return 'VALIDATION_FAILED'
  if (!startDate.value) return 'VALIDATION_FAILED'
  if (isSaving.value) {
    if (endDate.value && endDate.value <= startDate.value) return 'VALIDATION_FAILED'
    if (manualMaturity.value) {
      const exp = parsedExpectedMaturity()
      if (!Number.isInteger(exp) || exp < AMOUNT_MIN || exp > AMOUNT_MAX) return 'VALIDATION_FAILED'
    } else if (interestRate.value) {
      const rate = parsedRate()
      if (Number.isNaN(rate) || rate < 0 || rate > RATE_MAX) return 'VALIDATION_FAILED'
    }
  }
  return null
}

function close() {
  emit('close')
}

// 저축 조건부 필드를 페이로드에 싣는다(ITEM-05/06). 수동 모드면 예상 만기금액(공식 대신), 아니면 이율·세금유형.
// 비저축 항목이면 셋 다 보내지 않아(전체 교체에서 누락=서버 null) 깨끗이 비워진다.
function applySavingFields(payload: BudgetItemInput) {
  if (!isSaving.value) return
  payload.endDate = endDate.value || null
  if (manualMaturity.value) {
    payload.expectedMaturityAmount = parsedExpectedMaturity()
  } else if (interestRate.value) {
    payload.interestRate = parsedRate()
    payload.taxType = taxType.value
  }
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
  const payload: BudgetItemInput = {
    category: category.value,
    name: name.value.trim(),
    amount: parsedAmount(),
    accountId: accountId.value as number,
    startDate: startDate.value,
  }
  applySavingFields(payload)
  try {
    if (props.item) {
      // 전체 교체 — 저축 외 미입력 필드(endDate/memo)는 원본을 그대로 실어 보존한다.
      const update: BudgetItemUpdateInput = {
        ...payload,
        endDate: isSaving.value ? endDate.value || null : props.item.endDate,
        memo: props.item.memo,
      }
      await updateBudgetItem(props.item.id, update, applyToCurrentCycle.value)
    } else {
      await createBudgetItem(payload)
    }
    emit('saved')
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

async function remove() {
  if (!props.item || submitting.value) return
  submitting.value = true
  try {
    await deleteBudgetItem(props.item.id)
    emit('saved')
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
    confirmingDelete.value = false
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <!-- 추가/수정 공통 폼. 수정 모드는 기존 값 프리필 + 적용 시점 토글 + soft delete가 더해진다. -->
    <h3 class="title">{{ isManage ? $t('items.form.editTitle') : $t('items.form.addTitle') }}</h3>

    <span class="flabel">{{ $t('items.form.category') }}</span>
    <div class="chips" role="radiogroup">
      <button
        v-for="c in CATEGORIES"
        :key="c"
        type="button"
        class="chip"
        :class="{ on: category === c }"
        :aria-pressed="category === c"
        @click="category = c"
      >
        {{ $t(`items.category.${c}`) }}
      </button>
    </div>

    <label class="flabel" for="item-name">{{ $t('items.form.name') }}</label>
    <input
      id="item-name"
      v-model="name"
      class="input"
      type="text"
      :maxlength="NAME_MAX"
      :placeholder="$t('items.form.namePlaceholder')"
      autocomplete="off"
    />

    <label class="flabel" for="item-amount">{{ $t('items.form.amount') }}</label>
    <div class="amount-wrap">
      <input
        id="item-amount"
        v-model="amountDisplay"
        class="input"
        type="text"
        inputmode="numeric"
        :placeholder="$t('items.form.amountPlaceholder')"
        autocomplete="off"
      />
      <span class="unit">{{ $t('common.won') }}</span>
    </div>

    <label class="flabel" for="item-account">{{ $t('items.form.account') }}</label>
    <select id="item-account" v-model="accountId" class="input" :disabled="!hasAccounts">
      <option :value="null" disabled>{{ $t('items.form.accountPlaceholder') }}</option>
      <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
    </select>
    <p v-if="!hasAccounts" class="hint">{{ $t('items.form.noAccounts') }}</p>

    <label class="flabel" for="item-start">{{ $t('items.form.startDate') }}</label>
    <input id="item-start" v-model="startDate" class="input" type="date" />

    <!-- 저축 조건부 필드(ITEM-05/06) — 저축 선택 시에만 펼쳐진다. -->
    <template v-if="isSaving">
      <label class="flabel" for="item-end">{{ $t('items.form.maturityDate') }}</label>
      <input id="item-end" v-model="endDate" class="input" type="date" />

      <!-- 특수 상품 수동 입력 토글(ITEM-06): 표준 공식이 아닌 상품은 예상 만기금액을 직접 입력 -->
      <label class="toggle" for="item-manual">
        <span class="toggle-text">
          <span class="toggle-title">{{ $t('items.form.manualMaturity') }}</span>
          <span class="toggle-hint">{{ $t('items.form.manualMaturityHint') }}</span>
        </span>
        <input
          id="item-manual"
          v-model="manualMaturity"
          class="switch"
          type="checkbox"
          role="switch"
        />
      </label>

      <template v-if="manualMaturity">
        <label class="flabel" for="item-expected">{{ $t('items.form.expectedMaturity') }}</label>
        <div class="amount-wrap">
          <input
            id="item-expected"
            v-model="expectedMaturityDisplay"
            class="input"
            type="text"
            inputmode="numeric"
            :placeholder="$t('items.form.expectedMaturityPlaceholder')"
            autocomplete="off"
          />
          <span class="unit">{{ $t('common.won') }}</span>
        </div>
      </template>
      <template v-else>
        <label class="flabel" for="item-rate">{{ $t('items.form.interestRate') }}</label>
        <input
          id="item-rate"
          v-model="interestRate"
          class="input"
          type="text"
          inputmode="decimal"
          :placeholder="$t('items.form.interestRatePlaceholder')"
          autocomplete="off"
        />

        <span class="flabel">{{ $t('items.form.taxType') }}</span>
        <div class="chips" role="radiogroup">
          <button
            v-for="t in TAX_TYPES"
            :key="t"
            type="button"
            class="chip"
            :class="{ on: taxType === t }"
            :aria-pressed="taxType === t"
            @click="taxType = t"
          >
            {{ $t(`items.taxType.${t}`) }}
          </button>
        </div>

        <!-- 예상 만기금액 미리보기(ITEM-05). 초록=완료/수령. "세후 · 예상치" 고지 동반(NFR-07). -->
        <p v-if="maturityPreview" class="preview" data-testid="maturity-preview">
          <span class="preview-label">{{ $t('items.form.maturityPreview') }}</span>
          <b class="preview-amount">{{ maturityPreview.total.toLocaleString('ko-KR') }}{{ $t('common.won') }}</b>
          <small class="preview-note">{{ $t('items.form.maturityEstimateNote') }}</small>
        </p>
      </template>
    </template>

    <!-- 외화 적립 도우미(ITEM-04) — 투자 선택 시. 켜면 일/회 외화 금액·빈도·환율로 권장 월 이체액을 계산해 금액에 채운다. -->
    <template v-if="isInvestment">
      <label class="toggle" for="item-fx">
        <span class="toggle-text">
          <span class="toggle-title">{{ $t('items.form.fxHelper') }}</span>
          <span class="toggle-hint">{{ $t('items.form.fxHelperHint') }}</span>
        </span>
        <input id="item-fx" v-model="fxHelper" class="switch" type="checkbox" role="switch" />
      </label>

      <template v-if="fxHelper">
        <label class="flabel" for="item-fx-currency">{{ $t('items.form.fxCurrency') }}</label>
        <input
          id="item-fx-currency"
          v-model="fxCurrency"
          class="input"
          type="text"
          maxlength="10"
          :placeholder="$t('items.form.fxCurrencyPlaceholder')"
          autocomplete="off"
        />

        <label class="flabel" for="item-fx-unit">{{ $t('items.form.fxUnitAmount') }}</label>
        <input
          id="item-fx-unit"
          v-model="fxUnitAmount"
          class="input"
          type="text"
          inputmode="decimal"
          :placeholder="$t('items.form.fxUnitAmountPlaceholder')"
          autocomplete="off"
        />

        <span class="flabel">{{ $t('items.form.fxFrequency') }}</span>
        <div class="chips" role="radiogroup">
          <button
            v-for="f in FX_FREQUENCIES"
            :key="f"
            type="button"
            class="chip"
            :class="{ on: fxFrequency === f }"
            :aria-pressed="fxFrequency === f"
            @click="fxFrequency = f"
          >
            {{ $t(`items.form.fxFreq.${f}`) }}
          </button>
        </div>

        <label class="flabel" for="item-fx-rate">{{ $t('items.form.fxRate') }}</label>
        <input
          id="item-fx-rate"
          v-model="fxRate"
          class="input"
          type="text"
          inputmode="decimal"
          :placeholder="$t('items.form.fxRatePlaceholder')"
          autocomplete="off"
        />

        <!-- 권장 월 이체액 배너 — 파랑(배분/주요 동작). 버퍼 포함 고지 + "이 금액으로 채우기". -->
        <div v-if="fxPreview" class="fx-preview" data-testid="fx-preview">
          <div class="fx-preview-head">
            <span class="preview-label">{{ $t('items.form.fxRecommended') }}</span>
            <b class="preview-amount"
              >{{ fxPreview.recommendedMonthlyKrw.toLocaleString('ko-KR') }}{{ $t('common.won') }}</b
            >
          </div>
          <small class="preview-note">
            {{ $t('items.form.fxBufferNote', { percent: Math.round(fxPreview.bufferRate * 100) }) }}
          </small>
          <button type="button" class="fx-apply" @click="applyFxRecommendation">
            {{ $t('items.form.fxApply') }}
          </button>
        </div>
      </template>
    </template>

    <!-- 수정 모드: 적용 시점 토글. 기본 꺼짐=다음 사이클부터, 켜면 이번 달 미완료 라인 재계산(ITEM-07) -->
    <label v-if="isManage" class="toggle" for="item-apply">
      <span class="toggle-text">
        <span class="toggle-title">{{ $t('items.form.applyCurrentCycle') }}</span>
        <span class="toggle-hint">{{ $t('items.form.applyCurrentCycleHint') }}</span>
      </span>
      <input
        id="item-apply"
        v-model="applyToCurrentCycle"
        class="switch"
        type="checkbox"
        role="switch"
      />
    </label>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <button class="btn" type="button" :disabled="submitting || !hasAccounts" @click="submit">
      {{ $t('items.form.save') }}
    </button>

    <!-- 수정 모드: soft delete 2단 확인(규칙 5, ITEM-09) -->
    <template v-if="isManage">
      <button
        v-if="!confirmingDelete"
        class="btn ghost danger"
        type="button"
        :disabled="submitting"
        @click="confirmingDelete = true"
      >
        {{ $t('items.form.delete') }}
      </button>
      <div v-else class="confirm">
        <p class="confirm-q">{{ $t('items.form.deleteConfirm') }}</p>
        <div class="confirm-actions">
          <button class="btn ghost" type="button" :disabled="submitting" @click="confirmingDelete = false">
            {{ $t('items.form.cancel') }}
          </button>
          <button class="btn danger-solid" type="button" :disabled="submitting" @click="remove">
            {{ $t('items.form.delete') }}
          </button>
        </div>
      </div>
    </template>
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
.hint {
  font-size: 12px;
  color: var(--hint);
  margin-top: 8px;
  line-height: 1.5;
}
/* 예상 만기금액 미리보기 배너 — 초록(완료/수령 토큰). 화면설계.html banner.green 대응. */
.preview {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--r);
  background: var(--green-soft);
  color: var(--green);
  font-size: 13px;
  line-height: 1.5;
}
.preview-amount {
  font-size: 16px;
  font-weight: 700;
}
.preview-note {
  color: var(--green);
  opacity: 0.8;
  font-size: 12px;
}
/* 외화 권장 월 이체액 배너 — 파랑(배분/주요 동작 토큰). 버퍼 고지 + 적용 버튼. */
.fx-preview {
  margin-top: 14px;
  padding: 12px 14px;
  border-radius: var(--r);
  background: var(--blue-soft);
  color: var(--blue-deep);
}
.fx-preview-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
}
.fx-preview .preview-amount {
  font-size: 16px;
  font-weight: 700;
}
.fx-preview .preview-note {
  display: block;
  margin-top: 4px;
  color: var(--blue-deep);
  opacity: 0.8;
  font-size: 12px;
}
.fx-apply {
  margin-top: 10px;
  width: 100%;
  padding: 10px;
  border-radius: 10px;
  background: var(--blue);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}
.toggle {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 18px;
  cursor: pointer;
}
.toggle-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.toggle-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
}
.toggle-hint {
  font-size: 12px;
  color: var(--hint);
  line-height: 1.5;
}
/* 토스류 토글 스위치 — 체크박스를 직접 스타일링(UI 라이브러리 금지). 켜짐=파랑(주요 동작 색). */
.switch {
  appearance: none;
  flex: none;
  width: 44px;
  height: 26px;
  border-radius: 999px;
  background: var(--line-2);
  position: relative;
  cursor: pointer;
  transition: background 0.15s ease;
}
.switch::after {
  content: '';
  position: absolute;
  top: 3px;
  left: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.15s ease;
}
.switch:checked {
  background: var(--blue);
}
.switch:checked::after {
  transform: translateX(18px);
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
.btn.ghost {
  background: var(--bg);
  color: var(--ink);
}
.btn.danger {
  color: var(--red);
  margin-top: 10px;
}
.btn.danger-solid {
  background: var(--red);
  color: #fff;
}
.confirm {
  margin-top: 10px;
}
.confirm-q {
  font-size: 13px;
  color: var(--sub);
  text-align: center;
  margin-bottom: 8px;
}
.confirm-actions {
  display: flex;
  gap: 8px;
}
.confirm-actions .btn {
  margin-top: 0;
}
</style>
