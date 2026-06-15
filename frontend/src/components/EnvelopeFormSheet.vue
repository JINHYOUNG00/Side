<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import {
  createEnvelope,
  updateEnvelope,
  deleteEnvelope,
  type Envelope,
  type EnvelopeInput,
} from '@/api/envelopes'
import type { Account } from '@/api/accounts'

// MOD-02 봉투 추가·수정 폼. envelope=null이면 추가, 있으면 수정(같은 필드 전체 교체) + soft delete.
// 입력: 이름·목표 금액·다음 지출일·반복 주기(개월/일회성)·적립 통장(ENV-01).
const props = defineProps<{ open: boolean; envelope: Envelope | null; accounts: Account[] }>()
const emit = defineEmits<{ close: []; saved: [] }>()

// 서버 검증과 동일한 상한(EnvelopeController·EnvelopeRequest, 구현규칙 5장).
const NAME_MAX = 50
const AMOUNT_MIN = 1
const AMOUNT_MAX = 1_000_000_000
const CYCLE_MIN = 1
const CYCLE_MAX = 1200

const name = ref('')
const target = ref('') // 천 단위 구분 표시 문자열, 제출 시 정수로 파싱
const nextDueDate = ref('')
const recurring = ref(true) // true=반복(cycleMonths), false=일회성(null)
const cycle = ref('') // 반복 주기 개월(숫자 문자열)
const accountId = ref<number | null>(null)
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const confirmingDelete = ref(false)

const isEdit = computed(() => props.envelope !== null)
const hasAccounts = computed(() => props.accounts.length > 0)

// 천 단위 구분 표시용. 입력은 숫자만 남긴다.
const targetDisplay = computed({
  get: () => target.value,
  set: (raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '')
    target.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
  },
})
const cycleDisplay = computed({
  get: () => cycle.value,
  set: (raw: string) => {
    cycle.value = raw.replace(/[^0-9]/g, '')
  },
})

// 시트가 열릴 때마다 폼을 초기화. 추가 모드는 빈 값 + 다음 지출일 오늘 기본·반복(12개월),
// 수정 모드는 기존 봉투 값으로 프리필(cycleMonths=null이면 일회성).
watch(
  () => [props.open, props.envelope] as const,
  ([open, env]) => {
    if (!open) return
    name.value = env ? env.name : ''
    target.value = env ? env.targetAmount.toLocaleString('ko-KR') : ''
    nextDueDate.value = env ? env.nextDueDate : today()
    recurring.value = env ? env.cycleMonths !== null : true
    cycle.value = env && env.cycleMonths !== null ? String(env.cycleMonths) : '12'
    accountId.value = env ? env.accountId : null
    errorCode.value = null
    confirmingDelete.value = false
  },
  { immediate: true },
)

// KST 기준 오늘(YYYY-MM-DD). 서버가 Clock으로 next_due ≥ 오늘을 검증하므로 클라도 KST로 맞춘다(규칙 3).
function today(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul' }).format(new Date())
}

function parsedTarget(): number {
  return Number(target.value.replace(/[^0-9]/g, ''))
}
function parsedCycle(): number {
  return Number(cycle.value)
}

