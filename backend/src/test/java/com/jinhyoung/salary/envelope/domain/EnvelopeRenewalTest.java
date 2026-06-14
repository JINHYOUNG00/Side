package com.jinhyoung.salary.envelope.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 봉투 주기 갱신 순수 계산 단위 테스트(ENV-05). */
class EnvelopeRenewalTest {

    @Test
    void 반복_주기가_있으면_반복형이고_없으면_일회성이다() {
        assertThat(EnvelopeRenewal.isRecurring((short) 12)).isTrue();
        assertThat(EnvelopeRenewal.isRecurring((short) 1)).isTrue();
        assertThat(EnvelopeRenewal.isRecurring(null)).isFalse();
    }

    @Test
    void 다음_지출일은_현재_지출일에서_주기만큼_뒤로_이동한다() {
        // 매년(12개월) 자동차세: 2027-01-10 → 2028-01-10
        assertThat(EnvelopeRenewal.nextDueAfterRenewal(LocalDate.of(2027, 1, 10), 12))
                .isEqualTo(LocalDate.of(2028, 1, 10));
    }

    @Test
    void 분기형은_세_달_뒤로_이동한다() {
        assertThat(EnvelopeRenewal.nextDueAfterRenewal(LocalDate.of(2026, 7, 1), 3))
                .isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void 한_달_주기는_다음_달로_이동한다() {
        assertThat(EnvelopeRenewal.nextDueAfterRenewal(LocalDate.of(2026, 6, 15), 1))
                .isEqualTo(LocalDate.of(2026, 7, 15));
    }

    @Test
    void 월말_지출일은_짧은_달에서_말일로_보정된다() {
        // 1/31 + 1개월 = 2/28(자바 plusMonths 말일 보정)
        assertThat(EnvelopeRenewal.nextDueAfterRenewal(LocalDate.of(2026, 1, 31), 1))
                .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void 주기가_1_미만이면_거부한다() {
        assertThatThrownBy(() -> EnvelopeRenewal.nextDueAfterRenewal(LocalDate.of(2027, 1, 10), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
