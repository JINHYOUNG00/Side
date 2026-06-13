import api from './client'

// 통장 CRUD(SET-04, API명세 4장). 계좌번호 등 금융 식별 정보는 다루지 않는다(규칙 6).
// 서버 응답: { id, name, purpose, bankDeepLink, sortOrder }. purpose/bankDeepLink는 미입력 시 null.
export interface Account {
  id: number
  name: string
  purpose: string | null
  bankDeepLink: string | null
  sortOrder: number
}

// 생성·수정 공통 입력(MOD-03 폼). 빈 선택값은 null로 보낸다.
export interface AccountInput {
  name: string
  purpose: string | null
  bankDeepLink: string | null
}

export async function listAccounts(): Promise<Account[]> {
  const { data } = await api.get<Account[]>('/accounts')
  return data
}

export async function createAccount(input: AccountInput): Promise<Account> {
  const { data } = await api.post<Account>('/accounts', input)
  return data
}

export async function updateAccount(id: number, input: AccountInput): Promise<Account> {
  const { data } = await api.patch<Account>(`/accounts/${id}`, input)
  return data
}

// soft delete — 서버는 is_active=false로 처리(규칙 5). 응답 204.
export async function deleteAccount(id: number): Promise<void> {
  await api.delete(`/accounts/${id}`)
}
