package com.jinhyoung.salary.reminder.domain;

import java.time.LocalDate;
import java.time.Month;
import java.util.Set;

/**
 * 분기 1회 외화 예수금 점검일 판정(NOTI-06). 프레임워크 의존 없는 순수 클래스(규칙 9) — 단위 테스트 필수.
 *
 * <p>외화 적립식(주식모으기, INVESTMENT)은 환율 API 연동·일별 차감 추적 없이 버퍼로 누적 오차를 흡수하므로
 * (ITEM-04), 분기마다 한 번 "예수금을 점검하라"고 알린다(개발계획 부가 기능). 점검일은 각 분기 첫날
 * (1·4·7·10월 1일)로 고정한다 — 분기당 정확히 하루뿐이라 일일 배치가 그날만 대상으로 잡고, 발송 대상일을
 * 그 날짜로 두면 NOTI-04 멱등 게이트가 분기당 1회만 보내게 한다. 점검 대상 "사용자" 판정(활성 INVESTMENT
 * 항목 보유)은 발송 서비스가 맡는다 — 이 클래스는 "오늘이 점검일인가"라는 날짜 판정만 한다.
 */
public final class QuarterlyCheckup {

    /** 분기 시작 월(1·4·7·10월). */
    private static final Set<Month> QUARTER_START_MONTHS =
            Set.of(Month.JANUARY, Month.APRIL, Month.JULY, Month.OCTOBER);

    private QuarterlyCheckup() {}

    /** 주어진 날짜가 분기 점검일(분기 첫날 = 1·4·7·10월 1일)인지 판정한다(KST 기준일은 호출자가 주입). */
    public static boolean isCheckupDay(LocalDate date) {
        return date.getDayOfMonth() == 1 && QUARTER_START_MONTHS.contains(date.getMonth());
    }
}
