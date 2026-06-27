package com.jinhyoung.salary.notification;

/**
 * 알림 종류(ERD notification_logs.type). PAYDAY는 지급일 알림(NOTI-01), ENVELOPE_DUE는 봉투 지출 시기 알림
 * (NOTI-02 — 다음 지출일 전 준비 금액과 함께 발송), CHECK_IN은 월말 체크인 요청 알림(NOTI-03 — 다음 지급일 전일에
 * 발송)이다. MATURITY(SUG-01) 등은 각 요구사항이 도입될 때 값으로 추가한다 — 발송하지 않는 종류를 미리 열거하지
 * 않는다(고아 값 회피).
 */
public enum NotificationType {
    PAYDAY,
    ENVELOPE_DUE,
    CHECK_IN
}
