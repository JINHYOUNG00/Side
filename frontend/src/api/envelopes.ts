import api from './client'

// 봉투 CRUD + 지출 처리(ENV-01~05, API명세 4장). 봉투 = 비정기 지출(자동차세·명절 등)을 월할 적립하는 가상 단위.
// 서버 응답(EnvelopeResponse): progressPercent·dDay·monthlyAmount는 컬럼이 아닌 비저장 파생값(ENV-02·03 도메인 계산).
export type EnvelopeStatus = 'ACTIVE' | 'CLOSED' | 'DELETED'

// 지출 부족분 충당 출처(ENV-04, ERD envelope_transactions.shortfall_source). 칩 렌더에 재사용.
export const SHORTFALL_SOURCES = ['LIVING', 'EMERGENCY'] as const
export type ShortfallSource = (typeof SHORTFALL_SOURCES)[number]

export interface Envelope {
  id: number
  accountId: number
  name: string
  targetAmount: number
  savedAmount: number // 트랜잭션 합계 캐시(사용자 비편집)
  nextDueDate: string // YYYY-MM-DD (Asia/Seoul)
  cycleMonths: number | null // null = 일회성
  memo: string | null
  status: EnvelopeStatus
  progressPercent: number // 0~100, saved/target 내림
  dDay: number // next_due_date까지 남은 일수(KST). 0=당일, 음수=경과
  monthlyAmount: number // 이번 사이클 월 적립액 = ceil((target−saved) ÷ 남은 사이클 수)
}

// 생성·수정 공통 입력(MOD-02 폼, 전체 교체). cycleMonths=null이면 일회성.
// savedAmount·status는 서버가 관리하므로 보내지 않는다(ENV-04·05 소관).
export interface EnvelopeInput {
  accountId: number
  name: string
  targetAmount: number
  nextDueDate: string
  cycleMonths: number | null
  memo: string | null
}

// 지출 처리 입력(MOD-04, ENV-04). 차액 분류는 적립액을 아는 서버가 검증한다:
// 부족(actual>saved)→shortfallSource 필수·carryOver 금지, 잉여(actual<saved)→carryOver 필수·shortfallSource 금지, 정확→둘 다 null.
export interface SpendInput {
  actualAmount: number
  shortfallSource: ShortfallSource | null
  carryOver: boolean | null
}

export async function listEnvelopes(): Promise<Envelope[]> {
  const { data } = await api.get<Envelope[]>('/envelopes')
  return data
}

export async function createEnvelope(input: EnvelopeInput): Promise<Envelope> {
  const { data } = await api.post<Envelope>('/envelopes', input)
  return data
}

export async function updateEnvelope(id: number, input: EnvelopeInput): Promise<Envelope> {
  const { data } = await api.patch<Envelope>(`/envelopes/${id}`, input)
  return data
}

// soft delete — 서버는 status=DELETED로 처리(규칙 5). 응답 204.
export async function deleteEnvelope(id: number): Promise<void> {
  await api.delete(`/envelopes/${id}`)
}

// 지출 처리(ENV-04~05) — SPEND 기록·saved_amount 갱신 후, 반복형은 next_due_date를 주기만큼 이동(status ACTIVE 유지),
// 일회성은 status=CLOSED로 종료. 갱신된 봉투를 반환한다(클라가 status로 종료/갱신을 인지).
export async function spendEnvelope(id: number, input: SpendInput): Promise<Envelope> {
  const { data } = await api.post<Envelope>(`/envelopes/${id}/spend`, input)
  return data
}
