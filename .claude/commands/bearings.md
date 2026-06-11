---
description: 세션 시작 시 상황 파악 (작업 전)
allowed-tools: Bash, Read
---
작업 전 상황을 파악한다:

1. `pwd`로 작업 디렉터리 확인.
2. progress.md를 읽고 `git log --oneline -20`.
3. feature_list.json을 읽고 미완료 항목을 phase·우선순위 순으로 나열.
4. CLAUDE.md "실행 명령 사전"대로 앱을 기동(docker compose up -d / ./gradlew
   bootRun / npm run dev)하고, 현재 main에서 ./gradlew verify와 npm run verify가
   둘 다 초록불인지 확인.
5. verify가 빨간불이면 그것부터 고치고 보고. 초록불이면 다음 최우선 미완료 항목
   하나를 제안하고 내 승인을 기다린다. 아직 구현 시작하지 말 것.
