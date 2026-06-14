package com.jinhyoung.salary.cycle.infra;

import com.jinhyoung.salary.cycle.domain.PlanLineType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사이클 배분 라인 — 월 계획 스냅샷의 한 줄(ERD plan_lines, CYCLE-03). 엔티티는 infra에 둔다(아키텍처 v1.1).
 *
 * <p><b>불변 스냅샷</b>(규칙 4): 생성 시점의 이름·카테고리·통장 별칭·금액을 값으로 복사 보유한다. 원본
 * 항목/통장이 이후 수정·soft delete되어도 과거 사이클 기록은 영향받지 않는다(budget_item_id·account_id는
 * 참조용이며 끊겨도 무방 — FK on delete set null).
 *
 * <ul>
 *   <li><b>ITEM</b> — 활성 항목에서 파생. budget_item_id·account_id가 모두 채워지고 name/category 스냅샷은
 *       항목에서 복사. EMERGENCY 항목도 ITEM이며 category_snapshot으로 구분한다.
 *   <li><b>LIVING</b> — 폭포 나머지(생활비) 이체 1건. budget_item_id 없음, account_id=생활비 통장.
 *       뒷받침 항목이 없어 name/category 스냅샷은 머신 토큰 {@code "LIVING"}을 저장한다(규칙 7 — 서버는
 *       문장을 만들지 않고 구조화 토큰만 보관, 프론트가 i18n으로 렌더).
 * </ul>
 *
 * <p>적재는 {@link com.jinhyoung.salary.cycle.CycleSnapshotService}가 {@code unique(user_id, cycle_start)}
 * 멱등 게이트 안에서 수행한다. 상태 전이(PENDING→DONE/SKIPPED)는 CYCLE-06 소관.
 */
@Entity
@Table(name = "plan_lines")
public class PlanLine {

    /** LIVING 라인의 name/category 스냅샷 — 뒷받침 항목이 없어 line_type과 동일한 머신 토큰을 보관(규칙 7). */
    private static final String LIVING_SNAPSHOT = PlanLineType.LIVING.name();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "line_type", nullable = false)
    private PlanLineType lineType;

    /** 원본 budget_items 참조(끊겨도 무방). LIVING은 null. */
    @Column(name = "budget_item_id")
    private Long budgetItemId;

    /** 봉투(Phase 3) 참조. FLOW-03 라인은 항상 null. */
    @Column(name = "envelope_id")
    private Long envelopeId;

    /** 이체 대상 통장 — 체크리스트 group by 기준(CYCLE-06). soft delete라 참조 유지. */
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "name_snapshot", nullable = false)
    private String nameSnapshot;

    @Column(name = "category_snapshot", nullable = false)
    private String categorySnapshot;

    @Column(name = "account_name_snapshot", nullable = false)
    private String accountNameSnapshot;

    @Column(name = "planned_amount", nullable = false)
    private long plannedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanLineStatus status;

    protected PlanLine() {
        // JPA
    }

    private PlanLine(
            Long cycleId,
            PlanLineType lineType,
            Long budgetItemId,
            Long accountId,
            String nameSnapshot,
            String categorySnapshot,
            String accountNameSnapshot,
            long plannedAmount) {
        this.cycleId = cycleId;
        this.lineType = lineType;
        this.budgetItemId = budgetItemId;
        this.envelopeId = null;
        this.accountId = accountId;
        this.nameSnapshot = nameSnapshot;
        this.categorySnapshot = categorySnapshot;
        this.accountNameSnapshot = accountNameSnapshot;
        this.plannedAmount = plannedAmount;
        this.status = PlanLineStatus.PENDING;
    }

    /**
     * ITEM 라인 — 활성 항목에서 파생. 이름·카테고리·통장 별칭은 생성 시점 값을 복사해 박는다(불변 스냅샷).
     *
     * @param categorySnapshot 항목 카테고리(EMERGENCY 포함)의 이름 문자열
     */
    public static PlanLine item(
            Long cycleId,
            Long budgetItemId,
            Long accountId,
            String nameSnapshot,
            String categorySnapshot,
            String accountNameSnapshot,
            long plannedAmount) {
        return new PlanLine(
                cycleId,
                PlanLineType.ITEM,
                budgetItemId,
                accountId,
                nameSnapshot,
                categorySnapshot,
                accountNameSnapshot,
                plannedAmount);
    }

    /**
     * LIVING 라인 — 폭포 나머지(생활비)를 생활비 통장으로 이체. 뒷받침 항목이 없어 name/category 스냅샷은
     * 머신 토큰 {@code "LIVING"}으로 박는다(규칙 7). 통장 별칭만 실제 값을 복사한다.
     */
    public static PlanLine living(Long cycleId, Long livingAccountId, String accountNameSnapshot, long plannedAmount) {
        return new PlanLine(
                cycleId,
                PlanLineType.LIVING,
                null,
                livingAccountId,
                LIVING_SNAPSHOT,
                LIVING_SNAPSHOT,
                accountNameSnapshot,
                plannedAmount);
    }

    /**
     * 계획 금액 갱신 — 실수령액 확인(CYCLE-04)·"이번 달 반영" 재생성(ITEM-07) 시 LIVING 라인이 PENDING일 때만
     * 호출한다(구현규칙 3장). DONE 라인은 이미 이체된 사실이라 갱신하지 않는다(호출자가 상태를 가른다).
     */
    public void updatePlannedAmount(long plannedAmount) {
        this.plannedAmount = plannedAmount;
    }

    public Long getId() {
        return id;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public PlanLineType getLineType() {
        return lineType;
    }

    public Long getBudgetItemId() {
        return budgetItemId;
    }

    public Long getEnvelopeId() {
        return envelopeId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getNameSnapshot() {
        return nameSnapshot;
    }

    public String getCategorySnapshot() {
        return categorySnapshot;
    }

    public String getAccountNameSnapshot() {
        return accountNameSnapshot;
    }

    public long getPlannedAmount() {
        return plannedAmount;
    }

    public PlanLineStatus getStatus() {
        return status;
    }
}
