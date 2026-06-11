# 월급폭포 (가칭 · 이름 변경 예정) — 월급 배분 관리 서비스

> 노션·엑셀로 하던 월급 배분, 비정기 지출 적립, 적금 만기 관리를 자동화한 서비스

## 문제

통장 쪼개기를 노션으로 관리하면 이런 일이 반복된다:
- 항목 하나 바뀔 때마다 잔액 캐스케이드를 전부 손으로 재계산
- 자동차세·명절·보험 연납 같은 "한 방에 나가는 돈"의 적립 현황 추적 불가
- 적금 만기로 풀린 돈의 재배치를 매번 문서 뜯어고치며 수동 처리
- 만기·해지 항목이 취소선으로 쌓이며 문서가 이력과 뒤섞임

이 서비스는 그 수동 작업을 자동화한다. 가계부가 아니다 — 쓴 돈의 기록이 아니라 **들어온 돈의 배분 시스템**을 다룬다.

## 핵심 기능

- **월급 폭포** — 수입에서 저축·고정지출·보험·구독을 차감한 잔액 자동 계산
- **비정기 지출 봉투** — 일시 지출의 월할 적립액 계산, 진행률 추적, 지출 시기 알림
- **월급날 체크리스트** — 통장별 이체 금액 자동 합산, 주말·공휴일 지급일 조정
- **만기 리밸런싱** — 적금 만기 전 예상 수령액과 함께 재배치 제안
- **계획 vs 실제** — 월 1회 잔액 입력만으로 추이 분석, 비현실적 계획의 보정 제안

## 만들지 않는 것

건별 지출 기록(가계부화 금지) · 계좌 연동(마이데이터) · 투자 평가액 추적 · 금융상품 추천 · 커뮤니티

## 기술 스택

| 구분 | 스택 |
|---|---|
| Backend | Java 21, Spring Boot 3.x, Spring Security (OAuth2), Spring Data JPA, Flyway |
| Database | PostgreSQL 16 |
| Frontend | Vue 3, Vite, vue-i18n, Pinia |
| Mobile (예정) | Flutter + FCM |
| Infra | Docker Compose, GitHub Actions, Oracle Cloud |

## 문서

| 문서 | 내용 |
|---|---|
| [CLAUDE.md](CLAUDE.md) | 에이전트·개발 헌법: 완료 정의, 절대 규칙, 분담 |
| [docs/개발계획.md](docs/개발계획.md) | 제품 정의, 아키텍처, 마일스톤, 리스크 |
| [docs/요구사항정의서.md](docs/요구사항정의서.md) | 기능 42건 + 비기능 10건, 우선순위, 비범위 (v1.3) |
| [docs/ERD.md](docs/ERD.md) | 테이블 11개 명세와 설계 결정 (v1.2) |
| [docs/아키텍처.md](docs/아키텍처.md) | 시스템 구성, 계층·패키지 구조, 배치, ADR 9건 |
| [docs/구현규칙.md](docs/구현규칙.md) | 반올림·연동·검증 규칙, 운영 조정 상수 |
| [docs/화면흐름도.md](docs/화면흐름도.md) | 화면 15개 목록과 이동 관계 |
| [docs/mockups/화면설계.html](docs/mockups/화면설계.html) | 전 화면 시각 설계 + 디자인 토큰 (브라우저로 열기) |
| [docs/API명세초안.md](docs/API명세초안.md) | 엔드포인트 초안 (정본은 Swagger) |
| [feature_list.json](feature_list.json) | 작업 추적기(단일) — 완료 정의는 verify 통과 |
| [docs/_archive/백로그_Phase0-1.md](docs/_archive/백로그_Phase0-1.md) | 초기 백로그(보관용) |

## 실행 방법

```bash
docker compose up -d                # PostgreSQL 16
cd backend && ./gradlew bootRun     # API 서버 (Windows: .\gradlew.bat bootRun)
cd frontend && npm run dev          # Vue 개발 서버

# 검증 (완료 정의)
cd backend && ./gradlew verify
cd frontend && npm run verify
```

## 프로젝트 상태

Phase 0 (셋업) 진행 중 — [마일스톤](docs/개발계획.md#6-개발-마일스톤) 참조
