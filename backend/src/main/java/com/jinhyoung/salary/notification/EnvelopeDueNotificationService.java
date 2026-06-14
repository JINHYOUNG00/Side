package com.jinhyoung.salary.notification;

import com.jinhyoung.salary.envelope.domain.EnvelopeDueNotice;
import com.jinhyoung.salary.envelope.infra.Envelope;
import com.jinhyoung.salary.envelope.infra.EnvelopeRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeStatus;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 봉투 지출 시기 알림 판정·발송(NOTI-02, 요구사항 "지출일 전 준비 금액과 함께 알림"). 지급일 알림
 * ({@link PaydayNotificationService})과 같은 일일 판정 방식이다 — 매일 한 번, 다음 지출일이 알림 윈도우
 * ({@link EnvelopeDueNotice}: 오늘 ~ 오늘+LEAD_DAYS, 양끝 포함)에 든 활성 봉투를 골라 그 소유자에게 발송한다.
 * 기준일은 주입된 KST {@code Clock}으로 산출한다(규칙 3, 직접 호출 금지).
 *
 * <p>발송 채널·중복 방지는 이 서비스의 책임이 아니다 — {@link NotificationSender}(기본 로그, NOTI-05 이메일)에
 * 위임하고, 동일 (user, ENVELOPE_DUE, 다음 지출일) 알림 중복 차단은 NOTI-04 게이트가 그 포트를 감싸 처리한다.
 * <b>대상일을 봉투의 다음 지출일로 잡으므로</b>, 일일 배치가 윈도우 기간(D-LEAD~D-0) 내내 돌아도 봉투당 1회만
 * 발송된다(멱등). 다음 지출일이 같은 봉투가 한 사용자에게 둘 이상이면 멱등 키가 겹쳐 1건만 나간다(알려진 한계 —
 * 멱등 키 unique(user, type, target_date)에 봉투 식별자가 없음).
 *
 * <p>준비 금액(목표액)과 봉투명은 본문 렌더용 구조화 데이터로만 넘긴다 — 문장은 메시지 번들이 조립한다(규칙 7).
 * 조회만 하므로(readOnly) 이 서비스 자체에 쓰기가 없다.
 */
@Service
public class EnvelopeDueNotificationService {

    private final EnvelopeRepository envelopeRepository;
    private final NotificationSender notificationSender;
    private final Clock clock;

    public EnvelopeDueNotificationService(
            EnvelopeRepository envelopeRepository, NotificationSender notificationSender, Clock clock) {
        this.envelopeRepository = envelopeRepository;
        this.notificationSender = notificationSender;
        this.clock = clock;
    }

    /**
     * 다음 지출일이 알림 윈도우에 든 활성 봉투의 소유자에게 ENVELOPE_DUE 알림을 발송하고 발송 시도 건수를 반환한다.
     * 본문 인자는 [봉투명, 준비 금액(목표액)] 순 — 채널이 대상일에 이어 메시지 번들에 넘긴다.
     */
    @Transactional(readOnly = true)
    public int notifyDueEnvelopes() {
        LocalDate today = LocalDate.now(clock);
        int notified = 0;
        for (Envelope envelope : envelopeRepository.findByStatusAndNextDueDateBetween(
                EnvelopeStatus.ACTIVE, today, EnvelopeDueNotice.windowEnd(today))) {
            notificationSender.send(
                    NotificationType.ENVELOPE_DUE,
                    envelope.getUserId(),
                    envelope.getNextDueDate(),
                    envelope.getName(),
                    envelope.getTargetAmount());
            notified++;
        }
        return notified;
    }
}
