package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

/**
 * 실제 지급일 산출 — 의존성 없는 순수 클래스(CYCLE-01, ArchUnit 규칙 9).
 *
 * <p>월급일(1~31)과 조정 규칙으로 특정 월의 실제 지급일을 결정한다. 2단계로 동작한다.
 *
 * <ol>
 *   <li><b>말일 clamp</b> — 해당 월에 없는 날짜(예: 월급일 31일의 2월)는 그 달의 말일로 간주한다.
 *   <li><b>영업일 조정</b> — 명목 지급일이 주말·공휴일이면 조정 규칙대로 이동한다. {@code NONE}은
 *       이동하지 않고, {@code PREV_BUSINESS_DAY}/{@code NEXT_BUSINESS_DAY}는 영업일이 될 때까지
 *       하루씩 뒤/앞으로 옮긴다(연휴·주말이 연속이면 모두 건너뛰며, 월 경계도 넘을 수 있다).
 * </ol>
 *
 * <p><b>공휴일은 호출자가 주입한다</b>(아키텍처.md 2장: domain은 값 객체만 받는 결정론적 코드).
 * 공공데이터포털 특일 API 수집·캐싱과 폴백 정책은 infra/배치의 몫이며, 여기서는 주어진 집합만
 * 본다. 따라서 <b>차년도 데이터 미제공·API 장애 시에는 호출자가 빈(또는 부분) 집합을 넘기고,
 * 그 경우 주말 회피만 수행된다</b>(CYCLE-01 폴백). 외부 의존이 없어 골든 테스트가 결정적이다.
 */
public final class PaydayResolver {

    private PaydayResolver() {}

    /**
     * @param month 대상 월(예: 2026-04)
     * @param payday 월급일(1~31). 해당 월에 없는 날은 말일로 clamp
     * @param adjustment 지급일 조정 규칙(주말·공휴일일 때)
     * @param holidays 공휴일 집합(KST 기준 날짜). 빈 집합이면 주말 회피만 — 호출자가 주입
     * @return 조정이 반영된 실제 지급일
     */
    public static LocalDate resolve(YearMonth month, int payday, PaydayAdjustment adjustment, Set<LocalDate> holidays) {
        Objects.requireNonNull(month, "month");
        Objects.requireNonNull(adjustment, "adjustment");
        Objects.requireNonNull(holidays, "holidays");
        if (payday < 1 || payday > 31) {
            throw new IllegalArgumentException("payday는 1~31 범위여야 한다: " + payday);
        }

        // 1단계 — 명목 지급일. 해당 월에 없는 날짜는 그 달 말일로 clamp(CYCLE-01).
        int dayOfMonth = Math.min(payday, month.lengthOfMonth());
        LocalDate nominal = month.atDay(dayOfMonth);

        // 2단계 — 조정 규칙. NONE은 명목일 그대로, PREV/NEXT는 영업일까지 ±1일 이동.
        return switch (adjustment) {
            case NONE -> nominal;
            case PREV_BUSINESS_DAY -> shiftToBusinessDay(nominal, -1, holidays);
            case NEXT_BUSINESS_DAY -> shiftToBusinessDay(nominal, 1, holidays);
        };
    }

    /** 영업일이 될 때까지 step(±1)만큼 이동. 연속한 공휴일·주말을 모두 건너뛴다. */
    private static LocalDate shiftToBusinessDay(LocalDate from, int step, Set<LocalDate> holidays) {
        LocalDate date = from;
        while (!isBusinessDay(date, holidays)) {
            date = date.plusDays(step);
        }
        return date;
    }

    /** 영업일 = 토·일이 아니고 공휴일 집합에 없는 날. */
    private static boolean isBusinessDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays.contains(date);
    }
}
