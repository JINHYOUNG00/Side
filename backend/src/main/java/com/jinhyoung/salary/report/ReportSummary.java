package com.jinhyoung.salary.report;

import com.jinhyoung.salary.budgetitem.domain.MaturityArchiveStats;
import com.jinhyoung.salary.cycle.domain.SavingsRate;

/**
 * 리포트 요약(RPT-02, API명세 6장 {@code GET /reports/summary}). 화면(SCR-06)이 상단 메트릭으로 쓰는
 * 세 집계를 한 응답에 담는다 — 각 집계는 이미 있는 순수 도메인을 재사용해 산정한다(중복 정의 방지).
 *
 * <ul>
 *   <li>{@code savingsRate} — 저축률(SET-02). 폭포와 같은 {@link SavingsRate} 정의(투자 포함 토글 반영)를
 *       공유한다. 폭포 조립(WaterfallQueryService)이 산정한 값을 그대로 가져온다.
 *   <li>{@code maturity} — 만기 수령 누적 통계(ITEM-08). 보관(ARCHIVED) 항목 실수령액으로부터
 *       {@link MaturityArchiveStats}가 집계한다.
 *   <li>{@code envelopeSpentTotal} — 봉투 집행(지출) 합계(원). 봉투 트랜잭션 SPEND 실제 지출액의 총합.
 * </ul>
 *
 * <p>문장은 만들지 않는다 — 구조화 데이터만 싣고 문구는 클라이언트가 i18n으로 조립한다(규칙 7).
 *
 * @param savingsRate 저축률(투자 포함 토글 반영)
 * @param maturity 만기 수령 누적 통계
 * @param envelopeSpentTotal 봉투 집행(지출) 누적액(원)
 */
public record ReportSummary(SavingsRate savingsRate, MaturityArchiveStats maturity, long envelopeSpentTotal) {}
