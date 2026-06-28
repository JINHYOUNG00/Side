package com.jinhyoung.salary.suggestion.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 보정/리밸런싱 제안 룰(SUG-01·SUG-02) 순수 단위 테스트. 구현규칙 7장의 발동 조건·반올림·단절(break) 의미론·중복
 * 방지를 입력만으로 검증한다(DB·시계 무관). JUnit·AssertJ만 의존 — domain 패키지의 프레임워크 자유(규칙 9)를 지킨다.
 */
class SuggestionRuleTest {

    private static final int STREAK = 3;
    private static final long SURPLUS_THRESHOLD = 30_000L;

    /** 최신→과거 순 사이클 결과(결측은 null). 라벨은 인덱스로 채운다(판정에 무관). */
    private static List<CheckInOutcome> outcomes(Long... overspendsNewestFirst) {
        List<CheckInOutcome> list = new ArrayList<>();
        for (int i = 0; i < overspendsNewestFirst.length; i++) {
            list.add(new CheckInOutcome("c" + i, overspendsNewestFirst[i]));
        }
        return list;
    }

    @Nested
    class RaiseLiving {

        @Test
        void 연속_초과면_평균을_만원단위_올림해_생활비_증액을_제안한다() {
            // 평균 초과액 15,000 → 10,000 단위 올림 = 20,000.
            Optional<SuggestionDraft> draft =
                    SuggestionRule.raiseLiving(outcomes(12_000L, 15_000L, 18_000L), STREAK, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().type()).isEqualTo(SuggestionType.RAISE_LIVING);
            assertThat(draft.get().dedupKey()).isEqualTo("RAISE_LIVING");
            assertThat(draft.get().payload())
                    .containsEntry("suggestedIncrease", 20_000L)
                    .containsEntry("avgOverspend", 15_000L)
                    .containsEntry("streak", STREAK);
        }

        @Test
        void streak보다_적게_쌓이면_발동하지_않는다() {
            assertThat(SuggestionRule.raiseLiving(outcomes(5_000L, 8_000L), STREAK, Set.of()))
                    .isEmpty();
        }

        @Test
        void 가장_최근_streak개만_본다() {
            // 앞 3개 평균 = 20,000 → 20,000. 네 번째(40,000)는 무시.
            Optional<SuggestionDraft> draft =
                    SuggestionRule.raiseLiving(outcomes(10_000L, 20_000L, 30_000L, 40_000L), STREAK, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().payload()).containsEntry("suggestedIncrease", 20_000L);
        }

        @Test
        void 최근_사이클_결측이면_단절되어_미발동() {
            assertThat(SuggestionRule.raiseLiving(outcomes(null, 10_000L, 10_000L), STREAK, Set.of()))
                    .isEmpty();
        }

        @Test
        void 중간_사이클_결측이면_단절되어_미발동() {
            assertThat(SuggestionRule.raiseLiving(outcomes(10_000L, null, 10_000L), STREAK, Set.of()))
                    .isEmpty();
        }

        @Test
        void 정확히_쓴_사이클이_끼면_단절된다() {
            // overspend=0은 초과(>0)가 아니므로 단절.
            assertThat(SuggestionRule.raiseLiving(outcomes(10_000L, 0L, 10_000L), STREAK, Set.of()))
                    .isEmpty();
        }

        @Test
        void 잉여_사이클이_끼면_단절된다() {
            assertThat(SuggestionRule.raiseLiving(outcomes(10_000L, -5_000L, 10_000L), STREAK, Set.of()))
                    .isEmpty();
        }

        @Test
        void 초과가_아주_작아도_최소_한단위는_올림한다() {
            // 평균 1원 → 올림하면 10,000원.
            Optional<SuggestionDraft> draft = SuggestionRule.raiseLiving(outcomes(1L, 1L, 1L), STREAK, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().payload())
                    .containsEntry("suggestedIncrease", 10_000L)
                    .containsEntry("avgOverspend", 1L);
        }

        @Test
        void 같은_type의_PENDING_제안이_있으면_중복_생성하지_않는다() {
            assertThat(SuggestionRule.raiseLiving(outcomes(12_000L, 15_000L, 18_000L), STREAK, Set.of("RAISE_LIVING")))
                    .isEmpty();
        }
    }

    @Nested
    class RaiseSaving {

