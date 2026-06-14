package com.jinhyoung.salary.envelope.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 봉투 지출 처리 순수 계산 단위 테스트(ENV-04). */
class EnvelopeSpendTest {

    @Test
    void 부족분은_실지출이_적립액을_넘는_만큼이다() {
        assertThat(EnvelopeSpend.shortfall(200_000, 300_000)).isEqualTo(100_000);
        assertThat(EnvelopeSpend.shortfall(300_000, 300_000)).isZero();
        assertThat(EnvelopeSpend.shortfall(500_000, 300_000)).isZero(); // 잉여면 부족 0
    }

    @Test
    void 잉여분은_적립액이_실지출을_넘는_만큼이다() {
        assertThat(EnvelopeSpend.surplus(500_000, 300_000)).isEqualTo(200_000);
        assertThat(EnvelopeSpend.surplus(300_000, 300_000)).isZero();
        assertThat(EnvelopeSpend.surplus(200_000, 300_000)).isZero(); // 부족이면 잉여 0
    }

    @Test
    void 잉여_이월이면_남는_적립액은_잉여분이다() {
        // carryOver=true → 잉여 200,000을 다음 지출까지 봉투에 남긴다.
        assertThat(EnvelopeSpend.savedAfterSpend(500_000, 300_000, true)).isEqualTo(200_000);
    }

    @Test
    void 잉여_회수면_봉투는_비워진다() {
        // carryOver=false → 잉여를 회수해 봉투는 0.
        assertThat(EnvelopeSpend.savedAfterSpend(500_000, 300_000, false)).isZero();
    }

    @Test
    void 정확히_지출하면_carryOver와_무관하게_0이다() {
        assertThat(EnvelopeSpend.savedAfterSpend(300_000, 300_000, true)).isZero();
        assertThat(EnvelopeSpend.savedAfterSpend(300_000, 300_000, false)).isZero();
    }

    @Test
    void 부족하게_지출하면_carryOver와_무관하게_0이다() {
        // 잉여가 없으므로 이월할 것이 없다 — 봉투는 소진되어 0.
        assertThat(EnvelopeSpend.savedAfterSpend(200_000, 300_000, true)).isZero();
        assertThat(EnvelopeSpend.savedAfterSpend(200_000, 300_000, false)).isZero();
    }

    @Test
    void 적립액이_0이어도_지출은_부족으로_소진된다() {
        assertThat(EnvelopeSpend.shortfall(0, 300_000)).isEqualTo(300_000);
        assertThat(EnvelopeSpend.savedAfterSpend(0, 300_000, false)).isZero();
    }
}
