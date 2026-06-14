package com.jinhyoung.salary.envelope.infra;

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
 * 봉투(ERD envelopes, ENV-01). 비정기 지출(자동차세·명절 등)을 위해 월할 적립하는 가상 적립 단위.
 * 엔티티는 infra에 둔다(아키텍처 v1.1) — domain은 순수 계산만.
 *
 * <p>현재 상태만 보유한다(ERD 설계 축): 이름·목표액·다음 지출일·반복 주기·적립 통장과 적립액 캐시
 * (saved_amount). 월 적립액은 컬럼이 아니라 계산값 {@code ceil((target−saved) ÷ 남은 사이클)}이다(ENV-02,
 * 구현규칙 1장). 금액은 long 원 단위 — double/float 금지(규칙 2). soft delete는 status로(규칙 5),
 * 일회성 종료도 status로(CLOSED, ENV-05).
 */
@Entity
@Table(name = "envelopes")
public class Envelope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false)
    private long targetAmount;

    /** 적립액 — 트랜잭션(DEPOSIT/SPEND) 합계의 캐시값. 생성 시 0, 갱신은 체크리스트 연동·지출(ENV-04·CYCLE-07). */
    @Column(name = "saved_amount", nullable = false)
    private long savedAmount;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    /** 반복 주기(개월). NULL이면 일회성 봉투(지출 후 CLOSED 종료, ENV-05). smallint 컬럼이라 Short로 매핑. */
    @Column(name = "cycle_months")
    private Short cycleMonths;

    @Column
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnvelopeStatus status;

    protected Envelope() {
        // JPA
    }

    private Envelope(
            Long userId,
            Long accountId,
            String name,
            long targetAmount,
            LocalDate nextDueDate,
            Short cycleMonths,
            String memo) {
        this.userId = userId;
        this.accountId = accountId;
        this.name = name;
        this.targetAmount = targetAmount;
        this.savedAmount = 0L;
        this.nextDueDate = nextDueDate;
        this.cycleMonths = cycleMonths;
        this.memo = memo;
        this.status = EnvelopeStatus.ACTIVE;
    }

    /**
     * 새 봉투 생성(ENV-01). saved_amount는 0으로 시작하고 status는 ACTIVE. cycleMonths가 null이면 일회성이다.
     */
    public static Envelope create(
            Long userId,
            Long accountId,
            String name,
            long targetAmount,
            LocalDate nextDueDate,
            Short cycleMonths,
            String memo) {
        return new Envelope(userId, accountId, name, targetAmount, nextDueDate, cycleMonths, memo);
    }

    /**
     * 봉투 수정(ENV-01) — 적립 통장·이름·목표액·다음 지출일·반복 주기·메모를 갱신한다. user_id·saved_amount·
     * status는 불변이다(적립액은 트랜잭션으로만 변하고, 상태 전이는 soft delete·일회성 종료가 따로 수행).
     */
    public void update(
            Long accountId, String name, long targetAmount, LocalDate nextDueDate, Short cycleMonths, String memo) {
        this.accountId = accountId;
        this.name = name;
        this.targetAmount = targetAmount;
        this.nextDueDate = nextDueDate;
        this.cycleMonths = cycleMonths;
        this.memo = memo;
    }

    /** soft delete(ENV-01) — 상태를 DELETED로 전환한다. 행은 잔존하며 물리 삭제는 회원 탈퇴 cascade뿐(규칙 5). */
    public void markDeleted() {
        this.status = EnvelopeStatus.DELETED;
    }

    /**
     * 지출 처리 후 적립액 캐시 갱신(ENV-04). 지출 산술은 순수
     * {@link com.jinhyoung.salary.envelope.domain.EnvelopeSpend}가 계산하고 여기선 그 결과만 반영한다.
     * saved_amount만 바뀐다 — 다음 지출일 이동·적립 재시작·일회성 종료는 ENV-05 소관이라 건드리지 않는다.
     */
    public void applySpend(long savedAmount) {
        this.savedAmount = savedAmount;
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

    public String getName() {
        return name;
    }

    public long getTargetAmount() {
        return targetAmount;
    }

    public long getSavedAmount() {
        return savedAmount;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public Short getCycleMonths() {
        return cycleMonths;
    }

    public String getMemo() {
        return memo;
    }

    public EnvelopeStatus getStatus() {
        return status;
    }
}
