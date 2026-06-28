import api from './client'
import type { SavingsRate } from './waterfall'
import type { MaturityArchiveStats } from './budgetItems'

// 추이·요약 리포트 조회(RPT-02, API명세 6장). SCR-06 리포트 화면의 데이터원. 조회 전용 — 서버가
// principal=userId로 본인 데이터만 준다. 저축률·만기 통계 타입은 폭포(SET-02)·보관함(ITEM-08)과 공유한다.

// 추이 한 점 — 한 사이클의 "계획 vs 실제"(RPT-02). actual이 null이면 결측(체크인 미수행, checkedIn=false)이라
// 차트가 빈 점으로 구분 표시한다(예외 흐름 5.1). planned는 LIVING(생활비) 계획액, actual=planned+overspend.
export interface TrendPoint {
  label: string // 사이클 라벨(예: "2026-05")
  planned: number
  actual: number | null
  checkedIn: boolean
}

// GET /reports/summary 응답 — 상단 메트릭 세 집계. 각 값은 기존 순수 도메인 재사용분(중복 정의 없음).
export interface ReportSummary {
  savingsRate: SavingsRate // 저축률(투자 포함 토글 반영, 폭포와 공통)
  maturity: MaturityArchiveStats // 만기 수령 누적 통계(보관 실수령액)
  envelopeSpentTotal: number // 봉투 집행(SPEND) 실지출 누적액(원)
}

// GET /reports/annual?year=N 응답 — 그 해 결산(RPT-04). summary와 같은 세 집계를 연 단위로 재사용한다.
export interface AnnualReport {
  year: number
  savingsRate: SavingsRate // 그 해 저축률(투자 포함 토글 반영)
  maturity: MaturityArchiveStats // 그 해 만기(end_date) 수령 누적 통계
  envelopeSpentTotal: number // 그 해 봉투 집행(SPEND) 실지출액(원)
}

// 사이클별 계획 vs 실제 추이. months는 최근 사이클 수(1~36, 기본은 서버 결정 6). 시간순(오래된→최근)으로 온다.
export async function getTrend(months?: number): Promise<TrendPoint[]> {
  const { data } = await api.get<TrendPoint[]>('/reports/trend', {
    params: months != null ? { months } : undefined,
  })
  return data
}

// 저축률·만기 수령 누적·봉투 집행 합계 요약. 신규 사용자도 0 값으로 안전하게 응답한다(RPT-03 빈 상태).
export async function getSummary(): Promise<ReportSummary> {
  const { data } = await api.get<ReportSummary>('/reports/summary')
  return data
}

// 연 단위 결산(RPT-04). year는 필수(2000~현재 연도+1), 범위 밖이면 서버가 VALIDATION_FAILED(400). 데이터 없는 해도
// 0/기본 저축률로 안전하게 응답한다.
export async function getAnnual(year: number): Promise<AnnualReport> {
  const { data } = await api.get<AnnualReport>('/reports/annual', { params: { year } })
  return data
}
