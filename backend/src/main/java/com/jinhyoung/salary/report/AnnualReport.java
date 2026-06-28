package com.jinhyoung.salary.report;

import com.jinhyoung.salary.budgetitem.domain.MaturityArchiveStats;
import com.jinhyoung.salary.cycle.domain.SavingsRate;

/**
 * 연간 리포트(RPT-04, 요구사항 2.7) — 한 해의 결산 메트릭 세 가지. 추이·요약(RPT-02)이 쓰는 순수 도메인을
 * <b>연 단위로 재사용</b>한다(새 계산 없음): 저축률은 그 해 사이클들의 카테고리별 계획액·실수령액으로
 * {@link SavingsRate}(SET-02)가, 만기 수령 누적은 그 해 만기(end_date)된 보관 항목 실수령액으로
 * {@link MaturityArchiveStats}(ITEM-08)가 집계하며, 봉투 집행은 그 해 SPEND 실지출 합이다.
 *
 * <p>비율은 BigDecimal(double/float 금지, 규칙 2), 문장은 만들지 않고 구조화 데이터만 싣는다(규칙 7) —
 * 문구 조립은 클라이언트 i18n이 담당한다. 신규 사용자는 모두 0/기본값으로 안전하게 응답한다.
 *
 * @param year 결산 연도(KST 기준)
 * @param savingsRate 그 해 저축률(투자 포함 토글 반영)
 * @param maturity 그 해 만기 수령 누적 통계
 * @param envelopeSpentTotal 그 해 봉투 집행(지출) 누적액(원)
 */
public record AnnualReport(int year, SavingsRate savingsRate, MaturityArchiveStats maturity, long envelopeSpentTotal) {}
