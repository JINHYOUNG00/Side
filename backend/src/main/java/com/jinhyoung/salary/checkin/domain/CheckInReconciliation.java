package com.jinhyoung.salary.checkin.domain;

/**
 * 월말 체크인 초과액 보정 산술(RPT-01, ERD check_ins.overspend). 의존성 없는 순수 클래스 — 사이클 종료 시점의
 * 생활비 통장 잔액(livingRemaining)과 사이클 중 추가 투입액(toppedUp)만으로 초과액을 계산한다(규칙 9, 금액은
 * long 원 단위 — 규칙 2).
 *
 * <p>의미론: 생활비를 다 쓰고 통장에 {@code livingRemaining}이 남았는데, 모자라서 사이클 중 {@code toppedUp}만큼
 * 추가로 메웠다면, 계획 대비 실제 초과 사용액은 {@code toppedUp − livingRemaining}이다. 잔액만으로는 충당 후
 * 초과를 알 수 없어 추가 투입액을 보조 입력으로 받아 보정한다(API명세 6장).
 *
 * <ul>
 *   <li><b>초과</b>(overspend &gt; 0) — 충당한 금액이 남은 잔액보다 커서 계획을 넘겨 썼다.
 *   <li><b>정확</b>(overspend == 0) — 충당분과 남은 잔액이 같아 계획대로 맞았다.
 *   <li><b>잉여</b>(overspend &lt; 0) — 남은 잔액이 충당분보다 커서 계획보다 덜 썼다.
 * </ul>
 *
 * <p>이 값은 입력 시점 계획에 의존하는 스냅샷이라 계산값 비저장 원칙의 예외로 계산 후 저장한다(ERD 3장).
 */
public final class CheckInReconciliation {

    private CheckInReconciliation() {}

    /** 초과액 = toppedUp − livingRemaining (양수=초과, 0=정확, 음수=잉여). */
    public static long overspend(long livingRemaining, long toppedUp) {
        return toppedUp - livingRemaining;
    }
}
