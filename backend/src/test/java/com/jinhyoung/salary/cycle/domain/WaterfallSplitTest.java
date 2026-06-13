package com.jinhyoung.salary.cycle.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * WaterfallSplit 단위 테스트(FLOW-03) — 정상 분배, 경계(living 0), 과배분(shortfall), 입력 검증,
 * 불변식. 순수 JUnit/assertj만 사용. 실데이터 정상 케이스(생활비 356,107)는 WaterfallGoldenTest 소관.
 */
class WaterfallSplitTest {

    @Test
    void 정상이면_비상금_전액과_나머지_생활비로_나뉜다() {
        WaterfallSplit s = WaterfallSplit.of(556_107, 200_000);

        assertThat(s.emergency()).isEqualTo(200_000);
        assertThat(s.living()).isEqualTo(356_107);
        assertThat(s.shortfall()).isFalse();
    }

    @Test
    void split_합은_항상_remaining과_같다() {
        WaterfallSplit s = WaterfallSplit.of(556_107, 200_000);

        assertThat(s.emergency() + s.living()).isEqualTo(556_107);
    }

    @Test
    void 비상금이_0이면_생활비가_remaining_전부다() {
        WaterfallSplit s = WaterfallSplit.of(556_107, 0);

        assertThat(s.emergency()).isZero();
        assertThat(s.living()).isEqualTo(556_107);
        assertThat(s.shortfall()).isFalse();
    }

    @Test
    void 생활비가_정확히_0이면_부족이_아니다() {
        WaterfallSplit s = WaterfallSplit.of(200_000, 200_000);

        assertThat(s.living()).isZero();
        assertThat(s.shortfall()).isFalse();
    }

    @Test
    void 남은_돈이_비상금보다_작으면_생활비_음수에_shortfall_true() {
        // 남는 돈 100,000 < 비상금 200,000 — 과배분. 자동 분배 안 하고 부족만 노출.
        WaterfallSplit s = WaterfallSplit.of(100_000, 200_000);

        assertThat(s.emergency()).isEqualTo(200_000);
        assertThat(s.living()).isEqualTo(-100_000); // 부족액 = emergencyTotal − remaining = 100,000
        assertThat(s.shortfall()).isTrue();
    }

    @Test
    void remaining이_음수여도_split_합은_remaining과_같다() {
        WaterfallSplit s = WaterfallSplit.of(-50_000, 200_000);

        assertThat(s.living()).isEqualTo(-250_000);
        assertThat(s.shortfall()).isTrue();
        assertThat(s.emergency() + s.living()).isEqualTo(-50_000);
    }

    @Test
    void WaterfallResult에서_바로_분배할_수_있다() {
        WaterfallResult r = new WaterfallResult(2_473_110, java.util.List.of(), 0, 556_107, 200_000);

        WaterfallSplit s = WaterfallSplit.from(r);

        assertThat(s.emergency()).isEqualTo(200_000);
        assertThat(s.living()).isEqualTo(356_107);
    }

    @Test
    void 잘못된_입력은_거부된다() {
        // emergencyTotal 음수
        assertThatThrownBy(() -> WaterfallSplit.of(556_107, -1)).isInstanceOf(IllegalArgumentException.class);
        // 불변식 위반: shortfall이 living<0과 불일치하게 직접 생성
        assertThatThrownBy(() -> new WaterfallSplit(200_000, 356_107, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WaterfallSplit(200_000, -100_000, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
