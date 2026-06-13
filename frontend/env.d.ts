/// <reference types="vite/client" />

// OAuth 공급자 클라이언트 ID(빌드 시 주입). 비밀키는 서버 전용 — 여기 두지 않는다(규칙 6).
interface ImportMetaEnv {
  readonly VITE_KAKAO_CLIENT_ID?: string
  readonly VITE_GOOGLE_CLIENT_ID?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
