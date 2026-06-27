package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.cycle.PaydayService;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월말 체크인 요청 알림 판정·발송(NOTI-03, 요구사항 "다음 지급일 전일에 월말 체크인을 요청한다"). 지급일 알림
 * ({@link PaydayNotificationService})·봉투 지출 알림({@link EnvelopeDueNotificationService})과 같은 일일 판정
 * 방식이다 — 매일 한 번, "내일이 각 사용자의 실제 지급일인가"를 {@link PaydayService}로 판정해, 그렇다면 오늘이
 * 그 사용자의 <b>다음 지급일 전일</b>(= 이번 사이클 마지막 날, CYCLE-02)이므로 CHECK_IN 알림을 보낸다. 기준일은
 * 주입된 KST {@code Clock}으로 산출한다(규칙 3, 직접 호출 금지).
 *
 * <p>"내일이 지급일인가"는 {@code resolvePaydayMonth(today+1)}로 판정한다 — 지급일 산출(영업일·공휴일·월말 조정)을
 * NOTI-01과 동일 경로로 재사용하므로, 조정으로 밀린 실지급일의 전일에도 정확히 한 번 발송된다. 지급일은 약 한 달
 * 간격이라 "내일이 지급일"인 날은 사이클당 하루뿐이다.
 *
 * <p>발송 채널·중복 방지는 이 서비스의 책임이 아니다 — {@link NotificationSender}(기본 로그, NOTI-05 이메일)에
 * 위임하고, 동일 (user, CHECK_IN, 오늘) 알림 중복 차단은 NOTI-04 게이트가 그 포트를 감싸 처리한다. 대상일을 오늘로
 * 잡고 배치도 하루 한 번이라 사이클당 1회만 발송된다(멱등). 체크인 본문은 대상일 외 부가 데이터가 없어 PAYDAY처럼
 * messageArgs를 비운다 — 문장은 메시지 번들이 조립한다(규칙 7). 조회만 하므로(readOnly) 쓰기가 없다.
 *
 * <p>온보딩 전(base_income=0, 플레이스홀더 월급일) 사용자는 사이클·체크인이 없어 대상에서 제외한다(NOTI-01과 동일).
 * 실제 체크인 기록 존재 여부는 보지 않는다 — 이 알림은 "체크인을 하라"는 요청이고, 입력 시점 중복은 RPT-01의
 * CHECK_IN_ALREADY_EXISTS가 막는다.
 */
@Service
public class CheckInReminderNotificationService {

    private final UserRepository userRepository;
    private final PaydayService paydayService;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public CheckInReminderNotificationService(
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
     * 오늘이 다음 지급일 전일인 사용자에게 CHECK_IN 알림을 발송하고 발송 시도 건수를 반환한다. 대상일은 오늘(체크인을
     * 요청하는 날)이다 — 멱등 키 (user, CHECK_IN, 오늘)로 사이클당 1회만 나간다.
     */
    @Transactional(readOnly = true)
    public int notifyCheckInReminders() {
        LocalDate today = LocalDate.now(clock);
        LocalDate tomorrow = today.plusDays(1);
        int notified = 0;
        for (User user : userRepository.findByBaseIncomeGreaterThan(0L)) {
            if (isPaydayOn(user, tomorrow)) {
                notificationSender.send(NotificationType.CHECK_IN, user.getId(), today);
                notified++;
            }
        }
        return notified;
    }

    /** 주어진 날짜가 이 사용자의 실제 지급일인지 판정한다 — NOTI-01과 동일하게 {@link PaydayService}에 위임. */
    private boolean isPaydayOn(User user, LocalDate date) {
        return paydayService
                .resolvePaydayMonth(date, user.getPayday(), user.getPaydayAdjustment())
                .isPresent();
    }
}
