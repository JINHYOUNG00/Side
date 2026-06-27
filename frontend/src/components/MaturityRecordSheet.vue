<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import { ApiError } from '@/api/client'
import { recordMaturity, type ArchivedItem } from '@/api/budgetItems'

// 보관함 실수령액 기록(ITEM-08, SCR-08). 만기·중도해지로 보관된 항목에 실제 받은 금액을 기록한다.
// 이미 기록된 항목이면 값을 프리필해 정정 기록한다. 예상 만기금액(ITEM-05/06)이 있으면 함께 보여준다.
const props = defineProps<{ open: boolean; item: ArchivedItem | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const AMOUNT_MIN = 1
const AMOUNT_MAX = 1_000_000_000

const actual = ref('') // 천 단위 구분 표시 문자열
const errorCode = ref<string | null>(null)
const submitting = ref(false)

const expected = computed(() => props.item?.expectedMaturityAmount ?? null)

const actualDisplay = computed({
  get: () => actual.value,
  set: (raw: string) => {
    const digits = raw.replace(/[^0-9]/g, '')
    actual.value = digits ? Number(digits).toLocaleString('ko-KR') : ''
  },
})

const amount = computed(() => Number(actual.value.replace(/[^0-9]/g, '')))

// 시트가 열릴 때마다 기존 기록값을 프리필(정정)하거나 비운다(신규 기록).
watch(
  () => [props.open, props.item] as const,
  ([open, item]) => {
    if (!open) return
    errorCode.value = null
    const recorded = item?.maturityActualAmount ?? null
    actual.value = recorded != null ? recorded.toLocaleString('ko-KR') : ''
  },
  { immediate: true },
)

function validate(): string | null {
  if (!Number.isInteger(amount.value) || amount.value < AMOUNT_MIN || amount.value > AMOUNT_MAX) {
    return 'VALIDATION_FAILED'
  }
  return null
}

function close() {
  emit('close')
}

async function submit() {
  if (submitting.value || !props.item) return
  const invalid = validate()
  if (invalid) {
    errorCode.value = invalid
    return
  }
  errorCode.value = null
  submitting.value = true
  try {
    await recordMaturity(props.item.id, amount.value)
    emit('saved')
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <h3 class="title">{{ $t('archive.recordSheet.title', { name: item?.name }) }}</h3>

    <p v-if="expected !== null" class="sub">
      {{ $t('archive.recordSheet.expected') }}
      <MoneyText :amount="expected" :unit="$t('common.won')" />
    </p>

    <label class="flabel" for="maturity-actual">{{ $t('archive.recordSheet.amount') }}</label>
    <div class="amount-wrap">
      <input
        id="maturity-actual"
        v-model="actualDisplay"
        class="input"
        type="text"
        inputmode="numeric"
        :placeholder="$t('archive.recordSheet.amountPlaceholder')"
        autocomplete="off"
      />
      <span class="unit">{{ $t('common.won') }}</span>
    </div>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <button class="btn" type="button" :disabled="submitting" @click="submit">
      {{ $t('archive.recordSheet.save') }}
    </button>
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
  background: var(--green);
  color: #fff;
  margin-top: 16px;
}
.btn:disabled {
  opacity: 0.5;
}
</style>
