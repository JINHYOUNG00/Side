<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import { createBudgetItem, CATEGORIES, type BudgetItemInput, type Category } from '@/api/budgetItems'
import { parseImportTable, type ParsedRow } from '@/lib/notionImport'
import type { Account } from '@/api/accounts'

// MOD-07 노션 임포트(DATA-01). 노션·엑셀 표 텍스트를 붙여넣으면 항목 후보를 파싱해 보여주고,
// 사용자가 분류·대상 통장·포함 여부를 확정하면 기존 생성 API(ITEM-01)로 일괄 등록한다.
// "완전 자동이 아닌 후보 제시 + 수동 확정" — 파싱은 이름·금액만, 분류는 표 단위 일괄 선택(기본 고정지출).
const props = defineProps<{ open: boolean; accounts: Account[] }>()
const emit = defineEmits<{ close: []; imported: [count: number] }>()

const raw = ref('')
const candidates = ref<ParsedRow[]>([])
const included = ref<boolean[]>([])
const category = ref<Category>('FIXED') // 붙여넣는 표는 보통 한 분류(고정지출 등) — 일괄 적용
const accountId = ref<number | null>(null)
const startDate = ref('')
const submitting = ref(false)
const errorCode = ref<string | null>(null)

const hasAccounts = computed(() => props.accounts.length > 0)
const includedCount = computed(() => included.value.filter(Boolean).length)
const hasText = computed(() => raw.value.trim().length > 0)
const canRegister = computed(
  () =>
    hasAccounts.value &&
    includedCount.value > 0 &&
    accountId.value !== null &&
    startDate.value !== '' &&
    !submitting.value,
)

// 시트가 열릴 때마다 초기화. raw를 비우면 raw 워처가 후보를 다시 비운다.
watch(
  () => props.open,
  (open) => {
    if (!open) return
    raw.value = ''
    category.value = 'FIXED'
    accountId.value = null
    startDate.value = today()
    errorCode.value = null
  },
  { immediate: true },
)

// 텍스트가 바뀌면 후보를 다시 파싱한다(전부 포함=true로 초기화). 직전 등록 에러는 지운다.
watch(raw, () => {
  candidates.value = parseImportTable(raw.value)
  included.value = candidates.value.map(() => true)
  errorCode.value = null
})

// 오늘 날짜(YYYY-MM-DD, ItemFormSheet와 동일) — 등록 항목의 시작일 기본값(사용자가 바꿀 수 있다).
function today(): string {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

function toggle(i: number) {
  included.value[i] = !included.value[i]
}

function close() {
  emit('close')
}

// 포함 후보를 순서대로 생성한다. 성공한 후보는 목록에서 제거해 재시도 시 중복 등록을 막는다.
// 도중 실패(예: 100개 상한 409)하면 거기서 멈추고 에러를 표시하되, 이미 등록된 건은 빠진 채 시트가 열려 있다.
async function register() {
  if (submitting.value) return
  if (!hasAccounts.value || accountId.value === null || startDate.value === '') {
    errorCode.value = 'VALIDATION_FAILED'
    return
  }
  const targets = candidates.value
    .map((c, i) => ({ c, i }))
    .filter(({ i }) => included.value[i])
  if (targets.length === 0) {
    errorCode.value = 'VALIDATION_FAILED'
    return
  }
  errorCode.value = null
  submitting.value = true
  const done = new Set<number>()
  try {
    for (const { c, i } of targets) {
      const payload: BudgetItemInput = {
        category: category.value,
        name: c.name,
        amount: c.amount,
        accountId: accountId.value,
        startDate: startDate.value,
      }
      await createBudgetItem(payload)
      done.add(i)
    }
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    submitting.value = false
  }
  if (done.size > 0) {
    candidates.value = candidates.value.filter((_, i) => !done.has(i))
    included.value = included.value.filter((_, i) => !done.has(i))
  }
  if (!errorCode.value && done.size > 0) emit('imported', done.size)
}
</script>

<template>
  <BottomSheet :open="open" @close="close">
    <h3 class="title">{{ $t('import.title') }}</h3>
    <p class="sub">{{ $t('import.subtitle') }}</p>

    <textarea
      id="import-text"
      v-model="raw"
      class="input textarea"
      :placeholder="$t('import.placeholder')"
      rows="4"
    />

    <!-- 텍스트가 있는데 인식된 후보가 없으면 안내, 있으면 인식 건수 배너 + 후보 리스트 -->
    <p v-if="hasText && candidates.length === 0" class="hint">{{ $t('import.none') }}</p>

    <template v-if="candidates.length > 0">
      <div class="banner">{{ $t('import.recognized', { n: candidates.length }) }}</div>

      <ul class="cands">
        <li v-for="(c, i) in candidates" :key="`${c.name}-${i}`" class="cand">
          <label class="cand-label">
            <input
              class="check"
              type="checkbox"
              :checked="included[i]"
              @change="toggle(i)"
            />
            <span class="cand-name">{{ c.name }}</span>
          </label>
          <span class="cand-amount">{{ c.amount.toLocaleString('ko-KR') }} {{ $t('common.won') }}</span>
        </li>
      </ul>

      <span class="flabel">{{ $t('import.category') }}</span>
      <div class="chips" role="radiogroup">
        <button
          v-for="cat in CATEGORIES"
          :key="cat"
          type="button"
          class="chip"
          :class="{ on: category === cat }"
          :aria-pressed="category === cat"
          @click="category = cat"
        >
          {{ $t(`items.category.${cat}`) }}
        </button>
      </div>

      <label class="flabel" for="import-account">{{ $t('import.account') }}</label>
      <select id="import-account" v-model="accountId" class="input" :disabled="!hasAccounts">
        <option :value="null" disabled>{{ $t('import.accountPlaceholder') }}</option>
        <option v-for="a in accounts" :key="a.id" :value="a.id">{{ a.name }}</option>
      </select>
      <p v-if="!hasAccounts" class="hint">{{ $t('import.noAccounts') }}</p>

      <label class="flabel" for="import-start">{{ $t('import.startDate') }}</label>
      <input id="import-start" v-model="startDate" class="input" type="date" />

      <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

      <button class="btn" type="button" :disabled="!canRegister" @click="register">
        {{ $t('import.register', { n: includedCount }) }}
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
  margin-bottom: 14px;
  line-height: 1.5;
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
.textarea {
  margin-top: 0;
  resize: vertical;
  min-height: 96px;
  font-size: 13px;
  line-height: 1.6;
  font-family: inherit;
}
.banner {
  font-size: 13px;
  color: var(--blue);
  background: var(--blue-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  margin-top: 14px;
  line-height: 1.5;
}
.cands {
  margin-top: 8px;
}
.cand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--line-2);
}
.cand:last-child {
  border-bottom: 0;
}
.cand-label {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  min-width: 0;
}
.check {
  appearance: none;
  flex: none;
  width: 20px;
  height: 20px;
  border-radius: 6px;
  background: var(--bg);
  border: 1.5px solid var(--line-2);
  position: relative;
  cursor: pointer;
}
.check:checked {
  background: var(--blue);
  border-color: var(--blue);
}
.check:checked::after {
  content: '';
  position: absolute;
  left: 6px;
  top: 2px;
  width: 5px;
  height: 10px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.cand-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.cand-amount {
  font-size: 14px;
  color: var(--sub);
  flex: none;
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
