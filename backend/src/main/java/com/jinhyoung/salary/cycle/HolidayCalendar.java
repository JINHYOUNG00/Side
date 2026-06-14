package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.infra.Holiday;
import com.jinhyoung.salary.cycle.infra.HolidayRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 캐시된 공휴일을 {@code Set<LocalDate>}로 제공하는 읽기 측 서비스(CYCLE-01). {@link
 * com.jinhyoung.salary.cycle.domain.PaydayResolver}에 주입할 값을 만든다 — 캐시가 비어 있으면 빈 집합을
 * 돌려주고, 그 경우 resolver는 주말 회피만 수행한다(차년도 미제공·API 장애 폴백).
 */
@Service
public class HolidayCalendar {

    private final HolidayRepository holidayRepository;

    public HolidayCalendar(HolidayRepository holidayRepository) {
        this.holidayRepository = holidayRepository;
    }

    /**
     * 해당 월의 지급일 조정에 필요한 공휴일을 읽는다. 조정이 월·연 경계를 넘을 수 있어(연휴) 전월 1일부터 다음 달
     * 말일까지 넉넉한 구간을 본다 — 실무상 어떤 영업일 이동도 이 범위를 벗어나지 않는다.
     */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidaysAround(YearMonth month) {
        LocalDate from = month.minusMonths(1).atDay(1);
        LocalDate to = month.plusMonths(1).atEndOfMonth();
        return holidayRepository.findByHolidayDateBetween(from, to).stream()
                .map(Holiday::getHolidayDate)
                .collect(Collectors.toUnmodifiableSet());
    }
}
