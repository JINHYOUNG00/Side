package com.jinhyoung.salary.reminder;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.reminder.infra.Reminder;
import com.jinhyoung.salary.reminder.infra.ReminderRepository;
import com.jinhyoung.salary.reminder.infra.ReminderStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 정의 리마인더 CRUD 유스케이스(NOTI-06). 모든 변경·조회는 호출 사용자의 소유분에 한정한다 — 소유권
 * 검증을 이 한 곳(+리포지토리 쿼리)으로 모아 컨트롤러가 우회할 수 없게 한다(아키텍처 8장). DELETE는 물리
 * 삭제가 아닌 status=DELETED(soft delete, 규칙 5).
 *
 * <p>다음 알림일(next_remind_date)은 KST 오늘 이후여야 한다(과거 알림 예약 무의미) — 판정은 주입된
 * {@code Clock}으로 수행한다(규칙 3, LocalDate.now() 직접 호출 금지). 발송·다음 주기 이월은 일일 배치
 * ({@code CustomReminderNotificationService})가 맡는다 — 이 서비스는 설정(CRUD)만 담당한다.
 */
@Service
public class ReminderService {

    /** 활성 리마인더 개수 상한 — 통장/봉투와 같은 abuse 방지선. */
    static final long MAX_ACTIVE_REMINDERS = 50;

    private final ReminderRepository reminderRepository;
    private final Clock clock;

    public ReminderService(ReminderRepository reminderRepository, Clock clock) {
        this.reminderRepository = reminderRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Reminder> list(long userId) {
        return reminderRepository.findByUserIdAndStatusOrderByIdAsc(userId, ReminderStatus.ACTIVE);
    }

    @Transactional
    public Reminder create(long userId, String label, short intervalMonths, LocalDate nextRemindDate) {
        requireNextRemindNotPast(nextRemindDate);
        if (reminderRepository.countByUserIdAndStatus(userId, ReminderStatus.ACTIVE) >= MAX_ACTIVE_REMINDERS) {
            throw new ApiException(ErrorCode.REMINDER_LIMIT_EXCEEDED, Map.of("limit", MAX_ACTIVE_REMINDERS));
        }
        return reminderRepository.save(Reminder.create(userId, label, intervalMonths, nextRemindDate));
    }

    /**
     * 리마인더 수정(NOTI-06). 호출 사용자의 활성 리마인더만 다룬다(미소유·삭제·부재는 NOT_FOUND로 존재
     * 비노출). 개수는 변하지 않으므로 상한 검사는 없다. status는 갱신하지 않는다.
     */
    @Transactional
    public Reminder update(long userId, long reminderId, String label, short intervalMonths, LocalDate nextRemindDate) {
        requireNextRemindNotPast(nextRemindDate);
        Reminder reminder = ownedActiveOrThrow(userId, reminderId);
        reminder.update(label, intervalMonths, nextRemindDate);
        return reminder;
    }

    /**
     * 리마인더 soft delete(NOTI-06) — 활성 리마인더를 DELETED로 전환한다. 이후 조회·발송에서 제외된다.
     * 이미 삭제·타인·부재는 NOT_FOUND(존재 비노출).
     */
    @Transactional
    public void delete(long userId, long reminderId) {
        ownedActiveOrThrow(userId, reminderId).markDeleted();
    }

    /** 리마인더 소유권 + 활성 검증의 단일 관문 — 미소유·삭제·부재는 모두 NOT_FOUND. */
    private Reminder ownedActiveOrThrow(long userId, long reminderId) {
        return reminderRepository
                .findByIdAndUserIdAndStatus(reminderId, userId, ReminderStatus.ACTIVE)
                .orElseThrow(
                        () -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "reminder", "id", reminderId)));
    }

    /** 다음 알림일은 KST 오늘 이후여야 한다. 과거면 400 VALIDATION_FAILED. */
    private void requireNextRemindNotPast(LocalDate nextRemindDate) {
        if (nextRemindDate.isBefore(LocalDate.now(clock))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("field", "nextRemindDate"));
        }
    }
}
