package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CycleSnapshotBuilder 단위 테스트(FLOW-03) — plan_line 구성 규칙: ITEM 순서 보존·EMERGENCY는 ITEM,
 * LIVING 생성 조건(통장 지정 && living&gt;0), 과배분·0원·미지정 시 LIVING 생략, 합 불변식, 입력 검증.
 * 순수 JUnit/assertj만 사용. 실데이터 금액 일치(생활비 356,107)는 CycleSnapshotGoldenTest 소관.
 */
class CycleSnapshotBuilderTest {

    private static final long LIVING_ACCOUNT = 42L;

    private static List<WaterfallLine> sampleLines() {
        return List.of(
                new WaterfallLine(1, Category.SAVING, 700_000),
                new WaterfallLine(2, Category.INVESTMENT, 800_000),
                new WaterfallLine(6, Category.EMERGENCY, 200_000));
    }

    @Test
    void ITEM_라인은_입력_순서를_보존하고_EMERGENCY도_ITEM이다() {
        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, sampleLines(), 0, LIVING_ACCOUNT);

        // ITEM 3건(입력 순서) + LIVING 1건.
        assertThat(drafts).hasSize(4);
        assertThat(drafts.subList(0, 3)).extracting(PlanLineDraft::lineType).containsOnly(PlanLineType.ITEM);
        assertThat(drafts.subList(0, 3))
                .extracting(PlanLineDraft::budgetItemId, PlanLineDraft::category, PlanLineDraft::plannedAmount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1L, Category.SAVING, 700_000L),
                        org.assertj.core.groups.Tuple.tuple(2L, Category.INVESTMENT, 800_000L),
                        org.assertj.core.groups.Tuple.tuple(6L, Category.EMERGENCY, 200_000L));
        // ITEM 라인의 accountId는 영속화 시 해석 → 여기선 비어 있다.
        assertThat(drafts.subList(0, 3)).extracting(PlanLineDraft::accountId).containsOnlyNulls();
    }

    @Test
    void LIVING_라인은_말미에_living_금액과_생활비_통장으로_생성된다() {
        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, sampleLines(), 0, LIVING_ACCOUNT);

        PlanLineDraft living = drafts.get(drafts.size() - 1);
        assertThat(living.lineType()).isEqualTo(PlanLineType.LIVING);
        assertThat(living.accountId()).isEqualTo(LIVING_ACCOUNT);
        assertThat(living.budgetItemId()).isNull();
        assertThat(living.category()).isNull();
        // living = remaining − emergencyTotal = (2,473,110 − 1,500,000) − 200,000 = 773,110.
        assertThat(living.plannedAmount()).isEqualTo(773_110);
    }

    @Test
    void 봉투가_없으면_라인_합은_income과_같다() {
        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, sampleLines(), 0, LIVING_ACCOUNT);

        long sum = drafts.stream().mapToLong(PlanLineDraft::plannedAmount).sum();
        assertThat(sum).isEqualTo(2_473_110);
    }

    @Test
    void 봉투가_있으면_라인_합은_income에서_봉투를_뺀_값이다() {
        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, sampleLines(), 50_000, LIVING_ACCOUNT);

        long sum = drafts.stream().mapToLong(PlanLineDraft::plannedAmount).sum();
        assertThat(sum).isEqualTo(2_473_110 - 50_000);
    }

    @Test
    void 생활비_통장_미지정이면_LIVING_라인을_만들지_않는다() {
        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, sampleLines(), 0, null);

        assertThat(drafts).hasSize(3).extracting(PlanLineDraft::lineType).containsOnly(PlanLineType.ITEM);
    }

    @Test
    void 과배분이면_통장이_지정돼도_LIVING_라인을_만들지_않는다() {
        // 비상금 800,000인데 남는 돈이 그보다 작아 living<0(과배분).
        List<WaterfallLine> lines = List.of(
                new WaterfallLine(1, Category.SAVING, 2_000_000), new WaterfallLine(6, Category.EMERGENCY, 800_000));

        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, lines, 0, LIVING_ACCOUNT);

        // remaining = 473,110, living = 473,110 − 800,000 = −326,890 → LIVING 생략.
        assertThat(drafts).hasSize(2).extracting(PlanLineDraft::lineType).containsOnly(PlanLineType.ITEM);
    }

    @Test
    void 생활비가_정확히_0이면_LIVING_라인을_만들지_않는다() {
        // 항목 합 = income, EMERGENCY 0 → remaining 0 → living 0(이체 0원이라 라인 노이즈 제거).
        List<WaterfallLine> lines = List.of(new WaterfallLine(1, Category.SAVING, 2_473_110));

        List<PlanLineDraft> drafts = CycleSnapshotBuilder.build(2_473_110, lines, 0, LIVING_ACCOUNT);

        assertThat(drafts).hasSize(1).extracting(PlanLineDraft::lineType).containsOnly(PlanLineType.ITEM);
    }

    @Test
    void 항목이_없고_통장도_없으면_빈_목록이다() {
        assertThat(CycleSnapshotBuilder.build(2_473_110, List.of(), 0, null)).isEmpty();
    }

    @Test
    void lines가_null이면_거부한다() {
        assertThatThrownBy(() -> CycleSnapshotBuilder.build(2_473_110, null, 0, LIVING_ACCOUNT))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void ITEM_라인은_accountId를_가질_수_없다() {
        assertThatThrownBy(() -> new PlanLineDraft(PlanLineType.ITEM, 1L, Category.SAVING, 9L, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void LIVING_라인은_통장이_있어야_한다() {
        assertThatThrownBy(() -> new PlanLineDraft(PlanLineType.LIVING, null, null, null, 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void plannedAmount는_양수여야_한다() {
        assertThatThrownBy(() -> PlanLineDraft.living(LIVING_ACCOUNT, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
