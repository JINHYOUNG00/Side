package com.jinhyoung.salary.envelope.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 봉투 조회·저장(ENV-01). 모든 조회는 user_id를 함께 건다 — 소유권 검증을 데이터 접근 계층에서
 * 강제한다(아키텍처 8장). 통장(AccountRepository)·항목(BudgetItemRepository)과 동일 패턴.
 */
public interface EnvelopeRepository extends JpaRepository<Envelope, Long> {

    /** 특정 상태(ACTIVE 등)의 봉투 목록을 id 순으로(≈생성순). 종료(CLOSED)·삭제(DELETED)는 상태 인자로 거른다. */
    List<Envelope> findByUserIdAndStatusOrderByIdAsc(Long userId, EnvelopeStatus status);

    /** 소유권 + 상태 동시 검증 — 미소유·종료·삭제·부재는 모두 empty(존재 여부를 노출하지 않음). */
    Optional<Envelope> findByIdAndUserIdAndStatus(Long id, Long userId, EnvelopeStatus status);

    /** 개수 상한(활성 봉투 50, 구현규칙 5장) 판정용 활성 봉투 수. */
    long countByUserIdAndStatus(Long userId, EnvelopeStatus status);

    /**
     * 다음 지출일이 [from, to] 구간(양끝 포함)에 드는 특정 상태 봉투 — 지출 시기 알림 대상 조회(NOTI-02).
     * 전 사용자 횡단 조회(배치)라 user_id를 걸지 않는다(소유권 검증 불요 — 알림은 봉투 소유자 본인에게만 발송).
     */
    List<Envelope> findByStatusAndNextDueDateBetween(EnvelopeStatus status, LocalDate from, LocalDate to);
}
