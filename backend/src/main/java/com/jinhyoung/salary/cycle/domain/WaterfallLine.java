package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.util.Objects;

/**
 * 폭포 캐스케이드 입력 단위 — 엔티티가 아닌 순수 값 객체(아키텍처.md 2장).
 *
 * <p>호출자(서비스 레이어)가 활성(ACTIVE) 항목만 sort_order로 정렬해 전달한다. budgetItemId는
 * 응답 items[]에 accountName·만기금액을 재조립하기 위한 식별자로 그대로 들고 다닌다(소계와
 * items[] 불일치 방지). LIVING은 항목 카테고리가 아니므로 여기 오지 않는다(ERD budget_items 주석).
 *
 * @param budgetItemId 원본 budget_items.id(응답 재조립용, &gt;0)
 * @param category 카테고리(EMERGENCY는 groups에서 제외되고 emergencyTotal로 집계)
 * @param amount 월 배분 금액(원, long, &gt;0)
 */
public record WaterfallLine(long budgetItemId, Category category, long amount) {

    public WaterfallLine {
        Objects.requireNonNull(category, "category");
        if (budgetItemId <= 0) {
            throw new IllegalArgumentException("budgetItemId는 0보다 커야 한다: " + budgetItemId);
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount는 0보다 커야 한다: " + amount);
        }
    }
}
