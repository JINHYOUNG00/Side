// 공급자 authorize 엔드포인트로 보낼 URL을 만든다. code 교환·userinfo는 백엔드가 수행(AUTH-01).
// 클라이언트는 redirect로 code만 받아 POST /auth/{provider}로 넘긴다.
// 활성 공급자는 KAKAO·GOOGLE·NAVER(네이버는 AUTH-02 검수 통과로 활성화) — OAuthProvider enabled와 정합.

export type ActiveProvider = 'kakao' | 'google' | 'naver'

export const ACTIVE_PROVIDERS: readonly ActiveProvider[] = ['kakao', 'google', 'naver'] as const

interface ProviderConfig {
  authorizeUri: string
  clientId: string
  scope?: string
  // 네이버 authorize는 state(CSRF 방지)를 필수로 요구한다. true면 호출 시마다 난수 state를 단다.
  requiresState?: boolean
}

// 클라이언트 ID는 빌드 시 주입(VITE_*). 비밀키·토큰 교환은 서버 전용이라 여기 없음(규칙 6).
const CONFIG: Record<ActiveProvider, ProviderConfig> = {
  kakao: {
    authorizeUri: 'https://kauth.kakao.com/oauth/authorize',
    clientId: import.meta.env.VITE_KAKAO_CLIENT_ID ?? '',
  },
  google: {
    authorizeUri: 'https://accounts.google.com/o/oauth2/v2/auth',
    clientId: import.meta.env.VITE_GOOGLE_CLIENT_ID ?? '',
    scope: 'openid email profile',
  },
  naver: {
    authorizeUri: 'https://nid.naver.com/oauth2.0/authorize',
    clientId: import.meta.env.VITE_NAVER_CLIENT_ID ?? '',
    requiresState: true,
  },
}

// 콜백 라우트와 일치해야 하며 서버의 redirect-uri 설정과도 동일해야 한다.
export function redirectUri(provider: ActiveProvider, origin: string): string {
  return `${origin}/login/callback/${provider}`
}

export function authorizeUrl(provider: ActiveProvider, origin: string): string {
  const cfg = CONFIG[provider]
  const params = new URLSearchParams({
    client_id: cfg.clientId,
    redirect_uri: redirectUri(provider, origin),
    response_type: 'code',
  })
  if (cfg.scope) {
    params.set('scope', cfg.scope)
  }
  if (cfg.requiresState) {
    params.set('state', crypto.randomUUID())
  }
  return `${cfg.authorizeUri}?${params.toString()}`
}
