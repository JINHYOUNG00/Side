package com.jinhyoung.salary.cycle.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

    /**
     * 최근 사이클 N건(RPT-02 추이 리포트) — 시작일 내림차순으로 {@code Pageable}이 정한 만큼 가져온다. 호출자가
     * 차트용으로 시간순(오래된→최근)으로 뒤집는다. 소유권은 user_id로 건다(데이터 접근 계층 강제).
     */
    List<Cycle> findByUserIdOrderByCycleStartDesc(Long userId, Pageable pageable);

    /**
     * 최근 <b>닫힌</b> 사이클 N건(SUG-02 보정 제안) — {@code cycle_end < today}인 사이클만 최신순으로. 진행 중
     * 사이클은 아직 체크인 전이라 결측으로 오인되어 streak을 단절시키므로 제외한다(구현규칙 7장). 소유권은 user_id로
     * 건다. 호출자는 가져온 만큼으로 연속 초과/잉여 패턴을 판정한다.
     */
    List<Cycle> findByUserIdAndCycleEndLessThanOrderByCycleStartDesc(Long userId, LocalDate today, Pageable pageable);
}
