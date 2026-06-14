package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.domain.PaydayResolver;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 실제 지급일 산출 적용(CYCLE-01). 순수 계산은 {@link PaydayResolver}에 위임하고, 이 서비스는 캐시된 공휴일을
 * 모아 주입하는 application 경계만 맡는다(계산 0줄). 캐시가 비어 있으면 {@link HolidayCalendar}가 빈 집합을
 * 돌려주고 resolver는 주말 회피만 수행한다 — 차년도 미제공·특일 API 장애 시의 폴백이 설계상 공짜로 모델링된다.
 */
@Service
public class PaydayService {

    private final HolidayCalendar holidayCalendar;

    public PaydayService(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = holidayCalendar;
    }

    /**
     * 특정 월의 실제 지급일을 산출한다.
     *
     * @param month 대상 월
     * @param payday 월급일(1~31, 해당 월에 없는 날은 말일로 clamp)
     * @param adjustment 지급일 조정 규칙
     * @return 말일 clamp·영업일 조정이 반영된 실제 지급일
     */
    public LocalDate resolveActualPayday(YearMonth month, int payday, PaydayAdjustment adjustment) {
        Set<LocalDate> holidays = holidayCalendar.holidaysAround(month);
        return PaydayResolver.resolve(month, payday, adjustment, holidays);
    }
}
