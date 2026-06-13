import axios, { AxiosError } from 'axios'
import { useAuthStore } from '@/stores/auth'
import router from '@/router'

// 서버 오류 응답 본문: { code, params }. 문장이 아닌 코드만 옴(NFR-06) — 메시지는 클라 i18n.
export interface ApiErrorBody {
  code: string
  params?: Record<string, unknown>
}

// 호출부가 catch에서 .code로 i18n 키(errors.{code})를 만들 수 있도록 코드를 실어 reject한다.
export class ApiError extends Error {
  readonly code: string
  readonly params: Record<string, unknown>
  readonly status?: number

  constructor(code: string, params: Record<string, unknown>, status?: number) {
    super(code)
    this.name = 'ApiError'
    this.code = code
    this.params = params
    this.status = status
  }
}

const api = axios.create({
  baseURL: '/api/v1',
})

// 요청 인터셉터: 저장된 JWT를 Authorization 헤더에 부착(로그인 요청은 토큰 없으니 자연 통과).
api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.set('Authorization', `Bearer ${auth.token}`)
  }
  return config
})

// 응답 인터셉터: 401이면 세션 정리 후 로그인으로, 그 외 오류는 ApiError(code)로 정규화.
api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiErrorBody>) => {
    const status = error.response?.status
    const body = error.response?.data
    const code = body?.code ?? (status === 401 ? 'UNAUTHORIZED' : 'INTERNAL_ERROR')

    if (status === 401) {
      const auth = useAuthStore()
      auth.logout()
      if (router.currentRoute.value.name !== 'login') {
        void router.push({ name: 'login' })
      }
    }

    return Promise.reject(new ApiError(code, body?.params ?? {}, status))
  },
)

export default api
