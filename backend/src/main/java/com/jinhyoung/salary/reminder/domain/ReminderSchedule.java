package com.jinhyoung.salary.reminder.domain;

import java.time.LocalDate;

/**
 * 사용자 정의 리마인더의 다음 알림일 산술(NOTI-06). 프레임워크 의존 없는 순수 클래스(규칙 9) — 단위 테스트 필수.
 *
 * <p>발송 후 다음 알림일은 직전 알림일(anchor)에 주기를 더해 오늘보다 이후가 되는 첫 날로 정한다. 매 스텝을
 * anchor 기준으로 더해(anchor+months, anchor+2·months, …) 누적 드리프트를 피한다 — 예: 1/31 기준 1개월
 * 주기는 2/28·3/31·4/30…처럼 각 달 말일로 정렬된다({@link LocalDate#plusMonths}의 말일 보정). 배치가 며칠
 * 밀렸어도(다음 알림일이 과거로 누적) 한 번에 오늘 이후로 수렴해 발송이 한 주기에 1회만 나가게 한다.
 */
public final class ReminderSchedule {

    private ReminderSchedule() {}

    /**
     * 직전 알림일({@code anchor})과 주기({@code intervalMonths})로, 오늘({@code today}) 이후가 되는 다음 알림일을
     * 반환한다. anchor가 today보다 한참 과거여도 anchor 기준 배수로 더해 오늘 이후 첫 날로 수렴한다.
     *
     * @param anchor 직전(방금 발송한) 알림일
     * @param today 기준일(KST, 주입 Clock 산출) — 다음 알림일은 이 날보다 엄격히 이후여야 한다
     * @param intervalMonths 주기(개월), 1 이상
     * @return today 이후 첫 알림일
     */
    public static LocalDate nextAfter(LocalDate anchor, LocalDate today, int intervalMonths) {
        if (intervalMonths < 1) {
            throw new IllegalArgumentException("intervalMonths must be >= 1: " + intervalMonths);
        }
        long step = 1;
        LocalDate next = anchor.plusMonths((long) intervalMonths * step);
        while (!next.isAfter(today)) {
            step++;
            next = anchor.plusMonths((long) intervalMonths * step);
        }
        return next;
    }
}
