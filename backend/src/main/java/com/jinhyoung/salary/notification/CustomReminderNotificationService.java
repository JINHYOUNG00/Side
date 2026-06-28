package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.reminder.domain.ReminderSchedule;
import com.jinhyoung.salary.reminder.infra.Reminder;
import com.jinhyoung.salary.reminder.infra.ReminderRepository;
import com.jinhyoung.salary.reminder.infra.ReminderStatus;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 정의 리마인더 발송 판정·발송(NOTI-06, "메모 기반 사용자 정의 리마인더"). 봉투 지출 알림
 * ({@link EnvelopeDueNotificationService})과 같은 일일 판정 방식이다 — 매일 한 번, 다음 알림일이 도래한
 * (오늘 이하) 활성 리마인더를 그 소유자에게 발송한다. 기준일은 주입된 KST {@code Clock}으로 산출한다(규칙 3).
 *
 * <p>발송 채널·중복 방지는 이 서비스의 책임이 아니다 — {@link NotificationSender}(기본 로그, NOTI-05 이메일)에
 * 위임하고, 동일 (user, CUSTOM, 알림일) 중복 차단은 NOTI-04 게이트가 처리한다. 발송 후에는 다음 알림일을 주기만큼
 * 미뤄(순수 {@link ReminderSchedule}) 한 주기에 1회만 나가게 한다 — 배치가 며칠 밀려 알림일이 과거로 누적돼도
 * 한 번에 오늘 이후로 수렴한다. 같은 사용자가 같은 알림일에 둘 이상 리마인더를 두면 멱등 키(user, CUSTOM,
 * target_date)가 겹쳐 1건만 발송된다(알려진 한계 — 키에 리마인더 식별자가 없음, ENVELOPE_DUE와 동일).
 *
 * <p>본문은 사용자 메모(label)를 구조화 데이터로만 넘긴다 — 문장은 메시지 번들이 조립한다(규칙 7). 알림일 이월은
 * 쓰기라 readOnly가 아니다 — 발송이 예외로 실패하면 이 트랜잭션이 롤백돼 알림일이 안 밀리므로 다음 배치에서
 * 재시도된다(NOTI-04 "기록 먼저, 발송 후 확정"과 결이 같다).
 */
@Service
public class CustomReminderNotificationService {

    private final ReminderRepository reminderRepository;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public CustomReminderNotificationService(
            ReminderRepository reminderRepository, NotificationSender notificationSender, Clock clock) {
        this.reminderRepository = reminderRepository;
        this.notificationSender = notificationSender;
        this.clock = clock;
    }

    /**
     * 다음 알림일이 도래한 활성 리마인더의 소유자에게 CUSTOM 알림을 발송하고, 알림일을 다음 주기로 미룬 뒤 발송
     * 시도 건수를 반환한다. 대상일은 도래한 알림일이다 — 멱등 키 (user, CUSTOM, 알림일)로 한 주기당 1회만 나간다.
     * 본문 인자는 [사용자 메모].
     */
    @Transactional
    public int notifyDueReminders() {
        LocalDate today = LocalDate.now(clock);
        int notified = 0;
        for (Reminder reminder :
                reminderRepository.findByStatusAndNextRemindDateLessThanEqual(ReminderStatus.ACTIVE, today)) {
            notificationSender.send(
                    NotificationType.CUSTOM, reminder.getUserId(), reminder.getNextRemindDate(), reminder.getLabel());
            reminder.rescheduleTo(
                    ReminderSchedule.nextAfter(reminder.getNextRemindDate(), today, reminder.getIntervalMonths()));
            notified++;
        }
        return notified;
    }
}
