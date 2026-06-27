package com.jinhyoung.salary.cycle.infra;

import com.jinhyoung.salary.cycle.domain.PlanLineType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * plan_lines 조회·저장(CYCLE-03). 사이클당 라인 목록을 다루며, 멱등 적재 자체는 cycles의
 * {@code unique(user_id, cycle_start)} 게이트가 보장한다({@link CycleRepository} 참조).
 */
public interface PlanLineRepository extends JpaRepository<PlanLine, Long> {

    List<PlanLine> findByCycleIdOrderByIdAsc(Long cycleId);

    /**
     * 여러 사이클의 특정 종류 라인을 한 번에(RPT-02 추이 리포트) — 사이클별 LIVING(생활비) 계획액을 모아 계획 vs
     * 실제를 산정할 때 쓴다. 사이클당 LIVING은 보통 1건이지만 호출자가 사이클 기준으로 합산한다.
     */
    List<PlanLine> findByCycleIdInAndLineType(Collection<Long> cycleIds, PlanLineType lineType);
}
