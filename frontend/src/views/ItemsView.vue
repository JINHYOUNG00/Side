<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/base/Card.vue'
import EmptyState from '@/components/base/EmptyState.vue'
import MoneyText from '@/components/base/MoneyText.vue'
import ItemFormSheet from '@/components/ItemFormSheet.vue'
import { ApiError } from '@/api/client'
import { listBudgetItems, type BudgetItem } from '@/api/budgetItems'
import { listAccounts, type Account } from '@/api/accounts'

// MOD-01 항목 전체 목록 + 폼(추가·삭제) 진입점. SCR-07 전체 탭에서 진입.
// 짝 백엔드 ITEM-01(생성·조회)·ITEM-09(soft delete). 수정(ITEM-07)은 v1 제외.
// 대상 통장 선택을 위해 통장 목록(SET-04)을 함께 읽어 폼에 넘긴다.
const router = useRouter()

const items = ref<BudgetItem[]>([])
const accounts = ref<Account[]>([])
const loading = ref(true)
const errorCode = ref<string | null>(null)

const sheetOpen = ref(false)
const editing = ref<BudgetItem | null>(null)

function accountName(id: number): string {
  return accounts.value.find((a) => a.id === id)?.name ?? '—'
}

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    ;[items.value, accounts.value] = await Promise.all([listBudgetItems(), listAccounts()])
  } catch (e) {
    errorCode.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  } finally {
    loading.value = false
  }
}

function openAdd() {
  editing.value = null
  sheetOpen.value = true
}

function openManage(item: BudgetItem) {
  editing.value = item
  sheetOpen.value = true
}

function closeSheet() {
  sheetOpen.value = false
}

// 추가·삭제가 성공하면 시트를 닫고 목록을 다시 읽어 반영한다.
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
  <section class="items">
    <header class="head">
      <button class="back" type="button" :aria-label="$t('items.back')" @click="goBack">‹</button>
      <h1 class="title">{{ $t('items.title') }}</h1>
    </header>

    <p v-if="loading" class="state">{{ $t('items.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else>
      <Card v-if="items.length > 0" class="list">
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="row"
          @click="openManage(item)"
        >
          <span class="left">
            <span class="nm">{{ item.name }}</span>
            <span class="meta">{{ $t(`items.category.${item.category}`) }} · {{ accountName(item.accountId) }}</span>
          </span>
          <MoneyText class="amt" :amount="item.amount" :unit="$t('common.won')" />
        </button>
      </Card>

      <EmptyState v-else :title="$t('items.emptyTitle')" :body="$t('items.emptyBody')" />
    </template>

    <button class="btn add" type="button" @click="openAdd">{{ $t('items.add') }}</button>

    <ItemFormSheet
      :open="sheetOpen"
      :item="editing"
      :accounts="accounts"
      @close="closeSheet"
      @saved="onSaved"
    />
  </section>
</template>

<style scoped>
.items {
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
.amt {
  font-size: 15px;
  color: var(--ink);
}
.btn.add {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
  background: var(--blue);
  color: #fff;
  margin-top: 6px;
}
</style>
