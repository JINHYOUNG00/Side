package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.cycle.infra.Holiday;
import com.jinhyoung.salary.cycle.infra.HolidayRepository;
import com.jinhyoung.salary.user.domain.PaydayAdjustment;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
 * 지급일 알림 판정·발송(NOTI-01) 통합 테스트. 실 PostgreSQL(Testcontainers) + 날짜를 바꿔 끼울 수 있는 KST
 * Clock(규칙 3 주입) + 발송을 기록하는 {@link NotificationSender} 스텁으로, 오늘이 실제 지급일인 사용자만 알림 대상이
 * 되는지(공휴일·주말·월 경계 조정 반영), 비지급일엔 아무도 발송되지 않는지, 온보딩 전 사용자는 제외되는지를 결정론적으로
 * 검증한다. 실지급일 산출 자체는 PaydayResolver 단위·골든·PaydayService 통합 테스트가 별도로 덮는다.
 */
@SpringBootTest
@Testcontainers
@Import({
    PaydayNotificationServiceIntegrationTest.MutableClockConfig.class,
    PaydayNotificationServiceIntegrationTest.RecordingSenderConfig.class
})
class PaydayNotificationServiceIntegrationTest {

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

    /** 발송 채널 대신 호출을 기록 — "누구에게 어떤 알림이 갔는가"만 검증한다. */
    static final class RecordingNotificationSender implements NotificationSender {
        record Dispatch(NotificationType type, long userId, LocalDate targetDate) {}

        final List<Dispatch> sent = new ArrayList<>();

        @Override
        public void send(NotificationType type, long userId, LocalDate targetDate) {
            sent.add(new Dispatch(type, userId, targetDate));
        }
    }

    @TestConfiguration
    static class RecordingSenderConfig {
        @Bean
        @Primary
        RecordingNotificationSender recordingNotificationSender() {
            return new RecordingNotificationSender();
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    PaydayNotificationService paydayNotificationService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    HolidayRepository holidayRepository;

    @Autowired
    MutableClock clock;

    @Autowired
    RecordingNotificationSender sender;

    @BeforeEach
    void setUp() {
        sender.sent.clear();
        userRepository.deleteAll();
        holidayRepository.deleteAll();
    }

    private long saveOnboardedUser(String providerId, int payday, PaydayAdjustment adjustment) {
        User user = User.createFromOAuth("KAKAO", providerId, providerId + "@x.com", providerId);
        user.updateSettings(2_473_110L, (short) payday, adjustment, null);
        return userRepository.save(user).getId();
    }

    @Test
    void 오늘이_실지급일인_사용자에게만_PAYDAY_알림을_보낸다() {
        // 2026-01-26(월) = today. A는 26일 지급(평일 그대로) → 대상, B는 15일 지급 → 비대상.
        long alice = saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        saveOnboardedUser("bob", 15, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 26));

        int notified = paydayNotificationService.notifyPaydays();

        assertThat(notified).isEqualTo(1);
        assertThat(sender.sent)
                .containsExactly(new RecordingNotificationSender.Dispatch(
                        NotificationType.PAYDAY, alice, LocalDate.of(2026, 1, 26)));
    }

    @Test
    void 비지급일에는_아무에게도_발송하지_않는다() {
        saveOnboardedUser("alice", 26, PaydayAdjustment.NONE);
        saveOnboardedUser("bob", 15, PaydayAdjustment.NONE);
        clock.setToday(LocalDate.of(2026, 1, 20)); // 두 사람 모두 지급일이 아님

        int notified = paydayNotificationService.notifyPaydays();

        assertThat(notified).isZero();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void 월말_NEXT_조정으로_다음_달로_밀린_실지급일_당일에_발송한다() {
        // 월급일 31일 + NEXT. 2026-01-31(토) → 2/1(일) → 2/2(월)로 이동. 명목 1월 지급일이 2월 2일에 떨어진다.
        long carol = saveOnboardedUser("carol", 31, PaydayAdjustment.NEXT_BUSINESS_DAY);

        clock.setToday(LocalDate.of(2026, 2, 2));
        assertThat(paydayNotificationService.notifyPaydays()).isEqualTo(1);
        assertThat(sender.sent)
                .containsExactly(new RecordingNotificationSender.Dispatch(
                        NotificationType.PAYDAY, carol, LocalDate.of(2026, 2, 2)));
    }

    @Test
    void 조정으로_밀린_경우_명목_월급일_당일에는_발송하지_않는다() {
        // 위와 같은 사용자라도 명목일 1/31(토)에는 발송하지 않는다 — 실지급일은 2/2이기 때문.
        saveOnboardedUser("carol", 31, PaydayAdjustment.NEXT_BUSINESS_DAY);
        clock.setToday(LocalDate.of(2026, 1, 31));

        assertThat(paydayNotificationService.notifyPaydays()).isZero();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void 공휴일을_피해_이동한_실지급일에_발송하고_공휴일_당일에는_발송하지_않는다() {
        // 2026-01-01(목)은 신정 공휴일. 월급일 1일 + NEXT → 1/2(금)로 이동.
        holidayRepository.save(Holiday.of(LocalDate.of(2026, 1, 1), "신정", 2026));
        long dave = saveOnboardedUser("dave", 1, PaydayAdjustment.NEXT_BUSINESS_DAY);

        clock.setToday(LocalDate.of(2026, 1, 1));
        assertThat(paydayNotificationService.notifyPaydays()).isZero();

        sender.sent.clear();
        clock.setToday(LocalDate.of(2026, 1, 2));
        assertThat(paydayNotificationService.notifyPaydays()).isEqualTo(1);
        assertThat(sender.sent)
                .containsExactly(new RecordingNotificationSender.Dispatch(
                        NotificationType.PAYDAY, dave, LocalDate.of(2026, 1, 2)));
    }

    @Test
    void 온보딩_전_플레이스홀더_사용자는_지급일이_겹쳐도_제외한다() {
        // createFromOAuth 플레이스홀더: base_income=0, payday=1(NONE). 2026-01-01이 명목 지급일이지만 온보딩 전이라 제외.
        userRepository.save(User.createFromOAuth("KAKAO", "newbie", "newbie@x.com", "newbie"));
        clock.setToday(LocalDate.of(2026, 1, 1));

        assertThat(paydayNotificationService.notifyPaydays()).isZero();
        assertThat(sender.sent).isEmpty();
    }
}
