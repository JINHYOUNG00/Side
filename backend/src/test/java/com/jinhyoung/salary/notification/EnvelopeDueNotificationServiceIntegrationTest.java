package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.jinhyoung.salary.account.infra.Account;
import com.jinhyoung.salary.account.infra.AccountRepository;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
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
 * 봉투 지출 시기 알림(NOTI-02) 통합 테스트. 실 PostgreSQL(Testcontainers) + 날짜를 바꿔 끼울 수 있는 KST
 * Clock(규칙 3 주입)으로 — 다음 지출일이 알림 윈도우(오늘 ~ 오늘+LEAD_DAYS)에 든 활성 봉투만 그 소유자에게
 * 발송되는지, 경과·먼 미래·종료·삭제 봉투는 제외되는지, 윈도우 기간 매일 재실행해도 봉투당 1회만 발송되는지(대상일=
 * 다음 지출일로 멱등)를 결정론적으로 검증한다.
 *
 * <p>"누가 알림을 받았는가"는 발송 기록(notification_logs)으로 확인한다 — NOTI-04 중복 방지 게이트({@link
 * DeduplicatingNotificationSender})가 발송 시 행을 적재하므로 이 기록이 디스패치 대상의 충실한 증거다.
 */
@SpringBootTest
@Testcontainers
@Import(EnvelopeDueNotificationServiceIntegrationTest.MutableClockConfig.class)
class EnvelopeDueNotificationServiceIntegrationTest {

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
    EnvelopeDueNotificationService envelopeDueNotificationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    EnvelopeRepository envelopeRepository;

    @Autowired
    NotificationLogRepository notificationLogRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    private long aliceId;
    private long aliceAccountId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from notification_logs");
        jdbcTemplate.update("delete from envelopes");
        accountRepository.deleteAll();
        userRepository.deleteAll();

        aliceId = userRepository
                .save(User.createFromOAuth("KAKAO", "alice", "alice@x.com", "alice"))
                .getId();
        aliceAccountId = accountRepository
                .save(Account.create(aliceId, "비상금통장", null, null, 0))
                .getId();
        clock.setToday(LocalDate.of(2026, 6, 14)); // window = [2026-06-14, 2026-06-17]
    }

    private long activeEnvelope(String name, long target, LocalDate nextDue, Short cycleMonths) {
        return envelopeRepository
                .save(Envelope.create(aliceId, aliceAccountId, name, target, nextDue, cycleMonths, null))
                .getId();
    }

    @Test
    void 지출일이_윈도우에_든_활성_봉투의_소유자에게만_발송한다() {
        activeEnvelope("자동차세", 1_200_000L, LocalDate.of(2026, 6, 16), (short) 12); // 윈도우 안(D-2) → 대상
        activeEnvelope("명절비", 500_000L, LocalDate.of(2026, 7, 1), (short) 6); // 먼 미래 → 제외
        activeEnvelope("지난지출", 300_000L, LocalDate.of(2026, 6, 10), (short) 3); // 경과 → 제외

        int notified = envelopeDueNotificationService.notifyDueEnvelopes();

        assertThat(notified).isEqualTo(1);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getUserId, NotificationLog::getType, NotificationLog::getTargetDate)
                .containsExactly(tuple(aliceId, NotificationType.ENVELOPE_DUE, LocalDate.of(2026, 6, 16)));
    }

    @Test
    void 지출일_당일과_윈도우_마지막날도_대상이다() {
        activeEnvelope("오늘봉투", 100_000L, LocalDate.of(2026, 6, 14), null); // D-0
        activeEnvelope("막날봉투", 200_000L, LocalDate.of(2026, 6, 17), null); // D-3(윈도우 끝)

        int notified = envelopeDueNotificationService.notifyDueEnvelopes();

        assertThat(notified).isEqualTo(2);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getTargetDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 6, 14), LocalDate.of(2026, 6, 17));
    }

    @Test
    void 윈도우에_봉투가_없으면_아무에게도_발송하지_않는다() {
        activeEnvelope("먼봉투", 100_000L, LocalDate.of(2026, 8, 1), (short) 12);
        clock.setToday(LocalDate.of(2026, 6, 14));

        assertThat(envelopeDueNotificationService.notifyDueEnvelopes()).isZero();
        assertThat(notificationLogRepository.findAll()).isEmpty();
    }

    @Test
    void 종료된_봉투와_삭제된_봉투는_제외한다() {
        long closed = activeEnvelope("종료봉투", 100_000L, LocalDate.of(2026, 6, 15), null);
        long deleted = activeEnvelope("삭제봉투", 100_000L, LocalDate.of(2026, 6, 15), (short) 12);
        jdbcTemplate.update("update envelopes set status = 'CLOSED' where id = ?", closed);
        jdbcTemplate.update("update envelopes set status = 'DELETED' where id = ?", deleted);
        activeEnvelope("활성봉투", 100_000L, LocalDate.of(2026, 6, 15), (short) 12);

        int notified = envelopeDueNotificationService.notifyDueEnvelopes();

        assertThat(notified).isEqualTo(1);
        assertThat(notificationLogRepository.findAll())
                .extracting(NotificationLog::getType)
                .containsExactly(NotificationType.ENVELOPE_DUE);
    }

    @Test
    void 윈도우_기간_매일_재실행해도_봉투당_한_번만_발송한다() {
        // 다음 지출일 2026-06-16. 대상일=다음 지출일이라 D-3·D-1·D-0 어느 날 돌려도 멱등 키가 같다.
        activeEnvelope("자동차세", 1_200_000L, LocalDate.of(2026, 6, 16), (short) 12);

        clock.setToday(LocalDate.of(2026, 6, 13)); // D-3
        assertThat(envelopeDueNotificationService.notifyDueEnvelopes()).isEqualTo(1);
        clock.setToday(LocalDate.of(2026, 6, 15)); // D-1
        envelopeDueNotificationService.notifyDueEnvelopes();
        clock.setToday(LocalDate.of(2026, 6, 16)); // D-0
        envelopeDueNotificationService.notifyDueEnvelopes();

        assertThat(notificationLogRepository.count()).isEqualTo(1);
    }
}