        @Test
        void 연속_잉여면_평균을_만원단위_내림해_저축_증액을_제안한다() {
            // 잉여(=−overspend) 평균 40,000 → 10,000 단위 내림 = 40,000.
            Optional<SuggestionDraft> draft = SuggestionRule.raiseSaving(
                    outcomes(-35_000L, -45_000L, -40_000L), STREAK, SURPLUS_THRESHOLD, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().type()).isEqualTo(SuggestionType.RAISE_SAVING);
            assertThat(draft.get().dedupKey()).isEqualTo("RAISE_SAVING");
            assertThat(draft.get().payload())
                    .containsEntry("suggestedIncrease", 40_000L)
                    .containsEntry("avgSurplus", 40_000L)
                    .containsEntry("streak", STREAK);
        }

        @Test
        void 잉여가_기준액_경계면_발동한다() {
            // overspend = −30,000 = −surplusThreshold 경계(≤ 조건 충족).
            Optional<SuggestionDraft> draft = SuggestionRule.raiseSaving(
                    outcomes(-30_000L, -30_000L, -30_000L), STREAK, SURPLUS_THRESHOLD, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().payload()).containsEntry("suggestedIncrease", 30_000L);
        }

        @Test
        void 잉여가_기준액에_못_미치는_사이클이_끼면_단절된다() {
            // −29,999 > −30,000 → 기준 미달로 단절.
            assertThat(SuggestionRule.raiseSaving(
                            outcomes(-29_999L, -40_000L, -40_000L), STREAK, SURPLUS_THRESHOLD, Set.of()))
                    .isEmpty();
        }

        @Test
        void 평균_잉여를_만원_미만은_버린다() {
            // 잉여 평균 38,000 → 내림 = 30,000.
            Optional<SuggestionDraft> draft = SuggestionRule.raiseSaving(
                    outcomes(-38_000L, -38_000L, -38_000L), STREAK, SURPLUS_THRESHOLD, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().payload())
                    .containsEntry("suggestedIncrease", 30_000L)
                    .containsEntry("avgSurplus", 38_000L);
        }

        @Test
        void 결측이_끼면_단절되어_미발동() {
            assertThat(SuggestionRule.raiseSaving(
                            outcomes(-35_000L, null, -40_000L), STREAK, SURPLUS_THRESHOLD, Set.of()))
                    .isEmpty();
        }

        @Test
        void 작은_초과는_RAISE_SAVING_조건이_아니다() {
            // 초과(양수)는 잉여 조건(≤ −threshold)을 만족하지 않아 단절.
            assertThat(SuggestionRule.raiseSaving(
                            outcomes(10_000L, 10_000L, 10_000L), STREAK, SURPLUS_THRESHOLD, Set.of()))
                    .isEmpty();
        }

        @Test
        void 내림_결과가_0이면_제안하지_않는다() {
            // 기준액을 5,000으로 낮춰 조건은 통과하지만 잉여 평균 9,000 → 내림 10,000 = 0 → 미생성.
            assertThat(SuggestionRule.raiseSaving(outcomes(-9_000L, -9_000L, -9_000L), STREAK, 5_000L, Set.of()))
                    .isEmpty();
        }

        @Test
        void 같은_type의_PENDING_제안이_있으면_중복_생성하지_않는다() {
            assertThat(SuggestionRule.raiseSaving(
                            outcomes(-95_000L, -95_000L, -95_000L), STREAK, SURPLUS_THRESHOLD, Set.of("RAISE_SAVING")))
                    .isEmpty();
        }
    }

    @Nested
    class Windfall {

        private static final long THRESHOLD = 30_000L;
        private static final long CYCLE_ID = 7L;
        private static final long BASE = 2_000_000L;

        @Test
        void 평소보다_기준_이상_많으면_WINDFALL을_제안한다() {
            Optional<SuggestionDraft> draft = SuggestionRule.windfall(CYCLE_ID, BASE, 2_050_000L, THRESHOLD, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().type()).isEqualTo(SuggestionType.WINDFALL);
            assertThat(draft.get().dedupKey()).isEqualTo("WINDFALL:7");
            assertThat(draft.get().payload())
                    .containsEntry("difference", 50_000L)
                    .containsEntry("cycleId", CYCLE_ID);
        }

        @Test
        void 평소보다_기준_이상_적으면_SHORTFALL을_제안한다() {
            Optional<SuggestionDraft> draft = SuggestionRule.windfall(CYCLE_ID, BASE, 1_960_000L, THRESHOLD, Set.of());

            assertThat(draft).isPresent();
            assertThat(draft.get().type()).isEqualTo(SuggestionType.SHORTFALL);
            assertThat(draft.get().dedupKey()).isEqualTo("SHORTFALL:7");
            assertThat(draft.get().payload()).containsEntry("difference", 40_000L);
        }

