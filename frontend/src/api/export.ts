import api from './client'

// 데이터 내보내기(DATA-02, API명세 7장). GET /export?format=md|csv로 활성 배분 항목을 표 텍스트로 받는다.
// 임포트(DATA-01)와 포맷을 공유해 라운드트립(내보내기→가져오기)이 보장된다 — 받은 텍스트는 그대로 다시 붙여넣어 가져올 수 있다.
export const EXPORT_FORMATS = ['md', 'csv'] as const
export type ExportFormat = (typeof EXPORT_FORMATS)[number]

// 서버가 text/markdown·text/csv 본문을 내리므로 문자열로 받는다(JSON 파싱 안 함).
export async function fetchExport(format: ExportFormat): Promise<string> {
  const { data } = await api.get('/export', { params: { format }, responseType: 'text' })
  return data as string
}
