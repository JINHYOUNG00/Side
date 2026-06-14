package com.jinhyoung.salary.cycle;

import java.time.LocalDate;
import java.util.List;

/**
 * 공휴일 공급 포트(CYCLE-01). 공공데이터포털 특일 API 등 외부 출처에서 한 해의 공휴일을 가져온다. 구현은
 * infra({@link com.jinhyoung.salary.cycle.infra.RestClientHolidayApiClient})에 두고, 캐싱·폴백 정책은
 * {@link HolidayCacheService}가 담당한다. 테스트는 이 포트를 스텁으로 대체한다(OAuthClient 패턴과 동형).
 */
public interface HolidayApiClient {

    /**
     * 주어진 연도의 공휴일 목록을 가져온다. 데이터 미제공·외부 장애 시 호출자가 폴백하도록 예외를 던질 수 있다.
     *
     * @param year 양력 연도(예: 2026)
     * @return 해당 연도 공휴일(날짜·이름). 공휴일이 없으면 빈 목록
     */
    List<RawHoliday> fetchHolidays(int year);

    /** API에서 받은 공휴일 1건(날짜·이름)의 출처 무관 표현. */
    record RawHoliday(LocalDate date, String name) {}
}
