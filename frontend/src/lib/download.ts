// 텍스트를 파일로 내려받게 하는 브라우저 부수효과(DATA-02). Blob + 임시 앵커 클릭으로 다운로드한다.
// 순수 로직이 아닌 DOM 부수효과라 호출부(뷰)는 이 함수를 모킹해 동선만 검증한다.
export function downloadTextFile(filename: string, mime: string, text: string): void {
  const blob = new Blob([text], { type: mime })
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}
