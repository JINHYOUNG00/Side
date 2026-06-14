package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.HolidayApiClient.RawHoliday;
import com.jinhyoung.salary.cycle.infra.Holiday;
import com.jinhyoung.salary.cycle.infra.HolidayRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 공휴일 캐시 적재(CYCLE-01). 공공데이터포털 특일 API에서 연 1회 수집해 holidays 테이블에 적재한다.
 *
 * <p><b>멱등(규칙 8)</b>: 이미 수집한 연도({@code source_year} 존재)는 다시 가져오지 않고, 적재 시에도
 * {@code holiday_date} 중복은 건너뛴다. <b>폴백(CYCLE-01)</b>: 외부 장애·차년도 미제공으로 수집이 실패하면
 * 예외를 삼키고 캐시를 그대로 둔다 — 그 해는 캐시가 비어 PaydayResolver가 주말 회피만 수행하고, 다음 배치가
 * 재시도한다.
 */
@Service
public class HolidayCacheService {

    private static final Logger log = LoggerFactory.getLogger(HolidayCacheService.class);

    private final HolidayApiClient holidayApiClient;
    private final HolidayRepository holidayRepository;

    public HolidayCacheService(HolidayApiClient holidayApiClient, HolidayRepository holidayRepository) {
        this.holidayApiClient = holidayApiClient;
        this.holidayRepository = holidayRepository;
    }

    /**
     * 해당 연도가 아직 캐싱되지 않았으면 특일 API에서 수집해 적재한다. 이미 캐싱됐으면 아무것도 하지 않는다(연 1회).
     *
     * @return 새로 적재한 공휴일 수(이미 캐싱됨·수집 실패 시 0)
     */
    @Transactional
    public int ensureCached(int year) {
        if (holidayRepository.existsBySourceYear((short) year)) {
            return 0;
        }
        List<RawHoliday> fetched;
        try {
            fetched = holidayApiClient.fetchHolidays(year);
        } catch (RuntimeException e) {
            // 차년도 미제공·API 장애: 캐시를 비운 채로 둬서 주말 회피 폴백으로 동작하게 하고 다음 배치가 재시도.
            log.warn("Holiday fetch failed for year {} — falling back to weekend-only resolution", year, e);
            return 0;
        }
        int inserted = 0;
        for (RawHoliday raw : fetched) {
            if (holidayRepository.existsByHolidayDate(raw.date())) {
                continue;
            }
            holidayRepository.save(Holiday.of(raw.date(), raw.name(), year));
            inserted++;
        }
        log.info("Holiday cache: {} holiday(s) loaded for year {}", inserted, year);
        return inserted;
    }
}
