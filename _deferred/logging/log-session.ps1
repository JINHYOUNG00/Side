# Stop / SubagentStop 로깅 훅 (Windows). 세션·서브에이전트 종료 시 logs/날짜.md에 append.
# best-effort — 절대 막지 않음(항상 exit 0). 터미널을 꺼도 기록이 파일로 남는다.
$ErrorActionPreference = "SilentlyContinue"

# 훅 입력(JSON)을 stdin에서 읽음
$raw = [Console]::In.ReadToEnd()
$ev = $null
try { $ev = $raw | ConvertFrom-Json } catch { }

$eventName = if ($ev.hook_event_name) { $ev.hook_event_name } else { "Stop" }
$session   = if ($ev.session_id)      { $ev.session_id }      else { "?" }
$tpath     = $ev.transcript_path
$cwd       = if ($ev.cwd)             { $ev.cwd }             else { (Get-Location).Path }

# logs 디렉터리 (프로젝트 루트 기준)
$logDir = Join-Path $cwd "logs"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$logFile = Join-Path $logDir ((Get-Date).ToString("yyyy-MM-dd") + ".md")
$ts = (Get-Date).ToString("HH:mm:ss")

# transcript에서 마지막 assistant 텍스트(=방금 반환한 요약) 추출 시도
$excerpt = ""
if ($tpath -and (Test-Path $tpath)) {
  try {
    $lines = Get-Content $tpath
    for ($i = $lines.Count - 1; $i -ge 0; $i--) {
      $obj = $null
      try { $obj = $lines[$i] | ConvertFrom-Json } catch { continue }
      if ($obj.type -eq "assistant" -and $obj.message.content) {
        $parts = @()
        foreach ($c in $obj.message.content) {
          if ($c.type -eq "text" -and $c.text) { $parts += $c.text }
        }
        if ($parts.Count -gt 0) { $excerpt = ($parts -join "`n").Trim(); break }
      }
    }
  } catch { }
}

# 너무 길면 자르기
if ($excerpt.Length -gt 4000) { $excerpt = $excerpt.Substring(0, 4000) + " …(생략)" }

# append
$entry = "`n## $ts — $eventName  (session $session)`n"
if ($excerpt) {
  $entry += "`n$excerpt`n"
} else {
  $entry += "`n(요약 추출 실패 — 전체 트랜스크립트: $tpath)`n"
}
Add-Content -Path $logFile -Value $entry -Encoding UTF8
exit 0
