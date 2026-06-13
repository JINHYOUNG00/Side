package com.jinhyoung.salary.cycle.domain;

import com.jinhyoung.salary.budgetitem.domain.Category;
import java.util.List;

/**
 * 폭포의 카테고리 그룹 1건 — 소계 + 그 그룹에 속한 라인(입력 순서=sort_order 보존).
 * EMERGENCY·LIVING은 group으로 만들지 않는다(API명세 3장). 항목이 없는 카테고리는
 * 애초에 group을 생성하지 않는다(빈 group 생략).
 *
 * @param category 그룹 카테고리
 * @param subtotal 그룹 내 amount 합(원, long)
 * @param lines 그룹에 속한 라인(불변 복사본, 입력 순서 보존)
 */
public record WaterfallGroup(Category category, long subtotal, List<WaterfallLine> lines) {}
