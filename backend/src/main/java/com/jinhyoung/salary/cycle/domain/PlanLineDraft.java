package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.budgetitem.domain.Category;

/**
 * 사이클 스냅샷에 들어갈 plan_line 1건의 <b>계산 산출물</b> — 의존성 없는 순수 값 객체(FLOW-03).
 *
 * <p>여기엔 폭포 계산이 결정하는 값만 담는다: 라인 종류·원본 항목 식별자·카테고리·이체 금액.
 * 이름/통장 별칭 스냅샷(name_snapshot·account_name_snapshot)과 status(PENDING)는 영속화 시점
 * 메타데이터라 여기 두지 않는다 — 스냅샷 영속화(CYCLE-03, Phase 2)가 budgetItemId로 항목·통장을
 * 조회해 채운다(WaterfallQueryService가 응답 메타를 재조립하는 것과 같은 분담).
 *
 * <p>타입별 불변식:
 *
 * <ul>
 *   <li>{@link PlanLineType#ITEM} — {@code budgetItemId>0}, {@code category!=null}, {@code accountId=null}
 *       (이체 대상 통장은 budgetItemId→항목→통장으로 영속화 시 해석). EMERGENCY 항목도 ITEM이며
 *       category=EMERGENCY로 구분한다.
 *   <li>{@link PlanLineType#LIVING} — {@code budgetItemId=null}, {@code category=null},
 *       {@code accountId>0}(= users.living_account_id). 생활비 이체 1건.
 * </ul>
 *
 * @param lineType 라인 종류(FLOW-03은 ITEM·LIVING만 산출)
 * @param budgetItemId 원본 budget_items.id(ITEM은 &gt;0, LIVING은 null)
 * @param category 카테고리 스냅샷(ITEM은 항목 카테고리, LIVING은 null)
 * @param accountId 이체 대상 통장(LIVING은 living_account_id, ITEM은 null=영속화 시 해석)
 * @param plannedAmount 이체 금액(원, long, &gt;0)
 */
public record PlanLineDraft(
        PlanLineType lineType, Long budgetItemId, Category category, Long accountId, long plannedAmount) {

    public PlanLineDraft {
        if (lineType == null) {
            throw new IllegalArgumentException("lineType은 null일 수 없다");
        }
        if (plannedAmount <= 0) {
            throw new IllegalArgumentException("plannedAmount는 0보다 커야 한다: " + plannedAmount);
        }
        switch (lineType) {
            case ITEM -> {
                if (budgetItemId == null || budgetItemId <= 0) {
                    throw new IllegalArgumentException("ITEM 라인은 budgetItemId>0이어야 한다: " + budgetItemId);
                }
                if (category == null) {
                    throw new IllegalArgumentException("ITEM 라인은 category가 있어야 한다");
                }
                if (accountId != null) {
                    throw new IllegalArgumentException("ITEM 라인의 accountId는 영속화 시 해석한다(여기선 null)");
                }
            }
            case LIVING -> {
                if (accountId == null || accountId <= 0) {
                    throw new IllegalArgumentException("LIVING 라인은 accountId>0(생활비 통장)이어야 한다: " + accountId);
                }
                if (budgetItemId != null || category != null) {
                    throw new IllegalArgumentException("LIVING 라인은 budgetItemId·category가 없어야 한다");
                }
            }
            default -> throw new IllegalArgumentException("FLOW-03 빌더는 ITEM·LIVING만 산출한다: " + lineType);
        }
    }

    /** ITEM 라인 — 항목에서 파생. accountId는 영속화 시 항목으로 해석하므로 여기선 비운다. */
    public static PlanLineDraft item(long budgetItemId, Category category, long plannedAmount) {
        return new PlanLineDraft(PlanLineType.ITEM, budgetItemId, category, null, plannedAmount);
    }

    /** LIVING 라인 — 폭포 나머지(생활비)를 생활비 통장으로 이체. */
    public static PlanLineDraft living(long livingAccountId, long plannedAmount) {
        return new PlanLineDraft(PlanLineType.LIVING, null, null, livingAccountId, plannedAmount);
    }
}
