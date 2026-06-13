package com.jinhyoung.salary.user.domain;

/**
 * 월급일 조정 규칙(SET-01, ERD users.payday_adjustment). 월급일이 주말·공휴일일 때 실제 지급일을
 * 어느 영업일로 옮길지 정한다 — 실제 지급일 산출(PaydayResolver, CYCLE-01)이 이 값을 사용한다.
 *
 * <p>프레임워크 비의존 순수 enum(CLAUDE.md 규칙 9). DB에는 이름 문자열로 저장(@Enumerated STRING).
 */
public enum PaydayAdjustment {
    /** 전 영업일로 이동(기본값). */
    PREV_BUSINESS_DAY,
    /** 후 영업일로 이동. */
    NEXT_BUSINESS_DAY,
    /** 조정하지 않음 — 월급일 그대로. */
    NONE
}
