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

## 2026-06-13  —  [SET-04 완료] 통장 CRUD + 소유권 검증 공통 처리 (Phase 1 진입)
- 한 일: `account` 패키지 신설 — `account/infra/Account`(엔티티, ERD accounts 1:1: name·purpose·bank_deep_link·sort_order·is_active, 계좌번호류 컬럼 없음/규칙 6) + `AccountRepository`(모든 조회에 user_id를 거는 파생 쿼리: 활성 목록 정렬·소유권+활성 동시검증 fetch·활성 count·maxSortOrder) + `AccountService`(CRUD 유스케이스, 소유권 단일 관문 `ownedOrThrow` → 미소유·삭제·부재 전부 NOT_FOUND로 존재 비노출) + `AccountController`(`GET|POST /api/v1/accounts`, `PATCH|DELETE /accounts/{id}`, principal=userId는 `@AuthenticationPrincipal Long`, POST 201·DELETE 204, DTO는 nested record + bean validation). `common/ErrorCode`에 `ACCOUNT_LIMIT_EXCEEDED(409)` 추가. soft delete=is_active false(규칙 5, 행 잔존 → 과거 스냅샷·plan_lines 참조 유지), 목록·조회는 활성만. 검증(구현규칙 6장): name 1~50자·purpose≤100·deepLink≤500·**활성 통장 20개 상한**. sortOrder는 생성 시 서버가 maxSortOrder+1로 끝자리 부여, PATCH는 값이 올 때만 재정렬.
- 초록불: `./gradlew verify` BUILD SUCCESSFUL — **AccountIntegrationTest 13건**(Testcontainers postgres:16-alpine + Flyway V1까지 실검증): 생성·sortOrder 0/1 부여, **소유권 위반 PATCH/DELETE 차단(타인 통장 → NOT_FOUND, 원본 불변)**, 타인 통장 목록 비노출, 수정 반영, **soft delete 후 목록 제외·행 잔존(is_active=false)**, 삭제분 재수정 거부, 20개 상한 초과 409, 삭제로 자리 나면 재생성 가능, 빈/51자 이름 VALIDATION_FAILED, 토큰 없이 401. 기존 단위·골든·ArchUnit·AuthIntegrationTest 전부 유지. `npm run verify` 통과(프론트 미변경, 회귀 없음).
- passes 전환: **SET-04 → true**.
- 다음: 짝 프론트 **MOD-03 통장 폼**(SET-04 의존) 또는 백엔드 **ITEM-01 budget_items CRUD**(account_id FK로 SET-04 위에 얹음). 온보딩 **SCR-02**도 후보(users PATCH/living_account_id는 별개 — SET-01 영역).
- 메모: ① 소유권 검증은 "리포지토리 쿼리에 user_id를 항상 포함 + 서비스 단일 관문"의 2중 강제 — 컨트롤러가 우회 불가. 후속 도메인(budget_items·envelopes)도 같은 패턴 권장(공통화 여지: 향후 base 소유권 헬퍼). ② `@AuthenticationPrincipal Long userId`는 JwtAuthenticationFilter가 principal에 Long userId를 넣어둔 덕에 바로 주입됨. ③ ACCOUNT_LIMIT_EXCEEDED는 409(CONFLICT)로 매핑 — CYCLE_ALREADY_EXISTS와 동급의 상태 충돌. ④ 통합 테스트에서 FK(accounts.user_id→users) 때문에 사용자 먼저 persist 후 jwtProvider.issue(id)로 실토큰 발급. ⑤ Flyway 새 버전 없음(accounts는 V1에 이미 있음). ⑥ 골든 케이스 무수정 통과. ⑦ JAVA_HOME 여전히 jdk-11 부재 경로 → `$env:JAVA_HOME='C:/Program Files/Java/jdk-22'` 우회로 gradle 실행(영구 수정 권장).

