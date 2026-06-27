package com.jinhyoung.salary.checkin.infra;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 체크인 조회·저장(RPT-01). {@code cycle_id}는 ERD unique 제약 — 사이클당 1건 멱등성의 근거다. 기록 전
 * 존재 확인({@link #existsByCycleId})으로 중복 기록을 막는다(규칙 8).
 */
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByCycleId(Long cycleId);

    /**
     * 여러 사이클의 체크인을 한 번에(RPT-02 추이 리포트) — 사이클별 초과액을 모아 실제 소진액을 도출한다. 체크인이
     * 없는(결측) 사이클은 결과에 빠지므로 호출자가 사이클 기준으로 매핑해 미수행을 가린다.
     */
    List<CheckIn> findByCycleIdIn(Collection<Long> cycleIds);
}
