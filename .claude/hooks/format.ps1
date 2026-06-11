# PostToolUse 포맷터 (Windows/PowerShell). best-effort — Claude를 막지 않음.
$ErrorActionPreference = "SilentlyContinue"

# 편집된 파일 경로: 버전별로 env var가 다르거나 stdin JSON으로 올 수 있어 모두 시도.
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
if (-not $f -or -not (Test-Path $f)) { exit 0 }

switch -Regex ($f) {
  '\.(vue|ts|tsx|js|jsx|json|css|scss|html)$' {
    npx --no-install prettier --write "$f" | Out-Null
    break
  }
  '\.java$' {
    # 자바 포맷은 `./gradlew verify` 안의 spotlessCheck가 강제한다.
    # 편집마다 spotlessApply는 Gradle 기동 비용이 커서 생략 — verify/fix 루프에서
    # 에이전트가 `.\gradlew spotlessApply`를 돌린다.
    break
  }
}
exit 0
