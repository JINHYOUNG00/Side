import api from './client'

// 배분 항목 CRUD(ITEM-01·09·07, API명세 4장) + 저축 조건부 필드(이율·세금·예상금액 = ITEM-05/06)
// + 외화 적립 도우미(ITEM-04, preview-fx).

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

// 이자 과세 유형(ITEM-05, 서버 TaxType enum과 동일). 세금우대는 농특세 1.4%만.
export const TAX_TYPES = ['NORMAL_15_4', 'PREFERENTIAL', 'TAX_FREE'] as const
export type TaxType = (typeof TAX_TYPES)[number]

// 금액 입력 단위(ITEM-03, 서버 InputCycle enum과 동일). DAILY는 일 금액·빈도로 월 환산(amount)을 서버가 계산한다.
export const INPUT_CYCLES = ['MONTHLY', 'DAILY'] as const
export type InputCycle = (typeof INPUT_CYCLES)[number]

// 일 단위 입력 원본(ITEM-03). DAILY 항목의 보존된 일 금액(원)·빈도(매일/영업일) — 수정 시 프리필용. MONTHLY면 null.
export interface DailyMeta {
  dailyAmount: number
  frequency: FxFrequency
}

// 서버 응답(BudgetItemResponse). 저축 조건부 필드(interestRate·taxType·expectedMaturityAmount)는 비저축
// 항목이면 null. expectedMaturityAmount는 수동 입력 원본값(폼 프리필용) — 보관함의 표시용 해석값과 구분된다.
export interface BudgetItem {
  id: number
  category: Category
  name: string
  amount: number
  accountId: number
  startDate: string // YYYY-MM-DD (Asia/Seoul)
  endDate: string | null
  interestRate: number | null
  taxType: TaxType | null
  expectedMaturityAmount: number | null
  memo: string | null
  sortOrder: number
  // 입력 단위(ITEM-03). amount는 두 경우 모두 월 환산 금액. DAILY면 inputMeta에 원본 일 금액·빈도가 실린다.
  inputCycle: InputCycle
  inputMeta: DailyMeta | null
}

// 생성 입력(MOD-01 폼) — 공통 필드 + 저축 조건부 필드(선택). 저축 항목일 때만 채워 보낸다.
// 일 단위 입력(ITEM-03)이면 amount 대신 inputCycle='DAILY'+inputMeta(일 금액·빈도)를 보내고 서버가 월 환산한다.
export interface BudgetItemInput {
  category: Category
  name: string
  amount?: number
  accountId: number
  startDate: string
  endDate?: string | null
  interestRate?: number | null
  taxType?: TaxType | null
  expectedMaturityAmount?: number | null
  inputCycle?: InputCycle
  inputMeta?: DailyMeta
}

// 수정 입력(ITEM-07 PATCH) — 부분 갱신이 아닌 전체 교체라 endDate/memo도 함께 보낸다.
// 폼이 입력하지 않는 필드는 기존 항목 값을 그대로 실어 보존한다(누락 시 서버에서 null이 됨).
export interface BudgetItemUpdateInput extends BudgetItemInput {
  endDate: string | null
  memo: string | null
}

// 만기금액 미리보기(ITEM-05). 저장 없이 단리 공식 분해(원금·이자·세금·만기 실수령액)를 받는다.
export interface MaturityPreviewInput {
  monthlyAmount: number
  months: number
  interestRate: number
  taxType: TaxType
}
export interface MaturityPreview {
  principal: number
  interest: number
  tax: number
  total: number
}

// 외화 적립 도우미(ITEM-04, 서버 FxFrequency enum과 동일). 매일/영업일 빈도로 월 평균 일수를 환산한다.
export const FX_FREQUENCIES = ['DAILY', 'BUSINESS_DAYS'] as const
export type FxFrequency = (typeof FX_FREQUENCIES)[number]

// 외화 도우미 입력(MOD-01 폼, 투자 카테고리). 통화는 표시용 메타, unitAmount/fxRate는 외화 금액·환율(소수 가능).
export interface FxPreviewInput {
  currency: string
  unitAmount: number
  frequency: FxFrequency
  fxRate: number
}
// 권장 월 이체액(원, 1,000원 단위 올림) + 적용 버퍼율("버퍼 N% 포함" 고지용). 저장은 원화 월액으로만 한다.
export interface FxPreview {
  recommendedMonthlyKrw: number
  bufferRate: number
}

