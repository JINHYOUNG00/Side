# 서브에이전트 (B: 비대칭 검토) — 월급앱

빌더가 만들고, 독립 리뷰어가 검수하는 generator-evaluator 구성. 두 에이전트 모두
**보고·제안만 하고 코드를 고치지 않는다** — `tools`에 Edit/Write가 없어서 구조적으로
수정이 불가능하다(부탁이 아니라 기계적 보장).

## 들어있는 것
```
.claude/agents/
  code-reviewer.md   # 변경(diff) 독립 검수 — CLAUDE.md 규칙·구현규칙·골든 위반을 심각도별 보고
  architect.md       # owner-only 도메인 로직(WaterfallService 등) 설계 리뷰·테스트 케이스 제안
```

## 배치
- 메인 패키지의 `.claude/` 안에 `agents/` 폴더를 합친다(`.claude/agents/*.md`).
- 1인 프로젝트라 그냥 커밋. (팀이면 project 스코프라 팀원도 자동 상속.)

## 호출
- **자동 위임**: 각 파일 `description`이 라우팅 트리거다. "방금 만든 코드 검수해줘" 같은
  요청이면 Claude가 code-reviewer를 알아서 부른다.
- **명시 호출**: `@code-reviewer 이 auth 변경 검수해` / "code-reviewer 서브에이전트로 봐줘".
  owner-only 로직 설계 땐 `@architect`.

## 루프에 끼우는 법
1. 일반 작업: `/task ITEM-01`로 구현 → `@code-reviewer`로 독립 검수 → Critical/Warning을
   빌더(메인 세션)가 반영 → `gradlew verify` / `npm run verify` 재실행 → `/wrap`.
2. owner-only 로직(WaterfallService·PaydayResolver·스냅샷·보정 룰): **소유자가 작성**하고,
   `@architect`로 설계 리뷰·반례 테스트를 받고, `@code-reviewer`로 코드·테스트를 점검.
   두 에이전트 모두 구현을 대신하지 않는다.

## 비용
서브에이전트는 별도 과금은 아니지만 각자 컨텍스트를 가져서 토큰 볼륨이 늘 수 있다(서브에이전트
많이 쓰는 워크플로는 단일 세션의 수 배). 그러니 **항상 돌리지 말고 의미 있는 변경 후에만** 부른다.

## "검토가 실제로 효과 내게" 하는 한 가지 레버
연구상 멀티에이전트가 효과를 내는 핵심 조건은 구조가 아니라 **관점의 다양성**이다. 그래서
리뷰어 모델을 빌더와 다르게/더 강하게 두면 효과가 커진다 — 예: 빌더는 sonnet으로 돌리고
`code-reviewer.md`의 `model:`을 `opus`로 바꾸면, 같은 모델이 자기 코드를 보는 게 아니라 다른
시각이 들어온다. (frontmatter `model` 한 줄만 수정.)
