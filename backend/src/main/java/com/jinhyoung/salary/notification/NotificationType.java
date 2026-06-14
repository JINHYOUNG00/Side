package com.jinhyoung.salary.notification;

/**
 * 알림 종류(ERD notification_logs.type). NOTI-01은 지급일 알림(PAYDAY)만 판정·발송한다. ENVELOPE_DUE(NOTI-02)·
 * CHECK_IN(NOTI-03)·MATURITY(SUG-01) 등은 각 요구사항이 도입될 때 값으로 추가한다 — 발송하지 않는 종류를 미리
 * 열거하지 않는다(고아 값 회피).
 */
public enum NotificationType {
    PAYDAY
}
