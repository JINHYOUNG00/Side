package com.jinhyoung.salary.budgetitem.infra;

import com.jinhyoung.salary.budgetitem.domain.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 배분 항목(ERD budget_items, ITEM-01). 엔티티는 infra에 둔다(아키텍처 v1.1) — domain은 순수 계산만.
 *
 * <p>ITEM-01은 생성·조회만 다룬다: 카테고리·이름·금액·대상 통장·시작일. 만기·이율·세금(ITEM-02/05),
 * 일 단위 입력(ITEM-03), 수정 적용 시점(ITEM-07)은 별도 요구사항이라 해당 컬럼은 매핑하지 않고 DB
 * 기본값/NULL로 둔다. 금액은 long 원 단위 — double/float 금지(규칙 2). soft delete는 status로(규칙 5).
 */
@Entity
@Table(name = "budget_items")
public class BudgetItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private long amount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column
    private String memo;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ItemStatus status;

    protected BudgetItem() {
        // JPA
    }

    private BudgetItem(
            Long userId,
            Long accountId,
            Category category,
            String name,
            long amount,
            LocalDate startDate,
            String memo,
            int sortOrder) {
        this.userId = userId;
        this.accountId = accountId;
        this.category = category;
        this.name = name;
        this.amount = amount;
        this.startDate = startDate;
        this.memo = memo;
        this.sortOrder = sortOrder;
        this.status = ItemStatus.ACTIVE;
    }

    /** 새 항목 생성(ITEM-01). sortOrder는 호출 측(서비스)이 사용자별 끝자리로 부여한다. status는 ACTIVE. */
    public static BudgetItem create(
            Long userId,
            Long accountId,
            Category category,
            String name,
            long amount,
            LocalDate startDate,
            String memo,
            int sortOrder) {
        return new BudgetItem(userId, accountId, category, name, amount, startDate, memo, sortOrder);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public Category getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public String getMemo() {
        return memo;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public ItemStatus getStatus() {
        return status;
    }
}
