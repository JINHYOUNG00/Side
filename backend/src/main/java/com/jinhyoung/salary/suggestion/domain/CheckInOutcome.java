package com.jinhyoung.salary.suggestion.domain;

/**
 * 한 닫힌 사이클의 체크인 결과(SUG-02 룰 입력). 의존성 없는 순수 값(규칙 9). 연속 초과/잉여 패턴 판정의 한 점이다.
 *
 * <p>{@code overspend}는 체크인(RPT-01)이 계산·저장한 초과액(toppedUp − livingRemaining, 양수=초과·음수=잉여)이다.
 * 체크인이 없는 결측 사이클은 {@code overspend == null}로 들어와 streak을 <b>단절</b>시킨다(구현규칙 7장 — 결측은
 * 제외가 아니라 단절). 입력은 항상 닫힌 사이클(cycle_end 경과)만 담는다 — 진행 중 사이클은 체크인 전이라 제외한다.
 *
 * @param cycleLabel 사이클 라벨(예: "2026-05") — 제안 payload 디버깅·표시 보조
 * @param overspend 그 사이클 체크인의 초과액(원). 체크인 미수행(결측)이면 {@code null}
 */
public record CheckInOutcome(String cycleLabel, Long overspend) {

    /** 체크인이 기록돼 초과액을 알 수 있는지(= streak 판정에 쓸 수 있는 점인지). */
    public boolean hasCheckIn() {
        return overspend != null;
    }
}
