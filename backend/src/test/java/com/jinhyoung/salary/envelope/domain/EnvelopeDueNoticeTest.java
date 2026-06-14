package com.jinhyoung.salary.envelope.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 봉투 지출 시기 알림 대상 판정(NOTI-02) 순수 단위 테스트. 윈도우 경계(D-0·D-LEAD 포함, D-(LEAD+1) 제외, 경과분
 * 제외)를 결정론적으로 고정한다. LEAD_DAYS=3 기준.
 */
class EnvelopeDueNoticeTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 14);

    @Test
    void 윈도우_상한은_오늘에서_LEAD_DAYS_뒤다() {
        assertThat(EnvelopeDueNotice.windowEnd(TODAY)).isEqualTo(LocalDate.of(2026, 6, 17));
    }

    @Test
    void 지출일이_오늘이면_대상이다() {
        assertThat(EnvelopeDueNotice.isDueSoon(TODAY, TODAY)).isTrue();
    }

    @Test
    void 지출일이_윈도우_마지막날이면_대상이다() {
        assertThat(EnvelopeDueNotice.isDueSoon(TODAY, LocalDate.of(2026, 6, 17)))
                .isTrue();
    }

    @Test
    void 지출일이_윈도우_하루_뒤면_대상이_아니다() {
        assertThat(EnvelopeDueNotice.isDueSoon(TODAY, LocalDate.of(2026, 6, 18)))
                .isFalse();
    }

    @Test
    void 지출일이_이미_지났으면_대상이_아니다() {
        assertThat(EnvelopeDueNotice.isDueSoon(TODAY, LocalDate.of(2026, 6, 13)))
                .isFalse();
    }
}
