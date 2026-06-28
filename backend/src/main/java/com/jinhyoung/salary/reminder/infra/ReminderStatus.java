package com.jinhyoung.salary.reminder.infra;

/**
 * 사용자 정의 리마인더 상태(ERD reminders.status, NOTI-06). 영속 상태 마커라 infra에 둔다.
 *
 * <ul>
 *   <li>{@code ACTIVE} — 일일 배치가 다음 알림일 도래 시 발송 대상으로 삼는다
 *   <li>{@code DELETED} — soft delete(규칙 5). 행은 잔존하며 물리 삭제는 회원 탈퇴 cascade뿐
 * </ul>
 */
public enum ReminderStatus {
    ACTIVE,
    DELETED
}
