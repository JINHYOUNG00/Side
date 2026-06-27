---
description: 세션 종료 — 커밋 + progress 기록으로 깔끔하게 인계
allowed-tools: Bash, Read, Edit
---
다음 세션을 위해 깨끗한 상태로 남긴다:

1. ./gradlew verify와 npm run verify가 둘 다 초록불인지, 미완성 편집이 없는지 확인.
   있으면 끝내거나 되돌린다.
2. `[요구사항ID] 요약` 형식으로 커밋.
3. progress.md에 날짜 항목 추가: 한 일 / 초록불이 된 검증 / true로 바뀐
   feature_list id / 다음 항목 / 메모(추가한 Flyway 버전, 골든 케이스, 결정사항).
4. README.md "프로젝트 상태" 섹션을 점검한다. 이번 세션으로 진척이 바뀌었으면
   (기능 완료·Phase 이동·구현됨/남은 작업 목록 변동) 현재 상태로 갱신한다 — 단,
   feature_list verify 통과 기준의 진척만 적고 라이브 배포·실연동을 단정하지 말 것
   (과장 금지). 상태 변동이 없으면 건드리지 않는다. progress.md와 함께 한 doc 커밋으로.
5. `git status`로 작업 트리가 깨끗한지 확인.
6. `git push origin main`으로 원격에 푸시한다(force push 금지 — settings deny). 푸시
   결과(`origin/main`과 동기됐는지)까지 보고.
7. 마지막 줄에 이번 세션 작업명을 반영한 추천 세션 제목을 다음 형식으로 출력한다
   (rename은 직접 실행 불가 — 사용자가 복붙해 실행):
   `/rename <요구사항ID>-<한단어요약>`   예) `/rename FLOW-04-비상금분배`
   요구사항 ID가 여럿이면 대표 ID 하나, 없으면 작업 성격을 한국어 한두 단어로.
