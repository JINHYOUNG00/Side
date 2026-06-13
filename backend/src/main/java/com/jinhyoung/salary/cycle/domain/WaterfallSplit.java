package com.jinhyoung.salary.cycle.domain;

/**
 * 남는 돈 분배(FLOW-03) — remaining을 비상금/생활비로 나눈 결과. 의존성 없는 순수 값 객체.
 *
 * <p>규칙(구현규칙 3장, API명세 3장): {@code emergency = emergencyTotal}(EMERGENCY 항목은 실제
 * 배분이라 전액 보장), {@code living = remaining − emergencyTotal}. split 합은 항상 remaining과 같다.
 *
 * <p>living이 음수면({@code shortfall=true}) <b>과배분</b> — 빠질 것 다 빠진 남은 돈이 비상금
 * 설정액에도 못 미치는 상황이다. 이때 계산기는 <b>자동으로 분배를 결정하지 않는다</b>: living을
 * 음수 그대로(부족 신호로) 두고 shortfall만 세운다. 비상금을 줄일지·생활비를 줄일지·비율을 유지할지는
 * 사용자 판단이며, UI가 조정을 요청한다(FLOW-02 overAllocated·조정 후보와 연결). 부족액은
 * {@code -living}(= emergencyTotal − remaining).
 *
 * @param emergency 비상금(= emergencyTotal)
 * @param living 생활비(= remaining − emergencyTotal, 음수 가능)
 * @param shortfall living &lt; 0 — 과배분이라 사용자 조정 필요
 */
public record WaterfallSplit(long emergency, long living, boolean shortfall) {

    public WaterfallSplit {
        // 내부 불변식: shortfall은 living<0과 정확히 일치해야 한다(어느 경로로 만들어도 보장).
        if (shortfall != (living < 0)) {
            throw new IllegalArgumentException(
                    "shortfall은 living<0과 일치해야 한다: living=" + living + ", shortfall=" + shortfall);
        }
    }

    /** remaining·emergencyTotal에서 분배를 계산한다. emergencyTotal은 ≥0, remaining은 음수 가능. */
    public static WaterfallSplit of(long remaining, long emergencyTotal) {
        if (emergencyTotal < 0) {
            throw new IllegalArgumentException("emergencyTotal은 음수일 수 없다: " + emergencyTotal);
        }
        long living = Math.subtractExact(remaining, emergencyTotal);
        return new WaterfallSplit(emergencyTotal, living, living < 0);
    }

    /** FLOW-01 결과에서 바로 분배를 계산하는 편의 메서드(응답 조립용). */
    public static WaterfallSplit from(WaterfallResult result) {
        return of(result.remaining(), result.emergencyTotal());
    }
}
