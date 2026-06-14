package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.notification.infra.NotificationLog;
import com.jinhyoung.salary.notification.infra.NotificationLogRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 알림 중복 발송 방지 게이트(NOTI-04). 실제 채널({@link LoggingNotificationSender}, NOTI-05가 이메일 어댑터로 대체)을
 * 감싸, 같은 (user, type, target_date) 알림이 두 번 나가지 않게 한다(규칙 8 멱등 — notification_logs
 * unique(user_id, type, target_date)).
 *
 * <p>아키텍처 4장의 "기록 먼저, 발송 후 확정" 순서를 {@code REQUIRES_NEW} 트랜잭션으로 구현한다: ① 이미 보낸 기록이
 * 있으면 스킵(정상 경로 멱등) ② 없으면 발송 기록을 적재한 뒤 ③ 채널로 발송하고 ④ 커밋으로 확정한다. 발송이 예외로
 * 실패하면 이 트랜잭션이 롤백돼 기록이 남지 않으므로 다음 일일 배치에서 재시도된다(중복 발송보다 누락-후-재시도를 택함).
 * 호출자({@link PaydayNotificationService})가 readOnly 트랜잭션이라도 {@code REQUIRES_NEW}로 독립 쓰기 트랜잭션을
 * 열어 사용자별로 원자적으로 처리한다.
 *
 * <p>{@link Primary}라 {@code NotificationSender}를 주입받는 곳(예: {@link PaydayNotificationService})은 자동으로
 * 이 게이트를 거친다 — NOTI-01은 변경 없이 중복 방지가 적용된다. 발송 일시는 주입된 KST {@code Clock}으로 산출한다(규칙 3).
 *
 * <p>감쌀 실제 채널은 {@link RealChannel} 한정자로 주입받는다(NOTI-05) — {@code app.notification.channel}이 고른
 * 단일 채널 빈(기본 {@link LoggingNotificationSender}, {@code email}이면 {@link EmailNotificationSender}). 이 게이트가
 * {@code @Primary}라 타입만으로는 자기 자신이 잡히므로 한정자로 채널을 명시한다.
 */
@Component
@Primary
public class DeduplicatingNotificationSender implements NotificationSender {

    private final NotificationSender channel;
    private final NotificationLogRepository logRepository;
    private final Clock clock;

    public DeduplicatingNotificationSender(
            @RealChannel NotificationSender channel, NotificationLogRepository logRepository, Clock clock) {
        this.channel = channel;
        this.logRepository = logRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(NotificationType type, long userId, LocalDate targetDate) {
        if (logRepository.existsByUserIdAndTypeAndTargetDate(userId, type, targetDate)) {
            return; // 이미 발송됨 — 재발송 스킵(멱등)
        }
        logRepository.save(NotificationLog.of(userId, type, targetDate, channel.channel(), clock.instant()));
        channel.send(type, userId, targetDate);
    }

    @Override
    public NotificationChannel channel() {
        return channel.channel();
    }
}
