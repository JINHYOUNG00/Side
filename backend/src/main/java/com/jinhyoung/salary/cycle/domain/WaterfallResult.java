package com.jinhyoung.salary.cycle.domain;

import java.util.List;

/**
 * 폭포 캐스케이드 산출물(전부 원 단위 long). FLOW-01의 책임 범위 — 그룹·소계·잔액까지다.
 *
 * <p>정의(API명세 3장): {@code remaining = income − Σ(groups.subtotal) − envelopeContribution}.
 * remaining은 음수일 수 있고 여기서 0으로 clamp하지 않는다(초과 판정 overAllocated는 FLOW-02가
 * remaining&lt;0으로 읽는다). emergencyTotal은 EMERGENCY 라인 합으로, split{emergency, living}
 * 분배(FLOW-03)가 이 값을 소비한다 — 여기서 분배는 하지 않는다.
 *
 * @param income 이번 사이클 수입(원)
 * @param groups EMERGENCY·LIVING 제외, 표시 순서로 정렬된 그룹들(빈 카테고리 생략, 불변)
 * @param envelopeContribution 봉투 월할 적립 합계(입력으로 받은 값)
 * @param remaining 남는 돈(음수 가능)
 * @param emergencyTotal EMERGENCY 라인 합(FLOW-03 split이 소비)
 */
public record WaterfallResult(
        long income, List<WaterfallGroup> groups, long envelopeContribution, long remaining, long emergencyTotal) {}
