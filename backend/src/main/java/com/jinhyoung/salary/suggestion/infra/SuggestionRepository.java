package com.jinhyoung.salary.suggestion.infra;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 제안 조회·저장(SUG-02·SUG-03). 모든 조회는 user_id를 함께 건다 — 소유권을 데이터 접근 계층에서 강제한다
 * (아키텍처 8장). 중복 방지는 사용자별 PENDING 제안의 dedup 키 집합으로 서비스가 판정한다.
 */
public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    /** 사용자의 특정 상태 제안 목록(중복 방지 키 산출용 PENDING 조회 등). */
    List<Suggestion> findByUserIdAndStatus(Long userId, SuggestionStatus status);

    /** 사용자에게 노출할 제안 목록 — 최신순(GET /suggestions). */
    List<Suggestion> findByUserIdAndStatusOrderByIdDesc(Long userId, SuggestionStatus status);

    /** 소유권 게이트(반영·닫기) — 미소유·부재는 empty(존재 여부 비노출). */
    Optional<Suggestion> findByIdAndUserId(Long id, Long userId);
}
