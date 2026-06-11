# Progress Log — 월급 배분 관리 서비스

최신 항목이 위. 한 세션 = 한 항목. 다음 세션(새 컨텍스트)이 이걸 읽고 이어받는다.
작업 전 읽고, 멈추기 전 추가할 것.

<!-- 세션마다 이 템플릿 복사:

## YYYY-MM-DD  —  [요구사항ID] 작업명
- 한 일:      이번 세션에 구현/변경한 것
- 초록불:     ./gradlew verify / npm run verify 통과 여부, 통과한 골든 케이스
- passes 전환: true로 바뀐 feature_list id
- 다음:       다음 최우선 미완료 항목
- 메모:       추가한 Flyway 버전, 새 상수, 결정사항, 주의점
-->

## 2026-06-11  —  [HARNESS-verify] Phase 0 골격 생성 + verify 게이트 가동
- 한 일: zip 해제(D:\01_PROJECT\07_SALARY_APP) + git init. Spring Initializr(Boot 3.5.15·Java 21·Gradle 8.14.5) 골격을 backend/에 병합, `com.example.salary`→`com.ngsoft.salary` 전부 치환. settings.gradle에 foojay-resolver 추가(로컬 JDK 17뿐이라 toolchain이 JDK 21 자동 다운로드). application.yml 작성(flyway validate-on-migrate, datasource는 DB_* 환경변수+로컬 기본값). create-vue로 frontend/ 생성 후 verify 스크립트 병합. golden/maturity-cases.json input을 실데이터로 채움 — 단리→15.4% 공식으로 기대값 3,731,976 / 2,476,986 일치 교차검증. 공개 레포 대비 비식별화: 실수령액·적금 상품명·정밀 지출액을 합산이 맞는 가상 수치 세트로 교체(목업·API명세·개발계획·fixture). docker-compose.yml(PostgreSQL 16) 추가. CLAUDE.md 실행 명령 사전·README 실행 방법 채움.
- 초록불: `npm run verify` 완전 통과(oxlint·eslint 0건, vue-tsc, vitest 1건, build). `./gradlew verify`도 완전 통과 — 소유자가 goldenLock 직접 실행(같은 날)해 golden.sha256 생성 후 BUILD SUCCESSFUL 확인. 골든 케이스: 적금 만기 3,731,976 / 2,476,986.
- passes 전환: HARNESS-verify → true.
- 다음: 첫 커밋·push(origin = github.com/JINHYOUNG00/Side, main) → AUTH-01.
- 메모: ① Initializr 기본 contextLoads 테스트는 DB 필요(JPA datasource)라 제외 — 통합 테스트는 AUTH-01에서 도입. ② 2026 create-vue는 oxlint+eslint 조합에 tsconfig가 project references라 스니펫의 `vue-tsc --noEmit` 대신 `vue-tsc --build` 사용(verify 의미는 동일: 검사 전용). ③ ArchitectureTest domain 규칙에 `allowEmptyShould(true)` 추가 — ArchUnit 0.23+는 빈 매칭 집합이면 실패하므로 domain 패키지 생기기 전에도 초록 유지. ④ fixture 기대값은 구현규칙 1장 공식 기준 — 은행 실수령과 수원 단위 차이 가능(ITEM-05 고지 대상). ⑤ spotlessApply가 키트 테스트 2개를 palantir 스타일로 재포맷함(정상).

## 2026-06-11  —  [통합] 최종 패키지 조립 (리뷰 반영)
- 한 일: docs/아키텍처.md 추가(엔티티는 infra, domain은 완전 순수 — ArchitectureTest의 엄격 규칙과 정합). settings.json deny에 goldenLock 4종 추가(에이전트 셀프 승인 차단 — goldenLock은 소유자 전용). 백로그 md를 docs/_archive/로 이동, 추적기는 feature_list.json로 일원화. README 문서 목록 갱신.
- 초록불: 아직 없음 — 빈 프로젝트.
- 다음: START-HERE 1~8 순서대로 Phase 0 (HARNESS-verify부터).
- 메모: JPA 엔티티는 각 기능의 infra 패키지에 둘 것(아키텍처.md v1.1 개정). goldenLock은 사람이 직접 실행.

## (초기) — 하네스 스캐폴딩 제공됨
- 한 일: feature_list.json, progress.md, .claude/(settings.json·commands·hooks) 생성. CLAUDE.md는 기존 것 유지.
- 초록불: 아직 없음 — 빈 프로젝트, 모든 항목 failing.
- 다음: Phase 0 HARNESS-verify → ENV-setup. CLAUDE.md 실행 명령 사전을 실제 명령으로 채우고, .claude/hooks 경로(골든 fixture·마이그레이션 위치) 확인.
- 메모: 1인 프로젝트라 모든 하네스 파일을 그냥 커밋한다(.local 불필요).
