package com.jinhyoung.salary.budgetitem;

import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만기 경과 항목 보관 배치 단계(ITEM-02, 아키텍처 4장 MaturityArchiveStep). 만기일(end_date)이 경과한 ACTIVE
 * 항목을 ARCHIVED로 전환한다. 기준일(today)은 주입된 KST {@code Clock}으로 산출 — {@code LocalDate.now()}
 * 직접 호출 금지(규칙 3).
 *
 * <p>멱등(규칙 8): ACTIVE 항목만 조회·전환하므로 같은 날 재실행하면 이미 ARCHIVED라 다시 잡히지 않는다.
 * 만기 1개월 전 리밸런싱 제안(REBALANCE_MATURITY, SUG-01)은 별도 요구사항·소유자 영역이라 여기서 다루지 않는다.
 */
@Service
public class MaturityArchiveService {

    private final BudgetItemRepository budgetItemRepository;
    private final Clock clock;

    public MaturityArchiveService(BudgetItemRepository budgetItemRepository, Clock clock) {
        this.budgetItemRepository = budgetItemRepository;
        this.clock = clock;
    }

    /**
     * 만기 경과 ACTIVE 항목을 ARCHIVED로 전환하고 전환 건수를 반환한다. 만기일 당일은 아직 보관하지 않으며
     * (경과=strict before) 다음 날부터 대상이 된다.
     */
    @Transactional
    public int archiveMaturedItems() {
        LocalDate today = LocalDate.now(clock);
        List<BudgetItem> candidates = budgetItemRepository.findByStatusAndEndDateNotNull(ItemStatus.ACTIVE);
        int archived = 0;
        for (BudgetItem item : candidates) {
            if (item.isMaturedAsOf(today)) {
                item.markArchived();
                archived++;
            }
        }
        return archived;
    }
}
