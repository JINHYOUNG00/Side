package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.domain.CycleResolver;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 사이클 경계 산출 적용(CYCLE-02). 순수 계산은 {@link CycleResolver}에 위임하고, 이 서비스는 캐시된
 * 공휴일을 모아 주입하는 application 경계만 맡는다(계산 0줄). 캐시가 비어 있으면 {@link HolidayCalendar}가
 * 빈 집합을 돌려주고 resolver는 주말 회피만 수행한다(차년도 미제공·특일 API 장애 폴백, CYCLE-01과 동형).
 *
 * <p>사용자의 월급일·조정 규칙은 호출자가 넘긴다 — 이 서비스는 "어느 달 사이클"을 경계로 환산할 뿐,
 * 스냅샷 영속화(cycles·plan_lines 적재)는 CYCLE-03 소관이다.
 */
@Service
public class CycleService {

    private final HolidayCalendar holidayCalendar;

    public CycleService(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = holidayCalendar;
    }

    /**
     * 시작 월의 사이클 경계를 산출한다.
     *
     * @param startMonth 시작 월(어느 달 월급 사이클인지)
     * @param payday 월급일(1~31, 해당 월에 없는 날은 말일로 clamp)
     * @param adjustment 지급일 조정 규칙
     * @return 실제 지급일 ~ 다음 지급일 전날 경계와 시작 월 라벨
     */
    public CycleDefinition resolveCycle(YearMonth startMonth, int payday, PaydayAdjustment adjustment) {
        return CycleResolver.resolve(startMonth, payday, adjustment, holidaysSpanning(startMonth));
    }

    /**
     * 경계 산출은 시작 월과 다음 달 두 지급일을 본다. 다음 달의 영업일 조정이 그 다음 달까지 닿을 수 있어,
     * 두 달의 조회 창(각각 전월~익월)을 합쳐 넉넉한 구간의 공휴일을 모은다.
     */
    private Set<LocalDate> holidaysSpanning(YearMonth startMonth) {
        Set<LocalDate> first = holidayCalendar.holidaysAround(startMonth);
        Set<LocalDate> second = holidayCalendar.holidaysAround(startMonth.plusMonths(1));
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        Set<LocalDate> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }
}
