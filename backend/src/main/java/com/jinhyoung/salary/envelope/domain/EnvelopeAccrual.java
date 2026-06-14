package com.jinhyoung.salary.envelope.domain;

import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import com.jinhyoung.salary.cycle.domain.CycleResolver;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 봉투 월할 적립 계산 — 의존성 없는 순수 클래스(ENV-02, 구현규칙 1장, ArchUnit 규칙 9).
 *
 * <p>봉투는 월 적립액을 컬럼으로 저장하지 않고 <b>매 사이클 동적 계산</b>한다:
 *
 * <pre>
 *   월 적립액 = ceil((target_amount − saved_amount) ÷ 남은 사이클 수), 최소 1개월
 * </pre>
 *
 * <p><b>올림인 이유</b>(구현규칙 1장): 내림이면 마지막 달에 부족분이 생긴다. 올림으로 생긴 잉여는
 * 매 사이클 갱신된 {@code saved_amount}로 다시 계산하면서 자연히 흡수된다 — 마지막 사이클은 남은
 * 사이클 수 1로 잔여 전액({@code target − saved})을 적립하게 되므로 별도 보정이 필요 없다.
 *
 * <p><b>남은 사이클 수</b>는 오늘이 속한 사이클부터 {@code next_due_date}가 속한 사이클까지의 개수다
 * (이번 사이클 포함, 최소 1). 사이클 경계는 owner의 {@link CycleResolver}(CYCLE-02)를 그대로 재사용해
 * 구하고, 이 클래스는 "어느 사이클에 속하는지"를 판정해 개수만 센다. 공휴일은 호출자가 주입한다
 * (PaydayResolver·CycleResolver와 동일한 규약 — 규칙 3, KST {@code Clock}으로 오늘을 산출).
 */
public final class EnvelopeAccrual {

    private EnvelopeAccrual() {}

    /**
     * 이번 사이클의 월 적립액을 계산한다.
     *
     * @param targetAmount 목표 금액(원, &gt; 0)
     * @param savedAmount 현재 적립액(원, ≥ 0)
     * @param remainingCycles 남은 사이클 수(≥ 1) — {@link #remainingCycles}로 산출
     * @return {@code ceil((target − saved) ÷ remainingCycles)}. 이미 충족(saved ≥ target)이면 0
     */
    public static long monthlyAmount(long targetAmount, long savedAmount, int remainingCycles) {
        if (remainingCycles < 1) {
            throw new IllegalArgumentException("남은 사이클 수는 1 이상이어야 한다: " + remainingCycles);
        }
        long remaining = targetAmount - savedAmount;
        if (remaining <= 0) {
            return 0L;
        }
        return BigDecimal.valueOf(remaining)
                .divide(BigDecimal.valueOf(remainingCycles), 0, RoundingMode.CEILING)
                .longValueExact();
    }

    /**
     * 남은 사이클 수 = 오늘이 속한 사이클부터 {@code nextDueDate}가 속한 사이클까지의 개수(이번 포함, 최소 1).
     *
     * <p>지출일이 이미 지났거나(과거) 이번 사이클 안이면 1을 돌려준다 — 마지막 달은 잔여 전액 적립이라는
     * 의미와 맞물린다.
     *
     * @param today 기준일(호출자가 주입한 KST {@code Clock}으로 산출 — 규칙 3)
     * @param nextDueDate 봉투의 다음 지출일
     * @param payday 월급일(1~31, 해당 월에 없는 날은 말일로 clamp)
     * @param adjustment 지급일 조정 규칙
     * @param holidays 공휴일 집합(KST). 오늘·지출일 사이 사이클 경계를 덮어야 한다 — 호출자가 주입
     * @return 남은 사이클 수(≥ 1)
     */
    public static int remainingCycles(
            LocalDate today, LocalDate nextDueDate, int payday, PaydayAdjustment adjustment, Set<LocalDate> holidays) {
        Objects.requireNonNull(today, "today");
        Objects.requireNonNull(nextDueDate, "nextDueDate");

        YearMonth current = startMonthContaining(today, payday, adjustment, holidays);
        YearMonth due = startMonthContaining(nextDueDate, payday, adjustment, holidays);

        long months = ChronoUnit.MONTHS.between(current, due);
        return (int) Math.max(1L, months + 1L);
    }

    /**
     * 주어진 날짜가 속한 사이클의 명목 시작 월을 찾는다. 사이클은 빈틈·중복 없이 연속하므로(CYCLE-02)
     * 날짜를 포함하는 사이클은 정확히 하나다. 영업일 조정이 월 경계를 넘어 시작일이 전월로 밀리거나
     * 다음 달로 미뤄질 수 있어, 날짜의 달력 월 주변(전월·당월·익월) 후보를 확인한다.
     */
    private static YearMonth startMonthContaining(
            LocalDate date, int payday, PaydayAdjustment adjustment, Set<LocalDate> holidays) {
        YearMonth base = YearMonth.from(date);
        for (YearMonth candidate : List.of(base.plusMonths(1), base, base.minusMonths(1))) {
            CycleDefinition cycle = CycleResolver.resolve(candidate, payday, adjustment, holidays);
            if (!date.isBefore(cycle.cycleStart()) && !date.isAfter(cycle.cycleEnd())) {
                return candidate;
            }
        }
        throw new IllegalStateException("어느 사이클에도 속하지 않는 날짜: " + date);
    }
}
