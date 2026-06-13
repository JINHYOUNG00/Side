package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 잔액 캐스케이드 계산 — 의존성 없는 순수 클래스(FLOW-01, ArchUnit 규칙 9).
 *
 * <p>수입에서 카테고리 그룹 소계를 차감하고 봉투 적립을 뺀 "남는 돈"을 산출한다.
 * EMERGENCY는 groups에 넣지 않고(나머지의 분배로 표현) emergencyTotal로만 집계하며, split
 * 분배(FLOW-03)와 초과 판정(FLOW-02)은 여기서 하지 않는다. 중간 계산은 전부 long이며
 * 합산·차감은 {@link Math#addExact}/{@link Math#subtractExact}로 오버플로를 차단한다.
 */
public final class WaterfallCalculator {

    /**
     * 그룹으로 표시할 카테고리의 순서(화면 폭포 위→아래). EMERGENCY는 의도적으로 제외 —
     * groups에 넣지 않고 emergencyTotal로 집계한다.
     */
    private static final List<Category> GROUP_DISPLAY_ORDER =
            List.of(Category.SAVING, Category.INVESTMENT, Category.FIXED, Category.INSURANCE, Category.SUBSCRIPTION);

    private WaterfallCalculator() {}

    /**
     * @param income 이번 사이클 수입(원, ≥0 — 신규 사용자 placeholder 0 허용)
     * @param lines 활성 항목 라인(호출자가 ACTIVE만·sort_order 정렬해 전달)
     * @param envelopeContribution 봉투 월할 적립 합계(envelope 도메인이 계산해 주입, ≥0)
     * @return 그룹·소계·남는 돈·EMERGENCY 합계
     */
    public static WaterfallResult calculate(long income, List<WaterfallLine> lines, long envelopeContribution) {
        Objects.requireNonNull(lines, "lines");
        if (income < 0) {
            throw new IllegalArgumentException("income은 음수일 수 없다: " + income);
        }
        if (envelopeContribution < 0) {
            throw new IllegalArgumentException("envelopeContribution은 음수일 수 없다: " + envelopeContribution);
        }

        // 카테고리별로 라인을 모은다(각 리스트는 입력 순서=sort_order를 보존).
        Map<Category, List<WaterfallLine>> byCategory = new EnumMap<>(Category.class);
        for (WaterfallLine line : lines) {
            byCategory.computeIfAbsent(line.category(), c -> new ArrayList<>()).add(line);
        }

        // 표시 순서대로 그룹을 만들되, 항목이 없는 카테고리는 group을 생성하지 않는다.
        List<WaterfallGroup> groups = new ArrayList<>();
        long groupSubtotalSum = 0;
        for (Category category : GROUP_DISPLAY_ORDER) {
            List<WaterfallLine> categoryLines = byCategory.get(category);
            if (categoryLines == null || categoryLines.isEmpty()) {
                continue;
            }
            long subtotal = 0;
            for (WaterfallLine line : categoryLines) {
                subtotal = Math.addExact(subtotal, line.amount());
            }
            groups.add(new WaterfallGroup(category, subtotal, List.copyOf(categoryLines)));
            groupSubtotalSum = Math.addExact(groupSubtotalSum, subtotal);
        }

        // EMERGENCY는 groups에서 빠지고 remaining에서도 차감되지 않는다 — 합계만 노출(FLOW-03이 분배).
        long emergencyTotal = 0;
        List<WaterfallLine> emergencyLines = byCategory.get(Category.EMERGENCY);
        if (emergencyLines != null) {
            for (WaterfallLine line : emergencyLines) {
                emergencyTotal = Math.addExact(emergencyTotal, line.amount());
            }
        }

        // remaining = income − Σ(groups.subtotal) − envelopeContribution. 음수 가능, clamp 금지.
        long remaining = Math.subtractExact(Math.subtractExact(income, groupSubtotalSum), envelopeContribution);

        return new WaterfallResult(income, List.copyOf(groups), envelopeContribution, remaining, emergencyTotal);
    }
}
