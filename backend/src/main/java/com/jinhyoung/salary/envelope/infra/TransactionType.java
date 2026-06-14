package com.jinhyoung.salary.envelope.infra;

/**
 * 봉투 트랜잭션 종류(ERD envelope_transactions.type).
 *
 * <ul>
 *   <li>{@code DEPOSIT} — 적립. 체크리스트 ENVELOPE 라인 DONE 시 생성(CYCLE-07).
 *   <li>{@code SPEND} — 지출. 실제 지출액·충당 출처·이월 여부를 함께 기록(ENV-04).
 * </ul>
 */
public enum TransactionType {
    DEPOSIT,
    SPEND
}
