package com.jinhyoung.salary.cycle.infra;

import com.jinhyoung.salary.cycle.HolidayCacheService;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 공휴일 캐시 갱신 트리거(CYCLE-01, 구현규칙 8장 일일 배치의 첫 단계 "공휴일 캐시 확인"). 매일 04:00 KST에 올해와
 * 내년 공휴일이 캐싱돼 있는지 확인하고, 없으면 특일 API에서 수집한다. 트리거는 얇게 — 멱등·폴백은 {@link
 * HolidayCacheService}가 가진다(이미 캐싱된 연도는 스킵, 차년도 미제공 시 다음 날 재시도).
 *
 * <p>기준 연도는 주입된 KST {@code Clock}으로 산출 — {@code LocalDate.now()} 직접 호출 금지(규칙 3).
 */
@Component
public class HolidayRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(HolidayRefreshScheduler.class);

    private final HolidayCacheService holidayCacheService;
    private final Clock clock;

    public HolidayRefreshScheduler(HolidayCacheService holidayCacheService, Clock clock) {
        this.holidayCacheService = holidayCacheService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int currentYear = LocalDate.now(clock).getYear();
        int loaded = holidayCacheService.ensureCached(currentYear) + holidayCacheService.ensureCached(currentYear + 1);
        log.info(
                "Holiday refresh batch: {} holiday(s) newly cached for {} and {}",
                loaded,
                currentYear,
                currentYear + 1);
    }
}
