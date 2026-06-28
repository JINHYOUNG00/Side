package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 여윳돈/부족 배분 산술(CYCLE-05) 순수 단위 테스트. 대상 라인 증감·LIVING 상대편 조정과 불변(cap·LIVING 잔액·0
 * 미만·비양수·빈 목록) 위반을 입력만으로 검증한다. JUnit·AssertJ만 의존(규칙 9).
 */
class WindfallAllocationTest {

    @Nested
    class DistributeWindfall {

        @Test
        void 고른_항목에_더하고_LIVING에서_같은_합을_뺀다() {
            // 여윳돈 100,000 중 30,000+20,000=50,000을 두 항목에 배분, LIVING 200,000 − 50,000.
            WindfallAllocation.Result result = WindfallAllocation.distributeWindfall(
                    100_000L,
                    200_000L,
                    List.of(
                            new WindfallAllocation.Line(50_000L, 30_000L),
                            new WindfallAllocation.Line(80_000L, 20_000L)));

            assertThat(result.newTargetAmounts()).containsExactly(80_000L, 100_000L);
            assertThat(result.newLiving()).isEqualTo(150_000L);
        }

        @Test
        void 합이_차액을_넘으면_거부한다() {
            assertThatThrownBy(() -> WindfallAllocation.distributeWindfall(
                            40_000L, 200_000L, List.of(new WindfallAllocation.Line(50_000L, 50_000L))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 합이_LIVING_잔액을_넘으면_거부한다() {
            assertThatThrownBy(() -> WindfallAllocation.distributeWindfall(
                            100_000L, 30_000L, List.of(new WindfallAllocation.Line(50_000L, 50_000L))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 비양수_배분액은_거부한다() {
            assertThatThrownBy(() -> WindfallAllocation.distributeWindfall(
                            100_000L, 200_000L, List.of(new WindfallAllocation.Line(50_000L, 0L))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 빈_목록은_거부한다() {
            assertThatThrownBy(() -> WindfallAllocation.distributeWindfall(100_000L, 200_000L, List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class CoverShortfall {

        @Test
        void LIVING이_있으면_항목을_줄이고_확보분을_LIVING에_더한다() {
            WindfallAllocation.Result result = WindfallAllocation.coverShortfall(
                    100_000L, 50_000L, List.of(new WindfallAllocation.Line(300_000L, 40_000L)));

            assertThat(result.newTargetAmounts()).containsExactly(260_000L);
            assertThat(result.newLiving()).isEqualTo(90_000L);
        }

        @Test
        void LIVING이_없으면_항목_축소만_반영한다() {
            WindfallAllocation.Result result = WindfallAllocation.coverShortfall(
                    100_000L, null, List.of(new WindfallAllocation.Line(300_000L, 40_000L)));

            assertThat(result.newTargetAmounts()).containsExactly(260_000L);
            assertThat(result.newLiving()).isNull();
        }

        @Test
        void 축소합이_부족분을_넘으면_거부한다() {
            assertThatThrownBy(() -> WindfallAllocation.coverShortfall(
                            30_000L, 50_000L, List.of(new WindfallAllocation.Line(300_000L, 40_000L))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 항목을_0_미만으로_줄이면_거부한다() {
            assertThatThrownBy(() -> WindfallAllocation.coverShortfall(
                            100_000L, 50_000L, List.of(new WindfallAllocation.Line(10_000L, 40_000L))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
