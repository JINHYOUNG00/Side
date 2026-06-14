package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.domain.PaydayResolver;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
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

    /**
     * 오늘이 어느 명목 월의 실제 지급일인지 판정한다. 영업일 조정(PREV/NEXT)이 월 경계를 넘으면 명목 월급일이 속한 달과
     * 오늘이 속한 달이 어긋날 수 있으므로(말일+NEXT가 다음 달 초로, 1일+PREV가 전월 말로 이동), 오늘 기준 전월·당월·익월
     * 세 후보 명목 월의 실지급일을 보고 하나라도 오늘과 같으면 그 명목 월을 돌려준다. 지급일은 약 한 달 간격이라 둘 이상이
     * 동시에 오늘과 같을 수 없다. 오늘이 지급일이 아니면 비어 있다.
     *
     * <p>지급일 알림(NOTI-01)과 지급일 스냅샷 트리거(CYCLE-03 후속)가 공유한다 — 전자는 발송 여부({@code isPresent})만
     * 보고, 후자는 어느 달 사이클을 박을지 결정하는 시작 월로 그대로 쓴다.
     *
     * @param today 기준일(호출자가 주입 KST {@code Clock}으로 산출 — 규칙 3)
     * @param payday 월급일(1~31, 해당 월에 없는 날은 말일로 clamp)
     * @param adjustment 지급일 조정 규칙
     * @return 오늘이 실지급일인 명목 월, 지급일이 아니면 {@link Optional#empty()}
     */
    public Optional<YearMonth> resolvePaydayMonth(LocalDate today, int payday, PaydayAdjustment adjustment) {
        YearMonth thisMonth = YearMonth.from(today);
        for (YearMonth nominalMonth : List.of(thisMonth.minusMonths(1), thisMonth, thisMonth.plusMonths(1))) {
            if (resolveActualPayday(nominalMonth, payday, adjustment).equals(today)) {
                return Optional.of(nominalMonth);
            }
        }
        return Optional.empty();
    }
}
