package com.jinhyoung.salary.notification;

import java.time.LocalDate;

/**
 * 알림 발송 포트(아키텍처 3장 notification 패키지의 NotificationSender). NOTI-01은 "누구에게 어떤 알림을 보낼지"
 * 판정만 하고, "어떻게 보낼지"(채널)는 이 포트 뒤로 숨긴다.
 *
 * <p>v1 기본 구현은 {@link LoggingNotificationSender}(로그). 실제 채널(이메일/웹푸시) 어댑터는 NOTI-05가,
 * 동일 알림 중복 발송 차단(notification_logs 기록 우선 — 멱등, 규칙 8)은 {@link DeduplicatingNotificationSender}가
 * 이 포트를 감싸 도입한다(NOTI-04). 인자 (type, userId, targetDate)는 unique(user_id, type, target_date) 키와 그대로 맞춰 둔다.
 */
public interface NotificationSender {

    /**
     * 한 사용자에게 알림 1건을 발송한다.
     *
     * @param type 알림 종류
     * @param userId 수신 사용자 id
     * @param targetDate 알림 대상일(지급일 알림이면 실제 지급일)
     */
    void send(NotificationType type, long userId, LocalDate targetDate);

    /**
     * 이 구현이 사용하는 발송 채널. 중복 방지 게이트({@link DeduplicatingNotificationSender})가 발송 기록
     * (notification_logs.channel)에 남길 값으로 쓴다 — 채널은 구현이 스스로 밝힌다.
     */
    NotificationChannel channel();
}
