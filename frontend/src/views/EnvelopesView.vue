<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/base/Card.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import ProgressBar from '@/components/base/ProgressBar.vue'
import EnvelopeFormSheet from '@/components/EnvelopeFormSheet.vue'
import EnvelopeSpendSheet from '@/components/EnvelopeSpendSheet.vue'
import { ApiError } from '@/api/client'
import { listEnvelopes, type Envelope } from '@/api/envelopes'
import { listAccounts, type Account } from '@/api/accounts'

// SCR-04 봉투 목록 — 진행률·D-day·이번 달 적립액 표시 + MOD-02 폼·MOD-04 지출 처리 진입점(ENV-01~05).
// 적립 통장 선택을 위해 통장 목록(SET-04)을 함께 읽어 폼에 넘긴다. soft delete라 목록은 활성만 온다.
const router = useRouter()

const envelopes = ref<Envelope[]>([])
const accounts = ref<Account[]>([])
const loading = ref(true)
const errorCode = ref<string | null>(null)

const formOpen = ref(false)
const editing = ref<Envelope | null>(null)
const spendOpen = ref(false)
const spending = ref<Envelope | null>(null)

// 이번 달 봉투 적립 합계(월 적립액 합) — 보라 헤더 카드.
const totalMonthly = computed(() => envelopes.value.reduce((sum, e) => sum + e.monthlyAmount, 0))

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    ;[envelopes.value, accounts.value] = await Promise.all([listEnvelopes(), listAccounts()])
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = null
  formOpen.value = true
}

function openEdit(envelope: Envelope) {
  editing.value = envelope
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
}

function openSpend(envelope: Envelope) {
  spending.value = envelope
  spendOpen.value = true
}

function closeSpend() {
  spendOpen.value = false
}

// 추가·수정·삭제·지출 처리가 성공하면 시트를 닫고 목록을 다시 읽어 반영한다.
async function onSaved() {
  formOpen.value = false
  spendOpen.value = false
  await load()
}

function goBack() {
  router.back()
}

onMounted(load)
</script>

<template>
  <section class="envelopes">
    <header class="head">
      <button class="back" type="button" :aria-label="$t('envelopes.back')" @click="goBack">‹</button>
      <h1 class="title">{{ $t('envelopes.title') }}</h1>
    </header>

    <p v-if="loading" class="state">{{ $t('envelopes.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else>
      <template v-if="envelopes.length > 0">
        <Card class="total">
          <p class="total-label">{{ $t('envelopes.monthlyTotal') }}</p>
          <MoneyText class="total-amt" :amount="totalMonthly" :unit="$t('common.won')" />
          <p class="total-sub">{{ $t('envelopes.count', { n: envelopes.length }) }}</p>
        </Card>

        <Card v-for="env in envelopes" :key="env.id" class="env">
          <div class="env-top">
            <span class="nm">{{ env.name }}</span>
            <span class="meta">
              {{
                env.cycleMonths === null
                  ? $t('envelopes.oneTime')
                  : $t('envelopes.everyMonths', { n: env.cycleMonths })
              }}
              ·
              {{
                env.dDay > 0
                  ? $t('envelopes.dday', { n: env.dDay })
                  : env.dDay === 0
                    ? $t('envelopes.dueToday')
                    : $t('envelopes.dueAgo', { n: -env.dDay })
              }}
            </span>
          </div>

          <div class="env-amt">
            <span class="saved">
              <MoneyText :amount="env.savedAmount" />
              <span class="slash">/</span>
              <MoneyText :amount="env.targetAmount" :unit="$t('common.won')" />
            </span>
            <span class="pct">{{ env.progressPercent }}%</span>
          </div>
          <ProgressBar :value="env.progressPercent" />

          <p class="monthly">
            <span>{{ $t('envelopes.monthlyLabel') }}</span>
            <MoneyText :amount="env.monthlyAmount" :unit="$t('common.won')" />
          </p>

          <div class="env-foot">
            <button class="btn ghost" type="button" @click="openEdit(env)">
              {{ $t('envelopes.edit') }}
            </button>
            <button class="btn spend" type="button" @click="openSpend(env)">
              {{ $t('envelopes.spend') }}
            </button>
          </div>
        </Card>
      </template>

      <EmptyState v-else :title="$t('envelopes.emptyTitle')" :body="$t('envelopes.emptyBody')" />
    </template>

    <button class="btn add" type="button" @click="openAdd">{{ $t('envelopes.add') }}</button>

    <EnvelopeFormSheet
      :open="formOpen"
      :envelope="editing"
      :accounts="accounts"
      @close="closeForm"
      @saved="onSaved"
    />
    <EnvelopeSpendSheet :open="spendOpen" :envelope="spending" @close="closeSpend" @saved="onSaved" />
  </section>
</template>

<style scoped>
.envelopes {
  flex: 1;
}
.head {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 0 18px;
}
.back {
  font-size: 26px;
  line-height: 1;
  color: var(--sub);
  width: 32px;
  margin-left: -6px;
}
.title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
}
.state {
  font-size: 14px;
  color: var(--hint);
  text-align: center;
  padding: 32px 0;
}
.state.error {
  color: var(--red);
}
/* 보라 합계 헤더(봉투/제안 색). */
.total {
  background: var(--purple);
  color: #fff;
}
.total-label {
  font-size: 13px;
  opacity: 0.85;
}
.total-amt {
  display: block;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: -0.5px;
  margin-top: 6px;
  color: #fff;
}
.total-amt :deep(.money-unit) {
  color: rgba(255, 255, 255, 0.85);
}
.total-sub {
  font-size: 12px;
  opacity: 0.75;
  margin-top: 6px;
}
.env-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}
.nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.meta {
  font-size: 12px;
  color: var(--hint);
  text-align: right;
}
.env-amt {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin: 14px 0 8px;
}
.saved {
  font-size: 14px;
  color: var(--ink);
}
.slash {
  color: var(--hint);
  margin: 0 2px;
}
.pct {
  font-size: 13px;
  font-weight: 600;
  color: var(--purple);
}
.monthly {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 13px;
  color: var(--sub);
  margin-top: 12px;
}
.env-foot {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
.btn {
  display: block;
  width: 100%;
  padding: 12px;
  border-radius: var(--r);
  font-size: 14px;
  font-weight: 600;
  background: var(--purple);
  color: #fff;
}
.btn.ghost {
  background: var(--bg);
  color: var(--ink);
}
.btn.spend {
  background: var(--purple);
  color: #fff;
}
.btn.add {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
  background: var(--purple);
  color: #fff;
  margin-top: 6px;
}
</style>
