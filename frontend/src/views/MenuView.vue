<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Card from '@/components/base/Card.vue'
import ImportSheet from '@/components/ImportSheet.vue'
import ProfileEditSheet from '@/components/ProfileEditSheet.vue'
import BottomSheet from '@/components/base/BottomSheet.vue'
import { ApiError } from '@/api/client'
import { listAccounts, type Account } from '@/api/accounts'
import { getMe, updateMe, deleteMe, type Me } from '@/api/me'
import { fetchExport, type ExportFormat } from '@/api/export'
import { downloadTextFile } from '@/lib/download'
import { setLocale, LOCALES, type Locale } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

// SCR-07 전체 — 허브 화면. 월급·월급일 수정(SET-01) + 통장·항목·보관함(SCR-08) 진입 +
// 노션 임포트(MOD-07/DATA-01) + (봉투는 하단 탭으로 승격돼 허브에서 제외 — 정본 SCR-07 봉투 미포함) +
// 데이터 내보내기(DATA-02) + 저축률 투자 포함 토글(SET-02) + 언어 설정(SET-03) + 로그아웃 + 회원 탈퇴(AUTH-04).
const router = useRouter()
const auth = useAuthStore()
const { locale } = useI18n()

const links = [
  { key: 'accounts', to: '/accounts' },
  { key: 'items', to: '/items' },
  { key: 'archive', to: '/archive' },
  { key: 'reminders', to: '/reminders' },
] as const

// 임포트 시트의 대상 통장 선택용 — 허브 진입 시 미리 읽어 둔다(실패해도 시트는 열리며 "통장 먼저" 안내).
const accounts = ref<Account[]>([])
const importOpen = ref(false)

// 언어 설정(SET-03). PATCH /me는 온보딩 필수값(실수령액·월급일·조정 규칙)을 함께 받으므로,
// 현재 설정을 읽어 두었다가 언어만 바꿔 전체 설정으로 되돌려 보낸다. 설정 미로드면 토글 비활성.
const me = ref<Me | null>(null)
const switching = ref(false)
const langError = ref(false)

// 저축률 투자 포함 토글(SET-02). 언어와 동일하게 현재 설정을 읽어 두었다가 토글만 바꿔 되돌려 보낸다.
const savingSwitching = ref(false)
const savingError = ref(false)

async function loadAccounts() {
  try {
    accounts.value = await listAccounts()
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    accounts.value = []
  }
}

// 현재 설정을 읽고, UI 언어를 서버 값(권위)에 맞춘다 — 다른 기기에서 바꿔도 허브 진입 시 동기화된다.
async function loadMe() {
  try {
    const loaded = await getMe()
    me.value = loaded
    if ((LOCALES as readonly string[]).includes(loaded.locale)) {
      setLocale(loaded.locale as Locale)
    }
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    me.value = null
  }
}

async function selectLocale(next: Locale) {
  const current = me.value
  if (switching.value || current === null || next === locale.value) return
  langError.value = false
  switching.value = true
  try {
    await updateMe({
      baseIncome: current.baseIncome,
      payday: current.payday,
      paydayAdjustment: current.paydayAdjustment,
      livingAccountId: current.livingAccountId,
      locale: next,
    })
    setLocale(next)
    me.value = { ...current, locale: next }
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    langError.value = true
  } finally {
    switching.value = false
  }
}

// 저축률 투자 포함 토글(SET-02). 언어와 동일하게 현재 전체 설정 + 바뀐 토글만 PATCH로 되돌려 보낸다.
async function toggleInvestment(next: boolean) {
  const current = me.value
  if (savingSwitching.value || current === null || next === current.includeInvestmentInSavingsRate) return
  savingError.value = false
  savingSwitching.value = true
  try {
    await updateMe({
      baseIncome: current.baseIncome,
      payday: current.payday,
      paydayAdjustment: current.paydayAdjustment,
      livingAccountId: current.livingAccountId,
      includeInvestmentInSavingsRate: next,
    })
    me.value = { ...current, includeInvestmentInSavingsRate: next }
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    savingError.value = true
  } finally {
    savingSwitching.value = false
  }
}

// 데이터 내보내기(DATA-02). 서버에서 활성 항목 표 텍스트(md/csv)를 받아 파일로 내려받는다. 실패하면 에러 노출.
const exporting = ref(false)
const exportError = ref(false)

