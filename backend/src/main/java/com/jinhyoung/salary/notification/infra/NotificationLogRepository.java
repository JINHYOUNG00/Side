package com.jinhyoung.salary.notification.infra;

import com.jinhyoung.salary.notification.NotificationType;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;

/** 알림 발송 기록 조회·적재(NOTI-04). */
public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    /**
     * 동일 알림이 이미 발송됐는지 — 중복 방지 게이트의 정상 경로 스킵 판정(규칙 8 멱등). unique(user_id, type,
     * target_date)와 같은 키이며, 제약은 동시/경합 시의 DB 레벨 최종 가드다.
     */
    boolean existsByUserIdAndTypeAndTargetDate(long userId, NotificationType type, LocalDate targetDate);
}
