package com.jinhyoung.salary.dataexport.domain;

import com.jinhyoung.salary.budgetitem.domain.Category;

/**
 * 내보내기 한 행(DATA-02) — 배분 항목의 이름·분류·금액(월 환산, 원). 순수 데이터 캐리어로 직렬화의 입력이다.
 *
 * <p>라운드트립(내보내기→임포트 동일성)의 단위는 이름·금액이다 — 임포트(DATA-01 {@code parseImportTable})가
 * 이름·금액만 복원하고 분류는 사용자가 UI에서 재선택하기 때문이다. 분류는 사람이 보기용 칸으로만 싣는다.
 */
public record ExportItem(String name, Category category, long amount) {}
