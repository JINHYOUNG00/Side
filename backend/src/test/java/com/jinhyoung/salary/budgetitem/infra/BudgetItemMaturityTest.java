package com.jinhyoung.salary.budgetitem.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * 만기 경과 판정(ITEM-02)의 순수 단위 테스트. {@link BudgetItem#isMaturedAsOf}는 주입된 기준일만으로
 * 결정론적으로 동작한다 — Spring·DB 없이 경계를 고정 검증한다(아키텍처 4장 "end_date 경과" = strict before).
 */
class BudgetItemMaturityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 13);

    private BudgetItem itemWithEndDate(LocalDate endDate) {
        return BudgetItem.create(1L, 1L, Category.SAVING, "정기적금", 300000, LocalDate.of(2026, 1, 1), endDate, null, 0);
    }

    @Test
    void 만기일이_기준일_이전이면_보관_대상이다() {
        assertThat(itemWithEndDate(TODAY.minusDays(1)).isMaturedAsOf(TODAY)).isTrue();
    }

    @Test
    void 만기일_당일은_아직_보관_대상이_아니다_경과는_strict_before() {
        assertThat(itemWithEndDate(TODAY).isMaturedAsOf(TODAY)).isFalse();
    }

    @Test
    void 만기일이_기준일_이후면_보관_대상이_아니다() {
        assertThat(itemWithEndDate(TODAY.plusDays(1)).isMaturedAsOf(TODAY)).isFalse();
    }

    @Test
    void 만기일이_없으면_기한_없는_항목이라_보관_대상이_아니다() {
        assertThat(itemWithEndDate(null).isMaturedAsOf(TODAY)).isFalse();
    }
}
