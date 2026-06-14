package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 이메일 알림 채널 어댑터(NOTI-05). 1차 발송 채널을 이메일로 구현한다(요구사항 NOTI-05, 모바일 앱 출시 시 FCM 승격은
 * Phase 8). {@link NotificationSender} 포트 구현으로, 수신자 이메일을 조회해 {@link MailClient} 포트로 발송한다 —
 * 실제 SMTP 처리는 MailClient 뒤로 숨겨 발송 인터페이스를 모킹·교체 가능하게 둔다(ADR-05).
 *
 * <p>{@code app.notification.channel=email}일 때만 빈으로 등록되고({@link ConditionalOnProperty}), {@link RealChannel}
 * 한정자로 중복 방지 게이트({@link DeduplicatingNotificationSender})에 주입돼 활성 채널이 된다. 기본값은 로그 채널
 * ({@link LoggingNotificationSender}) — SMTP 미설정 환경에서도 부팅·배치가 무손상이다.
 *
 * <p>이메일 미수집(동의 거부 — ERD {@code users.email} NULL) 사용자는 발송 대상이 아니므로 조용히 건너뛴다. 발송 자체가
 * 실패(MailClient 예외)하면 예외를 전파해 중복 방지 게이트가 기록을 롤백하고 다음 배치에서 재시도하게 한다(NOTI-04).
 * 제목·본문 문장은 이 클래스가 만들지 않는다 — 구조화 데이터만 MailClient에 넘긴다(규칙 7).
 */
@Component
@RealChannel
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "email")
public class EmailNotificationSender implements NotificationSender {

    private final UserRepository userRepository;
    private final MailClient mailClient;

    public EmailNotificationSender(UserRepository userRepository, MailClient mailClient) {
        this.userRepository = userRepository;
        this.mailClient = mailClient;
    }

    @Override
    public void send(NotificationType type, long userId, LocalDate targetDate, Object... messageArgs) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("알림 대상 사용자가 없다: " + userId));
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return; // 이메일 미수집(동의 거부) — 발송 대상 아님
        }
        mailClient.send(email, user.getLocale(), type, targetDate, messageArgs);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }
}
