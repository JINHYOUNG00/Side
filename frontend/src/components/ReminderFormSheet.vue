<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import {
  createReminder,
  updateReminder,
  deleteReminder,
  type Reminder,
  type ReminderInput,
} from '@/api/reminders'

// NOTI-06 사용자 정의 리마인더 추가·수정 폼. reminder=null이면 추가, 있으면 수정.
// 메모(label)·주기(개월)·다음 알림일만 다룬다 — 비범위(가계부)를 침범하지 않는다(규칙 1).
const props = defineProps<{ open: boolean; reminder: Reminder | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

// 서버 검증과 동일한 상한(ReminderController LABEL_MAX·INTERVAL_MONTHS_MIN/MAX).
const LABEL_MAX = 100
const INTERVAL_MONTHS_MIN = 1
const INTERVAL_MONTHS_MAX = 120

const label = ref('')
const intervalMonths = ref(3)
const nextRemindDate = ref('')
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const confirmingDelete = ref(false)

const isEdit = computed(() => props.reminder !== null)

// 시트가 열릴 때마다 대상 리마인더 값으로 폼을 초기화(추가 모드는 기본값).
watch(
  () => [props.open, props.reminder] as const,
  ([open]) => {
    if (!open) return
    label.value = props.reminder?.label ?? ''
    intervalMonths.value = props.reminder?.intervalMonths ?? 3
    nextRemindDate.value = props.reminder?.nextRemindDate ?? ''
    errorCode.value = null
    confirmingDelete.value = false
  },
  { immediate: true },
)

// 클라 검증(서버 규칙 미러링). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const l = label.value.trim()
  if (l.length === 0 || l.length > LABEL_MAX) return 'VALIDATION_FAILED'
  const m = intervalMonths.value
  if (!Number.isInteger(m) || m < INTERVAL_MONTHS_MIN || m > INTERVAL_MONTHS_MAX) return 'VALIDATION_FAILED'
  if (!nextRemindDate.value) return 'VALIDATION_FAILED'
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
  const payload: ReminderInput = {
    label: label.value.trim(),
    intervalMonths: intervalMonths.value,
    nextRemindDate: nextRemindDate.value,
  }
  try {
    if (props.reminder) {
      await updateReminder(props.reminder.id, payload)
    } else {
      await createReminder(payload)
    }
    emit('saved')
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

async function remove() {
  if (!props.reminder || submitting.value) return
  submitting.value = true
  try {
    await deleteReminder(props.reminder.id)
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
    <h3 class="title">{{ isEdit ? $t('reminders.form.editTitle') : $t('reminders.form.addTitle') }}</h3>

    <label class="flabel" for="reminder-label">{{ $t('reminders.form.label') }}</label>
    <input
      id="reminder-label"
      v-model="label"
      class="input"
      type="text"
      :maxlength="LABEL_MAX"
      :placeholder="$t('reminders.form.labelPlaceholder')"
      autocomplete="off"
    />

    <label class="flabel" for="reminder-interval">{{ $t('reminders.form.interval') }}</label>
    <input
      id="reminder-interval"
      v-model.number="intervalMonths"
      class="input"
      type="number"
      :min="INTERVAL_MONTHS_MIN"
      :max="INTERVAL_MONTHS_MAX"
    />

    <label class="flabel" for="reminder-date">{{ $t('reminders.form.nextDate') }}</label>
    <input id="reminder-date" v-model="nextRemindDate" class="input" type="date" />
    <p class="hint">{{ $t('reminders.form.note') }}</p>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <button class="btn" type="button" :disabled="submitting" @click="submit">
      {{ $t('reminders.form.save') }}
    </button>

    <template v-if="isEdit">
      <button
        v-if="!confirmingDelete"
        class="btn ghost danger"
        type="button"
        :disabled="submitting"
        @click="confirmingDelete = true"
      >
        {{ $t('reminders.form.delete') }}
      </button>
      <div v-else class="confirm">
        <p class="confirm-q">{{ $t('reminders.form.deleteConfirm') }}</p>
        <div class="confirm-actions">
          <button class="btn ghost" type="button" :disabled="submitting" @click="confirmingDelete = false">
            {{ $t('reminders.form.cancel') }}
          </button>
          <button class="btn danger-solid" type="button" :disabled="submitting" @click="remove">
            {{ $t('reminders.form.delete') }}
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
