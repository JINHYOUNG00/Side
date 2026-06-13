package com.jinhyoung.salary.budgetitem.infra;

import com.jinhyoung.salary.budgetitem.MaturityArchiveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 만기 보관 배치 트리거(ITEM-02). 매일 04:00 KST에 만기 경과 항목 ARCHIVED 전환을 수행한다(구현규칙 8장
 * 일일 배치). 트리거는 얇게 — 실제 로직·트랜잭션 경계는 {@link MaturityArchiveService}가 가진다. 모든 단계는
 * 멱등이라 재실행·중복 실행에 안전하다(아키텍처 4장, ADR-04 단일 인스턴스 @Scheduled).
 */
@Component
public class MaturityArchiveScheduler {

    private static final Logger log = LoggerFactory.getLogger(MaturityArchiveScheduler.class);

    private final MaturityArchiveService maturityArchiveService;

    public MaturityArchiveScheduler(MaturityArchiveService maturityArchiveService) {
        this.maturityArchiveService = maturityArchiveService;
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
    public void run() {
        int archived = maturityArchiveService.archiveMaturedItems();
        log.info("Maturity archive batch: {} item(s) transitioned to ARCHIVED", archived);
    }
}
