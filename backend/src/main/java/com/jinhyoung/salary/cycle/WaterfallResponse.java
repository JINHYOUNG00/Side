package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.cycle.domain.SavingsRate;
import java.time.LocalDate;
import java.util.List;

/**
 * 폭포 조회 응답(FLOW-02, API명세 3장). 순수 계산(그룹·소계·잔액·분배)은 owner의 도메인 클래스
 * {@link com.jinhyoung.salary.cycle.domain.WaterfallCalculator}/{@link
 * com.jinhyoung.salary.cycle.domain.WaterfallSplit}가 맡고, 이 record는 그 결과에 항목 메타(이름·통장)를
 * 붙여 노출하는 응답 형태일 뿐이다.
 *
 * <p>FLOW-02 범위로 한정한 필드:
 *
 * <ul>
 *   <li>{@code envelopeContribution}: 봉투 월할 적립 합계. 봉투(Phase 3) 미구현이라 현재 항상 0.
 *   <li>{@code overAllocated}: {@code living < 0}(과배분)일 때 true — 요구사항 "배분 합계가 수입 초과"는
 *       비상금까지 포함한 배분이 income을 넘는 상황(= living 음수)이다. 차단하지 않고 경고만 한다(FLOW-02).
 *       조정 후보의 유연성 순 정렬은 프론트(SCR-03)가 수행한다.
 *   <li>{@code split.living}: 음수 가능(과배분). 0으로 clamp하지 않는다(FLOW-03 결정).
 * </ul>
 *
 * <p>응답에서 의도적으로 제외:
 *
 * <ul>
 *   <li>{@code Item.expectedMaturityAmount}: 적금 만기 예상금액(ITEM-05, Phase 5) — 현재 항상 null.
 * </ul>
 *
 * <p>{@code savingsRate}(SET-02)는 저축률 산정 방식(투자 포함 토글)을 "폭포 표시에 공통 적용"한 결과다 — 순수
 * 도메인 {@link SavingsRate}가 저축액(SAVING + 토글 시 INVESTMENT) ÷ 수입으로 산정한다.
 *
 * @param income 이번 폭포의 수입(= users.base_income, 원)
 * @param groups EMERGENCY·LIVING 제외, 표시 순서로 정렬된 그룹(빈 카테고리 생략)
 * @param envelopeContribution 봉투 월할 적립 합계(현재 0)
 * @param remaining 남는 돈(= income − Σ소계 − envelopeContribution, 음수 가능)
 * @param split 남는 돈의 비상금/생활비 분배(FLOW-03)
 * @param overAllocated 과배분 경고(= split.living < 0)
 * @param savingsRate 저축률(SET-02, 투자 포함 토글 반영)
 */
public record WaterfallResponse(
        long income,
        List<Group> groups,
        long envelopeContribution,
        long remaining,
        Split split,
        boolean overAllocated,
        SavingsRate savingsRate) {

    /**
     * 카테고리 그룹 1건 — 소계 + 그 그룹의 항목(입력 순서=sort_order 보존).
     *
     * @param category 그룹 카테고리
     * @param subtotal 그룹 내 amount 합(원)
     * @param items 그룹에 속한 항목 메타
     */
    public record Group(Category category, long subtotal, List<Item> items) {}

    /**
     * 폭포에 노출되는 항목 1건. 소계와 항목 합의 불일치를 막기 위해 원본 budget_items.id를 그대로 들고 다닌다.
     *
     * @param id budget_items.id
     * @param name 항목 이름
     * @param amount 월 배분 금액(원)
     * @param accountId 대상 통장 id
     * @param accountName 대상 통장 별칭(통장이 비활성화돼 조회되지 않으면 null)
     * @param endDate 만기일(없으면 null)
     * @param expectedMaturityAmount 만기 예상금액(ITEM-05 미구현 — 현재 항상 null)
     */
    public record Item(
            long id,
            String name,
            long amount,
            long accountId,
            String accountName,
            LocalDate endDate,
            Long expectedMaturityAmount) {}

    /**
     * 남는 돈 분배(FLOW-03). split 합(emergency + living)은 항상 remaining과 같다.
     *
     * @param emergency 비상금(= EMERGENCY 항목 합)
     * @param living 생활비(= remaining − emergency, 음수 가능)
     */
    public record Split(long emergency, long living) {}
}
