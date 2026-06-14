package com.jinhyoung.salary.cycle.infra;

/**
 * 사이클 배분 라인의 체크리스트 상태(plan_lines.status, ERD). 닫힌 도메인이라 스키마의 3값을 모두 둔다.
 *
 * <ul>
 *   <li>{@link #PENDING} — 생성 직후 기본 상태(아직 이체 전).
 *   <li>{@link #DONE} — 이체 완료로 체크됨(CYCLE-06 상태 전이).
 *   <li>{@link #SKIPPED} — 이번 사이클 건너뜀(CYCLE-06 상태 전이).
 * </ul>
 *
 * <p>CYCLE-03 스냅샷 생성은 PENDING만 적재한다. DONE·SKIPPED 전이는 통장별 체크리스트(CYCLE-06)가 다룬다.
 */
public enum PlanLineStatus {
    PENDING,
    DONE,
    SKIPPED
}