async function exportData(format: ExportFormat) {
  if (exporting.value) return
  exportError.value = false
  exporting.value = true
  try {
    const text = await fetchExport(format)
    const mime = format === 'csv' ? 'text/csv;charset=utf-8' : 'text/markdown;charset=utf-8'
    downloadTextFile(`salary-export.${format}`, mime, text)
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    exportError.value = true
  } finally {
    exporting.value = false
  }
}

function go(to: string) {
  router.push(to)
}

function openImport() {
  importOpen.value = true
}

function closeImport() {
  importOpen.value = false
}

// 월급·월급일 수정(SET-01). 허브 진입 시 읽어 둔 me로 시트를 채우고, 저장하면 갱신된 me로 동기화한다.
const profileOpen = ref(false)

function openProfile() {
  if (me.value === null) return
  profileOpen.value = true
}

function closeProfile() {
  profileOpen.value = false
}

function onProfileSaved(updated: Me) {
  me.value = updated
  profileOpen.value = false
}

// 일괄 등록이 끝나면 시트를 닫고 항목 목록으로 이동해 결과를 바로 보여준다.
function onImported() {
  importOpen.value = false
  router.push('/items')
}

function logout() {
  auth.logout()
  router.push('/login')
}

// 회원 탈퇴(AUTH-04). 되돌릴 수 없는 전체 삭제라 확인 시트를 한 단계 거친다.
const withdrawOpen = ref(false)
const withdrawing = ref(false)
const withdrawError = ref(false)

function openWithdraw() {
  withdrawError.value = false
  withdrawOpen.value = true
}

function closeWithdraw() {
  if (withdrawing.value) return
  withdrawOpen.value = false
}

// 확인 시 서버에서 전체 데이터를 영구 삭제하고, 세션을 비운 뒤 로그인으로 보낸다. 실패하면 시트를 유지한 채 에러 노출.
async function confirmWithdraw() {
  if (withdrawing.value) return
  withdrawError.value = false
  withdrawing.value = true
  try {
    await deleteMe()
    auth.logout()
    router.push('/login')
  } catch (e) {
    if (!(e instanceof ApiError)) throw e
    withdrawError.value = true
  } finally {
    withdrawing.value = false
  }
}

onMounted(() => {
  loadAccounts()
  loadMe()
})
</script>

<template>
  <section class="menu">
    <header class="head">
      <h1 class="title">{{ $t('menu.title') }}</h1>
    </header>

    <Card class="list">
      <button type="button" class="row" :disabled="me === null" @click="openProfile">
        <span class="nm">{{ $t('menu.profile') }}</span>
        <span class="chev">›</span>
      </button>
      <button v-for="link in links" :key="link.key" type="button" class="row" @click="go(link.to)">
        <span class="nm">{{ $t(`menu.${link.key}`) }}</span>
        <span class="chev">›</span>
      </button>
      <button type="button" class="row" @click="openImport">
        <span class="nm">{{ $t('menu.import') }}</span>
        <span class="chev">›</span>
      </button>
    </Card>

    <Card class="lang">
      <div class="lang-row">
        <span class="nm">{{ $t('menu.export') }}</span>
        <div class="seg">
          <button type="button" class="seg-btn" :disabled="exporting" @click="exportData('md')">
            {{ $t('menu.exportMarkdown') }}
          </button>
          <button type="button" class="seg-btn" :disabled="exporting" @click="exportData('csv')">
            {{ $t('menu.exportCsv') }}
          </button>
        </div>
      </div>
      <p class="lang-hint">{{ $t('menu.exportHint') }}</p>
      <p v-if="exportError" class="lang-err" role="alert">{{ $t('menu.exportError') }}</p>
    </Card>

    <Card class="lang">
      <div class="lang-row">
        <span class="nm">{{ $t('menu.language') }}</span>
        <div class="seg" role="radiogroup" :aria-label="$t('menu.language')">
          <button
            v-for="l in LOCALES"
            :key="l"
            type="button"
            class="seg-btn"
            :class="{ on: locale === l }"
            :aria-pressed="locale === l"
            :disabled="me === null || switching"
            @click="selectLocale(l)"
          >
            {{ $t(`menu.lang.${l}`) }}
          </button>
        </div>
      </div>
      <p v-if="langError" class="lang-err" role="alert">{{ $t('menu.languageError') }}</p>
    </Card>

    <Card class="lang">
      <div class="lang-row">
        <span class="nm">{{ $t('menu.savingsInvestment') }}</span>
        <div class="seg" role="radiogroup" :aria-label="$t('menu.savingsInvestment')">
          <button
            type="button"
            class="seg-btn"
            :class="{ on: me?.includeInvestmentInSavingsRate === true }"
            :aria-pressed="me?.includeInvestmentInSavingsRate === true"
            :disabled="me === null || savingSwitching"
            @click="toggleInvestment(true)"
          >
            {{ $t('menu.savingsInclude') }}
          </button>
          <button
            type="button"
            class="seg-btn"
            :class="{ on: me?.includeInvestmentInSavingsRate === false }"
            :aria-pressed="me?.includeInvestmentInSavingsRate === false"
            :disabled="me === null || savingSwitching"
            @click="toggleInvestment(false)"
          >
            {{ $t('menu.savingsExclude') }}
          </button>
        </div>
      </div>
      <p class="lang-hint">{{ $t('menu.savingsInvestmentHint') }}</p>
      <p v-if="savingError" class="lang-err" role="alert">{{ $t('menu.savingsInvestmentError') }}</p>
    </Card>

    <button class="logout" type="button" @click="logout">{{ $t('menu.logout') }}</button>
    <button class="withdraw" type="button" @click="openWithdraw">{{ $t('menu.withdraw') }}</button>

    <ProfileEditSheet :open="profileOpen" :me="me" @close="closeProfile" @saved="onProfileSaved" />

    <ImportSheet :open="importOpen" :accounts="accounts" @close="closeImport" @imported="onImported" />

    <BottomSheet :open="withdrawOpen" @close="closeWithdraw">
      <h2 class="wd-title">{{ $t('menu.withdrawConfirmTitle') }}</h2>
      <p class="wd-body">{{ $t('menu.withdrawConfirmBody') }}</p>
      <p v-if="withdrawError" class="lang-err" role="alert">{{ $t('menu.withdrawError') }}</p>
      <div class="wd-actions">
        <button type="button" class="wd-cancel" :disabled="withdrawing" @click="closeWithdraw">
          {{ $t('menu.withdrawCancel') }}
        </button>
        <button type="button" class="wd-confirm" :disabled="withdrawing" @click="confirmWithdraw">
          {{ $t('menu.withdrawConfirm') }}
        </button>
      </div>
    </BottomSheet>
  </section>
