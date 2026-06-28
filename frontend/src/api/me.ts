import api from './client'

// 프로필·기본 정보(SET-01, API명세 2장). GET /me 조회 + PATCH /me 등록·수정.
// 월급일 조정 규칙 — 서버 PaydayAdjustment enum 미러. 실제 지급일 산출(CYCLE-01)이 이 값을 쓴다.
export const PAYDAY_ADJUSTMENTS = ['PREV_BUSINESS_DAY', 'NEXT_BUSINESS_DAY', 'NONE'] as const
export type PaydayAdjustment = (typeof PAYDAY_ADJUSTMENTS)[number]

// GET /me 응답. 투자 포함 토글(SET-02)·언어(SET-03)는 MeUpdate로 갱신한다(선택 필드).
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
// locale(SET-03)·includeInvestmentInSavingsRate(SET-02)는 선택 — 생략하면 서버가 기존값을 보존하고,
// 보내면 갱신한다(온보딩 필수값과 분리).
export interface MeUpdate {
  baseIncome: number
  payday: number
  paydayAdjustment: PaydayAdjustment
  livingAccountId: number | null
  locale?: string
  includeInvestmentInSavingsRate?: boolean
}

export async function getMe(): Promise<Me> {
  const { data } = await api.get<Me>('/me')
  return data
}

export async function updateMe(input: MeUpdate): Promise<Me> {
  const { data } = await api.patch<Me>('/me', input)
  return data
}

// DELETE /me 회원 탈퇴(AUTH-04). 전체 데이터를 서버에서 영구 삭제(cascade)한다 — 204 No Content.
export async function deleteMe(): Promise<void> {
  await api.delete('/me')
}
