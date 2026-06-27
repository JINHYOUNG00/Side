package com.jinhyoung.salary.suggestion.infra;

/**
 * 제안 처리 상태(ERD suggestions.status). PENDING으로 생성돼, 사용자가 반영하면 APPLIED, 닫으면 DISMISSED로
 * 전이한다(단방향, 한 번 해소되면 되돌리지 않음). PENDING만 사용자에게 노출하고 중복 방지(dedup) 대상이다.
 */
public enum SuggestionStatus {
    PENDING,
    APPLIED,
    DISMISSED
}