</template>

<style scoped>
.menu {
  flex: 1;
}
.head {
  padding: 4px 0 18px;
}
.title {
  font-size: 20px;
  font-weight: 700;
  letter-spacing: -0.4px;
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
  padding: 15px 0;
  border-bottom: 1px solid var(--line-2);
}
.row:last-child {
  border-bottom: 0;
}
.row:disabled {
  opacity: 0.5;
}
.nm {
  font-size: 15px;
  font-weight: 600;
  color: var(--ink);
}
.chev {
  font-size: 18px;
  color: var(--hint);
}
.lang {
  margin-top: 12px;
  padding: 4px 20px;
}
.lang-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 13px 0;
}
.seg {
  display: flex;
  gap: 6px;
}
.seg-btn {
  padding: 7px 14px;
  border-radius: 999px;
  background: var(--bg);
  color: var(--sub);
  font-size: 13px;
  font-weight: 600;
}
.seg-btn.on {
  background: var(--blue);
  color: #fff;
}
.seg-btn:disabled {
  opacity: 0.5;
}
.lang-hint {
  font-size: 12px;
  color: var(--hint);
  line-height: 1.5;
  padding: 0 0 13px;
}
.lang-err {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  margin: 0 0 12px;
  line-height: 1.5;
}
.logout {
  display: block;
  width: 100%;
  text-align: center;
  font-size: 13px;
  color: var(--hint);
  padding: 16px 0;
}
.withdraw {
  display: block;
  width: 100%;
  text-align: center;
  font-size: 13px;
  color: var(--red);
  padding: 4px 0 16px;
}
.wd-title {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: -0.3px;
  margin-bottom: 10px;
}
.wd-body {
  font-size: 14px;
  color: var(--sub);
  line-height: 1.6;
  margin-bottom: 18px;
}
.wd-actions {
  display: flex;
  gap: 10px;
}
.wd-cancel,
.wd-confirm {
  flex: 1;
  padding: 15px 0;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
}
.wd-cancel {
  background: var(--bg);
  color: var(--sub);
}
.wd-confirm {
  background: var(--red);
  color: #fff;
}
.wd-cancel:disabled,
.wd-confirm:disabled {
  opacity: 0.5;
}
</style>
