package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.jinhyoung.salary.notification.infra.NotificationLog;
import com.jinhyoung.salary.notification.infra.NotificationLogRepository;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 알림 중복 방지 게이트(NOTI-04) 통합 테스트. 실 PostgreSQL(Testcontainers)에서 {@link
 * DeduplicatingNotificationSender}가 동일 (user, type, target_date) 알림을 1회만 발송하고 재실행은 스킵하는지(규칙 8
 * 멱등), 대상일이 다르면 각각 발송하는지, 발송 기록에 채널·발송일시가 남는지, "발송 후 확정" 순서대로 발송 실패 시 기록이
 * 롤백돼 재시도 가능한지를 검증한다.
 *
 * <p>실제 채널({@link LoggingNotificationSender})은 {@link MockitoSpyBean}으로 감싸 호출 횟수를 직접 확인한다 —
 * 게이트가 채널을 한 번만 호출하는지(중복 시 미호출)가 핵심.
 */
@SpringBootTest
@Testcontainers
@Import(DeduplicatingNotificationSenderIntegrationTest.MutableClockConfig.class)
class DeduplicatingNotificationSenderIntegrationTest {

    private static final LocalDate PAYDAY_DATE = LocalDate.of(2026, 1, 26);

    /** notification_logs.user_id는 users를 FK 참조하므로 실제 사용자 행이 있어야 한다. setUp에서 생성. */
    private long userId;

    /** 발송일시(sent_at)를 결정론적으로 검증하기 위한 고정 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        Clock fixedKstClock() {
            Instant fixed = LocalDate.of(2026, 1, 26)
                    .atStartOfDay(ZoneId.of("Asia/Seoul"))
                    .toInstant();
            return Clock.fixed(fixed, ZoneId.of("Asia/Seoul"));
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    DeduplicatingNotificationSender sender;

    @Autowired
    NotificationLogRepository logRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    Clock clock;

    @MockitoSpyBean
    LoggingNotificationSender channel;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
        userRepository.deleteAll();
        userId = userRepository
                .save(User.createFromOAuth("KAKAO", "noti4", "noti4@x.com", "noti4"))
                .getId();
    }

    @Test
    void 처음_발송하면_채널로_보내고_기록을_남긴다() {
        sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);

        verify(channel, times(1)).send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        assertThat(logRepository.findAll()).singleElement().satisfies(row -> {
            assertThat(row.getUserId()).isEqualTo(userId);
            assertThat(row.getType()).isEqualTo(NotificationType.PAYDAY);
            assertThat(row.getTargetDate()).isEqualTo(PAYDAY_DATE);
            assertThat(row.getChannel()).isEqualTo(NotificationChannel.LOG);
            assertThat(row.getSentAt()).isEqualTo(clock.instant());
        });
    }

    @Test
    void 동일_알림을_다시_보내면_스킵하고_채널을_한_번만_호출한다() {
        sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE); // 재실행 — 멱등 스킵

        verify(channel, times(1)).send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        assertThat(logRepository.count()).isEqualTo(1);
    }

    @Test
    void 대상일이_다르면_각각_발송한다() {
        LocalDate nextPayday = LocalDate.of(2026, 2, 25);
        sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        sender.send(NotificationType.PAYDAY, userId, nextPayday);

        verify(channel, times(1)).send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        verify(channel, times(1)).send(NotificationType.PAYDAY, userId, nextPayday);
        assertThat(logRepository.findAll())
                .extracting(NotificationLog::getTargetDate)
                .containsExactlyInAnyOrder(PAYDAY_DATE, nextPayday);
    }

    @Test
    void 이미_기록된_알림은_채널을_호출하지_않는다() {
        // 다른 인스턴스/실행이 이미 기록·발송한 상황을 모사 — 기록만 선적재.
        logRepository.save(NotificationLog.of(
                userId, NotificationType.PAYDAY, PAYDAY_DATE, NotificationChannel.LOG, clock.instant()));

        sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);

        verify(channel, never()).send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        assertThat(logRepository.count()).isEqualTo(1);
    }

    @Test
    void 발송이_실패하면_기록이_롤백돼_재시도할_수_있다() {
        // "기록 먼저, 발송 후 확정" — 발송 예외 시 REQUIRES_NEW 트랜잭션이 롤백돼 기록이 남지 않아야 한다.
        doThrow(new RuntimeException("channel down"))
                .doNothing()
                .when(channel)
                .send(NotificationType.PAYDAY, userId, PAYDAY_DATE);

        assertThatThrownBy(() -> sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE))
                .isInstanceOf(RuntimeException.class);
        assertThat(logRepository.count()).as("발송 실패 시 기록 롤백").isZero();

        // 다음 배치에서 재시도 — 이번엔 성공해 기록이 확정된다.
        sender.send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        verify(channel, times(2)).send(NotificationType.PAYDAY, userId, PAYDAY_DATE);
        assertThat(logRepository.count()).isEqualTo(1);
    }

    @Test
    void notification_logs_unique_제약이_중복_행을_직접_차단한다() {
        // ERD 멱등 제약 unique(user_id, type, target_date) — 게이트의 최종 DB 레벨 가드.
        logRepository.save(NotificationLog.of(
                userId, NotificationType.PAYDAY, PAYDAY_DATE, NotificationChannel.LOG, clock.instant()));

        assertThatThrownBy(() -> logRepository.saveAndFlush(NotificationLog.of(
                        userId, NotificationType.PAYDAY, PAYDAY_DATE, NotificationChannel.LOG, clock.instant())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
