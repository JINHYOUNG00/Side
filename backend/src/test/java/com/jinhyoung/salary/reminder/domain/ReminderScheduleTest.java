package com.jinhyoung.salary.reminder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 사용자 정의 리마인더 다음 알림일 산술(NOTI-06) 단위 테스트. 발송 후 알림일은 직전 알림일(anchor)에 주기를
 * 더해 오늘 이후가 되는 첫 날이며, anchor 기준 배수로 더해 누적 드리프트가 없고 밀린 알림도 한 번에 수렴함을 본다.
 */
class ReminderScheduleTest {

    @Test
    void 발송_당일이면_한_주기_뒤로_미룬다() {
        // anchor=오늘이라 다음 알림일은 오늘보다 엄격히 이후여야 한다 → +주기.
        LocalDate next = ReminderSchedule.nextAfter(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10), 3);

        assertThat(next).isEqualTo(LocalDate.of(2026, 4, 10));
    }

    @Test
    void 배치가_여러_주기_밀렸어도_오늘_이후_첫_날로_수렴한다() {
        // anchor=1/10, 주기 3개월, 오늘=5/15 → 4/10(≤오늘)을 건너뛰고 7/10.
        LocalDate next = ReminderSchedule.nextAfter(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 5, 15), 3);

        assertThat(next).isEqualTo(LocalDate.of(2026, 7, 10));
    }

    @Test
    void 말일_anchor는_각_달_말일로_정렬돼_드리프트가_없다() {
        // anchor=1/31, 주기 1개월, 오늘=2/28 → 2/28(≤오늘)을 건너뛰고 anchor+2개월=3/31(드리프트면 3/28).
        LocalDate next = ReminderSchedule.nextAfter(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), 1);

        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void 주기가_1개월_미만이면_예외() {
        assertThatThrownBy(() -> ReminderSchedule.nextAfter(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
