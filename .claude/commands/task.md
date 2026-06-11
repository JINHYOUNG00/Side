---
description: 요구사항 하나를 end-to-end 구현. 사용법:/task [요구사항ID]
allowed-tools: Bash, Read, Edit, Write
---
요구사항 구현: $ARGUMENTS

1. docs/요구사항정의서.md에서 해당 ID를 찾아 읽는다. 이어서 관련 정본 문서를
   확인한다: docs/ERD.md(데이터 모델 정본), docs/구현규칙.md(반올림·연동·검증·
   상수), docs/API명세초안.md(API 형태), docs/화면흐름도.md(화면).
2. 소유권 확인: 이 요구사항이 소유자 전용 도메인 로직(WaterfallService /
   PaydayResolver / 사이클 스냅샷 생성 / 보정 제안 룰 — CLAUDE.md 작업 분담)이면
   통째로 구현하지 말 것. 설계 리뷰·테스트 케이스·순수 계산 클래스 골격만 제안하고
   멈춘다. (feature_list.json의 owner_only=true도 동일)
3. 이 요구사항만 구현한다. 도메인 계산 로직은 의존성 없는 순수 클래스 + JUnit 단위
   테스트. 금액은 long(원), 날짜는 주입된 Clock으로 Asia/Seoul.
4. 검증(완료 정의 — 전부 초록불이어야 함):
   - 백엔드: ./gradlew verify
   - 프론트: npm run verify
   - 골든 fixture는 무수정 통과(적금 만기 3,731,976 / 2,476,986, 폭포 기대값).
     골든이 깨지면 코드가 틀린 것 — fixture를 절대 수정하지 말 것.
   실패 시: 수정 → 재실행 루프. 같은 종류 실수가 반복되면 CLAUDE.md 규칙 추가나
   lint/ArchUnit 기계화를 제안.
5. 전부 초록불이면 `<type>($ARGUMENTS): 요약` 형식(CLAUDE.md 커밋 컨벤션)으로 커밋한다.
끝나면 변경 내용과 검증 방법을 요약하고, /wrap 실행을 권하라.
