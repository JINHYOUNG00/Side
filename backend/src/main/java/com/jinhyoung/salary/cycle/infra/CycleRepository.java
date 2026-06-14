package com.jinhyoung.salary.cycle.infra;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사이클 조회·저장(CYCLE-02). {@code (user_id, cycle_start)}는 ERD unique 제약 — 스냅샷 생성 멱등성의
 * 근거다. 생성 전 존재 확인({@link #existsByUserIdAndCycleStart})으로 중복 생성을 막는다(규칙 8, CYCLE-03).
 */
public interface CycleRepository extends JpaRepository<Cycle, Long> {

    boolean existsByUserIdAndCycleStart(Long userId, LocalDate cycleStart);

    Optional<Cycle> findByUserIdAndCycleStart(Long userId, LocalDate cycleStart);

    /** 소유권 게이트(CYCLE-04) — 미소유·부재는 모두 빈 Optional로 다뤄 존재 여부를 노출하지 않는다(AccountService 패턴). */
    Optional<Cycle> findByIdAndUserId(Long id, Long userId);

    /**
     * 오늘이 속한 사이클(CYCLE-06) — {@code cycle_start ≤ today ≤ cycle_end}. 사이클은 맞닿게 연속이라
     * 한 날짜에 최대 1건이다. 없으면(미생성) 빈 Optional → 호출자가 NOT_FOUND로 다뤄 스냅샷 생성 동선으로 보낸다.
     */
    Optional<Cycle> findByUserIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(
            Long userId, LocalDate startBound, LocalDate endBound);
}
