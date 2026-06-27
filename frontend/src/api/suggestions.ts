import api from './client'

// 보정/리밸런싱 제안(SUG-01~03, API명세 6장). 서버는 문장이 아닌 구조화 데이터(type+payload)만 준다(규칙 7) —
// 문구 조립은 클라이언트 i18n 템플릿이 한다. 제안 생성은 서버 일일 배치 소관이고, 여기선 조회·반영·닫기만 한다.

export type SuggestionType = 'RAISE_LIVING' | 'RAISE_SAVING' | 'REBALANCE_MATURITY'
export type SuggestionStatus = 'PENDING' | 'APPLIED' | 'DISMISSED'

// 제안 한 건. payload는 type별 구조화 파라미터(예: RAISE_*는 suggestedIncrease·avgOverspend/avgSurplus·streak,
// REBALANCE_MATURITY는 itemName·monthlyAmount·maturityDate·(있으면) expectedMaturityAmount). 금액은 원 단위 number.
export interface Suggestion {
  id: number
  type: SuggestionType
  status: SuggestionStatus
  payload: Record<string, unknown>
}

// 노출 대상(PENDING) 제안 목록 — 최신순.
export async function listSuggestions(): Promise<Suggestion[]> {
  const { data } = await api.get<Suggestion[]>('/suggestions')
  return data
}

// 제안 반영(MOD-06) — APPLIED로 전이. 실제 배분 변경은 호출 측이 payload 권고치로 편집 화면에서 수행한다.
export async function applySuggestion(id: number): Promise<Suggestion> {
  const { data } = await api.post<Suggestion>(`/suggestions/${id}/apply`)
  return data
}

// 제안 닫기(MOD-06) — DISMISSED로 전이.
export async function dismissSuggestion(id: number): Promise<Suggestion> {
  const { data } = await api.post<Suggestion>(`/suggestions/${id}/dismiss`)
  return data
}
