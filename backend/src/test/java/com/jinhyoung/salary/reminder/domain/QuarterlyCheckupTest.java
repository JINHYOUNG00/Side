package com.jinhyoung.salary.reminder.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 분기 외화 점검일 판정(NOTI-06) 단위 테스트. 점검일은 각 분기 첫날(1·4·7·10월 1일)뿐이고 그 외는 아님을 본다.
 */
class QuarterlyCheckupTest {

    @Test
    void 분기_첫날은_점검일이다() {
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 1, 1))).isTrue();
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 4, 1))).isTrue();
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 7, 1))).isTrue();
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 10, 1))).isTrue();
    }

    @Test
    void 분기_첫날이_아니면_점검일이_아니다() {
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 1, 2))).isFalse(); // 분기 첫달이지만 2일
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 2, 1))).isFalse(); // 분기 첫날 아닌 달의 1일
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 3, 1))).isFalse();
        assertThat(QuarterlyCheckup.isCheckupDay(LocalDate.of(2026, 12, 1))).isFalse();
    }
}
