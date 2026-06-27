import api from './client'

// 월말 체크인 기록(RPT-01, API명세 6장). MOD-05 체크인 시트가 현재 사이클(cycleId)에 대해 한 번 기록한다.
// 사이클당 1건이라 재요청은 409 CHECK_IN_ALREADY_EXISTS. 추이 리포트 조회는 reports.ts 소관.

// 체크인 입력 — 생활비 통장 잔액(필수)과 사이클 중 추가 투입액(선택, 기본 0). 둘 다 0~10억(원).
export interface CheckInInput {
  cycleId: number
  livingRemaining: number
  toppedUp?: number
}

// 체크인 응답 — 입력값 + 서버가 계산·저장한 overspend(toppedUp − livingRemaining). 양수=초과·0=정확·음수=잉여.
// 클라이언트가 부호로 달성/초과 피드백 문구를 조립한다(규칙 7 — 서버는 문장 미생성).
export interface CheckInResult {
  id: number
  cycleId: number
  livingRemaining: number
  toppedUp: number
  overspend: number
  note: string | null
}

export async function recordCheckIn(input: CheckInInput): Promise<CheckInResult> {
  const { data } = await api.post<CheckInResult>('/check-ins', input)
  return data
}
