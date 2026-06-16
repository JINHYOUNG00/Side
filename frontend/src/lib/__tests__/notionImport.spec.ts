import { describe, it, expect } from 'vitest'
import { parseImportTable } from '../notionImport'

describe('parseImportTable (DATA-01 표 파서)', () => {
  it('마크다운 표에서 헤더·구분선을 건너뛰고 데이터 행만 인식한다', () => {
    const text = [
      '| 항목 | 금액 | 주기 |',
      '| --- | --- | --- |',
      '| 도시락 | 22,000원 | 월 |',
      '| 기름값 | 80,000원 | 월 |',
      '| 헬스장 | 39,000원 | 월 |',
    ].join('\n')

    expect(parseImportTable(text)).toEqual([
      { name: '도시락', amount: 22000 },
      { name: '기름값', amount: 80000 },
      { name: '헬스장', amount: 39000 },
    ])
  })

  it('탭 구분(노션·엑셀 복사) 표를 인식한다', () => {
    const text = '도시락\t22000\n기름값\t80000'
    expect(parseImportTable(text)).toEqual([
      { name: '도시락', amount: 22000 },
      { name: '기름값', amount: 80000 },
    ])
  })

  it('금액의 천 단위 콤마·원 표기를 숫자로 정규화한다', () => {
    expect(parseImportTable('정기적금\t1,200,000원')).toEqual([{ name: '정기적금', amount: 1200000 }])
  })

  it('맨 앞 인덱스 번호 칸을 금액으로 오인하지 않는다(최댓값 숫자 칸 선택)', () => {
    // | 1 | 도시락 | 22000 |  → 금액은 22000, 이름은 도시락
    expect(parseImportTable('1\t도시락\t22000')).toEqual([{ name: '도시락', amount: 22000 }])
  })

  it('주기 칸의 작은 숫자("12개월")를 금액으로 오인하지 않는다', () => {
    expect(parseImportTable('적금\t300000\t12개월')).toEqual([{ name: '적금', amount: 300000 }])
  })

  it('금액이 없는 행(헤더만 있는 표 등)은 인식하지 않는다', () => {
    expect(parseImportTable('항목\t금액\t주기')).toEqual([])
  })

  it('금액이 범위(1~10억)를 벗어나면 제외한다', () => {
    // 0원·10억 초과는 서버 검증과 동일하게 후보에서 뺀다.
    expect(parseImportTable('공짜\t0\n초과\t1000000001')).toEqual([])
  })

  it('이름이 50자를 넘으면 제외한다', () => {
    const longName = 'ㄱ'.repeat(51)
    expect(parseImportTable(`${longName}\t10000`)).toEqual([])
    const okName = 'ㄱ'.repeat(50)
    expect(parseImportTable(`${okName}\t10000`)).toEqual([{ name: okName, amount: 10000 }])
  })

  it('구분자가 없는 줄·빈 줄은 무시한다', () => {
    const text = '\n안내문구 한 줄\n\n도시락\t22000\n'
    expect(parseImportTable(text)).toEqual([{ name: '도시락', amount: 22000 }])
  })

  it('콤마는 구분자가 아니라 천 단위 구분자로 본다(콤마 구분 표는 인식 안 함)', () => {
    // "도시락,22,000" 을 콤마로 쪼개면 금액이 망가지므로, 콤마 구분은 지원하지 않는다.
    expect(parseImportTable('도시락,22,000')).toEqual([])
  })

  it('빈 입력은 빈 배열을 반환한다', () => {
    expect(parseImportTable('')).toEqual([])
    expect(parseImportTable('   \n  \n')).toEqual([])
  })

  it('인식 행 수를 100건으로 제한한다', () => {
    const lines = Array.from({ length: 150 }, (_, i) => `항목${i}\t${(i + 1) * 1000}`)
    expect(parseImportTable(lines.join('\n'))).toHaveLength(100)
  })
})
