package com.jinhyoung.salary.notification.infra;

import com.jinhyoung.salary.cycle.PaydaySnapshotService;
import com.jinhyoung.salary.notification.PaydayNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지급일 일일 배치 트리거(구현규칙 8장 일일 배치). 매일 04:00 KST에 오늘이 실제 지급일인 사용자를 판정해
 * 아키텍처 4장 순서대로 두 단계를 차례로 실행한다: ① 그 달 사이클 스냅샷 생성(CYCLE-03,
 * {@link PaydaySnapshotService}) → ② 체크리스트 알림 발송(NOTI-01, {@link PaydayNotificationService}).
 * 스냅샷을 먼저 박아, 알림을 받고 홈에 들어온 사용자에게 이미 체크리스트가 준비돼 있게 한다.
 *
 * <p>트리거는 얇게 — 두 단계의 판정·적재 로직은 각 서비스가 가진다. 단계 순서를 한 메서드 안에서 보장하며
 * (ADR-04 단일 인스턴스), 두 단계 모두 멱등이라 같은 날 재실행에도 안전하다.
 */
@Component
public class PaydayBatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaydayBatchScheduler.class);

    private final PaydaySnapshotService paydaySnapshotService;
    private final PaydayNotificationService paydayNotificationService;

    public PaydayBatchScheduler(
            PaydaySnapshotService paydaySnapshotService, PaydayNotificationService paydayNotificationService) {
        this.paydaySnapshotService = paydaySnapshotService;
        this.paydayNotificationService = paydayNotificationService;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int snapshotted = paydaySnapshotService.createTodaysSnapshots();
        log.info("Payday batch — snapshot step: {} cycle snapshot(s) ensured", snapshotted);
        int notified = paydayNotificationService.notifyPaydays();
        log.info("Payday batch — notification step: {} user(s) notified", notified);
    }
}
