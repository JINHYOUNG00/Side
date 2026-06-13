import api from './client'

// 배분 항목 CRUD(ITEM-01·09, API명세 4장). MOD-01 v1은 공통 필드(생성·조회·삭제)만 다룬다.
// 저축 조건부 필드(만기일·이율·세금·예상금액 = ITEM-05/06)는 Phase 5, 외화 도우미(ITEM-04)는 Phase 3,
// 수정(PATCH, ITEM-07)은 Phase 2라 의도적으로 제외(백엔드 PATCH 미구현).

// LIVING은 항목 카테고리가 아니다 — 생활비는 폭포 나머지 계산값(서버 Category enum과 동일, ITEM-01).
export const CATEGORIES = [
  'SAVING',
  'INVESTMENT',
  'FIXED',
  'INSURANCE',
  'SUBSCRIPTION',
  'EMERGENCY',
] as const
export type Category = (typeof CATEGORIES)[number]

// 서버 응답(BudgetItemResponse). endDate/memo는 v1 폼에서 입력하지 않아 항상 null로 생성된다.
export interface BudgetItem {
  id: number
  category: Category
  name: string
  amount: number
  accountId: number
  startDate: string // YYYY-MM-DD (Asia/Seoul)
  endDate: string | null
  memo: string | null
  sortOrder: number
}

// 생성 입력(MOD-01 폼) — ITEM-01 공통 필드만. endDate/memo는 보내지 않아 서버에서 null이 된다.
export interface BudgetItemInput {
  category: Category
  name: string
  amount: number
  accountId: number
  startDate: string
}

export async function listBudgetItems(): Promise<BudgetItem[]> {
  const { data } = await api.get<BudgetItem[]>('/budget-items')
  return data
}

export async function createBudgetItem(input: BudgetItemInput): Promise<BudgetItem> {
  const { data } = await api.post<BudgetItem>('/budget-items', input)
  return data
}

// soft delete — 서버는 status=DELETED로 처리(규칙 5, ITEM-09). 응답 204.
export async function deleteBudgetItem(id: number): Promise<void> {
  await api.delete(`/budget-items/${id}`)
}
