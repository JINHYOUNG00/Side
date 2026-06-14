package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jinhyoung.salary.notification.infra.NotificationLog;
import com.jinhyoung.salary.notification.infra.NotificationLogRepository;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 이메일 채널 활성화 통합 테스트(NOTI-05). {@code app.notification.channel=email}일 때 {@link RealChannel} 한정자가
 * {@link EmailNotificationSender}로 풀리고, {@code @Primary} 중복 방지 게이트({@link DeduplicatingNotificationSender})가
 * 그 채널을 감싸 발송 기록에 {@code channel=EMAIL}을 남기는지 — 즉 채널 교체가 NOTI-04 게이트를 거쳐 동작하는지 검증한다.
 *
 * <p>발송 인터페이스({@link MailClient})는 실 SMTP 어댑터가 아직 없으므로 {@link MockitoBean}으로 대체한다.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "app.notification.channel=email")
class EmailNotificationChannelIntegrationTest {

    private static final LocalDate PAYDAY_DATE = LocalDate.of(2026, 1, 26);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    DeduplicatingNotificationSender gate;

    @Autowired
    NotificationLogRepository logRepository;

    @Autowired
    UserRepository userRepository;

    @MockitoBean
    MailClient mailClient;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 이메일_채널이_활성이면_게이트가_EMAIL로_발송하고_기록한다() {
        long userId = userRepository
                .save(User.createFromOAuth("KAKAO", "noti5", "noti5@x.com", "noti5"))
                .getId();

        gate.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);

        verify(mailClient).send("noti5@x.com", "ko", NotificationType.PAYDAY, PAYDAY_DATE);
        assertThat(logRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getChannel()).isEqualTo(NotificationChannel.EMAIL);
            assertThat(row.getTargetDate()).isEqualTo(PAYDAY_DATE);
        });
    }

    @Test
    void 이메일_미수집_사용자는_발송_없이_기록만_남아_재시도하지_않는다() {
        // 동의 거부(email NULL) — 보낼 곳이 없으므로 메일은 안 나가지만, 기록은 남아 다음 배치에서 재시도하지 않는다(멱등).
        long userId = userRepository
                .save(User.createFromOAuth("KAKAO", "noemail", null, "noemail"))
                .getId();

        gate.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);

        verify(mailClient, never()).send(anyString(), anyString(), any(), any());
        assertThat(logRepository.findAll())
                .singleElement()
                .extracting(NotificationLog::getChannel)
                .isEqualTo(NotificationChannel.EMAIL);
    }
}
