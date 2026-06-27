package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 저축률(SET-02). 저축액 ÷ 수입 × 100을 소수 첫째 자리로 반올림한 비율(%)이다(구현규칙 1장).
 *
 * <p>저축액 = SAVING 합 + (투자 포함 토글이 켜져 있으면 INVESTMENT 합). 투자 포함 여부는
 * users.include_investment_in_savings_rate가 정하며, 폭포·리포트가 같은 정의를 공유한다(요구사항 SET-02
 * "폭포·리포트 표시에 공통 적용"). EMERGENCY(비상금)·LIVING(생활비)은 저축액에 포함하지 않는다 — 요구사항이
 * 정의한 토글은 투자뿐이라, 그 둘은 어느 쪽으로도 저축액에 넣지 않는다(owner 검토 여지).
 *
 * <p>금액은 long(원)이지만 비율은 long으로 표현할 수 없어 BigDecimal로 둔다(double/float 금지, 규칙 2).
 * 중간 계산도 BigDecimal이다. income이 0 이하(온보딩 전)면 비율을 정의할 수 없어 0.0%를 돌려준다.
 *
 * @param value 저축률(%) — 소수 첫째 자리 반올림(예: 60.7)
 * @param includesInvestment 이 비율이 투자를 포함해 산정됐는지(= 토글 값)
 */
public record SavingsRate(BigDecimal value, boolean includesInvestment) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** 비율 표시 소수 자릿수(구현규칙 1장: 소수 첫째 자리 반올림). */
    private static final int PERCENT_SCALE = 1;

    /**
     * 폭포 그룹(카테고리 소계 보유)에서 저축액을 모아 저축률을 산정한다. EMERGENCY·LIVING은 애초에 groups에
     * 포함되지 않으므로(WaterfallGroup 규약) 별도 제외 처리가 필요 없다.
     *
     * @param groups 폭포 그룹들(EMERGENCY·LIVING 제외)
     * @param income 수입(원). 0 이하면 0.0% 반환
     * @param includeInvestment 투자 포함 토글(users 설정)
     */
    public static SavingsRate from(List<WaterfallGroup> groups, long income, boolean includeInvestment) {
        long savedAmount = 0L;
        for (WaterfallGroup group : groups) {
            if (group.category() == Category.SAVING || (includeInvestment && group.category() == Category.INVESTMENT)) {
                savedAmount += group.subtotal();
            }
        }
        BigDecimal value = income <= 0
                ? BigDecimal.ZERO.setScale(PERCENT_SCALE)
                : BigDecimal.valueOf(savedAmount)
                        .multiply(HUNDRED)
                        .divide(BigDecimal.valueOf(income), PERCENT_SCALE, RoundingMode.HALF_UP);
        return new SavingsRate(value, includeInvestment);
    }
}
