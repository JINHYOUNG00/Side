<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import {
  createBudgetItem,
  updateBudgetItem,
  deleteBudgetItem,
  CATEGORIES,
  type BudgetItem,
  type BudgetItemInput,
  type BudgetItemUpdateInput,
  type Category,
} from '@/api/budgetItems'
import type { Account } from '@/api/accounts'

// MOD-01 항목 폼 v1. item=null이면 추가(공통 필드 입력→생성), 있으면 수정(같은 필드 편집→PATCH) + soft delete.
// 수정 기본은 다음 사이클부터 적용 — '이번 달 반영' 토글을 켜야 현재 사이클 미완료 라인 재계산(ITEM-07, 구현규칙 4장).
const props = defineProps<{ open: boolean; item: BudgetItem | null; accounts: Account[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()

// 서버 검증과 동일한 상한(BudgetItemController NAME_MAX/AMOUNT_MIN/MAX, 구현규칙 5장).
const NAME_MAX = 50
const AMOUNT_MIN = 1
const AMOUNT_MAX = 1_000_000_000

const category = ref<Category>('SAVING')
const name = ref('')
const amount = ref('') // 숫자 문자열(천 단위 구분 표시), 제출 시 정수로 파싱
const accountId = ref<number | null>(null)
const startDate = ref('')
const applyToCurrentCycle = ref(false)
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const confirmingDelete = ref(false)

const isManage = computed(() => props.item !== null)
const hasAccounts = computed(() => props.accounts.length > 0)

// 천 단위 구분 표시용. 입력은 숫자만 남긴다.
const amountDisplay = computed({
  get: () => amount.value,
  set: (raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '')
    amount.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
  },
})

// 시트가 열릴 때마다 폼을 초기화. 추가 모드는 빈 값 + 시작일 오늘 기본,
// 수정 모드는 기존 항목 값으로 프리필(endDate/memo는 v1 입력란이 없어 제출 시 원본을 그대로 보존).
watch(
  () => [props.open, props.item] as const,
  ([open, item]) => {
    if (!open) return
    category.value = item ? item.category : 'SAVING'
    name.value = item ? item.name : ''
    amount.value = item ? item.amount.toLocaleString('ko-KR') : ''
    accountId.value = item ? item.accountId : null
    startDate.value = item ? item.startDate : today()
    applyToCurrentCycle.value = false
    errorCode.value = null
    confirmingDelete.value = false
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

// 클라 검증(서버 규칙 미러링). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const n = name.value.trim()
  if (n.length === 0 || n.length > NAME_MAX) return 'VALIDATION_FAILED'
  const amt = parsedAmount()
  if (!Number.isInteger(amt) || amt < AMOUNT_MIN || amt > AMOUNT_MAX) return 'VALIDATION_FAILED'
  if (accountId.value === null) return 'VALIDATION_FAILED'
  if (!startDate.value) return 'VALIDATION_FAILED'
  return null
}

function close() {
  emit('close')
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
  try {
    if (props.item) {
      // 전체 교체 — v1 미입력 필드(endDate/memo)는 원본을 그대로 실어 보존한다.
      const update: BudgetItemUpdateInput = {
        ...payload,
        endDate: props.item.endDate,
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
