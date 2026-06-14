package com.jinhyoung.salary.cycle;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.cycle.HolidayApiClient.RawHoliday;
import com.jinhyoung.salary.cycle.infra.Holiday;
import com.jinhyoung.salary.cycle.infra.HolidayRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 공휴일 캐시 적재(CYCLE-01) 통합 테스트. 특일 API 포트({@link HolidayApiClient})를 제어 가능한 스텁으로 대체해
 * 실 HTTP 없이 적재·멱등·폴백을 검증한다(OAuthClient 통합 테스트 패턴과 동형). 멱등: 이미 수집한 연도는
 * 재수집하지 않고 날짜 중복도 건너뛴다(규칙 8). 폴백: 외부 장애 시 예외를 삼켜 캐시를 비운 채 둔다(CYCLE-01).
 */
@SpringBootTest
@Testcontainers
@Import(HolidayCacheServiceIntegrationTest.StubConfig.class)
class HolidayCacheServiceIntegrationTest {

    /** 호출별로 반환·예외를 갈아끼우는 특일 API 스텁. @Primary로 실 RestClient 클라이언트를 가린다. */
    static class StubHolidayApiClient implements HolidayApiClient {
        List<RawHoliday> next = List.of();
        RuntimeException error = null;

        @Override
        public List<RawHoliday> fetchHolidays(int year) {
            if (error != null) {
                throw error;
            }
            return next;
        }
    }

    @TestConfiguration
    static class StubConfig {
        @Bean
        @Primary
        StubHolidayApiClient stubHolidayApiClient() {
            return new StubHolidayApiClient();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    HolidayCacheService holidayCacheService;

    @Autowired
    HolidayRepository holidayRepository;

    @Autowired
    StubHolidayApiClient stub;

    @BeforeEach
    void reset() {
        holidayRepository.deleteAll();
        stub.next = List.of();
        stub.error = null;
    }

    @Test
    void 미캐싱_연도는_특일API에서_수집해_적재한다() {
        stub.next = List.of(
                new RawHoliday(LocalDate.of(2030, 1, 1), "신정"), new RawHoliday(LocalDate.of(2030, 3, 1), "삼일절"));

        int inserted = holidayCacheService.ensureCached(2030);

        assertThat(inserted).isEqualTo(2);
        assertThat(holidayRepository.existsBySourceYear((short) 2030)).isTrue();
    }

    @Test
    void 이미_캐싱된_연도는_재수집하지_않는다() {
        stub.next = List.of(new RawHoliday(LocalDate.of(2030, 1, 1), "신정"));
        holidayCacheService.ensureCached(2030);

        int again = holidayCacheService.ensureCached(2030);

        assertThat(again).isZero();
        assertThat(holidayRepository.findByHolidayDateBetween(LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 1)))
                .hasSize(1);
    }

    @Test
    void 이미_적재된_날짜는_건너뛴다() {
        holidayRepository.save(Holiday.of(LocalDate.of(2031, 1, 1), "신정", 2029)); // 다른 source_year로 선적재
        stub.next = List.of(
                new RawHoliday(LocalDate.of(2031, 1, 1), "신정"), // 날짜 중복 → 스킵
                new RawHoliday(LocalDate.of(2031, 3, 1), "삼일절"));

        int inserted = holidayCacheService.ensureCached(2031);

        assertThat(inserted).isEqualTo(1);
    }

    @Test
    void API_장애_시_예외를_삼키고_캐시를_비운채_둔다() {
        stub.error = new RuntimeException("data.go.kr 5xx");

        int inserted = holidayCacheService.ensureCached(2032);

        assertThat(inserted).isZero();
        assertThat(holidayRepository.existsBySourceYear((short) 2032)).isFalse();
    }
}
