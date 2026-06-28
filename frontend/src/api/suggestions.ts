import api from './client'

// 보정/리밸런싱 제안(SUG-01~03, API명세 6장). 서버는 문장이 아닌 구조화 데이터(type+payload)만 준다(규칙 7) —
// 문구 조립은 클라이언트 i18n 템플릿이 한다. 제안 생성은 서버 일일 배치 소관이고, 여기선 조회·반영·닫기만 한다.

export type SuggestionType =
  | 'RAISE_LIVING'
  | 'RAISE_SAVING'
  | 'REBALANCE_MATURITY'
  | 'WINDFALL'
  | 'SHORTFALL'
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

// 배분 한 줄 — 대상 plan_line과 배분(WINDFALL)·축소(SHORTFALL) 금액(원, 양수).
export interface AllocationInput {
  planLineId: number
  amount: number
}

// 여윳돈/부족 제안 배분 적용(CYCLE-05, 인터랙티브) — 고른 plan_line별 금액으로 이번 사이클 계획을 조정하고
// 제안을 APPLIED로 닫는다. 합·상한·0 미만 등 위반은 서버가 VALIDATION_FAILED로 막는다.
export async function allocateSuggestion(id: number, allocations: AllocationInput[]): Promise<Suggestion> {
  const { data } = await api.post<Suggestion>(`/suggestions/${id}/allocate`, { allocations })
  return data
}
