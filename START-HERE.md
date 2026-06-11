# START HERE — 월급 배분 관리 서비스 최종 패키지

이 zip 하나에 프로젝트 시작에 필요한 전부가 들어 있다(Windows 노트북, 1인 프로젝트 기준).
지금까지 만든 조각들을 슬림 플랜 기준으로 통합한 최종본.

## 패키지 지도
```
CLAUDE.md, README.md, docs/        기획 정본 (요구사항·ERD·구현규칙·API·화면·백로그·목업)
backend/                            verify 게이트 (등뼈)
  build.gradle                        ./gradlew verify 정의 + goldenLock 태스크 — Initializr 골격에 병합
  config/checkstyle/checkstyle.xml    now() 직접 호출 검출 (규칙 3)
  src/test/java/...                   ArchitectureTest (규칙 2·9) + GoldenFixtureIntegrityTest (골든 불변)
  src/test/resources/golden/          골든 fixture — input을 노션 실데이터로 채울 것
frontend/package-verify-snippet.json  npm run verify 스크립트 — create-vue 골격에 병합
.github/workflows/ci.yml            CI = 로컬 verify와 동일 (드리프트 금지)
feature_list.json, progress.md      추적·인계 (추적기는 이거 하나만 쓸 것)
.claude/
  settings.json                     권한 allow/deny + 포맷·마이그레이션 훅 (Windows)
  commands/                         /bearings /task /wrap
  hooks/                            format.ps1, protect-migrations.ps1 (보조 — 본 방어선은 빌드)
.gitignore                          시작용
SLIM-PLAN.md                        무엇을 지금 쓰고 무엇을 보류하는지 + 근거
_deferred/                          ★ 보류분 — 아플 때 켠다 (아래 참조)
  agents/                           code-reviewer.md, architect.md (+ 노트)
  logging/log-session.ps1           세션·서브에이전트 로그 → logs/날짜.md
```

## 첫 세션 순서 (Phase 0)
1. 빈 폴더에 이 zip을 풀고 `git init` → 첫 커밋.
2. 골격 생성: Spring Initializr(Java 21, Gradle, web·jpa·security·validation·oauth2-client)를
   `backend/`에, create-vue(TS·Pinia·Router·Vitest·ESLint)를 `frontend/`에.
3. 병합: backend/build.gradle 내용을 골격 build.gradle에 합치고 **`com.example.salary` TODO를
   전부 본인 패키지로 치환**(테스트 2개의 package 선언 포함). frontend snippet의 scripts를
   package.json에 합침.
4. `application.yml`에 Flyway 검증 명시(마이그레이션 불변의 본 방어선):
   ```yaml
   spring:
     flyway:
       validate-on-migrate: true
   ```
5. golden/maturity-cases.json의 input을 노션 실데이터로 채우고 `./gradlew goldenLock`.
6. `./gradlew verify` && `npm run verify` 초록불 확인 — 이 순간부터 "완료 정의"가 실존한다.
7. CLAUDE.md "실행 명령 사전" TODO를 실제 명령으로 채우고 커밋.
8. 이후 매 작업: `/bearings` → `/task AUTH-01` → `/wrap`. 작업 하나 끝나면 `/clear`.

## 보류분 켜는 시점 (_deferred/)
- **code-reviewer**: 리뷰할 코드가 쌓였을 때. `_deferred/agents/code-reviewer.md`를
  `.claude/agents/`로 이동. 켜더라도 Critical(금액·KST·골든·범위)만 보게 좁힐 것.
- **architect**: WaterfallService 등 owner-only 로직을 설계하는 날. 같은 방식으로 이동 후
  `@architect` 명시 호출.
- **세션 로깅**: 세션 기록을 잃어 아쉬웠던 뒤에. `_deferred/logging/log-session.ps1`을
  `.claude/hooks/`로 옮기고 settings.json hooks에 추가:
  ```json
  "Stop":        [{ "hooks": [{ "type": "command", "command": "powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/log-session.ps1" }] }],
  "SubagentStop":[{ "hooks": [{ "type": "command", "command": "powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/log-session.ps1" }] }]
  ```
  (logs/는 .gitignore에 이미 있음)

## 기억할 한 줄
하네스의 본체는 게이트(verify)다. 골든이 깨지면 코드가 틀린 것이고,
fixture의 정당한 갱신만 `./gradlew goldenLock`으로 승인한다.
