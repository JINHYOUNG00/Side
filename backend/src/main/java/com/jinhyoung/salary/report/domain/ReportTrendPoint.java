package com.jinhyoung.salary.report.domain;

/**
 * 추이 리포트의 한 사이클 점(RPT-02, API명세 6장). 사이클별 "계획 vs 실제"를 나타낸다 — 의존성 없는 순수
 * 계산이라 domain에 둔다(규칙 9).
 *
 * <p>계획(planned)은 그 사이클의 LIVING(생활비) 이체 계획액이고, 실제(actual)는 실제로 소진한 생활비다.
 * 체크인(RPT-01)이 입력한 초과액(overspend = toppedUp − livingRemaining)으로부터
 * {@code actual = planned + overspend}로 도출한다 — 계획보다 더 썼으면(overspend 양수) actual이 크고,
 * 남겼으면(음수) 작다. 체크인이 없는 사이클은 결측이라(예외 흐름 5.1) actual을 측정할 수 없어 {@code null}이며
 * {@code checkedIn=false}로 구분한다(리포트에서 결측 표시).
 *
 * <p>금액은 long(원) — double/float·반올림 없음(규칙 2). 라벨은 시작 월 기준 문자열(cycles.label).
 *
 * @param label 사이클 라벨(예: "2026-05")
 * @param planned 계획 생활비(LIVING 계획액, 원)
 * @param actual 실제 소진 생활비(원). 체크인 미수행이면 {@code null}
 * @param checkedIn 그 사이클에 체크인이 기록됐는지(= actual 측정 가능 여부)
 */
public record ReportTrendPoint(String label, long planned, Long actual, boolean checkedIn) {

    /**
     * 사이클 라벨·계획액·초과액으로 추이 점을 만든다. {@code overspend}가 {@code null}이면 체크인 미수행(결측)으로
     * actual을 비우고 {@code checkedIn=false}로 둔다. 값이 있으면 {@code actual = planned + overspend}로 실제
     * 소진액을 도출한다(양수=초과, 음수=잉여).
     *
     * @param overspend 체크인이 계산·저장한 초과액(toppedUp − livingRemaining). 미수행이면 {@code null}
     */
    public static ReportTrendPoint of(String label, long planned, Long overspend) {
        if (overspend == null) {
            return new ReportTrendPoint(label, planned, null, false);
        }
        return new ReportTrendPoint(label, planned, planned + overspend, true);
    }
}
