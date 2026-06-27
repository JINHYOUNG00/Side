import api from './client'

// 프로필·기본 정보(SET-01, API명세 2장). GET /me 조회 + PATCH /me 등록·수정.
// 월급일 조정 규칙 — 서버 PaydayAdjustment enum 미러. 실제 지급일 산출(CYCLE-01)이 이 값을 쓴다.
export const PAYDAY_ADJUSTMENTS = ['PREV_BUSINESS_DAY', 'NEXT_BUSINESS_DAY', 'NONE'] as const
export type PaydayAdjustment = (typeof PAYDAY_ADJUSTMENTS)[number]

// GET /me 응답. 투자 포함 토글(SET-02)·언어(SET-03)는 현재 노출만, 쓰기는 각 Phase에서 추가.
export interface Me {
  id: number
  email: string | null
  nickname: string
  baseIncome: number
  payday: number
  paydayAdjustment: PaydayAdjustment
  includeInvestmentInSavingsRate: boolean
  locale: string
  livingAccountId: number | null
}

// PATCH /me 입력(전체 설정 갱신). livingAccountId는 step 2에서 지정 — onboarding step 1은 기존값 보존.
// locale(SET-03)은 선택 — 생략하면 서버가 기존 언어를 보존하고, 보내면 ko/en으로 갱신한다.
export interface MeUpdate {
  baseIncome: number
  payday: number
  paydayAdjustment: PaydayAdjustment
  livingAccountId: number | null
  locale?: string
}

export async function getMe(): Promise<Me> {
  const { data } = await api.get<Me>('/me')
  return data
}

export async function updateMe(input: MeUpdate): Promise<Me> {
  const { data } = await api.patch<Me>('/me', input)
  return data
}
