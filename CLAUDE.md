# CLAUDE.md

월급 배분 관리 서비스 — 노션으로 하던 월급 배분·비정기 지출 적립을 자동화한 앱.
Spring Boot 3.x (Java 21) + PostgreSQL 16 + Vue 3. 1인 사이드 프로젝트.

## 작업 완료의 정의 (가장 중요)

작업은 다음이 **전부 초록불일 때만** 완료다. 통과 출력 없이 "완료"라고 보고하지 말 것:
- 백엔드: `./gradlew verify` (단위·통합 테스트 + ArchUnit + 포맷 검사 + 빌드)
- 프론트: `npm run verify` (eslint + type check + vitest + build)
- 골든 테스트 통과: 실데이터 fixture(폭포 기대값, 적금 만기 3,731,976원 케이스 등)는 절대 수정 금지 — 깨지면 코드가 틀린 것
- 검증 실패 시: 수정 → 재실행 루프. 같은 종류의 실수가 반복되면 본 문서에 규칙 추가 또는 테스트/린트로 기계화를 제안할 것

## 정본 문서 (작업 전 해당 문서 확인)

- 데이터 모델 정본: `docs/ERD.md` — 테이블·컬럼·제약은 여기만 따른다 (개발계획.md 4장은 구버전 초안)
- 기능 정의: `docs/요구사항정의서.md` — 모든 작업은 요구사항 ID와 연결
- 구현 디테일(반올림, 연동 규칙, 검증, 상수): `docs/구현규칙.md`
- API 형태: `docs/API명세초안.md` (정본은 구현 후 Swagger)
- 화면: `docs/화면흐름도.md`

## 절대 규칙 (일부는 ArchUnit·린트로 기계 강제됨 — 우회 금지)

1. **비범위 침범 금지**: 건별 지출 기록(가계부), 계좌 연동, 투자 평가액/수익률 추적, 금융상품 추천, 커뮤니티 — 이 방향의 코드·필드·제안 일절 금지
2. **금액은 long 원 단위.** double/float 금지 [ArchUnit 강제]. 반올림은 구현규칙.md 1장
3. **날짜 연산은 전부 Asia/Seoul.** `LocalDate.now()` 직접 호출 금지 [린트 강제], `Clock` 주입
4. **스냅샷 불변**: cycles/plan_lines의 과거 데이터는 수정하지 않는다. 변경은 구현규칙.md 4장 재생성 절차로만
5. **soft delete**: budget_items·envelopes는 status, accounts는 is_active. 물리 삭제는 회원 탈퇴 cascade뿐
6. **금융 식별 정보(계좌번호 등) 저장 금지** — 컬럼 추가도 금지
7. **문구 하드코딩 금지**: Vue는 vue-i18n 키, 서버 응답은 에러 코드·구조화 데이터만 (문장 생성 금지)
8. **배치·생성 로직은 멱등** — unique 제약 + 존재 시 스킵
9. **domain 패키지는 프레임워크 의존 금지** [ArchUnit 강제] — 순수 자바, 단위 테스트 필수

## 코드 컨벤션

- 백엔드 패키지: `com.{이름}.salary` 아래 도메인별 — `auth`, `account`, `budgetitem`, `envelope`, `cycle`, `checkin`, `suggestion`, `notification`, `common`
- 도메인 계산 로직(폭포, 만기금액, PaydayResolver, 월할 적립)은 **의존성 없는 순수 클래스**로 분리하고 단위 테스트 필수
- Flyway 마이그레이션: `V{n}__설명.sql`, 기존 파일 수정 금지(새 버전 추가)
- 커밋 메시지: `<type>(<scope>): <subject>` — type은 fix/feat/doc/test/refactoring/etc, scope에는 가능하면 요구사항 ID(생략 가능), subject 한 줄. body는 빈 줄 후 작성(동기·전후 차이, 제목으로 충분하면 생략). 예: `feat(ENV-04): 봉투 지출 처리 API`
- 테스트: 도메인 로직은 JUnit 단위 테스트, API는 주요 흐름만 통합 테스트. 실데이터 검증 케이스(적금 만기 3,731,976원 등)는 구현규칙·ERD 참조

## 작업 분담 (중요)

다음 영역은 **소유자가 직접 작성하거나 페어로만 진행** — 통째로 구현해서 제출하지 말 것. 요청 시 설계 리뷰·테스트 케이스 제안·부분 보조만:
- WaterfallService(폭포 계산), PaydayResolver, 사이클 스냅샷 생성, 보정 제안 룰

다음은 위임 환영: 엔티티·리포지토리, CRUD 컨트롤러/DTO, Flyway SQL, 검증, Vue 폼·리스트 UI, 테스트 보일러플레이트.

## UI

모바일 퍼스트(380~430px), Pretendard, 토스류 절제 스타일. 시각 정본은 `docs/mockups/화면설계.html` — 디자인 토큰(색·radius·타이포)과 화면별 레이아웃을 여기서 따른다 (UI 라이브러리 사용 금지, 토큰은 CSS 변수화). 색: 파랑=배분/주요 동작, 보라=봉투/제안, 초록=완료, 빨강=초과.

## 실행 명령 사전 (막히면 여기부터)

```bash
# (Windows PowerShell에서는 ./gradlew 대신 .\gradlew.bat)
# DB 기동:           docker compose up -d            # PostgreSQL 16 (앱 컨테이너는 추후)
# 백엔드만:          cd backend && ./gradlew bootRun  # DB 먼저 기동할 것
# 프론트 개발 서버:   cd frontend && npm run dev
# 검증(완료 정의):    cd backend && ./gradlew verify   /   cd frontend && npm run verify
# 단일 테스트:        ./gradlew test --tests "*ArchitectureTest*"
# 포맷 적용:          ./gradlew spotlessApply          /   npm run lint:fix
# 골든 승인(소유자):  ./gradlew goldenLock             # 에이전트는 deny — 사람이 직접 실행
# 로그 확인:          docker compose logs -f db
# 시드 재투입:        (Phase 1에서 시드 스크립트 작성 후 채움)
# DB 초기화:          docker compose down -v && docker compose up -d  (운영 금지, 로컬 전용)
```
