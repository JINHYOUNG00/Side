<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ACTIVE_PROVIDERS, authorizeUrl, type ActiveProvider } from '@/api/oauth'

const route = useRoute()

// 콜백 실패 시 /login?error=CODE 로 돌아온다(예외 흐름 5.1). 코드만 받아 i18n으로 문구 조립.
const errorCode = computed(() => {
  const raw = route.query.error
  return typeof raw === 'string' && raw.length > 0 ? raw : null
})

function start(provider: ActiveProvider) {
  // 공급자 동의 화면으로 이동. 돌아오는 code는 AuthCallbackView가 받아 서버로 교환한다.
  window.location.assign(authorizeUrl(provider, window.location.origin))
}
</script>

<template>
  <section class="login">
    <div class="brand">
      <p class="wordmark">{{ $t('app.name') }}</p>
      <p class="tagline">{{ $t('login.tagline') }}</p>
    </div>

    <p v-if="errorCode" class="error" role="alert">{{ $t(`errors.${errorCode}`) }}</p>

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
.brand {
  margin-bottom: 48px;
}
.wordmark {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -1px;
  color: var(--blue);
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
.caption {
  font-size: 12px;
  color: var(--hint);
  margin-top: 18px;
}
</style>