## 2026-06-13  —  [SCR-01 완료] 로그인 프론트 + 패키지 리네임(com.ngsoft→com.jinhyoung)
- 한 일: ① **SCR-01** — AUTH-01 백엔드(`POST /api/v1/auth/{provider}`→`{accessToken,isNewUser}`)와 짝인 프론트 로그인 흐름. `LoginView`(/login: 카카오·구글 버튼, 워드마크·태그라인·개인정보 안내, 동의 거부 시 `?error=CODE`로 복귀해 코드→i18n 표시), `AuthCallbackView`(/login/callback/:provider: redirect로 받은 code를 서버와 교환→세션 저장→홈, 실패·미지원·code없음은 각 에러코드로 로그인 복귀), `api/client`(axios 인스턴스 baseURL `/api/v1` + 요청 인터셉터 Bearer 부착 + 응답 인터셉터 401→logout·로그인 이동/그 외 `ApiError(code)` 정규화), `api/oauth`(공급자 authorize URL 빌더, client_id는 `VITE_*`/비밀키는 서버 전용), `stores/auth`(Pinia: token·isNewUser·isAuthenticated, JWT만 localStorage 영속), router 가드(비로그인→/login 차단, 로그인 상태의 /login→홈, 로그인·콜백은 `chrome:false`로 BottomNav 숨김), i18n ko/en(`login.*`·`errors.{code}`). 테스트 20건 신규. ② **패키지 리네임** — 초기 골격 때 회사 이메일 도메인(ngsofti.com)에서 따온 `com.ngsoft.salary`를 개인 프로젝트답게 `com.jinhyoung.salary`로 정정(디렉터리 git mv 이력 보존, package·import·ArchUnit 기준 문자열·build.gradle group 일괄 치환, 40파일).
- 초록불: `npm run verify` 통과(lint 0/0·type-check·**vitest 23/23**·build) + `./gradlew clean verify` BUILD SUCCESSFUL(단위·골든·ArchUnit + **Testcontainers 통합 테스트** 패키지 이동 후 클린 재빌드 실검증). 브라우저 검증: 미인증 `/`→`/login` 리다이렉트, 로그인 페이지 렌더·콘솔 에러 0, `?error=OAUTH_EXCHANGE_FAILED`→i18n 문구 매핑 확인.
- passes 전환: **SCR-01 → true**.
- 다음: **SCR-02 온보딩**(실수령액·월급일·조정 규칙 입력 — 첫 가입 흐름, 로그인 직후 분기) 또는 백엔드 **SET-04**(accounts CRUD, Phase 1 진입).
- 메모: ① **axios 의존성 추가**(아키텍처.md가 axios 인스턴스+JWT 인터셉터를 정본으로 규정). `frontend/.env.example`에 `VITE_KAKAO_CLIENT_ID`/`VITE_GOOGLE_CLIENT_ID` 문서화(실값은 `.env.local`, gitignore). ② 콜백 성공 시 현재 항상 홈으로 — 첫 가입(`isNewUser`) 온보딩 분기는 SCR-02에서 라우트 연결 예정(신호는 `auth.isNewUser`에 보관). ③ 실제 OAuth 공급자 왕복은 client ID 설정 필요 — 라우팅·교환 흐름은 통합 테스트(mock 교환)+백엔드 AUTH-01 실검증으로 커버. ④ **테스트 함정**: 싱글톤 라우터+다중 pinia 테스트에서 `mount`의 pinia 플러그인이 전역 active pinia를 갱신 안 함 → 단언 시 `useAuthStore(pinia)`로 인스턴스 명시. 동일 경로 push는 내비게이션 무시(가드 테스트는 다른 경로에서 출발). fire-and-forget `router.replace`는 최종 currentRoute 대신 spy로 호출 의도 검증. ⑤ **리네임 함정**: git-bash `sed -i`가 CRLF→LF로 바꿔 spotless 빨간불 → `spotlessApply`로 정정(round-trip 상쇄, 줄바꿈 diff 0). ⑥ progress.md 과거 로그의 `com.ngsoft` 프로즈는 히스토리라 보존. ⑦ Flyway 새 버전 없음.

