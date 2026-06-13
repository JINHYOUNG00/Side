<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import { createAccount, updateAccount, deleteAccount, type Account, type AccountInput } from '@/api/accounts'

// MOD-03 통장 추가·수정 폼. account=null이면 추가, 있으면 수정.
// 계좌번호 등 금융 식별 정보 입력란은 두지 않는다(규칙 6) — 별칭·용도·은행 앱 링크만.
const props = defineProps<{ open: boolean; account: Account | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

// 서버 검증과 동일한 상한(AccountController NAME_MAX/PURPOSE_MAX/DEEP_LINK_MAX, 구현규칙 6장).
const NAME_MAX = 50
const PURPOSE_MAX = 100
const DEEP_LINK_MAX = 500

const name = ref('')
const purpose = ref('')
const bankDeepLink = ref('')
const errorCode = ref<string | null>(null)
const submitting = ref(false)
const confirmingDelete = ref(false)

const isEdit = computed(() => props.account !== null)

// 시트가 열릴 때마다 대상 통장 값으로 폼을 초기화(추가 모드는 빈 값).
watch(
  () => [props.open, props.account] as const,
  ([open]) => {
    if (!open) return
    name.value = props.account?.name ?? ''
    purpose.value = props.account?.purpose ?? ''
    bankDeepLink.value = props.account?.bankDeepLink ?? ''
    errorCode.value = null
    confirmingDelete.value = false
  },
  { immediate: true },
)

// 클라 검증(서버 규칙 미러링). 통과하면 null, 아니면 표시할 에러 코드.
function validate(): string | null {
  const n = name.value.trim()
  if (n.length === 0 || n.length > NAME_MAX) return 'VALIDATION_FAILED'
  if (purpose.value.length > PURPOSE_MAX) return 'VALIDATION_FAILED'
  if (bankDeepLink.value.length > DEEP_LINK_MAX) return 'VALIDATION_FAILED'
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
  const payload: AccountInput = {
    name: name.value.trim(),
    purpose: purpose.value.trim() || null,
    bankDeepLink: bankDeepLink.value.trim() || null,
  }
  try {
    if (props.account) {
      await updateAccount(props.account.id, payload)
    } else {
      await createAccount(payload)
    }
    emit('saved')
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
}

async function remove() {
  if (!props.account || submitting.value) return
  submitting.value = true
  try {
    await deleteAccount(props.account.id)
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
    <h3 class="title">{{ isEdit ? $t('accounts.form.editTitle') : $t('accounts.form.addTitle') }}</h3>

    <label class="flabel" for="account-name">{{ $t('accounts.form.name') }}</label>
    <input
      id="account-name"
      v-model="name"
      class="input"
      type="text"
      :maxlength="NAME_MAX"
      :placeholder="$t('accounts.form.namePlaceholder')"
      autocomplete="off"
    />

    <label class="flabel" for="account-purpose">{{ $t('accounts.form.purpose') }}</label>
    <input
      id="account-purpose"
      v-model="purpose"
      class="input"
      type="text"
      :maxlength="PURPOSE_MAX"
      :placeholder="$t('accounts.form.purposePlaceholder')"
      autocomplete="off"
    />

    <label class="flabel" for="account-link">{{ $t('accounts.form.deepLink') }}</label>
    <input
      id="account-link"
      v-model="bankDeepLink"
      class="input"
      type="text"
      :maxlength="DEEP_LINK_MAX"
      :placeholder="$t('accounts.form.deepLinkPlaceholder')"
      autocomplete="off"
    />
    <p class="hint">{{ $t('accounts.form.privacyNote') }}</p>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <button class="btn" type="button" :disabled="submitting" @click="submit">
      {{ $t('accounts.form.save') }}
    </button>

    <template v-if="isEdit">
      <button
        v-if="!confirmingDelete"
        class="btn ghost danger"
        type="button"
        :disabled="submitting"
        @click="confirmingDelete = true"
      >
        {{ $t('accounts.form.delete') }}
      </button>
      <div v-else class="confirm">
        <p class="confirm-q">{{ $t('accounts.form.deleteConfirm') }}</p>
        <div class="confirm-actions">
          <button class="btn ghost" type="button" :disabled="submitting" @click="confirmingDelete = false">
            {{ $t('accounts.form.cancel') }}
          </button>
          <button class="btn danger-solid" type="button" :disabled="submitting" @click="remove">
            {{ $t('accounts.form.delete') }}
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
