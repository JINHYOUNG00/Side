<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ACTIVE_PROVIDERS, type ActiveProvider } from '@/api/oauth'
import { ApiError } from '@/api/client'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function backToLogin(code: string) {
  void router.replace({ name: 'login', query: { error: code } })
}

onMounted(async () => {
  const provider = String(route.params.provider)
  const code = typeof route.query.code === 'string' ? route.query.code : ''

  // 동의 거부·중단: 공급자가 code 없이(또는 error=) 되돌린다 → 로그인 화면 복귀(예외 흐름 5.1).
  if (!code) {
    backToLogin('OAUTH_EXCHANGE_FAILED')
    return
  }
  if (!ACTIVE_PROVIDERS.includes(provider as ActiveProvider)) {
    backToLogin('PROVIDER_NOT_SUPPORTED')
    return
  }

  try {
    await auth.loginWithCode(provider, code)
    // 로그인 후 홈으로. 첫 가입(isNewUser)은 온보딩(SCR-02)으로 분기 예정 — 라우트는 Phase 1.
    // 분기 신호는 auth.isNewUser에 보관됨.
    await router.replace({ name: 'home' })
  } catch (e) {
    backToLogin(e instanceof ApiError ? e.code : 'INTERNAL_ERROR')
  }
})
</script>

<template>
  <section class="callback">
    <p class="msg">{{ $t('login.signingIn') }}</p>
  </section>
</template>

<style scoped>
.callback {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
.msg {
  font-size: 15px;
  color: var(--sub);
}
</style>
