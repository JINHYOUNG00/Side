// 노션·엑셀 표 텍스트 파서(DATA-01, MOD-07). 붙여넣은 표를 항목 후보 {이름, 금액}로 파싱한다.
// 순수 함수 — Vue·DOM 의존 없음(CLAUDE.md "도메인 계산 로직은 의존성 없는 순수 클래스 + 단위 테스트").
// "완전 자동이 아닌 후보 제시 + 수동 확정"(요구사항 DATA-01)이라 분류·대상 통장·포함 여부는 UI(ImportSheet)가 정한다.

export interface ParsedRow {
  name: string
  amount: number
}

// 서버 검증과 동일한 상한(BudgetItemController NAME_MAX/AMOUNT_MIN/MAX, 구현규칙 5장).
// 이 범위를 벗어나는 행은 인식 대상에서 제외해, 인식 건수 = 곧바로 등록 가능한 건수가 되게 한다.
const NAME_MAX = 50
const AMOUNT_MIN = 1
const AMOUNT_MAX = 1_000_000_000

// 한 번에 인식할 최대 행 수 — 비정상적으로 큰 붙여넣기를 막는 안전장치(서버는 활성 항목 100 상한).
const MAX_ROWS = 100

/**
 * 표 텍스트를 항목 후보 목록으로 파싱한다. 구분자는 탭(노션·엑셀 복사)과 파이프(마크다운 표)만 인정한다 —
 * 콤마는 한국어 천 단위 구분자("22,000")와 충돌하므로 구분자로 쓰지 않는다. 헤더 행·마크다운 구분선
 * (|---|---|)·금액 없는 행은 자연히 걸러진다. 같은 표를 다시 붙여넣어도 결과가 동일하다(순수).
 */
export function parseImportTable(text: string): ParsedRow[] {
  const rows: ParsedRow[] = []
  for (const line of text.split(/\r?\n/)) {
    if (rows.length >= MAX_ROWS) break
    const cells = splitCells(line)
    if (cells.length < 2) continue // 이름 + 금액 두 칸은 있어야 한다
    if (isSeparatorRow(cells)) continue // 마크다운 |---|---| 구분선
    const row = toRow(cells)
    if (row) rows.push(row)
  }
  return rows
}

// 한 줄을 칸으로 나눈다. 탭이 있으면 탭, 없고 파이프가 있으면 파이프 기준. 둘 다 없으면 표 행이 아니다([]).
// 빈 칸은 제거한다 — 마크다운의 양끝 파이프가 만드는 빈 칸("| a | b |" → ['','a','b',''])을 흡수한다.
function splitCells(line: string): string[] {
  const trimmed = line.trim()
  if (!trimmed) return []
  let parts: string[]
  if (trimmed.includes('\t')) parts = trimmed.split('\t')
  else if (trimmed.includes('|')) parts = trimmed.split('|')
  else return []
  return parts.map((c) => c.trim()).filter((c) => c !== '')
}

// 모든 칸이 대시(-)로만 이뤄진 마크다운 정렬 구분선(---, :--, --:, :-:)인지.
function isSeparatorRow(cells: string[]): boolean {
  return cells.every((c) => /^:?-+:?$/.test(c))
}

// 칸들에서 금액과 이름을 뽑는다. 금액 = 유효 범위 안에서 값이 가장 큰 숫자 칸(맨 앞 인덱스 번호나
// "12개월" 같은 주기 칸을 금액으로 오인하지 않게 최댓값을 택한다). 이름 = 금액 칸이 아니면서 숫자 외
// 문자를 포함한 첫 칸. 금액이나 이름을 못 찾거나 이름이 길이 상한을 넘으면 후보로 인정하지 않는다.
function toRow(cells: string[]): ParsedRow | null {
  let amount: number | null = null
  let amountIdx = -1
  for (let i = 0; i < cells.length; i++) {
    const cell = cells[i]
    if (cell === undefined) continue
    const n = parseAmount(cell)
    if (n !== null && (amount === null || n > amount)) {
      amount = n
      amountIdx = i
    }
  }
  if (amount === null) return null

  let name: string | null = null
  for (let i = 0; i < cells.length; i++) {
    if (i === amountIdx) continue
    const cell = cells[i]
    if (cell !== undefined && /\D/.test(cell)) {
      name = cell
      break
    }
  }
  if (name === null || name.length > NAME_MAX) return null
  return { name, amount }
}

// 칸에서 숫자만 남겨 금액으로 해석한다("22,000원" → 22000). 숫자가 없거나 범위를 벗어나면 null.
function parseAmount(cell: string): number | null {
  const digits = cell.replace(/[^0-9]/g, '')
  if (!digits) return null
  const n = Number(digits)
  if (!Number.isInteger(n) || n < AMOUNT_MIN || n > AMOUNT_MAX) return null
  return n
}
