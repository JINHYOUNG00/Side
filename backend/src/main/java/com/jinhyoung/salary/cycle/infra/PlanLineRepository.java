package com.jinhyoung.salary.cycle.infra;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * plan_lines 조회·저장(CYCLE-03). 사이클당 라인 목록을 다루며, 멱등 적재 자체는 cycles의
 * {@code unique(user_id, cycle_start)} 게이트가 보장한다({@link CycleRepository} 참조).
 */
public interface PlanLineRepository extends JpaRepository<PlanLine, Long> {

    List<PlanLine> findByCycleIdOrderByIdAsc(Long cycleId);
}
