package com.jinhyoung.salary.budgetitem.infra;

import com.jinhyoung.salary.budgetitem.domain.Category;
import com.jinhyoung.salary.budgetitem.domain.ExpectedMaturity;
import com.jinhyoung.salary.budgetitem.domain.InputCycle;
import com.jinhyoung.salary.budgetitem.domain.TaxType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 배분 항목(ERD budget_items, ITEM-01). 엔티티는 infra에 둔다(아키텍처 v1.1) — domain은 순수 계산만.
 *
 * <p>ITEM-01(생성·조회) + ITEM-02(만기일·만기 보관 전환) + ITEM-05/06(저축 조건부 필드)을 다룬다:
 * 카테고리·이름·금액·대상 통장·시작일·만기일(end_date)·연이율(interest_rate)·세금유형(tax_type)·예상 만기금액
 * 수동값(expected_maturity_amount). 일 단위 입력(ITEM-03)은 별도 요구사항이라 해당 컬럼은 매핑하지 않고
 * DB 기본값/NULL로 둔다. 금액은 long 원 단위 — double/float 금지(규칙 2). soft delete는 status로(규칙 5),
 * 만기 보관도 status로(ARCHIVED).
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

    /**
     * 금액 입력 단위(ITEM-03). MONTHLY(기본)는 amount를 그대로 입력, DAILY는 input_meta의 일 금액·빈도로
     * amount(월 환산)를 자동 계산한 항목임을 나타낸다. amount는 두 경우 모두 월 환산 금액이라 폭포·스냅샷은 불변.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "input_cycle", nullable = false, length = 10)
    private InputCycle inputCycle;

    /**
     * 원본 입력 보존(ITEM-03, ERD). DAILY 항목의 일 금액·빈도를 {@code {"dailyAmount":..,"frequency":".."}}
     * jsonb로 담아 수정 시 프리필한다. MONTHLY 항목은 null. {@link JdbcTypeCode SqlTypes.JSON}으로 매핑한다
     * (suggestions.payload 전례). 숫자는 Number로 왕복하므로 읽을 때 longValue로 해석한다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_meta", columnDefinition = "jsonb")
    private Map<String, Object> inputMeta;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** 만기일(ITEM-02). NULL이면 기한 없는 항목 — 만기 보관 대상이 아니다. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** 연이율(%)(ITEM-05). 저축 항목 예상 만기금액 공식 계산에 쓴다. NULL이면 계산하지 않는다. double 금지라 BigDecimal. */
    @Column(name = "interest_rate")
    private BigDecimal interestRate;

    /** 이자 과세 유형(ITEM-05). 예상 만기금액 공식의 세금 단계에 쓴다. NULL이면 계산하지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "tax_type")
    private TaxType taxType;

    /**
     * 예상 만기금액 수동 입력값(ITEM-06). 청년도약계좌 등 표준 공식이 적용되지 않는 특수 상품에서 사용자가 직접
     * 입력한 값으로, 있으면 공식 계산 대신 이 값을 쓴다(ERD). NULL이면 SAVING 항목은 공식으로 계산한다.
     */
    @Column(name = "expected_maturity_amount")
    private Long expectedMaturityAmount;

    /** 만기·중도해지 시 실수령액(ITEM-08). NULL이면 아직 미기록. 누적 수령 통계의 합산 대상. */
    @Column(name = "maturity_actual_amount")
    private Long maturityActualAmount;

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
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo,
            int sortOrder,
            InputCycle inputCycle,
            Map<String, Object> inputMeta) {
        this.userId = userId;
        this.accountId = accountId;
        this.category = category;
        this.name = name;
        this.amount = amount;
        this.startDate = startDate;
        this.endDate = endDate;
        this.interestRate = interestRate;
        this.taxType = taxType;
        this.expectedMaturityAmount = expectedMaturityAmount;
        this.memo = memo;
        this.sortOrder = sortOrder;
        this.inputCycle = inputCycle;
        this.inputMeta = inputMeta;
        this.status = ItemStatus.ACTIVE;
    }

    /**
     * 새 항목 생성(ITEM-01·02·03·05/06). endDate는 만기일로 NULL 허용(기한 없는 항목). interestRate·taxType·
     * expectedMaturityAmount는 저축 조건부 필드로 비저축 항목이면 NULL이다. inputCycle은 입력 단위(MONTHLY/DAILY),
     * inputMeta는 DAILY 항목의 원본 일 금액·빈도(ITEM-03, MONTHLY면 null) — amount는 호출 측이 도출한 월 환산
     * 금액이다. sortOrder는 호출 측(서비스)이 사용자별 끝자리로 부여한다. status는 ACTIVE.
     */
    public static BudgetItem create(
            Long userId,
            Long accountId,
            Category category,
            String name,
            long amount,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo,
            int sortOrder,
            InputCycle inputCycle,
            Map<String, Object> inputMeta) {
        return new BudgetItem(
                userId,
                accountId,
                category,
                name,
                amount,
                startDate,
                endDate,
                interestRate,
                taxType,
                expectedMaturityAmount,
                memo,
                sortOrder,
                inputCycle,
                inputMeta);
    }

    /** 저축 조건부 필드 없이 생성(ITEM-01·02) — 이율·세금·예상금액 수동값을 NULL, 입력 단위를 MONTHLY로 둔다. */
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
        return create(
                userId,
                accountId,
                category,
                name,
                amount,
                startDate,
                endDate,
                null,
                null,
                null,
                memo,
                sortOrder,
                InputCycle.MONTHLY,
                null);
    }

    /** 저축 조건부 필드를 받되 입력 단위는 MONTHLY로 고정 생성(ITEM-01·05/06 — 기존 호출 호환). */
    public static BudgetItem create(
            Long userId,
            Long accountId,
            Category category,
            String name,
            long amount,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo,
            int sortOrder) {
        return create(
                userId,
                accountId,
                category,
                name,
                amount,
                startDate,
                endDate,
                interestRate,
                taxType,
                expectedMaturityAmount,
                memo,
                sortOrder,
                InputCycle.MONTHLY,
                null);
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

    public InputCycle getInputCycle() {
        return inputCycle;
    }

    /** DAILY 항목의 원본 입력(일 금액·빈도) jsonb 맵 — MONTHLY면 null. 폼 프리필용(ITEM-03). */
    public Map<String, Object> getInputMeta() {
        return inputMeta;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public TaxType getTaxType() {
        return taxType;
    }

    /** 예상 만기금액 수동 입력값(ITEM-06). 공식 계산값이 아닌 사용자 입력값 — 폼 프리필·정정에 쓴다. */
    public Long getExpectedMaturityAmount() {
        return expectedMaturityAmount;
    }

    /**
     * 표시용 예상 만기금액(ITEM-05/06) — 수동 입력값이 있으면 그 값, 없으면 저축 항목 단리 공식으로 계산한다
     * (계산 불가 시 null). 보관함(ITEM-08)의 "예상 vs 실제" 등 조회 노출용. 순수 계산은 도메인이 맡는다.
     */
    public Long resolveExpectedMaturityAmount() {
        return ExpectedMaturity.resolve(
                category, amount, interestRate, startDate, endDate, taxType, expectedMaturityAmount);
    }

    public Long getMaturityActualAmount() {
        return maturityActualAmount;
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
     * 항목 수정(ITEM-07) — 카테고리·이름·금액·대상 통장·시작일·만기일·저축 조건부 필드(이율·세금·예상금액 수동값)·
     * 메모를 갱신한다. user_id·sort_order·status는
     * 불변이다. 이 수정은 <b>budget_items 원본만</b> 바꾼다 — 과거·현재 사이클의 plan_lines는 값을 복사 보유한
     * 불변 스냅샷이라(규칙 4) 영향받지 않으며, 수정값은 다음 사이클 스냅샷 생성 시 반영된다. 현재 사이클에 즉시
     * 반영하는 "이번 달 반영"은 {@link com.jinhyoung.salary.cycle.CycleSnapshotService#regenerateCurrentCycle}이
     * 별도로 수행한다(구현규칙 4장 재생성 절차).
     */
    public void update(
            Category category,
            String name,
            long amount,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo,
            InputCycle inputCycle,
            Map<String, Object> inputMeta) {
        this.category = category;
        this.name = name;
        this.amount = amount;
        this.accountId = accountId;
        this.startDate = startDate;
        this.endDate = endDate;
        this.interestRate = interestRate;
        this.taxType = taxType;
        this.expectedMaturityAmount = expectedMaturityAmount;
        this.memo = memo;
        this.inputCycle = inputCycle;
        this.inputMeta = inputMeta;
    }

    /** 저축 조건부 필드 없이 수정(ITEM-07) — 이율·세금·예상금액 수동값을 NULL, 입력 단위를 MONTHLY로 둔다. */
    public void update(
            Category category,
            String name,
            long amount,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            String memo) {
        update(category, name, amount, accountId, startDate, endDate, null, null, null, memo, InputCycle.MONTHLY, null);
    }

    /** 저축 조건부 필드를 받되 입력 단위는 MONTHLY로 고정 수정(ITEM-07 — 기존 호출 호환). */
    public void update(
            Category category,
            String name,
            long amount,
            Long accountId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal interestRate,
            TaxType taxType,
            Long expectedMaturityAmount,
            String memo) {
        update(
                category,
                name,
                amount,
                accountId,
                startDate,
                endDate,
                interestRate,
                taxType,
                expectedMaturityAmount,
                memo,
                InputCycle.MONTHLY,
                null);
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

    /**
     * 실수령액 기록(ITEM-08). 만기·중도해지 시 실제로 받은 금액(원)을 기록한다. 항목이 아직 ACTIVE면 이는
     * <b>중도해지</b>이므로 ARCHIVED로 함께 전환한다(만기 보관 배치 ITEM-02와 달리 사용자 주도, 날짜 무관).
     * 이미 ARCHIVED면 자연 만기 수령액 기록(또는 정정)으로 상태는 그대로 둔다. DELETED 항목은 서비스 단계의
     * 조회 게이트에서 걸러져 여기 도달하지 않는다. 과거 사이클 스냅샷(plan_lines)은 불변(규칙 4).
     */
    public void recordMaturityActual(long actualAmount) {
        this.maturityActualAmount = actualAmount;
        if (this.status == ItemStatus.ACTIVE) {
            this.status = ItemStatus.ARCHIVED;
        }
    }
}
