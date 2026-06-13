package com.jinhyoung.salary.budgetitem.domain;

/**
 * 배분 항목 카테고리(ITEM-01). 순수 enum — 프레임워크 의존 없음(ArchUnit 규칙 9).
 *
 * <p>LIVING(생활비)은 항목용으로 두지 않는다 — 생활비는 항상 폭포의 나머지 계산값이며 이체처는
 * users.living_account_id로 지정한다(ERD budget_items 주석). 따라서 클라이언트가 LIVING을 보내면
 * 이 enum으로 역직렬화되지 않아 VALIDATION_FAILED가 된다.
 */
public enum Category {
    SAVING,
    INVESTMENT,
    FIXED,
    INSURANCE,
    SUBSCRIPTION,
    EMERGENCY
}
