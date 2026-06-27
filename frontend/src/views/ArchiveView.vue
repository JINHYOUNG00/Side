<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/base/Card.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import MaturityRecordSheet from '@/components/MaturityRecordSheet.vue'
import { ApiError } from '@/api/client'
import {
  listArchive,
  type ArchivedItem,
  type MaturityArchiveStats,
} from '@/api/budgetItems'
import { listAccounts, type Account } from '@/api/accounts'

// SCR-08 보관함(ITEM-08). 만기·중도해지로 보관(ARCHIVED)된 항목 목록 + 예상 vs 실제 만기금액 + 누적 수령 통계.
// 각 항목을 누르면 실수령액 기록 시트(MaturityRecordSheet)가 열린다. SCR-07 전체 허브에서 진입.
// 대상 통장 이름 표기를 위해 통장 목록(SET-04)을 함께 읽는다. 색은 초록=완료(수령).
const router = useRouter()

const items = ref<ArchivedItem[]>([])
const stats = ref<MaturityArchiveStats>({ archivedCount: 0, recordedCount: 0, totalReceivedAmount: 0 })
const accounts = ref<Account[]>([])
const loading = ref(true)
const errorCode = ref<string | null>(null)

const sheetOpen = ref(false)
const editing = ref<ArchivedItem | null>(null)

function accountName(id: number | null): string {
  if (id === null) return '—'
  return accounts.value.find((a) => a.id === id)?.name ?? '—'
}

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    const [archive, accountList] = await Promise.all([listArchive(), listAccounts()])
    items.value = archive.items
    stats.value = archive.stats
    accounts.value = accountList
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

function openRecord(item: ArchivedItem) {
  editing.value = item
  sheetOpen.value = true
}

function closeSheet() {
  sheetOpen.value = false
}

// 실수령액을 기록하면 시트를 닫고 목록·통계를 다시 읽어 반영한다.
async function onSaved() {
  sheetOpen.value = false
  await load()
}

function goBack() {
  router.back()
}

onMounted(load)
</script>

<template>
  <section class="archive">
    <header class="head">
      <button class="back" type="button" :aria-label="$t('archive.back')" @click="goBack">‹</button>
      <h1 class="title">{{ $t('archive.title') }}</h1>
    </header>

    <p v-if="loading" class="state">{{ $t('archive.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else>
      <!-- 누적 수령 통계 헤더(초록=완료/수령) -->
      <Card class="stats">
        <p class="stats-label">{{ $t('archive.stats.received') }}</p>
        <MoneyText class="stats-amt" :amount="stats.totalReceivedAmount" :unit="$t('common.won')" />
        <p class="stats-meta">
          {{ $t('archive.stats.counts', { archived: stats.archivedCount, recorded: stats.recordedCount }) }}
        </p>
      </Card>

      <Card v-if="items.length > 0" class="list">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="row"
          @click="openRecord(item)"
        >
          <span class="left">
            <span class="nm">{{ item.name }}</span>
            <span class="meta">{{ $t(`items.category.${item.category}`) }} · {{ accountName(item.accountId) }}</span>
          </span>
          <span class="right">
            <MoneyText
              v-if="item.maturityActualAmount !== null"
              class="amt received"
              :amount="item.maturityActualAmount"
              :unit="$t('common.won')"
            />
            <span v-else class="amt unrecorded">{{ $t('archive.notRecorded') }}</span>
            <span
              v-if="item.expectedMaturityAmount !== null"
              class="expected"
            >
              {{ $t('archive.expected') }}
              <MoneyText :amount="item.expectedMaturityAmount" :unit="$t('common.won')" />
            </span>
          </span>
        </button>
      </Card>

      <Card v-else class="empty">
        <p class="empty-title">{{ $t('archive.emptyTitle') }}</p>
        <p class="empty-body">{{ $t('archive.emptyBody') }}</p>
      </Card>
    </template>

    <MaturityRecordSheet :open="sheetOpen" :item="editing" @close="closeSheet" @saved="onSaved" />
  </section>
</template>

<style scoped>
.archive {
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
.stats {
  padding: 20px;
  margin-bottom: 12px;
}
.stats-label {
  font-size: 13px;
  color: var(--sub);
}
.stats-amt {
  display: block;
  font-size: 26px;
  color: var(--green);
  margin-top: 6px;
}
.stats-meta {
  font-size: 13px;
  color: var(--hint);
  margin-top: 8px;
}
.list {
  padding: 4px 20px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  text-align: left;
  padding: 14px 0;
  border-bottom: 1px solid var(--line-2);
}
.row:last-child {
  border-bottom: 0;
}
.left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.meta {
  font-size: 13px;
  color: var(--hint);
}
.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}
.amt {
  font-size: 15px;
}
.amt.received {
  color: var(--green);
}
.amt.unrecorded {
  font-size: 13px;
  color: var(--hint);
}
.expected {
  font-size: 12px;
  color: var(--hint);
}
.empty {
  text-align: center;
  padding: 28px 12px;
}
.empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.empty-body {
  font-size: 13px;
  color: var(--hint);
  margin-top: 6px;
  line-height: 1.6;
}
</style>