// 클라 검증(서버 규칙 미러링). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const n = name.value.trim()
  if (n.length === 0 || n.length > NAME_MAX) return 'VALIDATION_FAILED'
  const amt = parsedTarget()
  if (!Number.isInteger(amt) || amt < AMOUNT_MIN || amt > AMOUNT_MAX) return 'VALIDATION_FAILED'
  if (accountId.value === null) return 'VALIDATION_FAILED'
  if (!nextDueDate.value || nextDueDate.value < today()) return 'VALIDATION_FAILED'
  if (recurring.value) {
    const m = parsedCycle()
    if (!Number.isInteger(m) || m < CYCLE_MIN || m > CYCLE_MAX) return 'VALIDATION_FAILED'
  }
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
  const payload: EnvelopeInput = {
    accountId: accountId.value as number,
    name: name.value.trim(),
    targetAmount: parsedTarget(),
    nextDueDate: nextDueDate.value,
    cycleMonths: recurring.value ? parsedCycle() : null,
    // memo는 v1 폼 입력란이 없다 — 전체 교체라 기존 값을 그대로 실어 보존(추가는 null).
    memo: props.envelope?.memo ?? null,
  }
  try {
    if (props.envelope) {
      await updateEnvelope(props.envelope.id, payload)
    } else {
      await createEnvelope(payload)
    }
    emit('saved')
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

async function remove() {
  if (!props.envelope || submitting.value) return
  submitting.value = true
  try {
    await deleteEnvelope(props.envelope.id)
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
    <h3 class="title">
      {{ isEdit ? $t('envelopes.form.editTitle') : $t('envelopes.form.addTitle') }}
    </h3>

    <label class="flabel" for="envelope-name">{{ $t('envelopes.form.name') }}</label>
    <input
      id="envelope-name"
      v-model="name"
      class="input"
      type="text"
      :maxlength="NAME_MAX"
      :placeholder="$t('envelopes.form.namePlaceholder')"
      autocomplete="off"
    />

    <label class="flabel" for="envelope-target">{{ $t('envelopes.form.target') }}</label>
    <div class="amount-wrap">
      <input
        id="envelope-target"
        v-model="targetDisplay"
        class="input"
        type="text"
        inputmode="numeric"
        :placeholder="$t('envelopes.form.amountPlaceholder')"
        autocomplete="off"
      />
      <span class="unit">{{ $t('common.won') }}</span>
    </div>

    <label class="flabel" for="envelope-due">{{ $t('envelopes.form.nextDue') }}</label>
    <input id="envelope-due" v-model="nextDueDate" class="input" type="date" :min="today()" />

    <span class="flabel">{{ $t('envelopes.form.repeat') }}</span>
    <div class="chips" role="radiogroup">
      <button
        type="button"
        class="chip"
        :class="{ on: !recurring }"
        :aria-pressed="!recurring"
        @click="recurring = false"
      >
        {{ $t('envelopes.form.oneTime') }}
      </button>
      <button
        type="button"
        class="chip"
        :class="{ on: recurring }"
        :aria-pressed="recurring"
        @click="recurring = true"
      >
        {{ $t('envelopes.form.recurring') }}
      </button>
    </div>

    <template v-if="recurring">
      <label class="flabel" for="envelope-cycle">{{ $t('envelopes.form.cycleMonths') }}</label>
      <div class="amount-wrap">
        <input
          id="envelope-cycle"
          v-model="cycleDisplay"
          class="input"
          type="text"
          inputmode="numeric"
          :placeholder="$t('envelopes.form.cycleMonthsPlaceholder')"
          autocomplete="off"
        />
        <span class="unit">{{ $t('envelopes.form.cycleMonthsUnit') }}</span>
      </div>
    </template>

    <label class="flabel" for="envelope-account">{{ $t('envelopes.form.account') }}</label>
    <select id="envelope-account" v-model="accountId" class="input" :disabled="!hasAccounts">
      <option :value="null" disabled>{{ $t('envelopes.form.accountPlaceholder') }}</option>
      <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
    </select>
    <p v-if="!hasAccounts" class="hint">{{ $t('envelopes.form.noAccounts') }}</p>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <button class="btn" type="button" :disabled="submitting || !hasAccounts" @click="submit">
      {{ $t('envelopes.form.save') }}
    </button>

    <!-- 수정 모드: soft delete 2단 확인(규칙 5) -->
    <template v-if="isEdit">
      <button
        v-if="!confirmingDelete"
        class="btn ghost danger"
        type="button"
        :disabled="submitting"
        @click="confirmingDelete = true"
      >
        {{ $t('envelopes.form.delete') }}
      </button>
      <div v-else class="confirm">
        <p class="confirm-q">{{ $t('envelopes.form.deleteConfirm') }}</p>
        <div class="confirm-actions">
          <button
            class="btn ghost"
            type="button"
            :disabled="submitting"
            @click="confirmingDelete = false"
          >
            {{ $t('envelopes.form.cancel') }}
          </button>
          <button class="btn danger-solid" type="button" :disabled="submitting" @click="remove">
            {{ $t('envelopes.form.delete') }}
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
/* 봉투 화면 강조색 = 보라(CLAUDE.md 색 규칙). */
.chip.on {
  background: var(--purple);
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
