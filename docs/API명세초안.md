# API 명세 초안 — 월급 배분 관리 서비스

| 문서 버전 | v1.0 (초안 — 구현 후 Swagger가 정본) |
|---|---|
| 작성일 | 2026-06-11 |
| 관련 문서 | 요구사항정의서.md (v1.1), ERD.md |

## 1. 공통 규약

- Base URL: `/api/v1`, 인증: `Authorization: Bearer {JWT}` (로그인 제외 전부)
- 날짜는 `YYYY-MM-DD`(Asia/Seoul), 금액은 원 단위 정수
- 오류 응답은 문장이 아닌 코드(NFR-06): `{ "code": "CYCLE_ALREADY_EXISTS", "params": {...} }` — 메시지 조립은 클라이언트 i18n
- 주요 오류 코드: `UNAUTHORIZED`, `NOT_FOUND`, `VALIDATION_FAILED`, `CYCLE_ALREADY_EXISTS`, `CHECK_IN_ALREADY_EXISTS`, `IMPORT_PARSE_EMPTY`

## 2. 인증

```
POST /auth/{provider}        provider: kakao | google | naver
req:  { "code": "..." }                       (OAuth authorization code)
res:  { "accessToken": "...", "isNewUser": true }
DELETE /me                   회원 탈퇴(전체 삭제, AUTH-04)
GET /me                      프로필+설정
PATCH /me                    { "baseIncome", "payday", "paydayAdjustment", "includeInvestmentInSavingsRate", "locale" }
```

## 3. 폭포 (핵심 응답 — 프론트/백 합의 기준)

```
GET /me/waterfall
res: {
  "income": 2500000,
  "groups": [
    { "category": "SAVING", "subtotal": 700000,
      "items": [ { "id": 1, "name": "청년도약계좌", "amount": 700000,
                   "accountId": 3, "accountName": "국민",
                   "endDate": "2029-11-07",
                   "expectedMaturityAmount": null } ] },
    { "category": "INVESTMENT", "subtotal": 800000, "items": [ ... ] },
    { "category": "FIXED", "subtotal": 280000, "items": [ ... ] }
  ],
  "envelopeContribution": 40000,
  "remaining": 575000,
  "split": { "emergency": 200000, "living": 375000 },
  "overAllocated": false,
  "savingsRate": { "value": 60.0, "includesInvestment": true }
}
```
정의: `remaining = income − Σ(groups.subtotal) − envelopeContribution`. 봉투 적립을 차감한 후의 값이 "남는 돈"이며, split의 합은 항상 remaining과 같다. EMERGENCY 카테고리 항목은 groups에서 제외하고 split.emergency로 집계한다(나머지의 분배로 표현). 단 체크리스트 이체 라인은 EMERGENCY 항목에 대해서도 정상 생성된다.
`overAllocated`는 **`split.living < 0`일 때 true**다(FLOW-02 구현 확정). 요구사항 "배분 합계가 수입 초과"는 비상금(EMERGENCY)까지 포함한 배분이 income을 넘는 상황이고, 그게 곧 생활비(living) 음수다 — 따라서 remaining이 양수여도 비상금이 그보다 크면 과배분이다(remaining<0은 그 부분집합). 차단하지 않고 경고만 하며(living은 음수 그대로, clamp 금지), 조정 후보의 유연성 순 정렬(LIVING·EMERGENCY > INVESTMENT > SAVING·FIXED)은 프론트(SCR-03)가 수행한다.

> **FLOW-02 구현 범위 메모(구현 후 정정).** `GET /me/waterfall`는 사이클 스냅샷이 아니라 현재 base_income·활성 항목 기준 라이브 폭포다. 현재 응답에서 `envelopeContribution`은 봉투(Phase 3) 미구현이라 항상 0, `items[].expectedMaturityAmount`는 만기 예상금액(ITEM-05, Phase 5) 미구현이라 항상 null이다. **`savingsRate`는 아직 응답에 포함하지 않는다** — 투자 포함 토글 적용(저축률 산정 방식)은 SET-02(Phase 5)가 "폭포 표시에 공통 적용"으로 소유하므로 그 Phase에서 추가한다.

## 4. 통장 / 항목 / 봉투 (CRUD)

