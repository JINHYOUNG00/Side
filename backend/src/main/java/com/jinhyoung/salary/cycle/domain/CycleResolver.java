package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;

/**
 * 사이클 경계 산출 — 의존성 없는 순수 클래스(CYCLE-02, ArchUnit 규칙 9).
 *
 * <p>사이클은 <b>실제 지급일부터 다음 지급일 전날까지</b>다(요구사항 CYCLE-02). 두 경계 모두
 * {@link PaydayResolver}로 도출한다.
 *
 * <ol>
 *   <li>{@code cycleStart} = 시작 월의 실제 지급일
 *   <li>{@code cycleEnd} = 다음 달의 실제 지급일 − 1일
 *   <li>{@code label} = 시작 월("yyyy-MM") — 어느 달 월급 사이클인지(명목 월, {@link CycleDefinition} 참조)
 * </ol>
 *
 * <p><b>공휴일은 호출자가 주입한다</b>(PaydayResolver와 동일). 두 달의 지급일을 보므로, 주입하는
 * 집합은 시작 월의 영업일 조정과 다음 달의 영업일 조정이 닿는 구간을 모두 덮어야 한다. 빈 집합이면
 * 주말 회피만 수행된다(차년도 미제공·API 장애 폴백).
 */
public final class CycleResolver {

    private CycleResolver() {}

    /**
     * @param startMonth 시작 월(이 사이클이 어느 달 월급인지)
     * @param payday 월급일(1~31, 해당 월에 없는 날은 말일로 clamp)
     * @param adjustment 지급일 조정 규칙
     * @param holidays 공휴일 집합(KST 기준). 시작 월·다음 달 조정 구간을 덮어야 한다 — 호출자가 주입
     * @return 실제 지급일부터 다음 지급일 전날까지의 경계와 시작 월 라벨
     */
    public static CycleDefinition resolve(
            YearMonth startMonth, int payday, PaydayAdjustment adjustment, Set<LocalDate> holidays) {
        Objects.requireNonNull(startMonth, "startMonth");

        LocalDate cycleStart = PaydayResolver.resolve(startMonth, payday, adjustment, holidays);
        LocalDate nextStart = PaydayResolver.resolve(startMonth.plusMonths(1), payday, adjustment, holidays);
        LocalDate cycleEnd = nextStart.minusDays(1);

        return new CycleDefinition(cycleStart, cycleEnd, startMonth.toString());
    }
}
