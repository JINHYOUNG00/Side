package com.jinhyoung.salary.cycle.domain;

/**
 * 사이클 배분 라인의 종류(plan_lines.line_type, ERD). 닫힌 도메인이라 스키마의 4값을 모두 둔다.
 *
 * <ul>
 *   <li>{@link #ITEM} — 배분 항목(budget_items)에서 파생. EMERGENCY 항목도 ITEM으로 두고
 *       category_snapshot으로 구분한다(plan_lines에 EMERGENCY 타입은 없다).
 *   <li>{@link #LIVING} — 폭포 나머지(생활비) 이체 1건. users.living_account_id 지정 시 생성(FLOW-03).
 *   <li>{@link #ENVELOPE} — 봉투 월할 적립 라인. envelope 도메인 도입(Phase 3)에서 emit.
 *   <li>{@link #EXTRA} — 여윳돈 배분 라인. 여윳돈 플로우(CYCLE-05, Phase 6)에서 emit.
 * </ul>
 *
 * <p>FLOW-03 스냅샷 빌더는 ITEM·LIVING만 산출한다. ENVELOPE·EXTRA는 해당 Phase가 추가한다.
 */
public enum PlanLineType {
    ITEM,
    ENVELOPE,
    LIVING,
    EXTRA
}
