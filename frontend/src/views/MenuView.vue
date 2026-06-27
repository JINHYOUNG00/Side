<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import Card from '@/components/base/Card.vue'
import ImportSheet from '@/components/ImportSheet.vue'
import { ApiError } from '@/api/client'
import { listAccounts, type Account } from '@/api/accounts'
import { getMe, updateMe, type Me } from '@/api/me'
import { setLocale, LOCALES, type Locale } from '@/i18n'
import { useAuthStore } from '@/stores/auth'

// SCR-07 전체 — 허브 화면. 통장·항목·봉투·보관함(SCR-08) 진입 + 노션 임포트(MOD-07/DATA-01) +
// 언어 설정(SET-03) + 로그아웃. 내보내기(DATA-02·P7)·투자 포함 토글(SET-02·P5)·탈퇴는 후속 Phase 의존이라
// 미연결(고아 링크 금지). 라우트가 생기면 links에 추가만 하면 된다.
const router = useRouter()
const auth = useAuthStore()
const { locale } = useI18n()

const links = [
  { key: 'accounts', to: '/accounts' },
  { key: 'items', to: '/items' },
  { key: 'envelopes', to: '/envelopes' },
  { key: 'archive', to: '/archive' },
] as const

// 임포트 시트의 대상 통장 선택용 — 허브 진입 시 미리 읽어 둔다(실패해도 시트는 열리며 "통장 먼저" 안내).
const accounts = ref<Account[]>([])
const importOpen = ref(false)

// 언어 설정(SET-03). PATCH /me는 온보딩 필수값(실수령액·월급일·조정 규칙)을 함께 받으므로,
// 현재 설정을 읽어 두었다가 언어만 바꿔 전체 설정으로 되돌려 보낸다. 설정 미로드면 토글 비활성.
const me = ref<Me | null>(null)
const switching = ref(false)
const langError = ref(false)

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

function go(to: string) {
  router.push(to)
}

function openImport() {
  importOpen.value = true
}

function closeImport() {
  importOpen.value = false
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

    <button class="logout" type="button" @click="logout">{{ $t('menu.logout') }}</button>

    <ImportSheet :open="importOpen" :accounts="accounts" @close="closeImport" @imported="onImported" />
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
</style>
