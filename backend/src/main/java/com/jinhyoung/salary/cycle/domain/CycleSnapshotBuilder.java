package com.jinhyoung.salary.cycle.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 사이클 스냅샷의 plan_line 구성 — 의존성 없는 순수 클래스(FLOW-03, ArchUnit 규칙 9).
 *
 * <p>활성 항목 라인과 사용자 설정으로 스냅샷에 박을 {@link PlanLineDraft} 목록을 산출한다.
 * <b>금액 계산은 owner의 {@link WaterfallCalculator}/{@link WaterfallSplit}에 위임</b>하고, 여기서는
 * 그 결과를 라인으로 펼치기만 한다 — FLOW-02 응답 조립이 계산을 위임하는 것과 같은 분담.
 *
 * <p>구성 규칙:
 *
 * <ul>
 *   <li><b>ITEM 라인</b> — 입력 라인마다 1건(sort_order 보존). EMERGENCY 항목도 ITEM이며
 *       category=EMERGENCY로 구분한다(plan_lines에 EMERGENCY 타입 없음). 금액은 항목 금액 그대로.
 *   <li><b>LIVING 라인</b> — 구현규칙 3장: {@code planned_amount = income − Σ항목 − Σ봉투 − ΣEMERGENCY}
 *       (= {@link WaterfallSplit#living()}). <b>생성 조건: living_account_id 지정 && living &gt; 0.</b>
 *       living_account_id 미지정이면 미생성(폭포 표시만, 구현규칙 3장). living &le; 0(과배분)이면
 *       이체할 돈이 없어 미생성 — 사용자가 비상금/생활비를 먼저 조정해야 한다(WaterfallSplit shortfall 의미론).
 * </ul>
 *
 * <p>불변식: 봉투(Phase 3)·여윳돈(Phase 6) 라인이 아직 없으므로
 * {@code Σ(ITEM)+LIVING = income − envelopeContribution}이며, 골든 케이스(envelope 0)에선 income과 같다.
 */
public final class CycleSnapshotBuilder {

    private CycleSnapshotBuilder() {}

    /**
     * @param income 이번 사이클 수입(원, ≥0)
     * @param lines 활성 항목 라인(호출자가 ACTIVE만·sort_order 정렬해 전달, EMERGENCY 포함)
     * @param envelopeContribution 봉투 월할 적립 합계(≥0, Phase 3 전엔 0)
     * @param livingAccountId 생활비 통장 id(미지정이면 null → LIVING 라인 미생성)
     * @return 스냅샷에 박을 plan_line drafts(ITEM 라인 입력 순서 + 조건 충족 시 LIVING 1건 말미)
     */
    public static List<PlanLineDraft> build(
            long income, List<WaterfallLine> lines, long envelopeContribution, Long livingAccountId) {
        Objects.requireNonNull(lines, "lines");

        WaterfallResult result = WaterfallCalculator.calculate(income, lines, envelopeContribution);
        WaterfallSplit split = WaterfallSplit.from(result);

        List<PlanLineDraft> drafts = new ArrayList<>(lines.size() + 1);
        // ITEM 라인 — 입력 순서(sort_order) 보존. EMERGENCY도 ITEM으로(category로 구분).
        for (WaterfallLine line : lines) {
            drafts.add(PlanLineDraft.item(line.budgetItemId(), line.category(), line.amount()));
        }
        // LIVING 라인 — 생활비 통장 지정 && 이체할 생활비가 양수일 때만(과배분·0원 이체 제외).
        if (livingAccountId != null && split.living() > 0) {
            drafts.add(PlanLineDraft.living(livingAccountId, split.living()));
        }
        return List.copyOf(drafts);
    }
}
