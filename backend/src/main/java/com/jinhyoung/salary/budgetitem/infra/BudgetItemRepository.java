package com.jinhyoung.salary.budgetitem.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 배분 항목 조회·저장(ITEM-01). 모든 조회는 user_id를 함께 건다 — 소유권 검증을 데이터 접근 계층에서
 * 강제한다(아키텍처 8장). 통장(AccountRepository)과 동일 패턴.
 */
public interface BudgetItemRepository extends JpaRepository<BudgetItem, Long> {

    /** 특정 상태(ACTIVE 등)의 항목 목록을 정렬 순으로. 삭제(DELETED)·보관(ARCHIVED)은 상태 인자로 거른다. */
    List<BudgetItem> findByUserIdAndStatusOrderBySortOrderAsc(Long userId, ItemStatus status);

    /** 소유권 + 상태 동시 검증 — 미소유·비활성·부재는 모두 empty(존재 여부를 노출하지 않음). */
    Optional<BudgetItem> findByIdAndUserIdAndStatus(Long id, Long userId, ItemStatus status);

    /** 개수 상한(활성 항목 100, 구현규칙 6장) 판정용 활성 항목 수. */
    long countByUserIdAndStatus(Long userId, ItemStatus status);

    /** 신규 항목의 sortOrder를 끝자리로 부여하기 위한 현재 최댓값(없으면 -1 → 신규는 0). */
    @Query("select coalesce(max(b.sortOrder), -1) from BudgetItem b"
            + " where b.userId = :userId and b.status = com.jinhyoung.salary.budgetitem.infra.ItemStatus.ACTIVE")
    int maxSortOrder(Long userId);
}
