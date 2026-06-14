package com.jinhyoung.salary.notification;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 알림 채널 — 발송을 로그로만 남긴다. {@code app.notification.channel}이 {@code log}이거나 미설정일 때 활성 채널
 * ({@link RealChannel})로 쓰인다. 실 발송 채널(이메일)은 NOTI-05의 {@link EmailNotificationSender}가 {@code
 * channel=email}로 대체한다. SMTP 미설정 환경에서도 무손상이라 기본값으로 둔다(문장은 만들지 않음 — 규칙 7,
 * 여기선 페이로드 없음).
 */
@Component
@RealChannel
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "log", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationType type, long userId, LocalDate targetDate, Object... messageArgs) {
        log.info("Notification dispatched: type={} userId={} targetDate={}", type, userId, targetDate);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.LOG;
    }
}
