package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 분기 외화 점검 알림(NOTI-06) 통합 테스트. 실 PostgreSQL(Testcontainers) + 날짜를 바꿔 끼울 수 있는 KST
 * Clock(규칙 3 주입)으로 — 오늘이 분기 첫날(1·4·7·10월 1일)일 때만, 활성 INVESTMENT(외화 적립식) 항목을 가진
 * 사용자에게만 발송되는지(SAVING만 가진 사용자·DELETED 항목·비점검일은 제외), 같은 점검일 재실행에도 분기당
 * 1회만 나가는지(대상일=점검일 멱등)를 결정론적으로 검증한다.
 *
 * <p>"누가 받았는가"는 발송 기록(notification_logs)으로 확인한다 — NOTI-04 게이트가 발송 시 행을 적재한다.
 */
@SpringBootTest
@Testcontainers
@Import(FxCheckupNotificationServiceIntegrationTest.MutableClockConfig.class)
class FxCheckupNotificationServiceIntegrationTest {

    /** 테스트마다 기준일을 바꿔 끼우기 위한 가변 KST Clock(now() 직접 호출 대신 주입 — 규칙 3). */
    static final class MutableClock extends Clock {
        private static final ZoneId KST = ZoneId.of("Asia/Seoul");
        private volatile Instant instant =
                LocalDate.of(2026, 4, 1).atStartOfDay(KST).toInstant();

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
    FxCheckupNotificationService fxCheckupNotificationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    NotificationLogRepository notificationLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    private long fxUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from notification_logs");
        jdbcTemplate.update("delete from budget_items");
        accountRepository.deleteAll();
        userRepository.deleteAll();

        fxUserId = userRepository
                .save(User.createFromOAuth("KAKAO", "fx", "fx@x.com", "fx"))
                .getId();
        long fxAccountId = accountRepository
                .save(Account.create(fxUserId, "외화통장", null, null, 0))
                .getId();
        budgetItem(fxUserId, fxAccountId, "INVESTMENT", "애플 모으기", "ACTIVE");

        clock.setToday(LocalDate.of(2026, 4, 1)); // 분기 첫날
    }

    private void budgetItem(long userId, long accountId, String category, String name, String status) {
        jdbcTemplate.update(
                "insert into budget_items"
                        + " (user_id, account_id, category, name, amount, start_date, sort_order, status)"
                        + " values (?, ?, ?, ?, ?, ?, ?, ?)",
                userId,
                accountId,
                category,
                name,
                300_000L,
                LocalDate.of(2026, 1, 1),
                0,
                status);
    }

    @Test
    void 분기_첫날에_활성_외화적립식_보유자에게만_발송한다() {
        // SAVING만 가진 사용자 → 제외
        long saverId = userRepository
                .save(User.createFromOAuth("GOOGLE", "saver", "saver@x.com", "saver"))
                .getId();
        long saverAccount = accountRepository
                .save(Account.create(saverId, "적금통장", null, null, 0))
                .getId();
        budgetItem(saverId, saverAccount, "SAVING", "적금", "ACTIVE");

        int notified = fxCheckupNotificationService.notifyQuarterlyCheckups();

        assertThat(notified).isEqualTo(1);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getUserId, NotificationLog::getType, NotificationLog::getTargetDate)
                .containsExactly(tuple(fxUserId, NotificationType.FX_CHECKUP, LocalDate.of(2026, 4, 1)));
    }

    @Test
    void 삭제된_외화적립식만_있으면_제외한다() {
        // fxUser의 INVESTMENT 항목을 DELETED로 — 활성 외화 항목이 없어진다.
        jdbcTemplate.update("update budget_items set status = 'DELETED' where user_id = ?", fxUserId);

        assertThat(fxCheckupNotificationService.notifyQuarterlyCheckups()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    void 분기_첫날이_아니면_아무에게도_발송하지_않는다() {
        clock.setToday(LocalDate.of(2026, 4, 2)); // 점검일 아님

        assertThat(fxCheckupNotificationService.notifyQuarterlyCheckups()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    void 같은_점검일_재실행해도_분기당_한_번만_발송한다() {
        assertThat(fxCheckupNotificationService.notifyQuarterlyCheckups()).isEqualTo(1);
        fxCheckupNotificationService.notifyQuarterlyCheckups(); // 같은 날 재실행

        assertThat(notificationLogRepository.count()).isEqualTo(1);
    }
}
