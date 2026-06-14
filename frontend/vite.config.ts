import { fileURLToPath, URL } from 'node:url'

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 백엔드 출처. 기본 8080이지만, 8080이 다른 서비스(예: Oracle TNS 리스너)에 점유된 환경에선
  // .env.local 의 BACKEND_ORIGIN 으로 덮어쓴다(예: http://localhost:8081). .env.local은 gitignore.
  const env = loadEnv(mode, process.cwd(), '')
  const backendOrigin = env.BACKEND_ORIGIN || 'http://localhost:8080'

  return {
    plugins: [
      vue(),
      vueDevTools(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      },
    },
    // 개발 서버: /api 요청을 백엔드로 프록시. axios baseURL '/api/v1'이 동일 출처로 보이게 해
    // 로그인(실 OAuth·dev 로그인 모두)·이후 인증 호출이 백엔드에 도달한다.
    server: {
      proxy: {
        '/api': backendOrigin,
      },
    },
  }
})
