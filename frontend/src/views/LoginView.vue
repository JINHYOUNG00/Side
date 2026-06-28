<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ACTIVE_PROVIDERS, authorizeUrl, type ActiveProvider } from '@/api/oauth'
import { useAuthStore } from '@/stores/auth'
import { ApiError } from '@/api/client'
import BrandLogo from '@/components/base/BrandLogo.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 콜백 실패 시 /login?error=CODE 로 돌아온다(예외 흐름 5.1). 코드만 받아 i18n으로 문구 조립.
const errorCode = computed(() => {
  const raw = route.query.error
  return typeof raw === 'string' && raw.length > 0 ? raw : null
})

function start(provider: ActiveProvider) {
  // 공급자 동의 화면으로 이동. 돌아오는 code는 AuthCallbackView가 받아 서버로 교환한다.
  window.location.assign(authorizeUrl(provider, window.location.origin))
}

// 로컬 전용 dev 로그인(VERIFY-notion-match) — 운영 빌드(import.meta.env.DEV=false)에선 버튼 자체가 숨는다.
const isDev = import.meta.env.DEV
const devError = ref<string | null>(null)

async function startDev() {
  devError.value = null
  try {
    const session = await auth.loginAsDev()
    await router.replace({ name: session.isNewUser ? 'onboarding' : 'home' })
  } catch (e) {
    devError.value = e instanceof ApiError ? e.code : 'INTERNAL_ERROR'
  }
}
</script>

<template>
  <section class="login">
    <div class="brand">
      <BrandLogo class="logo" :size="42" />
      <p class="tagline">{{ $t('login.tagline') }}</p>
    </div>

    <p v-if="errorCode || devError" class="error" role="alert">
      {{ $t(`errors.${devError ?? errorCode}`) }}
    </p>

    <div class="actions">
      <button
        v-for="provider in ACTIVE_PROVIDERS"
        :key="provider"
        class="btn"
        :class="provider"
        type="button"
        @click="start(provider)"
      >
        {{ $t(`login.continue.${provider}`) }}
      </button>
      <button v-if="isDev" class="btn dev" type="button" @click="startDev">
        {{ $t('login.devLogin') }}
      </button>
    </div>

    <p class="caption">{{ $t('login.privacyNote') }}</p>
  </section>
</template>

<style scoped>
.login {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding-bottom: 40px;
  text-align: center;
}
/* 데스크톱(웹 대응) — 풀폭으로 늘어진 버튼 대신 좁은 중앙 컬럼(인증 플로우는 사이드바 없음). */
@media (min-width: 900px) {
  .login {
    max-width: 380px;
    width: 100%;
    margin: 0 auto;
  }
}
.brand {
  margin-bottom: 48px;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.logo {
  margin: 0 auto;
}
.tagline {
  font-size: 15px;
  color: var(--sub);
  margin-top: 10px;
  line-height: 1.6;
  white-space: pre-line;
}
.error {
  font-size: 13px;
  color: var(--red);
  background: var(--red-soft);
  border-radius: var(--r);
  padding: 12px 14px;
  margin-bottom: 16px;
  line-height: 1.5;
}
.actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.btn {
  display: block;
  width: 100%;
  padding: 15px;
  border-radius: var(--r);
  font-size: 15px;
  font-weight: 600;
}
.btn.kakao {
  background: #fee500;
  color: #191600;
}
.btn.google {
  background: var(--surface);
  color: var(--ink);
  border: 1px solid var(--line-2);
}
.btn.naver {
  background: #03c75a;
  color: #fff;
}
/* 로컬 전용 dev 로그인 — 운영 빌드엔 렌더되지 않음. 점선 테두리로 임시 도구임을 시각 구분. */
.btn.dev {
  background: transparent;
  color: var(--sub);
  border: 1px dashed var(--line-2);
}
.caption {
  font-size: 12px;
  color: var(--hint);
  margin-top: 18px;
}
</style>
