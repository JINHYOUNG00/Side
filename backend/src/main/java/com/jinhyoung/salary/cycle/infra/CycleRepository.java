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
}
