import api from './client'

// 현재 사이클 + 체크리스트(CYCLE-04/06, API명세 5장). 지급일~D+3에 홈 최상단 체크리스트 카드(SCR-03b)가
// 소비한다. 라이브 폭포(waterfall.ts)와 달리 cycles/plan_lines 스냅샷 기준이라 사이클 라벨·경계가 있다.

// plan_lines.status — 서버 PlanLineStatus enum 미러. SKIPPED·DONE 모두 "처리됨"(PENDING 아님).
export type PlanLineStatus = 'PENDING' | 'DONE' | 'SKIPPED'

// 체크리스트 한 줄 — plan_line 1건. name은 항목명 스냅샷이지만 LIVING 라인은 머신 토큰 'LIVING'이
// 와서 클라이언트가 i18n으로 렌더한다(규칙 7). PATCH /plan-lines/{id} 응답도 이 형태(단건).
export interface ChecklistLine {
  id: number
  name: string
  plannedAmount: number
  status: PlanLineStatus
}

// 이체 대상 통장별로 묶은 라인 묶음. total = 그룹 내 plannedAmount 합. accountName은 통장 별칭 스냅샷.
export interface ChecklistAccountGroup {
  accountId: number
  accountName: string
  total: number
  lines: ChecklistLine[]
}

// 진행도 — done = 처리된(PENDING 아닌) 라인 수, total = 전체. done == total이면 남은 이체 없음.
export interface ChecklistProgress {
  done: number
  total: number
}

// GET /cycles/current 응답(ChecklistResponse). 오늘이 속한 사이클 헤더 + 통장별 체크리스트 + 진행도.
export interface CurrentCycle {
  id: number
  label: string
  cycleStart: string // YYYY-MM-DD (Asia/Seoul) — 실제 지급일
  cycleEnd: string
  income: number
  incomeConfirmed: boolean
  checklist: ChecklistAccountGroup[]
  progress: ChecklistProgress
}

// PATCH /cycles/{id}/income 응답(CycleResponse). 체크리스트는 LIVING 재계산 반영을 위해 별도 재조회한다.
export interface ConfirmedCycle {
  id: number
  label: string
  cycleStart: string
  cycleEnd: string
  income: number
  incomeConfirmed: boolean
}

// 오늘이 속한 사이클 + 체크리스트. 스냅샷 미생성이면 서버가 404 NOT_FOUND를 준다(카드 미노출 신호).
export async function getCurrentCycle(): Promise<CurrentCycle> {
  const { data } = await api.get<CurrentCycle>('/cycles/current')
  return data
}

// 실수령액 확인·수정(CYCLE-04). 확정 시 income_confirmed=true + LIVING 라인이 재계산된다.
export async function confirmIncome(cycleId: number, income: number): Promise<ConfirmedCycle> {
  const { data } = await api.patch<ConfirmedCycle>(`/cycles/${cycleId}/income`, { income })
  return data
}

// 현재 사이클 지급일 재보정(SET-01 후속). 이미 만들어진 사이클의 경계가 틀린 월급일로 박혀 있을 때, 바뀐
// 설정(payday)으로 경계를 다시 도출해 이번 사이클을 다시 만든다. 이체(DONE) 시작 전 사이클만 — 시작했으면
// 409 CYCLE_LOCKED. 현재 사이클 없으면 404. 확인된 실수령액은 보존된다.
export async function recalibrateCurrentCycle(): Promise<ConfirmedCycle> {
  const { data } = await api.post<ConfirmedCycle>('/cycles/current/recalibrate')
  return data
}

// 라인 상태 전이 PENDING ↔ DONE/SKIPPED(CYCLE-06). 갱신된 라인 단건을 반환한다.
export async function changeLineStatus(lineId: number, status: PlanLineStatus): Promise<ChecklistLine> {
  const { data } = await api.patch<ChecklistLine>(`/plan-lines/${lineId}`, { status })
  return data
}
