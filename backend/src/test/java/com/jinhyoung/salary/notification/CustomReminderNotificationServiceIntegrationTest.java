package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jinhyoung.salary.notification.infra.NotificationLog;
import com.jinhyoung.salary.notification.infra.NotificationLogRepository;
import com.jinhyoung.salary.reminder.infra.Reminder;
import com.jinhyoung.salary.reminder.infra.ReminderRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 사용자 정의 리마인더 발송(NOTI-06) 통합 테스트. 실 PostgreSQL(Testcontainers) + 날짜를 바꿔 끼울 수 있는 KST
 * Clock(규칙 3 주입)으로 — 다음 알림일이 도래한(오늘 이하) 활성 리마인더만 그 소유자에게 발송되는지(미래·삭제는
 * 제외), 발송 후 알림일이 다음 주기로 이월돼 같은 날 재실행에도 한 주기당 1회만 나가는지, 며칠 밀린 알림이 한
 * 번에 오늘 이후로 수렴하는지를 결정론적으로 검증한다.
 */
@SpringBootTest
@Testcontainers
@Import(CustomReminderNotificationServiceIntegrationTest.MutableClockConfig.class)
class CustomReminderNotificationServiceIntegrationTest {

    /** 테스트마다 기준일을 바꿔 끼우기 위한 가변 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 6, 14).atStartOfDay(KST).toInstant();

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
    CustomReminderNotificationService customReminderNotificationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ReminderRepository reminderRepository;

    @Autowired
    NotificationLogRepository notificationLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    private long userId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from notification_logs");
        jdbcTemplate.update("delete from reminders");
        userRepository.deleteAll();

        userId = userRepository
                .save(User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"))
                .getId();
        clock.setToday(LocalDate.of(2026, 6, 14));
    }

    private long reminder(String label, short intervalMonths, LocalDate nextRemindDate) {
        return reminderRepository
                .save(Reminder.create(userId, label, intervalMonths, nextRemindDate))
                .getId();
    }

    @Test
    void 알림일이_도래한_활성_리마인더만_발송하고_다음_주기로_이월한다() {
        long due = reminder("전세 만기 점검", (short) 3, LocalDate.of(2026, 6, 14)); // 오늘 = 도래
        reminder("먼 리마인더", (short) 1, LocalDate.of(2026, 7, 1)); // 미래 → 제외

        int notified = customReminderNotificationService.notifyDueReminders();

        assertThat(notified).isEqualTo(1);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getUserId, NotificationLog::getType, NotificationLog::getTargetDate)
                .containsExactly(tuple(userId, NotificationType.CUSTOM, LocalDate.of(2026, 6, 14)));
        // 발송 후 알림일은 다음 주기(+3개월)로 이월된다.
        assertThat(reminderRepository.findById(due).orElseThrow().getNextRemindDate())
                .isEqualTo(LocalDate.of(2026, 9, 14));
    }

    @Test
    void 삭제된_리마인더는_제외한다() {
        long deleted = reminder("삭제됨", (short) 1, LocalDate.of(2026, 6, 14));
        jdbcTemplate.update("update reminders set status = 'DELETED' where id = ?", deleted);

        assertThat(customReminderNotificationService.notifyDueReminders()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    void 같은_날_재실행해도_한_주기당_한_번만_발송한다() {
        reminder("월간 점검", (short) 1, LocalDate.of(2026, 6, 14));

        assertThat(customReminderNotificationService.notifyDueReminders()).isEqualTo(1);
        // 첫 발송으로 알림일이 7/14로 이월돼, 같은 날(6/14) 재실행은 도래 대상이 없다.
        assertThat(customReminderNotificationService.notifyDueReminders()).isZero();
        assertThat(notificationLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 며칠_밀린_알림은_오늘_이후_첫_날로_수렴한다() {
        long stale = reminder("밀린 리마인더", (short) 1, LocalDate.of(2026, 6, 10)); // 과거 → 도래

        int notified = customReminderNotificationService.notifyDueReminders();

        assertThat(notified).isEqualTo(1);
        // 대상일은 밀린 알림일(6/10), 다음 알림일은 오늘(6/14) 이후 첫 날 = 7/10.
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getTargetDate)
                .containsExactly(LocalDate.of(2026, 6, 10));
        assertThat(reminderRepository.findById(stale).orElseThrow().getNextRemindDate())
                .isEqualTo(LocalDate.of(2026, 7, 10));
    }
}
