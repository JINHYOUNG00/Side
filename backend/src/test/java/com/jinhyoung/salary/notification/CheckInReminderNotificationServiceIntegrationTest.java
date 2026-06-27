package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jinhyoung.salary.notification.infra.NotificationLog;
import com.jinhyoung.salary.notification.infra.NotificationLogRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 월말 체크인 요청 알림 판정·발송(NOTI-03) 통합 테스트. 실 PostgreSQL(Testcontainers) + 날짜를 바꿔 끼울 수 있는
 * KST Clock(규칙 3 주입)으로, 오늘이 "다음 지급일 전일"인 사용자만 알림 대상이 되는지(지급일 당일·비대상일 제외,
 * 영업일 조정으로 밀린 실지급일의 전일 포함), 같은 날 재실행해도 멱등인지, 온보딩 전 사용자는 제외되는지를 결정론적으로
 * 검증한다. "누가 받았는가"는 발송 기록(notification_logs)으로 확인한다 — NOTI-04 중복 방지 게이트가 발송 시 행을
 * 적재하므로 이 기록이 디스패치 대상의 충실한 증거다.
 */
@SpringBootTest
@Testcontainers
@Import(CheckInReminderNotificationServiceIntegrationTest.MutableClockConfig.class)
class CheckInReminderNotificationServiceIntegrationTest {

    /** 테스트마다 기준일을 바꿔 끼우기 위한 가변 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 1, 1).atStartOfDay(KST).toInstant();

        void setToday(LocalDate today) {
            this.instant = today.atStartOfDay(KST).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return KST;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MutableClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    CheckInReminderNotificationService checkInReminderNotificationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    NotificationLogRepository notificationLogRepository;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void setUp() {
        notificationLogRepository.deleteAll();
        userRepository.deleteAll();
    }

    private long saveOnboardedUser(String providerId, int payday, PaydayAdjustment adjustment) {
        User user = User.createFromOAuth("KAKAO", providerId, providerId + "@x.com", providerId);
        user.updateSettings(2_473_110L, (short) payday, adjustment, null);
        return userRepository.save(user).getId();
    }

    @Test
    void 다음_지급일_전일인_사용자에게만_CHECK_IN_알림을_보낸다() {
        // 2026-01-26(월) = alice의 실지급일(평일 그대로) → 전일 1/25가 대상일. bob은 15일 지급이라 비대상.
        long alice = saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        saveOnboardedUser("bob", 15, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 25));

        int notified = checkInReminderNotificationService.notifyCheckInReminders();

        assertThat(notified).isEqualTo(1);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getUserId, NotificationLog::getType, NotificationLog::getTargetDate)
                .containsExactly(tuple(alice, NotificationType.CHECK_IN, LocalDate.of(2026, 1, 25)));
    }

    @Test
    void 지급일_당일에는_체크인_알림을_보내지_않는다() {
        // 전일이 아니라 당일(1/26). 내일(1/27)은 지급일이 아니므로 발송하지 않는다 — "다음 지급일 전일" 의미 확인.
        saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 26));

        assertThat(checkInReminderNotificationService.notifyCheckInReminders()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    void 비대상일에는_아무에게도_발송하지_않는다() {
        saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        saveOnboardedUser("bob", 15, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 20)); // 두 사람 모두 내일이 지급일이 아님

        assertThat(checkInReminderNotificationService.notifyCheckInReminders()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    void 조정으로_밀린_실지급일의_전일에_발송한다() {
        // 월급일 31일 + NEXT. 명목 1월 지급일 1/31(토) → 2/1(일) → 2/2(월)로 이동. 실지급일 전일 2/1이 대상일.
        long carol = saveOnboardedUser("carol", 31, PaydayAdjustment.NEXT_BUSINESS_DAY);
        clock.setToday(LocalDate.of(2026, 2, 1));

        assertThat(checkInReminderNotificationService.notifyCheckInReminders()).isEqualTo(1);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getUserId, NotificationLog::getType, NotificationLog::getTargetDate)
                .containsExactly(tuple(carol, NotificationType.CHECK_IN, LocalDate.of(2026, 2, 1)));
    }

    @Test
    void 같은_날_재실행해도_사용자당_1건만_발송한다() {
        // 대상일=오늘(1/25)을 멱등 키로 잡으므로 같은 날 배치가 두 번 돌아도 1건만 적재된다(NOTI-04 게이트).
        long alice = saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 25));

        assertThat(checkInReminderNotificationService.notifyCheckInReminders()).isEqualTo(1);
        checkInReminderNotificationService.notifyCheckInReminders();

        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getUserId, NotificationLog::getType, NotificationLog::getTargetDate)
                .containsExactly(tuple(alice, NotificationType.CHECK_IN, LocalDate.of(2026, 1, 25)));
    }

    @Test
    void 온보딩_전_플레이스홀더_사용자는_제외한다() {
        // createFromOAuth 플레이스홀더: base_income=0, payday=1(NONE). 내일 1/1이 명목 지급일이지만 온보딩 전이라 제외.
        userRepository.save(User.createFromOAuth("KAKAO", "newbie", "newbie@x.com", "newbie"));
        clock.setToday(LocalDate.of(2025, 12, 31));

        assertThat(checkInReminderNotificationService.notifyCheckInReminders()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }
}
