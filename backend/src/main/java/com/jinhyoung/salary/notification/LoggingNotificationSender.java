package com.jinhyoung.salary.notification;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * v1 기본 알림 채널 — 발송을 로그로만 남긴다. 실제 채널(이메일/웹푸시) 어댑터는 NOTI-05가 별도 {@link
 * NotificationSender} 구현으로 도입하며, 그때 이 빈을 대체(@Primary 또는 교체)한다. NOTI-01은 판정·디스패치
 * 흐름만 완성하면 되므로 채널 세부는 이 자리표시 구현으로 둔다(문장은 클라이언트가 조립 — 규칙 7, 여기선 페이로드 없음).
 */
@Component
public class LoggingNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationSender.class);

    @Override
    public void send(NotificationType type, long userId, LocalDate targetDate) {
        log.info("Notification dispatched: type={} userId={} targetDate={}", type, userId, targetDate);
    }
}
