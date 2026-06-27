package com.jinhyoung.salary.checkin.infra;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 체크인 조회·저장(RPT-01). {@code cycle_id}는 ERD unique 제약 — 사이클당 1건 멱등성의 근거다. 기록 전
 * 존재 확인({@link #existsByCycleId})으로 중복 기록을 막는다(규칙 8).
 */
public interface CheckInRepository extends JpaRepository<CheckIn, Long> {

    boolean existsByCycleId(Long cycleId);
}
