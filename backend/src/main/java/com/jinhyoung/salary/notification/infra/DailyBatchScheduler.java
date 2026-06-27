package com.jinhyoung.salary.notification.infra;

import com.jinhyoung.salary.cycle.PaydaySnapshotService;
import com.jinhyoung.salary.notification.CheckInReminderNotificationService;
import com.jinhyoung.salary.notification.EnvelopeDueNotificationService;
import com.jinhyoung.salary.notification.PaydayNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일일 배치 트리거(구현규칙 8장 일일 배치). 매일 04:00 KST에 그날 처리해야 할 단계를 아키텍처 4장 순서대로
 * 차례로 실행한다: ① 오늘이 실제 지급일인 사용자의 그 달 사이클 스냅샷 생성(CYCLE-03,
 * {@link PaydaySnapshotService}) → ② 지급일 체크리스트 알림 발송(NOTI-01, {@link PaydayNotificationService})
 * → ③ 봉투 지출 시기 알림 발송(NOTI-02, {@link EnvelopeDueNotificationService}) → ④ 월말 체크인 요청 알림 발송
 * (NOTI-03, {@link CheckInReminderNotificationService} — 다음 지급일 전일 판정). 스냅샷을 먼저 박아, 알림을
 * 받고 홈에 들어온 사용자에게 이미 체크리스트가 준비돼 있게 한다. 봉투·체크인 알림은 지급일과 무관하게 매일 판정한다
 * (각각 다음 지출일 윈도우·다음 지급일 전일 기준).
 *
 * <p>트리거는 얇게 — 각 단계의 판정·적재 로직은 해당 서비스가 가진다. 단계 순서를 한 메서드 안에서 보장하며
 * (ADR-04 단일 인스턴스), 모든 단계가 멱등이라 같은 날 재실행에도 안전하다.
 */
@Component
public class DailyBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyBatchScheduler.class);

    private final PaydaySnapshotService paydaySnapshotService;
    private final PaydayNotificationService paydayNotificationService;
    private final EnvelopeDueNotificationService envelopeDueNotificationService;
    private final CheckInReminderNotificationService checkInReminderNotificationService;

    public DailyBatchScheduler(
            PaydaySnapshotService paydaySnapshotService,
            PaydayNotificationService paydayNotificationService,
            EnvelopeDueNotificationService envelopeDueNotificationService,
            CheckInReminderNotificationService checkInReminderNotificationService) {
        this.paydaySnapshotService = paydaySnapshotService;
        this.paydayNotificationService = paydayNotificationService;
        this.envelopeDueNotificationService = envelopeDueNotificationService;
        this.checkInReminderNotificationService = checkInReminderNotificationService;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int snapshotted = paydaySnapshotService.createTodaysSnapshots();
        log.info("Daily batch — snapshot step: {} cycle snapshot(s) ensured", snapshotted);
        int notified = paydayNotificationService.notifyPaydays();
        log.info("Daily batch — payday notification step: {} user(s) notified", notified);
        int envelopeNotified = envelopeDueNotificationService.notifyDueEnvelopes();
        log.info("Daily batch — envelope-due notification step: {} envelope(s) notified", envelopeNotified);
        int checkInReminded = checkInReminderNotificationService.notifyCheckInReminders();
        log.info("Daily batch — check-in reminder step: {} user(s) reminded", checkInReminded);
    }
}
