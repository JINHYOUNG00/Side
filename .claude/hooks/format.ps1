# PostToolUse 포맷터 (Windows/PowerShell). best-effort — Claude를 막지 않음(항상 exit 0).
# 이 프로젝트는 prettier가 없다(oxlint+eslint). 프론트 파일은 프로젝트 fixer로 정렬:
#   oxlint --fix → eslint --fix (= npm run lint:fix와 동일 도구, CLAUDE.md 실행 명령 사전).
# eslint flat config는 frontend/에서 실행해야 잡히므로 그 디렉터리로 이동해 단일 파일만 처리.
# 자바는 ./gradlew verify 안의 spotlessCheck가 강제(편집마다 Gradle 기동은 비싸 생략).
$ErrorActionPreference = "SilentlyContinue"

# 편집된 파일 경로: 버전별 env var 차이/stdin JSON 모두 시도.
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

$full = (Resolve-Path $f).Path
$frontend = (Resolve-Path (Join-Path $PSScriptRoot "..\..\frontend")).Path

switch -Regex ($full) {
  '\.(vue|ts|tsx|js|jsx|mjs|cjs)$' {
    # frontend 하위 파일만 — flat config가 cwd 기준이라 frontend에서 실행한다.
    if ($frontend -and $full.StartsWith($frontend, [System.StringComparison]::OrdinalIgnoreCase)) {
      Push-Location $frontend
      npx --no-install oxlint "$full" --fix 2>&1 | Out-Null
      npx --no-install eslint "$full" --fix 2>&1 | Out-Null
      Pop-Location
    }
    break
  }
  '\.java$' {
    # spotlessCheck(verify)가 강제 — 여기선 미처리.
    break
  }
}
exit 0
