package com.jinhyoung.salary.notification;

/**
 * 알림 발송 채널(ERD notification_logs.channel). {@code LOG}는 v1 기본·자리표시 채널({@link
 * LoggingNotificationSender}), {@code EMAIL}은 1차 실 발송 채널({@link EmailNotificationSender}, NOTI-05). WEB_PUSH /
 * FCM은 도입(Phase 8 등) 시 값으로 추가한다 — 발송하지 않는 채널을 미리 열거하지 않는다({@link NotificationType}와
 * 동일한 고아 값 회피).
 *
 * <p>채널은 발송 기록의 메타데이터일 뿐, 중복 방지 키(user_id, type, target_date)에는 포함되지 않는다(NOTI-04).
 */
public enum NotificationChannel {
    LOG,
    EMAIL
}
