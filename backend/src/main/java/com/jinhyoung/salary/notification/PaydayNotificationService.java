package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.cycle.PaydayService;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지급일 알림 판정·발송(NOTI-01, 아키텍처 4장 NotificationStep의 PAYDAY 부분). 고정 일자 크론이 아니라 매일 한 번
 * "오늘이 각 사용자의 실제 지급일인가"를 {@link PaydayService}로 판정해, 해당 사용자에게만 PAYDAY 알림을 디스패치한다.
 * 기준일(today)은 주입된 KST {@code Clock}으로 산출 — {@code LocalDate.now()} 직접 호출 금지(규칙 3).
 *
 * <p>발송 채널과 중복 방지는 이 서비스의 책임이 아니다 — 채널은 {@link NotificationSender}(기본 로그, 실 채널 NOTI-05),
 * 동일 (user, type, target_date) 중복 발송 차단은 NOTI-04가 그 포트를 감싸 도입한다(notification_logs 기록 우선).
 * 따라서 이 서비스 자체는 쓰기가 없고(readOnly), 판정 로직만 가진다.
 */
@Service
public class PaydayNotificationService {

    private final UserRepository userRepository;
    private final PaydayService paydayService;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public PaydayNotificationService(
            UserRepository userRepository,
            PaydayService paydayService,
            NotificationSender notificationSender,
            Clock clock) {
        this.userRepository = userRepository;
        this.paydayService = paydayService;
        this.notificationSender = notificationSender;
        this.clock = clock;
    }

    /**
     * 오늘이 실제 지급일인 사용자에게 PAYDAY 알림을 발송하고 발송 대상 수를 반환한다. 온보딩 전(base_income=0,
     * 플레이스홀더 월급일) 사용자는 체크리스트가 없어 대상에서 제외한다.
     */
    @Transactional(readOnly = true)
    public int notifyPaydays() {
        LocalDate today = LocalDate.now(clock);
        int notified = 0;
        for (User user : userRepository.findByBaseIncomeGreaterThan(0L)) {
            if (isPaydayToday(user, today)) {
                notificationSender.send(NotificationType.PAYDAY, user.getId(), today);
                notified++;
            }
        }
        return notified;
    }

    /**
     * 오늘이 이 사용자의 실제 지급일인지 판정한다. 영업일 조정(PREV/NEXT)이 월 경계를 넘으면 명목 월급일이 속한 달과
     * 오늘이 속한 달이 어긋날 수 있으므로(예: 말일+NEXT가 다음 달 초로, 1일+PREV가 전월 말로 이동), 오늘 기준 전월·당월·
     * 익월 세 후보 명목 월의 실지급일을 보고 하나라도 오늘과 같으면 지급일로 본다. 지급일은 약 한 달 간격이라 둘 이상이
     * 동시에 오늘과 같을 수 없다.
     */
    private boolean isPaydayToday(User user, LocalDate today) {
        YearMonth thisMonth = YearMonth.from(today);
        for (YearMonth nominalMonth : List.of(thisMonth.minusMonths(1), thisMonth, thisMonth.plusMonths(1))) {
            LocalDate actualPayday =
                    paydayService.resolveActualPayday(nominalMonth, user.getPayday(), user.getPaydayAdjustment());
            if (actualPayday.equals(today)) {
                return true;
            }
        }
        return false;
    }
}
