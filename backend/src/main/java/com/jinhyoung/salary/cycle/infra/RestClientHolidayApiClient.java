package com.jinhyoung.salary.cycle.infra;

import com.jinhyoung.salary.cycle.HolidayApiClient;
import com.jinhyoung.salary.cycle.HolidayProperties;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 공공데이터포털 특일 API(getRestDeInfo) 기반 공휴일 공급(CYCLE-01). 한 해를 월별로 조회해 {@code isHoliday=Y}
 * 인 날만 추린다. 응답 JSON의 items는 0건일 때 빈 문자열, 1건일 때 객체, 다건일 때 배열로 오므로 방어적으로 파싱한다.
 *
 * <p>HTTP·파싱 실패는 {@link org.springframework.web.client.RestClientException}으로 전파되며, 폴백
 * 처리는 {@link com.jinhyoung.salary.cycle.HolidayCacheService}가 담당한다(주말 회피만 수행).
 */
public class RestClientHolidayApiClient implements HolidayApiClient {

    private static final DateTimeFormatter LOCDATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final HolidayProperties properties;

    public RestClientHolidayApiClient(RestClient restClient, HolidayProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public List<RawHoliday> fetchHolidays(int year) {
        List<RawHoliday> holidays = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            holidays.addAll(fetchMonth(year, month));
        }
        return holidays;
    }

    private List<RawHoliday> fetchMonth(int year, int month) {
        // serviceKey(공공데이터포털 일반 인증키)는 이미 URL 인코딩돼 있어 build(true)로 재인코딩을 막는다.
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUri())
                .queryParam("serviceKey", properties.serviceKey())
                .queryParam("solYear", year)
                .queryParam("solMonth", String.format("%02d", month))
                .queryParam("numOfRows", 100)
                .queryParam("_type", "json")
                .build(true)
                .toUri();
        Map<String, Object> response = restClient.get().uri(uri).retrieve().body(MAP_TYPE);
        return parseItems(response);
    }

    @SuppressWarnings("unchecked")
    private List<RawHoliday> parseItems(Map<String, Object> response) {
        Object body = nested(response, "response", "body");
        if (!(body instanceof Map<?, ?> bodyMap)) {
            return List.of();
        }
        Object itemsObj = bodyMap.get("items");
        if (!(itemsObj instanceof Map<?, ?> itemsMap)) {
            return List.of(); // totalCount=0이면 items가 빈 문자열로 온다
        }
        Object item = itemsMap.get("item");
        List<Map<String, Object>> rows;
        if (item instanceof List<?> list) {
            rows = (List<Map<String, Object>>) list;
        } else if (item instanceof Map<?, ?> single) {
            rows = List.of((Map<String, Object>) single);
        } else {
            return List.of();
        }

        List<RawHoliday> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (!"Y".equals(String.valueOf(row.get("isHoliday")))) {
                continue;
            }
            LocalDate date = LocalDate.parse(String.valueOf(row.get("locdate")), LOCDATE);
            String name = String.valueOf(row.getOrDefault("dateName", ""));
            result.add(new RawHoliday(date, name));
        }
        return result;
    }

    private static Object nested(Map<String, Object> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> m)) {
                return null;
            }
            current = m.get(key);
        }
        return current;
    }
}
