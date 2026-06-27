package com.jinhyoung.salary.budgetitem.domain;

import java.util.List;

/**
 * 보관함 이력 통계(ITEM-08). 보관(ARCHIVED)된 항목들의 실수령액(maturity_actual_amount) 목록으로부터
 * 누적 통계를 집계한다 — 의존성 없는 순수 계산이라 domain에 둔다(아키텍처 v1.1, 규칙 9).
 *
 * <p>금액은 long 원 단위 합산만 — double/float·반올림 없음(규칙 2). 실수령액이 아직 기록되지 않은 보관
 * 항목은 {@code null}로 들어오며 누적액 합산에서 제외하되 보관 건수에는 포함한다.
 *
 * @param archivedCount 보관 항목 총 개수
 * @param recordedCount 그중 실수령액이 기록된 개수
 * @param totalReceivedAmount 만기 수령 누적액(기록된 실수령액의 합)
 */
public record MaturityArchiveStats(int archivedCount, int recordedCount, long totalReceivedAmount) {

    /**
     * 보관 항목별 실수령액 목록으로부터 통계를 집계한다. {@code null} 항목(미기록)은 누적액에서 빠지고
     * 보관 건수에만 반영된다. 빈 목록이면 모두 0.
     */
    public static MaturityArchiveStats from(List<Long> actualAmounts) {
        int recorded = 0;
        long total = 0;
        for (Long actual : actualAmounts) {
            if (actual != null) {
                recorded++;
                total += actual;
            }
        }
        return new MaturityArchiveStats(actualAmounts.size(), recorded, total);
    }
}
