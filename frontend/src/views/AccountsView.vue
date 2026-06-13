<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Card from '@/components/base/Card.vue'
import AccountFormSheet from '@/components/AccountFormSheet.vue'
import { ApiError } from '@/api/client'
import { listAccounts, type Account } from '@/api/accounts'

// 통장 관리 화면 — 목록 + MOD-03 폼(추가·수정·삭제) 진입점.
// 짝 백엔드 SET-04(GET|POST|PATCH|DELETE /accounts). soft delete라 목록은 활성만 온다.
const router = useRouter()

const accounts = ref<Account[]>([])
const loading = ref(true)
const errorCode = ref<string | null>(null)

const sheetOpen = ref(false)
const editing = ref<Account | null>(null)

async function load() {
  loading.value = true
  errorCode.value = null
  try {
    accounts.value = await listAccounts()
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

function openEdit(account: Account) {
  editing.value = account
  sheetOpen.value = true
}

function closeSheet() {
  sheetOpen.value = false
}

// 추가·수정·삭제가 성공하면 시트를 닫고 목록을 다시 읽어 반영한다.
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
  <section class="accounts">
    <header class="head">
      <button class="back" type="button" :aria-label="$t('accounts.back')" @click="goBack">‹</button>
      <h1 class="title">{{ $t('accounts.title') }}</h1>
    </header>

    <p v-if="loading" class="state">{{ $t('accounts.loading') }}</p>
    <p v-else-if="errorCode" class="state error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

    <template v-else>
      <Card v-if="accounts.length > 0" class="list">
        <button
          v-for="account in accounts"
          :key="account.id"
          type="button"
          class="row"
          @click="openEdit(account)"
        >
          <span class="nm">{{ account.name }}</span>
          <span class="purpose">{{ account.purpose || '—' }}</span>
        </button>
      </Card>

      <Card v-else class="empty">
        <p class="empty-title">{{ $t('accounts.emptyTitle') }}</p>
        <p class="empty-body">{{ $t('accounts.emptyBody') }}</p>
      </Card>
    </template>

    <button class="btn add" type="button" @click="openAdd">{{ $t('accounts.add') }}</button>

    <AccountFormSheet :open="sheetOpen" :account="editing" @close="closeSheet" @saved="onSaved" />
  </section>
</template>

<style scoped>
.accounts {
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
.nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.purpose {
  font-size: 13px;
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
