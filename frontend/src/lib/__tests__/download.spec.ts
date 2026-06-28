import { describe, it, expect, vi, afterEach } from 'vitest'
import { downloadTextFile } from '../download'

// 다운로드 부수효과(DATA-02) — jsdom에 없는 URL.createObjectURL을 스텁하고, 임시 앵커가 올바른 파일명·href로
// 클릭되는지 본다. 실제 파일 저장은 검증할 수 없으므로 동선(앵커 생성·클릭·정리)만 확인한다.
describe('downloadTextFile', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('Blob URL로 앵커를 만들어 지정 파일명으로 클릭하고 정리한다', () => {
    const createUrl = vi.fn<() => string>(() => 'blob:fake')
    const revokeUrl = vi.fn<(url: string) => void>()
    vi.stubGlobal('URL', { createObjectURL: createUrl, revokeObjectURL: revokeUrl })

    const anchor = document.createElement('a')
    const click = vi.spyOn(anchor, 'click').mockImplementation(() => {})
    vi.spyOn(document, 'createElement').mockReturnValue(anchor)

    downloadTextFile('salary-export.csv', 'text/csv;charset=utf-8', 'name,category,amount\n')

    expect(createUrl).toHaveBeenCalledOnce()
    expect(anchor.download).toBe('salary-export.csv')
    expect(anchor.getAttribute('href')).toBe('blob:fake')
    expect(click).toHaveBeenCalledOnce()
    expect(revokeUrl).toHaveBeenCalledWith('blob:fake')
  })
})
