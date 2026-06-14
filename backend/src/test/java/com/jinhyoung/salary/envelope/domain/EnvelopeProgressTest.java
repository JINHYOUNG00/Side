package com.jinhyoung.salary.envelope.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 봉투 진행률·D-day 순수 계산 단위 테스트(ENV-03). */
class EnvelopeProgressTest {

    @Test
    void 진행률은_적립액_나누기_목표액_퍼센트다() {
        assertThat(EnvelopeProgress.progressPercent(300_000, 600_000)).isEqualTo(50);
        assertThat(EnvelopeProgress.progressPercent(150_000, 600_000)).isEqualTo(25);
    }

    @Test
    void 진행률은_내림으로_충족_전엔_100이_되지_않는다() {
        // 99.6% → 99 (반올림이면 100이 떠 '다 모았다'처럼 보이는 것 방지).
        assertThat(EnvelopeProgress.progressPercent(598_000, 600_000)).isEqualTo(99);
        // 1원 모자라도 100이 아니다.
        assertThat(EnvelopeProgress.progressPercent(599_999, 600_000)).isEqualTo(99);
    }

    @Test
    void 적립액이_0이면_진행률_0() {
        assertThat(EnvelopeProgress.progressPercent(0, 600_000)).isZero();
    }

    @Test
    void 적립액이_목표_이상이면_진행률_100() {
        assertThat(EnvelopeProgress.progressPercent(600_000, 600_000)).isEqualTo(100);
        assertThat(EnvelopeProgress.progressPercent(700_000, 600_000)).isEqualTo(100);
    }

    @Test
    void 목표액이_0이하면_거부한다() {
        assertThatThrownBy(() -> EnvelopeProgress.progressPercent(100, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void Dday는_지출일_빼기_오늘_일수다() {
        LocalDate today = LocalDate.of(2026, 6, 25);
        assertThat(EnvelopeProgress.dDay(today, LocalDate.of(2026, 7, 5))).isEqualTo(10);
    }

    @Test
    void Dday는_당일이면_0이다() {
        LocalDate today = LocalDate.of(2026, 6, 25);
        assertThat(EnvelopeProgress.dDay(today, today)).isZero();
    }

    @Test
    void Dday는_지출일이_지났으면_음수다() {
        LocalDate today = LocalDate.of(2026, 6, 25);
        assertThat(EnvelopeProgress.dDay(today, LocalDate.of(2026, 6, 20))).isEqualTo(-5);
    }
}
