package com.jinhyoung.salary.account.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * 통장 조회·저장(SET-04). 모든 조회는 user_id를 함께 건다 — 소유권 검증을 데이터 접근 계층에서 강제(NFR 8장).
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /** 활성 통장 목록(정렬 순). 삭제(is_active=false)된 통장은 제외. */
    List<Account> findByUserIdAndActiveTrueOrderBySortOrderAsc(Long userId);

    /** 소유권 + 활성 동시 검증 — 미소유·삭제·부재는 모두 empty(존재 여부를 노출하지 않음). */
    Optional<Account> findByIdAndUserIdAndActiveTrue(Long id, Long userId);

    /** 개수 상한(통장 20, 구현규칙 6장) 판정용 활성 통장 수. */
    long countByUserIdAndActiveTrue(Long userId);

    /** 신규 통장의 sortOrder를 끝자리로 부여하기 위한 현재 최댓값(없으면 -1 → 신규는 0). */
    @Query("select coalesce(max(a.sortOrder), -1) from Account a where a.userId = :userId and a.active = true")
    int maxSortOrder(Long userId);
}
