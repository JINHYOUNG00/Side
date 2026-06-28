package com.jinhyoung.salary.cycle.domain;

import java.util.List;

/**
 * 여윳돈/부족 배분 산술(CYCLE-05). 의존성 없는 순수 클래스(규칙 9) — 이번 사이클 plan_lines를 어떻게 조정할지
 * (금액 델타)만 계산하고, 영속화·소유권·상태 게이트는 서비스가 맡는다. 금액은 long 원 단위(규칙 2).
 *
 * <p>여윳돈/부족은 <b>이번 사이클의 이체 계획(plan_lines)</b>에만 반영한다 — 일회성 차액이라 반복 budget_items는
 * 건드리지 않는다(다음 사이클은 평소대로). 생활비(LIVING) 라인이 차액의 상대편이다:
 *
 * <ul>
 *   <li><b>WINDFALL</b>(여윳돈) — 실수령액 확인 시 차액은 전부 LIVING이 흡수해 있다(구현규칙 3장). 사용자가 그중
 *       일부/전부를 고른 항목들로 옮긴다: 각 대상 += 배분액, LIVING −= 합. 남는 차액은 LIVING에 그대로 둔다.
 *       LIVING이 원천이라 합은 LIVING 잔액 이하·차액(cap) 이하여야 한다.
 *   <li><b>SHORTFALL</b>(부족) — 실수령액이 줄어 LIVING이 모자라거나 사라졌다. 사용자가 고른 항목들을 줄여
 *       (각 대상 −= 축소액, 0 미만 불가) 부족분(cap)까지 확보한다. LIVING 라인이 있으면 확보분을 LIVING에 더하고,
 *       없으면(과배분) 항목 축소만으로 과배분을 줄인다.
 * </ul>
 *
 * <p>유효하지 않은 입력(비양수 배분액·cap 초과·0 미만 축소·빈 목록)은 {@link IllegalArgumentException}으로 알린다 —
 * 서비스가 이를 VALIDATION_FAILED로 변환한다.
 */
public final class WindfallAllocation {

    private WindfallAllocation() {}

    /**
     * 한 대상 라인의 현재 계획액과 사용자가 입력한 배분/축소액.
     *
     * @param currentPlanned 대상 plan_line의 현재 계획 금액(원)
     * @param amount 이 라인에 더하거나(WINDFALL) 뺄(SHORTFALL) 금액(원, 양수)
     */
    public record Line(long currentPlanned, long amount) {}

    /**
     * 적용 결과 — 대상 라인들의 새 계획액(입력 순서)과 LIVING의 새 계획액({@code null}이면 LIVING 미변경).
     */
    public record Result(List<Long> newTargetAmounts, Long newLiving) {}

    /**
     * 여윳돈 배분(WINDFALL) — 고른 항목들에 차액을 나눠 넣고 LIVING에서 같은 합을 뺀다. 합은 차액({@code cap})·LIVING
     * 잔액 이하여야 한다.
     *
     * @param cap 배분 가능한 최대 합(여윳돈 차액)
     * @param livingPlanned LIVING 라인의 현재 계획액(원천)
     * @param lines 대상 라인들(현재액·배분액)
     */
    public static Result distributeWindfall(long cap, long livingPlanned, List<Line> lines) {
        long total = totalOfPositiveAmounts(lines);
        if (total > cap) {
            throw new IllegalArgumentException("allocation exceeds windfall");
        }
        if (total > livingPlanned) {
            throw new IllegalArgumentException("allocation exceeds living balance");
        }
        List<Long> newTargets = lines.stream()
                .map(line -> line.currentPlanned() + line.amount())
                .toList();
        return new Result(newTargets, livingPlanned - total);
    }

    /**
     * 부족 충당(SHORTFALL) — 고른 항목들을 줄여 부족분({@code cap})까지 확보한다. 각 항목은 현재액 이하로만 줄일 수
     * 있다(0 미만 불가). LIVING 라인이 있으면 확보분을 더하고, 없으면 항목 축소만 반영한다.
     *
     * @param cap 줄일 수 있는 최대 합(부족 차액)
     * @param livingPlanned LIVING 라인의 현재 계획액. 라인이 없으면 {@code null}
     * @param lines 대상 라인들(현재액·축소액)
     */
    public static Result coverShortfall(long cap, Long livingPlanned, List<Line> lines) {
        long total = totalOfPositiveAmounts(lines);
        if (total > cap) {
            throw new IllegalArgumentException("reduction exceeds shortfall");
        }
        for (Line line : lines) {
            if (line.amount() > line.currentPlanned()) {
                throw new IllegalArgumentException("reduction below zero");
            }
        }
        List<Long> newTargets = lines.stream()
                .map(line -> line.currentPlanned() - line.amount())
                .toList();
        Long newLiving = livingPlanned == null ? null : livingPlanned + total;
        return new Result(newTargets, newLiving);
    }

    /** 배분/축소액은 모두 양수여야 하고 목록은 비어 있으면 안 된다. 합을 돌려준다. */
    private static long totalOfPositiveAmounts(List<Line> lines) {
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("no allocation lines");
        }
        long total = 0;
        for (Line line : lines) {
            if (line.amount() <= 0) {
                throw new IllegalArgumentException("non-positive amount");
            }
            total += line.amount();
        }
        return total;
    }
}