        @Test
        void 차이가_기준_미만이면_제안하지_않는다() {
            assertThat(SuggestionRule.windfall(CYCLE_ID, BASE, 2_020_000L, THRESHOLD, Set.of()))
                    .isEmpty();
            assertThat(SuggestionRule.windfall(CYCLE_ID, BASE, 1_980_000L, THRESHOLD, Set.of()))
                    .isEmpty();
        }

        @Test
        void 차이가_기준_경계면_발동한다() {
            // +30,000 = 기준 경계(≥).
            assertThat(SuggestionRule.windfall(CYCLE_ID, BASE, 2_030_000L, THRESHOLD, Set.of()))
                    .isPresent();
        }

        @Test
        void 같은_사이클_제안이_있으면_중복_생성하지_않는다() {
            assertThat(SuggestionRule.windfall(CYCLE_ID, BASE, 2_050_000L, THRESHOLD, Set.of("WINDFALL:7")))
                    .isEmpty();
        }
    }

    @Nested
    class RebalanceMaturity {

        private static final LocalDate MATURITY = LocalDate.of(2026, 7, 31);

        private static MaturingItem item(long id, Long expected) {
            return new MaturingItem(id, "OO적금", 300_000L, expected, MATURITY);
        }

        @Test
        void 만기_30일_전이_도래하면_항목별로_제안한다() {
            // threshold = 2026-07-01. 그 날이면 도래.
            List<SuggestionDraft> drafts =
                    SuggestionRule.rebalanceMaturity(List.of(item(5L, 3_731_976L)), LocalDate.of(2026, 7, 1), Set.of());

            assertThat(drafts).hasSize(1);
            SuggestionDraft draft = drafts.get(0);
            assertThat(draft.type()).isEqualTo(SuggestionType.REBALANCE_MATURITY);
            assertThat(draft.dedupKey()).isEqualTo("REBALANCE_MATURITY:5");
            assertThat(draft.payload())
                    .containsEntry("itemId", 5L)
                    .containsEntry("itemName", "OO적금")
                    .containsEntry("monthlyAmount", 300_000L)
                    .containsEntry("expectedMaturityAmount", 3_731_976L)
                    .containsEntry("maturityDate", "2026-07-31");
        }

        @Test
        void 도래_전이면_제안하지_않는다() {
            // threshold 2026-07-01 하루 전.
            assertThat(SuggestionRule.rebalanceMaturity(
                            List.of(item(5L, 3_731_976L)), LocalDate.of(2026, 6, 30), Set.of()))
                    .isEmpty();
        }

        @Test
        void 도래_이후_만기_전까지는_계속_대상이다() {
            assertThat(SuggestionRule.rebalanceMaturity(
                            List.of(item(5L, 3_731_976L)), LocalDate.of(2026, 7, 10), Set.of()))
                    .hasSize(1);
        }

        @Test
        void 이미_같은_항목_제안이_있으면_중복_생성하지_않는다() {
            assertThat(SuggestionRule.rebalanceMaturity(
                            List.of(item(5L, 3_731_976L)), LocalDate.of(2026, 7, 1), Set.of("REBALANCE_MATURITY:5")))
                    .isEmpty();
        }

        @Test
        void 여러_만기_항목은_각각_제안한다() {
            List<SuggestionDraft> drafts = SuggestionRule.rebalanceMaturity(
                    List.of(item(5L, 3_731_976L), item(6L, 2_476_986L)), LocalDate.of(2026, 7, 1), Set.of());

            assertThat(drafts)
                    .extracting(SuggestionDraft::dedupKey)
                    .containsExactlyInAnyOrder("REBALANCE_MATURITY:5", "REBALANCE_MATURITY:6");
        }

        @Test
        void 예상_만기금액이_없으면_payload에서_생략한다() {
            List<SuggestionDraft> drafts =
                    SuggestionRule.rebalanceMaturity(List.of(item(5L, null)), LocalDate.of(2026, 7, 1), Set.of());

            assertThat(drafts).hasSize(1);
            assertThat(drafts.get(0).payload())
                    .doesNotContainKey("expectedMaturityAmount")
                    .containsKey("monthlyAmount");
        }

        @Test
        void 도래한_항목만_골라_제안한다() {
            MaturingItem due = new MaturingItem(5L, "도래", 300_000L, null, LocalDate.of(2026, 7, 31));
            MaturingItem notDue = new MaturingItem(6L, "아직", 200_000L, null, LocalDate.of(2026, 12, 31));

            List<SuggestionDraft> drafts =
                    SuggestionRule.rebalanceMaturity(List.of(due, notDue), LocalDate.of(2026, 7, 1), Set.of());

            assertThat(drafts).extracting(SuggestionDraft::dedupKey).containsExactly("REBALANCE_MATURITY:5");
        }
    }
}
