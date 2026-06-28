package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.reminder.domain.QuarterlyCheckup;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 분기 외화 예수금 점검 알림 판정·발송(NOTI-06, "분기 1회 외화 예수금 점검"). 지급일 알림
 * ({@link PaydayNotificationService})과 같은 일일 판정 방식이다 — 매일 한 번, 오늘이 분기 점검일
 * (1·4·7·10월 1일, 순수 {@link QuarterlyCheckup})인지 보고, 그렇다면 외화 적립식(활성 INVESTMENT 항목)을
 * 가진 사용자에게 발송한다. 기준일은 주입된 KST {@code Clock}으로 산출한다(규칙 3).
 *
 * <p>외화 적립식은 환율 API 연동·일별 차감 추적 없이 버퍼로 누적 오차를 흡수하므로(ITEM-04), 분기마다
 * 예수금 점검을 유도한다 — 가계부(건별 지출 기록)를 침범하지 않는 알림 발송만이다(규칙 1). 발송 채널·중복 방지는
 * 이 서비스의 책임이 아니다 — {@link NotificationSender}(기본 로그, NOTI-05 이메일)에 위임하고, 동일
 * (user, FX_CHECKUP, 점검일) 중복 차단은 NOTI-04 게이트가 처리한다. 대상일을 오늘(점검일)로 잡고 점검일이
 * 분기당 하루뿐이라 분기당 1회만 발송된다(멱등). 본문 부가 데이터가 없어 messageArgs를 비운다 — 문장은 메시지
 * 번들이 조립한다(규칙 7). 조회만 하므로(readOnly) 쓰기가 없다.
 */
@Service
public class FxCheckupNotificationService {

    private final BudgetItemRepository budgetItemRepository;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public FxCheckupNotificationService(
            BudgetItemRepository budgetItemRepository, NotificationSender notificationSender, Clock clock) {
        this.budgetItemRepository = budgetItemRepository;
        this.notificationSender = notificationSender;
        this.clock = clock;
    }

    /**
     * 오늘이 분기 점검일이면 외화 적립식 보유 사용자에게 FX_CHECKUP 알림을 발송하고 발송 시도 건수를 반환한다.
     * 점검일이 아니면 아무 것도 하지 않는다(0). 대상일은 오늘(점검일)이라 분기당 1회만 나간다(멱등).
     */
    @Transactional(readOnly = true)
    public int notifyQuarterlyCheckups() {
        LocalDate today = LocalDate.now(clock);
        if (!QuarterlyCheckup.isCheckupDay(today)) {
            return 0;
        }
        int notified = 0;
        for (Long userId :
                budgetItemRepository.findDistinctUserIdsByStatusAndCategory(ItemStatus.ACTIVE, Category.INVESTMENT)) {
            notificationSender.send(NotificationType.FX_CHECKUP, userId, today);
            notified++;
        }
        return notified;
    }
}
