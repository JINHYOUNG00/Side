# 슬림 플랜 — 비판 반영판 운영 가이드

앞서 짚은 문제(게이트 부재·허상 보호·삼중 추적·과한 무게)를 반영해, 기존 키트에서
**지금 쓸 것 / 보류할 것 / 대체된 것**을 정리한다.

## 지금 쓸 것 (Phase 0의 진짜 하네스)
- `CLAUDE.md` (기존 그대로) — 규칙의 정본
- **verify 게이트 (이 키트)** — 완료 정의의 실체:
  - backend: `./gradlew verify` = spotless + checkstyle(now() 검출) + test(ArchUnit·골든 무결성 포함) + build
  - frontend: `npm run verify` = eslint + vue-tsc + vitest + build
  - CI(ci.yml) = 로컬 verify와 동일 (드리프트 금지)
- git + `progress.md` (인계 한 곳)
- 슬래시 커맨드 `/bearings` `/task` `/wrap` — 가벼우니 유지

## 강제가 "훅 → 빌드"로 대체된 것 (더 견고)
- ~~protect-migrations.ps1 훅~~ → **Flyway 체크섬 검증**이 본진.
  적용된 마이그레이션이 변경되면 Flyway가 기동을 거부한다(validate-on-migrate 기본 on).
  application.yml에 명시해 의도를 드러낼 것:
  ```yaml
  spring:
    flyway:
      validate-on-migrate: true
  ```
  훅은 "편집 시점에 미리 알려주는" 보조로 남겨도 되고 빼도 된다 — 본 방어선이 아님.
- ~~settings.json의 골든 deny 글롭~~ → **GoldenFixtureIntegrityTest** (이 키트).
  어떤 경로로 fixture가 바뀌어도 verify가 잡는다. 정당한 갱신만 `./gradlew goldenLock`.

## 보류 (아플 때 추가)
- `architect` 서브에이전트 — owner-only 로직을 실제로 설계하는 날 명시 호출. 상시 비치 불필요.
- `code-reviewer` 서브에이전트 — 리뷰할 코드가 쌓이면 추가. 추가하더라도 Critical
  (금액 타입·KST·골든·범위 침범)만 보게 좁힐 것 — 체크리스트 전체를 훑으면 리뷰 피로로 안 읽게 된다.
- `logs/` 세션 로깅 훅 — 세션 기록을 실제로 잃어 아쉬웠던 뒤에. progress.md+git으로 시작.

## 추적 일원화 (삼중 → 일원)
- 추적기는 **하나만**: feature_list.json 또는 GitHub Issues 중 택1. 백로그 md는 옮기고 나면 폐기.
- feature_list를 쓴다면 `passes: true`의 의미를 좁힐 것 — "에이전트가 끝났다고 말함"이
  아니라 **"verify 안에 해당 기능의 테스트가 존재하고 통과함"**. 장기적으로는 테스트가
  곧 추적기가 되는 방향(기능↔테스트 매핑)이 이상적.

## 적용 순서 (Phase 0 첫 세션)
1. Spring Initializr(Java 21, Gradle, web·jpa·security·validation·oauth2-client) + create-vue로 골격 생성
2. 이 키트 합치기: build.gradle 내용 병합 → `com.example.salary` TODO 전부 본인 패키지로 치환,
   checkstyle.xml·테스트 2개·golden/ 배치, package.json scripts 병합, ci.yml 배치
3. golden fixture의 input을 노션 실데이터로 채우고 `./gradlew goldenLock`
4. `./gradlew verify` && `npm run verify` 초록불 확인 → 이 순간부터 "완료 정의"가 실존
5. CLAUDE.md 실행 명령 사전 TODO를 실제 명령으로 채우고 첫 커밋
6. 이제 기능 작업 시작 (`/task AUTH-01` …)

## 한 줄 요약
하네스의 본체는 의식(커맨드·에이전트·로그)이 아니라 **게이트(verify)** 다.
게이트를 먼저 세우고, 나머지는 아플 때 하나씩.
