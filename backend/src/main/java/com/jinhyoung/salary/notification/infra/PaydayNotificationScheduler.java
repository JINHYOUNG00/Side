package com.jinhyoung.salary.notification.infra;

import com.jinhyoung.salary.notification.PaydayNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 지급일 알림 배치 트리거(NOTI-01, 구현규칙 8장 일일 배치의 알림 판정·발송 단계). 매일 04:00 KST에 오늘이 실제
 * 지급일인 사용자를 판정해 알림을 보낸다. 트리거는 얇게 — 판정 로직은 {@link PaydayNotificationService}가 가진다.
 *
 * <p>아키텍처 4장은 알림 단계를 스냅샷 생성(CYCLE-03) 뒤에 두지만, 현재는 공휴일·만기 배치와 마찬가지로 독립
 * {@code @Scheduled}로 운용한다(ADR-04 단일 인스턴스, 모든 단계 멱등). 스냅샷 단계가 들어오면 실행 순서를
 * 보장하는 통합 러너로 묶을 수 있다.
 */
@Component
public class PaydayNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PaydayNotificationScheduler.class);

    private final PaydayNotificationService paydayNotificationService;

    public PaydayNotificationScheduler(PaydayNotificationService paydayNotificationService) {
        this.paydayNotificationService = paydayNotificationService;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int notified = paydayNotificationService.notifyPaydays();
        log.info("Payday notification batch: {} user(s) notified", notified);
    }
}
