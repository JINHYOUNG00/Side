import api from './client'
import type { Category } from './budgetItems'

// 라이브 폭포 조회(FLOW-02, API명세 3장). GET /me/waterfall — base_income·활성 항목 기준 라이브 계산.
// 사이클 스냅샷이 아니라 현재 상태의 폭포다(사이클 라벨·날짜는 응답에 없음).

// 폭포에 노출되는 항목 1건. expectedMaturityAmount는 ITEM-05(Phase 5) 미구현이라 항상 null.
export interface WaterfallItem {
  id: number
  name: string
  amount: number
  accountId: number
  accountName: string | null // 통장이 비활성화돼 조회 안 되면 null
  endDate: string | null
  expectedMaturityAmount: number | null
}

// 카테고리 그룹 1건 — 소계 + 항목(EMERGENCY·LIVING은 groups에서 제외). 표시 순서로 정렬돼 온다.
export interface WaterfallGroup {
  category: Category
  subtotal: number
  items: WaterfallItem[]
}

// 남는 돈 분배(FLOW-03). emergency + living = remaining(불변식). living은 과배분 시 음수 가능.
export interface WaterfallSplit {
  emergency: number
  living: number
}

// 저축률(SET-02). value는 소수 첫째 자리 비율(%) — 저축액(SAVING + 토글 시 INVESTMENT) ÷ 수입.
// includesInvestment는 투자 포함 토글 값(users 설정). 폭포·리포트가 같은 정의를 공유한다.
export interface SavingsRate {
  value: number
  includesInvestment: boolean
}

// GET /me/waterfall 응답(WaterfallResponse). remaining = income − Σ소계 − envelopeContribution.
// overAllocated = split.living < 0(비상금 포함 배분이 income 초과). envelopeContribution은 봉투(Phase 3)
// 미구현이라 현재 항상 0.
export interface Waterfall {
  income: number
  groups: WaterfallGroup[]
  envelopeContribution: number
  remaining: number
  split: WaterfallSplit
  overAllocated: boolean
  savingsRate: SavingsRate
}

export async function getWaterfall(): Promise<Waterfall> {
  const { data } = await api.get<Waterfall>('/me/waterfall')
  return data
}
