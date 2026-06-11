# PreToolUse(Edit|Write): 기존 Flyway 마이그레이션 수정 차단 (Windows/PowerShell).
# 새 V{n}__*.sql 생성은 허용 — 이미 존재하는 파일 편집만 막는다.
# exit 2 = 차단.
$ErrorActionPreference = "SilentlyContinue"

$f = $env:CLAUDE_FILE_PATH
if (-not $f) { $f = $env:CLAUDE_TOOL_INPUT_FILE_PATH }
if (-not $f) {
  $raw = [Console]::In.ReadToEnd()
  if ($raw) {
    try {
      $j = $raw | ConvertFrom-Json
      if ($j.tool_input.file_path) { $f = $j.tool_input.file_path }
      elseif ($j.tool_input.path)  { $f = $j.tool_input.path }
    } catch { }
  }
}
if (-not $f) { exit 0 }

# 경로 구분자 통일 후 매칭
$norm = $f -replace '\\', '/'
if ($norm -match '/db/migration/V[^/]*\.sql$') {
  if (Test-Path $f) {
    [Console]::Error.WriteLine("차단: 기존 Flyway 마이그레이션($f)은 수정 금지. 새 V{n}__설명.sql을 추가하세요 (CLAUDE.md 규칙).")
    exit 2
  }
}
exit 0