```
GET|POST /accounts          PATCH|DELETE /accounts/{id}      (DELETE = is_active false)
GET|POST /budget-items      PATCH|DELETE /budget-items/{id}  (DELETE = status DELETED, ITEM-09)
  POST body 예: { "category": "SAVING", "name": "OO적금", "amount": 300000,
                  "accountId": 2, "startDate": "2026-07-01", "endDate": "2027-06-30",
                  "interestRate": 4.5, "taxType": "NORMAL",
                  "inputCycle": "MONTHLY", "inputMeta": null }
  PATCH 쿼리: ?applyToCurrentCycle=true → 현재 사이클 미완료 라인 재생성(ITEM-07)
POST /budget-items/preview-maturity     만기금액 미리보기(저장 없음, ITEM-05)
  req: { "amount", "months", "interestRate", "taxType" } → res: { "principal", "interest", "tax", "total" }
POST /budget-items/preview-fx           외화 도우미(ITEM-04)
  req: { "unitAmount": 7, "currency": "USD", "frequency": "BUSINESS_DAYS", "fxRate": 1380 }
  res: { "recommendedMonthlyKrw": 223000, "bufferRate": 0.05 }

GET|POST /envelopes         PATCH|DELETE /envelopes/{id}
GET /envelopes/{id}/transactions
POST /envelopes/{id}/spend  (ENV-04~05)
  req: { "actualAmount": 230000, "shortfallSource": "LIVING", "carryOver": null }
  res: { "envelope": {...갱신된 next_due_date, saved_amount...} }
```

## 5. 사이클 / 체크리스트

```
POST /cycles                          스냅샷 생성(멱등, CYCLE-03). 중복 시 409 CYCLE_ALREADY_EXISTS
GET  /cycles/current                  현재 사이클 + 체크리스트
res: {
  "id": 12, "label": "2026-06", "cycleStart": "2026-06-25", "cycleEnd": "2026-07-26",
  "income": 2500000, "incomeConfirmed": false,
  "checklist": [
    { "accountId": 2, "accountName": "케이뱅크", "total": 300000,
      "lines": [ { "id": 101, "name": "도시락", "plannedAmount": 22000, "status": "PENDING" } ] }
  ],
  "progress": { "done": 1, "total": 4 }
}
PATCH /cycles/{id}/income             { "income": 3300000 } → 차액 기준 초과 시 res에 windfall 제안 포함(CYCLE-05)
PATCH /plan-lines/{id}                { "status": "DONE" | "SKIPPED" | "PENDING" }  (CYCLE-06~07)
GET   /cycles?from=&to=               과거 사이클 목록(리포트용)
```

## 6. 체크인 / 리포트 / 제안

```
POST /check-ins              { "cycleId": 12, "livingRemaining": 41000, "toppedUp": 0 } (사이클당 1건, RPT-01)
  toppedUp(선택, 기본 0): 사이클 중 생활비 통장에 추가 투입한 금액. 초과액 = toppedUp − livingRemaining
  (잔액만으로는 충당 후 초과를 측정할 수 없으므로 보조 입력으로 보정)
GET  /reports/trend?months=6 res: [ { "label": "2026-05", "planned": 375000, "actual": 387000, "checkedIn": true }, ... ]
GET  /reports/summary        저축률·만기 수령 누적·봉투 집행 합계
GET  /suggestions            res: [ { "id": 7, "type": "REBALANCE_MATURITY", "status": "PENDING",
                                      "payload": { "itemName": "OO적금", "monthlyAmount": 300000,
                                                   "expectedMaturityAmount": 3731976, "maturityDate": "2026-07-06" } } ]
POST /suggestions/{id}/apply   payload 기반 변경 일괄 적용(SUG-01~02)
POST /suggestions/{id}/dismiss
```

## 7. 데이터 이동 / 기타

```
POST /import/parse           { "raw": "노션 표 텍스트" } → { "candidates": [ {항목 후보}... ] }  (저장 없음, DATA-01)
POST /import/commit          { "items": [...], "envelopes": [...] }
GET  /export?format=md|csv   전체 데이터 직렬화(DATA-02, 임포트와 라운드트립 보장)
GET  /holidays?year=2026     공휴일 목록(프론트 캘린더용)
```

## 8. 인증·보안 메모
JWT 만료 짧게(예: 1h) + 리프레시는 v1 생략(재로그인). 모든 리소스는 user_id 소유권 검증. CORS는 프론트 도메인 한정.
