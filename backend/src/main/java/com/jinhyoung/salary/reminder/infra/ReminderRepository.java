package com.jinhyoung.salary.reminder.infra;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 사용자 정의 리마인더 조회·저장(NOTI-06). 소유분 조회는 user_id를 함께 건다 — 소유권 검증을 데이터 접근
 * 계층에서 강제한다(아키텍처 8장). 통장(AccountRepository)·봉투(EnvelopeRepository)와 동일 패턴.
 */
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    /** 특정 상태(ACTIVE 등)의 리마인더 목록을 id 순으로(≈생성순). 삭제(DELETED)는 상태 인자로 거른다. */
    List<Reminder> findByUserIdAndStatusOrderByIdAsc(Long userId, ReminderStatus status);

    /** 소유권 + 상태 동시 검증 — 미소유·삭제·부재는 모두 empty(존재 여부를 노출하지 않음). */
    Optional<Reminder> findByIdAndUserIdAndStatus(Long id, Long userId, ReminderStatus status);

    /** 개수 상한(활성 리마인더, 구현규칙) 판정용 활성 리마인더 수. */
    long countByUserIdAndStatus(Long userId, ReminderStatus status);

    /**
     * 다음 알림일이 기준일 이하(도래)인 특정 상태 리마인더 — 사용자 정의 리마인더 발송 대상(NOTI-06). 전 사용자
     * 횡단 조회(배치)라 user_id를 걸지 않는다(소유권 불요 — 알림은 리마인더 소유자 본인에게만 발송).
     */
    List<Reminder> findByStatusAndNextRemindDateLessThanEqual(ReminderStatus status, LocalDate onOrBefore);
}
