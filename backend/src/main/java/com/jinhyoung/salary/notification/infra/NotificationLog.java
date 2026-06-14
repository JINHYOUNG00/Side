package com.jinhyoung.salary.notification.infra;

import com.jinhyoung.salary.notification.NotificationChannel;
import com.jinhyoung.salary.notification.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 알림 발송 기록(ERD notification_logs, NOTI-04). 한 사용자에게 같은 종류·같은 대상일의 알림이 두 번 나가지 않도록
 * {@code unique(user_id, type, target_date)} 제약을 둔다(규칙 8 멱등). {@link DeduplicatingNotificationSender}가
 * "기록 먼저, 발송 후 확정" 순서로 이 행을 적재한다.
 *
 * <p>엔티티는 infra에 둔다(아키텍처 v1.1) — domain은 순수 계산만. 발송 일시는 주입된 {@code Clock}으로 산출해
 * 넘긴다({@code Instant.now()} 직접 호출 금지 — 규칙 3).
 */
@Entity
@Table(name = "notification_logs")
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private NotificationChannel channel;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    protected NotificationLog() {
        // JPA
    }

    private NotificationLog(
            long userId, NotificationType type, LocalDate targetDate, NotificationChannel channel, Instant sentAt) {
        this.userId = userId;
        this.type = type;
        this.targetDate = targetDate;
        this.channel = channel;
        this.sentAt = sentAt;
    }

    public static NotificationLog of(
            long userId, NotificationType type, LocalDate targetDate, NotificationChannel channel, Instant sentAt) {
        return new NotificationLog(userId, type, targetDate, channel, sentAt);
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public NotificationChannel getChannel() {
        return channel;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
