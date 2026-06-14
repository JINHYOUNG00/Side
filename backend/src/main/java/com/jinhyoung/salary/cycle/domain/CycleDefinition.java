package com.jinhyoung.salary.cycle.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * 한 사이클의 경계와 라벨(CYCLE-02). 의존성 없는 값 객체 — {@link CycleResolver}가 산출한다.
 *
 * <ul>
 *   <li>{@code cycleStart} — 실제 지급일(말일 clamp·영업일 조정 반영). ERD cycles.cycle_start
 *   <li>{@code cycleEnd} — 다음 지급일 전날. ERD cycles.cycle_end
 *   <li>{@code label} — 시작 월(이 사이클이 어느 달 월급인지) 기준 "yyyy-MM". ERD cycles.label
 * </ul>
 *
 * <p><b>label은 명목 시작 월</b>이다(실제 cycle_start의 달력 월이 아니라). 조정이 월 경계를
 * 넘어 cycle_start가 전월로 밀리는 드문 경우(예: 1일 + PREV + 신정 → 전월 말일)에도, 연속한
 * 두 사이클이 같은 라벨을 갖는 충돌을 피하기 위함이다.
 */
public record CycleDefinition(LocalDate cycleStart, LocalDate cycleEnd, String label) {

    public CycleDefinition {
        Objects.requireNonNull(cycleStart, "cycleStart");
        Objects.requireNonNull(cycleEnd, "cycleEnd");
        Objects.requireNonNull(label, "label");
        if (cycleEnd.isBefore(cycleStart)) {
            throw new IllegalArgumentException("cycleEnd는 cycleStart 이후여야 한다: " + cycleStart + " ~ " + cycleEnd);
        }
    }
}
