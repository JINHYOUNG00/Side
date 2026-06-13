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
 * <p>ITEM-01(생성·조회) + ITEM-02(만기일·만기 보관 전환)을 다룬다: 카테고리·이름·금액·대상 통장·
 * 시작일·만기일(end_date). 이율·세금(ITEM-05), 일 단위 입력(ITEM-03), 수정 적용 시점(ITEM-07)은 별도
 * 요구사항이라 해당 컬럼은 매핑하지 않고 DB 기본값/NULL로 둔다. 금액은 long 원 단위 — double/float
 * 금지(규칙 2). soft delete는 status로(규칙 5), 만기 보관도 status로(ARCHIVED).
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

    /** 만기일(ITEM-02). NULL이면 기한 없는 항목 — 만기 보관 대상이 아니다. */
    @Column(name = "end_date")
    private LocalDate endDate;

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
            LocalDate endDate,
            String memo,
            int sortOrder) {
        this.userId = userId;
        this.accountId = accountId;
        this.category = category;
        this.name = name;
        this.amount = amount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.memo = memo;
        this.sortOrder = sortOrder;
        this.status = ItemStatus.ACTIVE;
    }

    /**
     * 새 항목 생성(ITEM-01·ITEM-02). endDate는 만기일로 NULL 허용(기한 없는 항목). sortOrder는 호출 측
     * (서비스)이 사용자별 끝자리로 부여한다. status는 ACTIVE.
     */
    public static BudgetItem create(
            Long userId,
            Long accountId,
            Category category,
            String name,
            long amount,
            LocalDate startDate,
            LocalDate endDate,
            String memo,
            int sortOrder) {
        return new BudgetItem(userId, accountId, category, name, amount, startDate, endDate, memo, sortOrder);
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

    public LocalDate getEndDate() {
        return endDate;
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

    /**
     * 만기 도래(경과) 여부 판정(ITEM-02) — 만기일이 있고 그 날짜가 기준일보다 과거이면 보관 대상이다. 기준일
     * (today)은 호출 측이 주입된 KST {@code Clock}으로 산출해 넘긴다(규칙 3). 아키텍처 4장의 "end_date 경과
     * 항목"을 따라 만기일 당일은 아직 보관하지 않고(부등호 strict), 다음 날부터 대상이 된다.
     */
    public boolean isMaturedAsOf(LocalDate today) {
        return endDate != null && endDate.isBefore(today);
    }

    /**
     * 만기 보관(ITEM-02 배치) — 상태를 ARCHIVED로 전환한다. ACTIVE 항목만 전환하므로 재실행에 멱등하다
     * (이미 ARCHIVED면 배치 조회 대상에서 빠진다). 과거 사이클 스냅샷(plan_lines)은 값 복사 보유라 불변(규칙 4).
     */
    public void markArchived() {
        this.status = ItemStatus.ARCHIVED;
    }

    /**
     * soft delete(ITEM-09) — 상태를 DELETED로 전환한다. 행은 잔존하며 물리 삭제는 회원 탈퇴 cascade뿐(규칙 5).
     * 과거 사이클 스냅샷(plan_lines)은 값을 복사 보유하므로 이 전환에 영향받지 않는다(ERD, 규칙 4).
     */
    public void markDeleted() {
        this.status = ItemStatus.DELETED;
    }
}
