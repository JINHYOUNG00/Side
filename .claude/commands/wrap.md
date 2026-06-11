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
4. `git status`로 작업 트리가 깨끗한지 확인하고 보고.
