import { createI18n } from 'vue-i18n'
import ko from './locales/ko.json'
import en from './locales/en.json'

// 문구 하드코딩 금지(CLAUDE.md 규칙 7) — 모든 화면 문구는 여기 키로만.
// globalInjection: 템플릿에서 $t 직접 사용 허용.

// 지원 언어(SET-03, ERD locale ko/en). 기본값 ko.
export const LOCALES = ['ko', 'en'] as const
export type Locale = (typeof LOCALES)[number]
const DEFAULT_LOCALE: Locale = 'ko'
const LOCALE_KEY = 'salary.locale'

function isLocale(value: string | null): value is Locale {
  return value === 'ko' || value === 'en'
}

// 새로고침 후에도 마지막으로 고른 UI 언어를 유지(JWT 토큰 지속과 동일 패턴, stores/auth).
// 알림 언어는 서버 user.locale이 권위이고, 이 값은 UI 표시 전용 캐시다.
function readLocale(): Locale {
  try {
    const saved = localStorage.getItem(LOCALE_KEY)
    if (isLocale(saved)) return saved
  } catch {
    // localStorage 불가 환경(시크릿 모드 등)은 기본 언어로.
  }
  return DEFAULT_LOCALE
}

const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: readLocale(),
  fallbackLocale: DEFAULT_LOCALE,
  messages: { ko, en },
})

// UI 언어를 즉시 전환하고 다음 방문을 위해 저장. 서버 반영(PATCH /me)은 호출 측이 별도로 한다.
export function setLocale(locale: Locale) {
  i18n.global.locale.value = locale
  try {
    localStorage.setItem(LOCALE_KEY, locale)
  } catch {
    // 저장 실패해도 이번 세션 전환은 유효(메모리).
  }
}

export default i18n