export async function listBudgetItems(): Promise<BudgetItem[]> {
  const { data } = await api.get<BudgetItem[]>('/budget-items')
  return data
}

export async function createBudgetItem(input: BudgetItemInput): Promise<BudgetItem> {
  const { data } = await api.post<BudgetItem>('/budget-items', input)
  return data
}

// 항목 수정(ITEM-07). 기본은 다음 사이클부터 적용 — budget_items 원본만 바뀌고 현재 사이클 스냅샷은 불변.
// applyToCurrentCycle=true면 서버가 현재 사이클 미완료 라인을 재계산한다(완료 라인 보존, 구현규칙 4장).
export async function updateBudgetItem(
  id: number,
  input: BudgetItemUpdateInput,
  applyToCurrentCycle: boolean,
): Promise<BudgetItem> {
  const { data } = await api.patch<BudgetItem>(`/budget-items/${id}`, input, {
    params: { applyToCurrentCycle },
  })
  return data
}

// soft delete — 서버는 status=DELETED로 처리(규칙 5, ITEM-09). 응답 204.
export async function deleteBudgetItem(id: number): Promise<void> {
  await api.delete(`/budget-items/${id}`)
}

// 만기금액 미리보기(ITEM-05). 저장 없는 순수 계산 — 폼의 실시간 "예상 만기금액" 표시에 쓴다.
export async function previewMaturity(input: MaturityPreviewInput): Promise<MaturityPreview> {
  const { data } = await api.post<MaturityPreview>('/budget-items/preview-maturity', input)
  return data
}

// 외화 도우미 미리보기(ITEM-04). 저장 없는 순수 계산 — 폼이 권장 월 이체액을 항목 금액에 채우는 데 쓴다.
export async function previewFx(input: FxPreviewInput): Promise<FxPreview> {
  const { data } = await api.post<FxPreview>('/budget-items/preview-fx', input)
  return data
}

// 일 단위 입력 미리보기(ITEM-03). 일 금액(원)·빈도로 월 환산 금액을 받는다 — 폼의 "월 환산 N원" 표시에 쓴다.
export interface DailyPreviewInput {
  dailyAmount: number
  frequency: FxFrequency
}
export interface DailyPreview {
  monthlyAmount: number
}
export async function previewDaily(input: DailyPreviewInput): Promise<DailyPreview> {
  const { data } = await api.post<DailyPreview>('/budget-items/preview-daily', input)
  return data
}

// 보관함(ITEM-08, SCR-08). 만기·중도해지로 보관(ARCHIVED)된 항목과 만기일·예상/실제 만기금액을 함께 싣는다.
// expectedMaturityAmount는 ITEM-05/06(Phase 5) 미구현이라 현재 항상 null, maturityActualAmount는 미기록 시 null.
export interface ArchivedItem {
  id: number
  category: Category
  name: string
  amount: number
  accountId: number | null
  startDate: string
  endDate: string | null
  expectedMaturityAmount: number | null
  maturityActualAmount: number | null
  memo: string | null
  sortOrder: number
}

// 보관함 이력 통계(MaturityArchiveStats) — 보관 건수·실수령액 기록 건수·만기 수령 누적액(기록분 합).
export interface MaturityArchiveStats {
  archivedCount: number
  recordedCount: number
  totalReceivedAmount: number
}

// GET /budget-items/archive 응답 — 보관 항목 목록 + 누적 통계.
export interface ArchiveResponse {
  items: ArchivedItem[]
  stats: MaturityArchiveStats
}

export async function listArchive(): Promise<ArchiveResponse> {
  const { data } = await api.get<ArchiveResponse>('/budget-items/archive')
  return data
}

// 만기 실수령액 기록(ITEM-08). ACTIVE 항목이면 서버가 ARCHIVED로 전환한다(중도해지). 보관함에선 정정 기록.
export async function recordMaturity(id: number, actualAmount: number): Promise<ArchivedItem> {
  const { data } = await api.patch<ArchivedItem>(`/budget-items/${id}/maturity`, { actualAmount })
  return data
}
