# 작업 백로그 — Phase 0~1

> GitHub Issues로 옮길 초안. 제목 형식: `[요구사항ID] 작업명`. Phase 2 이후는 해당 Phase 진입 시점에 쪼갠다.

## Phase 0 — 셋업 (목표: 로그인 후 빈 대시보드)

### 하네스 (검증 루프 — 에이전트·사람 공용)
- [ ] `./gradlew verify` 구성: test + spotless(포맷) + ArchUnit + build를 한 명령으로
- [ ] `npm run verify` 구성: eslint + vue-tsc + vitest + build
- [ ] ArchUnit 테스트: domain 패키지 프레임워크 의존 금지, 금액 필드 double/float 금지
- [ ] 커스텀 검사: `LocalDate.now()` 직접 호출 검출(checkstyle 정규식 또는 ArchUnit)
- [ ] 골든 fixture: 노션 실데이터 → 폭포 기대값 JSON, 적금 만기(3,731,976 / 2,476,986), PaydayResolver 공휴일·말일 케이스
- [ ] 시드 스크립트: docker compose up 후 실데이터 투입 → 로컬에서 즉시 동작 확인 가능 상태
- [ ] GitHub Actions = 로컬 verify와 동일 검사(드리프트 금지)
- [ ] CLAUDE.md 루트 배치, "완료 정의 = verify 통과" 워크플로로 첫 작업부터 운용

### 하네스 — Claude Code 설정 (.claude/)
- [ ] settings.json 권한: `./gradlew *`, `npm run *`, `docker compose *` 허용 / `git push --force`, `flyway clean` 등 파괴적 명령 차단
- [ ] PostToolUse 훅: 파일 편집 후 자동 포맷(spotless apply / prettier)
- [ ] PreToolUse 훅: 기존 Flyway 마이그레이션 파일(V*.sql) 수정 차단, 골든 fixture 파일 수정 차단
- [ ] 커스텀 커맨드 `/task`: 요구사항 ID 입력 → 정의서 확인 → 구현 → verify → `[ID] 요약` 커밋 워크플로 템플릿
- [ ] CLAUDE.md 하단에 실행 명령 사전 추가: 서버 기동, 단일 테스트 실행, 로그 확인, 시드 재투입 명령어 (Phase 0 완료 시점에 실제 명령으로 채움)

### 환경
- [ ] GitHub 리포지토리 생성, docs/ 입주, 브랜치 전략 결정(main + feature)
- [ ] Spring Boot 3.x 프로젝트 생성(Java 21, Gradle) — web, security, jpa, validation, oauth2-client
- [ ] docker-compose.yml: PostgreSQL 16 + 앱, .env 구성
- [ ] Flyway 셋업, V1__init.sql에 ERD 11개 테이블 마이그레이션 작성
- [ ] Vue 3 + Vite + Pinia + vue-router 프로젝트 생성
- [ ] vue-i18n 셋업, ko.json 골격, 문구 하드코딩 금지 컨벤션 README에 명시
- [ ] 디자인 토큰: CSS 변수(색·간격·폰트·radius) 정의, Pretendard 적용
- [ ] 기본 컴포넌트: Card, MoneyText, BottomSheet, ProgressBar, BottomNav
- [ ] GitHub Actions: 테스트 + 빌드 CI

### 인증
- [ ] [AUTH-01] 카카오 디벨로퍼스 앱 등록, 리다이렉트 URI 설정
- [ ] [AUTH-01] 구글 클라우드 콘솔 OAuth 클라이언트 등록
- [ ] [AUTH-01] Spring OAuth2 Client yml 구성(카카오 수동 공급자 등록)
- [ ] [AUTH-01] OAuthUserInfo 인터페이스 + Kakao/Google/Naver 어댑터(네이버는 비활성)
- [ ] [AUTH-01] 로그인 성공 핸들러 → users upsert → JWT 발급
- [ ] [AUTH-03] unique(provider, provider_id) 동작 확인 테스트
- [ ] [SCR-01] 로그인 페이지, JWT 저장·인터셉터, 가드 라우팅

## Phase 1 — 폭포 (목표: 본인 노션 숫자와 잔액 일치)

### 백엔드
- [ ] [SET-04] accounts CRUD + 소유권 검증 공통 처리
- [ ] [ITEM-01] budget_items CRUD (생성·조회)
- [ ] [ITEM-09] soft delete(status=DELETED) + 조회 필터
- [ ] [ITEM-02] end_date 보유 항목 ARCHIVED 전환 배치(@Scheduled, KST)
- [ ] [FLOW-01] WaterfallService: 카테고리 그룹·소계·잔액 캐스케이드 계산 + 단위 테스트
- [ ] [FLOW-03] 비상금/생활비 분배: EMERGENCY는 일반 항목, LIVING은 나머지 계산값 + users.living_account_id로 LIVING 라인 생성(ERD v1.1 확정)
- [ ] [FLOW-02] overAllocated 판정 + 응답 포함
- [ ] GET /me/waterfall 응답 조립(API명세 3장 형식)

### 프론트
- [ ] [SCR-02] 온보딩 스텝 1(실수령액·월급일·조정 규칙)
- [ ] [SCR-03] 홈: 남는 돈 헤더 + 폭포 리스트 + 카운트업
- [ ] [MOD-01] 항목 폼 v1(공통 필드만 — 저축 조건부 필드는 Phase 5, 외화 도우미는 Phase 3)
- [ ] [MOD-03] 통장 폼
- [ ] [FLOW-02] 초과 경고 배너 + 유연성 순 조정 후보
- [ ] [SCR-07] 전체 탭 골격(통장·항목 목록·설정 진입)

### 검증
- [ ] 본인 노션 데이터 전체 입력 → 모든 단계 잔액이 노션과 일치하는지 확인
- [ ] 폭포 계산 단위 테스트: 빈 항목 / 초과 / 카테고리 누락 케이스
