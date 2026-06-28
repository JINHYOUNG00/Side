package com.jinhyoung.salary.dataexport;

import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.dataexport.domain.DataExportSerializer;
import com.jinhyoung.salary.dataexport.domain.ExportFormat;
import com.jinhyoung.salary.dataexport.domain.ExportItem;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터 내보내기 유스케이스(DATA-02). 호출 사용자의 활성 배분 항목을 정렬 순으로 읽어 마크다운/CSV로
 * 직렬화한다(조회 전용). 소유권은 리포지토리 user_id 쿼리로 강제하고 DELETED·ARCHIVED는 자연히 제외된다 —
 * 이는 임포트(DATA-01)가 재등록하는 활성 항목 집합과 일치해 라운드트립을 일관되게 한다. 금융 식별정보는
 * 애초에 저장하지 않으므로 출력에 들어갈 수 없다(규칙 6).
 */
@Service
public class DataExportService {

    private final BudgetItemRepository budgetItemRepository;

    public DataExportService(BudgetItemRepository budgetItemRepository) {
        this.budgetItemRepository = budgetItemRepository;
    }

    @Transactional(readOnly = true)
    public String export(long userId, ExportFormat format) {
        List<ExportItem> items =
                budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ACTIVE).stream()
                        .map(item -> new ExportItem(item.getName(), item.getCategory(), item.getAmount()))
                        .toList();
        return DataExportSerializer.serialize(items, format);
    }
}
