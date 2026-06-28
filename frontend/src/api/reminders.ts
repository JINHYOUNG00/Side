import api from './client'

// 사용자 정의 리마인더 CRUD(NOTI-06). 메모 기반 리마인더를 사용자 정의 주기로 발송받는다.
// 분기 외화 점검은 서버 계산형 판정이라 클라가 다루는 리소스가 없다 — 여기선 사용자 정의 리마인더만.
// 서버 응답: { id, label, intervalMonths, nextRemindDate }.
export interface Reminder {
  id: number
  label: string
  intervalMonths: number
  nextRemindDate: string
}

// 생성·수정 공통 입력(폼). nextRemindDate는 ISO 날짜 문자열(YYYY-MM-DD).
export interface ReminderInput {
  label: string
  intervalMonths: number
  nextRemindDate: string
}

export async function listReminders(): Promise<Reminder[]> {
  const { data } = await api.get<Reminder[]>('/reminders')
  return data
}

export async function createReminder(input: ReminderInput): Promise<Reminder> {
  const { data } = await api.post<Reminder>('/reminders', input)
  return data
}

export async function updateReminder(id: number, input: ReminderInput): Promise<Reminder> {
  const { data } = await api.patch<Reminder>(`/reminders/${id}`, input)
  return data
}

// soft delete — 서버는 status=DELETED로 처리(규칙 5). 응답 204.
export async function deleteReminder(id: number): Promise<void> {
  await api.delete(`/reminders/${id}`)
}
