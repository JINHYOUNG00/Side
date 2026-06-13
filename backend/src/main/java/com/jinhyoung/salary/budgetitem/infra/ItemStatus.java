package com.jinhyoung.salary.budgetitem.infra;

/**
 * 배분 항목 생명주기 상태(ERD budget_items.status). 영속 상태 마커라 infra에 둔다.
 *
 * <ul>
 *   <li>{@code ACTIVE} — 현재 폭포·스냅샷 대상
 *   <li>{@code ARCHIVED} — 만기 도래로 보관(ITEM-02 배치)
 *   <li>{@code DELETED} — soft delete(ITEM-09). 물리 삭제는 회원 탈퇴 cascade뿐
 * </ul>
 */
public enum ItemStatus {
    ACTIVE,
    ARCHIVED,
    DELETED
}