## 2026-06-13  —  [AUTH-01 완료 + AUTH-03] 로그인 흐름 전체 + 스테이트리스 보안
- 한 일: 기존 코어(어댑터 3종·JwtProvider) 위에 로그인 흐름을 끝까지 배선. ① `OAuthClient` 포트 + `RestClientOAuthClient`(code→토큰 교환→userinfo→어댑터 정규화, 실패 시 OAUTH_EXCHANGE_FAILED). ② `AuthService`: `(provider, provider_id)`로 upsert + JWT 발급, 신규는 isNewUser=true. ③ `AuthController` `POST /api/v1/auth/{provider}` → `{accessToken, isNewUser}`. ④ `User` 엔티티(**user.infra**)·`UserRepository`(findByProviderAndProviderId). ⑤ `SecurityConfig` 스테이트리스(세션 없음, /api/v1/auth/** 공개, 그 외 JWT 필수) + `JwtAuthenticationFilter`(principal=userId) + `JwtAuthenticationEntryPoint`(401 {code:UNAUTHORIZED}). ⑥ `common`: `ErrorCode`/`ApiException`/`ApiErrorResponse`/`GlobalExceptionHandler`({code,params}), `TimeConfig`(KST `Clock` 빈). ⑦ `AuthProperties`(app.jwt·app.oauth), application.yml에 dev 기본값(시크릿은 env 주입). ⑧ build.gradle: spring-security-test + Testcontainers(postgresql·junit-jupiter·spring-boot-testcontainers).
- 초록불: `./gradlew verify` BUILD SUCCESSFUL — **AuthIntegrationTest 9건**(신규/재로그인 멱등/AUTH-03 공급자분리/닉네임 폴백/비활성 naver/미지원 provider/code 검증실패/보안 401·통과) + 기존 단위·골든·ArchUnit 전부. 통합 테스트가 Testcontainers PostgreSQL(postgres:16-alpine)을 띄워 **Flyway V1 + ddl-auto=validate까지 실검증**. `npm run verify` 통과(프론트 미변경).
- passes 전환: **AUTH-01 → true, AUTH-03 → true**.
- 다음: **SCR-01**(로그인 페이지 + JWT 저장·axios 인터셉터 + 가드 라우팅, 프론트) — 이번 백엔드와 짝. 또는 Phase 1 SET-04(accounts CRUD)/ITEM-01.
- 메모: ① **신규 사용자 플레이스홀더 결정**: ERD users의 base_income/payday/payday_adjustment가 NOT NULL인데 OAuth 가입은 온보딩 전이라 값이 없다 → `0 / 1 / NONE`으로 행 생성하고 isNewUser=true로 온보딩 유도(온보딩에서 덮어씀). User.java에 상수로 명시. ② 닉네임 NOT NULL인데 동의 거부 시 null → 이메일 로컬파트 → `provider_id` 순 폴백(데이터 기본값, UI 문구 아님). ③ 통합 테스트는 Testcontainers 채택(CI ci.yml에 PG 서비스 없음 — ubuntu 러너 docker로 컨테이너 기동). ④ JWT dev 시크릿은 32바이트↑ placeholder를 yml에 둠(실값은 JWT_SECRET 등 env). ⑤ JAVA_HOME이 여전히 jdk-11(부재) 가리킴 → gradle 호출 시 `$env:JAVA_HOME='...jdk-22'` 임시 지정으로 우회(영구 수정 권장). ⑥ Flyway는 새 버전 추가 없음 — V1 그대로 사용.

## 2026-06-13  —  [ENV-setup 완료] Docker/WSL2 셋업 + Flyway V1 실검증
- 한 일: 로컬에 Docker Desktop 설치(winget) + WSL2 설치(wsl --install) + 재부팅으로 엔진 기동. `docker compose up -d`로 PostgreSQL 16 컨테이너(salary-db) 기동, `./gradlew bootRun`으로 Flyway V1 실적용 검증.
- 초록불: Flyway "Successfully validated 1 migration" → "Migrating schema public to version 1 - init" → "Successfully applied". DB에서 \dt로 ERD 11테이블 전부 확인(+flyway_schema_history), flyway_schema_history version 1 success=t. ENV-setup의 마지막 미검증 항목(백엔드 DB 기동) 해소.
- passes 전환: **ENV-setup → true**.
- 다음: AUTH-01 백엔드 마무리 — User 엔티티·UserRepository, OAuth 코드 교환 클라이언트(공급자 secret), AuthService(upsert+JWT), POST /api/v1/auth/{provider}, SecurityConfig(스테이트리스+JWT 필터), 로그인 흐름 통합 테스트. AUTH-03(provider,provider_id 분리)도 함께. (코어 어댑터·JwtProvider는 이미 완료.)
- 메모: ① bootRun이 "Port 8080 already in use"로 종료했으나 이는 마이그레이션 이후 웹서버 바인딩 단계 — V1 적용은 그 전에 성공. 8080 점유 프로세스 정리 필요(통합 테스트는 random port라 무관). ② docker는 PATH에 `C:\Program Files\Docker\Docker\resources\bin` 추가 또는 새 셸 필요. ③ Windows 11 Home은 Hyper-V 없어 Docker가 WSL2 필수였음. ④ salary-db 컨테이너는 띄워둔 상태 — AUTH-01 작업에 사용.

## 2026-06-13  —  [AUTH-01 코어] OAuth 정규화 어댑터 + JwtProvider (docker-free 부분)
- 한 일: AUTH-01 중 DB 없이 완전 초록불 가능한 코어. `auth` 패키지: `OAuthProvider`(KAKAO/GOOGLE enabled, NAVER 비활성 + from() 파싱), `OAuthUserInfo`(정규화 record), `OAuthAttributesMapper` + 카카오/구글/네이버 어댑터 3종(공급자별 JSON Map → 정규화, 순수). `JwtProvider`(HS256, subject=userId, 발급·검증, **Clock 주입**으로 만료 결정론 — 규칙 3). build.gradle에 jjwt 0.12.6(api+impl+jackson) 추가. 단위 테스트: 어댑터 6건(중첩 구조·동의거부 null·필수 식별자 거부·provider 파싱·네이버 비활성), JwtProvider 3건(복원·만료 거부·위조 거부).
- 초록불: `./gradlew verify` BUILD SUCCESSFUL. 신규 9건 포함 전체 통과. (프론트 미변경.)
- passes 전환: 없음 — AUTH-01은 **코어만**. 남은 부분: 코드 교환 OAuth 클라이언트(공급자 secret 필요), 로그인 성공 → users upsert(User 엔티티·repo·DB), `POST /api/v1/auth/{provider}` 컨트롤러, Security 스테이트리스+JWT 필터, **로그인 흐름 통합 테스트(DB 필요 → docker)**. AUTH-03(unique(provider,provider_id) 분리)은 upsert 단계에서 함께.
- 다음: (docker 확보 후) ENV-setup V1 bootRun 검증 → AUTH-01 나머지(엔티티·upsert·컨트롤러·security·통합테스트). 통합 테스트 도입 시 V1 마이그레이션도 함께 실검증됨.
- 메모: ① auth 코어는 com.ngsoft.salary.auth(=..domain.. 아님)라 jjwt 의존 무방. 순수 변환이라 단위 테스트만으로 충분. ② JWT는 java.util.Date를 쓰지만 Clock.instant()로만 생성 — now() 직접 호출 없음(checkstyle 통과). ③ HS256 키 32바이트↑ 필요 — 테스트 시크릿도 그 길이. ④ API 형태: POST /api/v1/auth/{provider} {code} → {accessToken, isNewUser} (API명세 2장).

## 2026-06-13  —  [ENV-setup 대부분] Flyway V1 + 프론트 기반(i18n·토큰·컴포넌트·대시보드)
- 한 일: ① 백엔드 `db/migration/V1__init.sql` — ERD v1.2의 11테이블(users/accounts/budget_items/envelopes/envelope_transactions/cycles/plan_lines/check_ins/suggestions/notification_logs/holidays). 금액 bigint, 날짜 date, 시각 timestamptz, enum류 varchar, jsonb(input_meta·payload). 순환 FK(users.living_account_id↔accounts)는 ALTER로 해소, 멱등 unique 제약 포함. ② 프론트: vue-i18n(legacy:false·globalInjection) + locales/ko·en.json, styles/tokens.css(화면설계.html 토큰 1:1·Pretendard), components/base 5종(Card·MoneyText·ProgressBar·BottomSheet·BottomNav), App 셸(모바일 퍼스트 430px)+빈 대시보드 HomeView, 라우터 정리, create-vue 스캐폴드 잔재 제거. base 컴포넌트 한정 multi-word 규칙 off. MoneyText 단위 테스트 3건.
- 초록불: `./gradlew verify` BUILD SUCCESSFUL, `npm run verify` 통과(lint 0/0·type-check·vitest 3/3·build). 프론트 dev 서버 브라우저 기동 확인 — 빈 대시보드 렌더, 콘솔 에러 0, $t 템플릿 주입 정상.
- passes 전환: 없음 — ENV-setup은 **대부분 완료**, flag는 false 유지. 미검증 1건: 백엔드가 PG에 Flyway V1을 실제 적용하며 뜨는지(`docker compose up -d && ./gradlew bootRun`). 이 환경엔 docker/psql이 없어 마이그레이션 런타임 적용을 못 돌림. CI(ci.yml)는 이미 양쪽 verify를 그대로 수행 — ENV-setup의 CI 조건 충족.
- 다음: (소유자) docker 기동 후 bootRun으로 V1 적용 확인 → 문제없으면 ENV-setup passes:true. 그 후 AUTH-01(OAuth2+JWT) 또는 SET-04(accounts CRUD). HARNESS-golden 잔여(waterfall/payday fixture)도 여전히 소유자 단계.
- 메모: ① V1은 docker 부재로 런타임 미적용 — 첫 bootRun이 실질 검증(ddl-auto=validate라 엔티티 없는 현재는 통과). SQL 오타 리스크 있으니 bootRun 로그 확인할 것. ② launch.json(.claude/) 추가 — preview용 frontend dev 서버(port 5173). ③ vue/multi-word-component-names가 flat/essential에 포함돼 'Card'가 걸림 → base 디렉터리 한정 off. ④ Pretendard는 tokens.css에서 CDN @import(추후 self-host 검토).

## 2026-06-13  —  [HARNESS-golden 부분] 만기 골든 테스트 + MaturityCalculator
- 한 일: 잠긴 fixture(maturity-cases.json)를 실제로 강제하는 골든 테스트를 만들었다. ① `budgetitem/domain`에 순수 클래스 추가 — `MaturityCalculator`(단리: 이자=월납입×연이율×n(n+1)/2÷1200 → 원미만 HALF_UP, 세금=이자×세율 → 원미만 HALF_UP, 만기=원금+이자−세금), `MaturityInput`(record, 검증 포함), `MaturityResult`(원금·이자·세금·total 분해), `TaxType`(NORMAL_15_4=0.154, TAX_FREE). 전부 BigDecimal/long(double 금지). ② `golden/MaturityGoldenTest` — fixture를 @TestFactory로 읽어 두 실데이터 케이스(3,731,976 / 2,476,986)를 계산기가 재현하는지 검증. Jackson 의존이라 ..domain.. 밖 golden 패키지에 둠(ArchUnit이 테스트도 스캔). ③ `MaturityCalculatorTest` 단위 테스트 — 비과세·세금 HALF_UP(808.5→809)·입력 검증 경계.
- 초록불: `./gradlew verify` BUILD SUCCESSFUL. MaturityGoldenTest 2/2(만기 케이스), MaturityCalculatorTest 3/3, RuleEnforcementTest 3/3, 골든 무결성 통과. `npm run verify`는 직전 세션과 동일(미변경).
- passes 전환: 없음 — HARNESS-golden은 **부분 완료**. 만기 골든은 됐으나 waterfall-cases.json·payday-cases.json·시드 스크립트가 남음. 그 부분은 owner_only(WaterfallCalculator·PaydayResolver는 소유자 작성) + 기대값이 owner 실데이터 + 새 fixture는 goldenLock(소유자 전용) + 시드는 스키마(ENV-setup의 Flyway V1) 필요 → 협의·소유자 단계.
- 다음: HARNESS-golden 잔여(소유자와): waterfall/payday fixture 작성 → goldenLock. 또는 ENV-setup(Flyway V1__init.sql 11테이블)을 먼저 해 시드·스키마 기반 마련. (트래커 순서상 HARNESS-claude도 남음.)
- 메모: ① 만기 공식은 fixture로 교차검증 — 두 케이스 정확 일치. ② Jackson 쓰는 골든/통합 테스트는 ..domain..에 두면 domainIsFrameworkFree에 걸린다 → golden 등 별도 패키지. ③ 새 골든 fixture를 추가하면 GoldenFixtureIntegrityTest가 goldenLock 전까지 빨간불 — 그래서 이번엔 새 fixture 없이 기존 fixture만 소비. ④ MaturityResult 분해값은 ITEM-05 표시용 선반영.

## 2026-06-13  —  [HARNESS-archunit] 규칙 음성 테스트 + 윈도우 golden 무결성 복구
- 한 일: ① main에서 `./gradlew verify`가 빨간불이었음 — GoldenFixtureIntegrityTest가 sha256 불일치. 원인은 `core.autocrlf=true`+`.gitattributes` 부재로 golden fixture가 CRLF로 체크아웃돼 바이트 해시가 깨진 것(내용은 정당). repo 루트에 `.gitattributes` 추가(golden 경로 `eol=lf` 고정)하고 작업 트리 재정규화로 복구 — fixture 수정·재lock 없이. ② HARNESS-archunit 마무리: 기존 ArchitectureTest(규칙2 double/float·규칙9 domain 순수)와 checkstyle(규칙3 now() 금지)은 있었으나 "위반 시 실패" 증명이 없었음. `RuleEnforcementTest` 추가 — com.ngsoft.salary 밖 `archfixtures` 위반 픽스처(double 필드 / domain 패키지의 스프링 의존)를 명시 임포트해 두 ArchRule이 실제로 AssertionError를 던지는지, checkstyle 정규식을 config에서 읽어 인자 없는 now()는 잡고 now(clock)는 통과하는지 검증.
- 초록불: `./gradlew verify` BUILD SUCCESSFUL (RuleEnforcementTest 3/3, 골든 무결성 통과). `npm run verify` 통과(npm install 후). JAVA_HOME이 삭제된 jdk-11을 가리켜 JDK 22로 우회 실행.
- passes 전환: HARNESS-archunit → true.
- 다음: HARNESS-golden(골든 fixture 폭포 기대값·PaydayResolver 케이스 + 시드 스크립트) → HARNESS-claude → ENV-setup. (owner 메모는 AUTH-01을 가리켰으나 트래커 순서상 harness 항목이 먼저 — 협의.)
- 메모: ① 음성 테스트 픽스처는 com.ngsoft.salary 밖 `archfixtures`/`archfixtures.domain`에 둬야 전역 @AnalyzeClasses 스캔에 안 걸린다. ② 테스트 소스에 `LocalDate.now()` 리터럴을 두면 checkstyle이 잡으므로(=규칙 작동 방증) 문자열을 런타임 합성. ③ checkstyle.xml 파싱 시 외부 DTD(checkstyle.org) 로드 비활성화 — 오프라인 동작. ④ 환경 JAVA_HOME 고장(jdk-11 경로 없음) — 영구 수정 권장.

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
