package com.jinhyoung.salary.envelope.infra;

/**
 * 봉투 지출 부족분 충당 출처(ERD envelope_transactions.shortfall_source, ENV-04). 적립액이 실제 지출액에
 * 모자랄 때 어디서 메웠는지 기록한다 — 모자란 금액 자체는 다른 도메인의 잔액을 건드리지 않고 출처만 남긴다.
 *
 * <ul>
 *   <li>{@code LIVING} — 생활비에서 충당.
 *   <li>{@code EMERGENCY} — 비상금에서 충당.
 * </ul>
 */
public enum ShortfallSource {
    LIVING,
    EMERGENCY
}
