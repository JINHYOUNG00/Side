import { createI18n } from 'vue-i18n'
import ko from './locales/ko.json'
import en from './locales/en.json'

// 문구 하드코딩 금지(CLAUDE.md 규칙 7) — 모든 화면 문구는 여기 키로만.
// globalInjection: 템플릿에서 $t 직접 사용 허용.
const i18n = createI18n({
  legacy: false,
  globalInjection: true,
  locale: 'ko',
  fallbackLocale: 'ko',
  messages: { ko, en },
})

export default i18n
