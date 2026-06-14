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
 * 봉투 적립/지출 기록(ERD envelope_transactions). 봉투의 {@code saved_amount}는 이 기록들의 합계 캐시이고,
 * 이 테이블이 변동 이력을 보존한다. 엔티티는 infra에 둔다(아키텍처 v1.1) — 지출 산술은 순수
 * {@link com.jinhyoung.salary.envelope.domain.EnvelopeSpend}가 담당한다.
 *
 * <p><b>SPEND</b>(ENV-04): {@code amount}는 지출 시점 적립액(계획 금액), {@code actualAmount}는 실제 지출액.
 * 부족하면 {@code shortfallSource}(생활비/비상금)를, 잉여면 {@code carryOver}(이월/회수)를 함께 기록한다.
 * <b>DEPOSIT</b>(CYCLE-07)은 적립으로 {@code amount}만 채운다.
 *
 * <p>{@code created_at}은 DB default(now())에 맡겨 매핑하지 않는다(plan_lines 전례). 기록 일자
 * ({@code occurredOn})는 주입된 KST {@code Clock}으로 채운다(규칙 3).
 */
@Entity
@Table(name = "envelope_transactions")
public class EnvelopeTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envelope_id", nullable = false)
    private Long envelopeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    /** 계획 금액 — SPEND 시 지출 시점의 적립액, DEPOSIT 시 적립 금액. */
    @Column(nullable = false)
    private long amount;

    /** 실제 지출액(SPEND만, DEPOSIT은 null). */
    @Column(name = "actual_amount")
    private Long actualAmount;

    /** 부족분 충당 출처 — 부족 지출일 때만 채워진다(LIVING/EMERGENCY). */
    @Enumerated(EnumType.STRING)
    @Column(name = "shortfall_source")
    private ShortfallSource shortfallSource;

    /** 잉여분 이월(true)/회수(false) — 잉여 지출일 때만 채워진다. */
    @Column(name = "carry_over")
    private Boolean carryOver;

    /** 어느 사이클의 기록인지(끊겨도 무방 — FK on delete set null). 현재 사이클이 없으면 null. */
    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    protected EnvelopeTransaction() {
        // JPA
    }

    private EnvelopeTransaction(
            Long envelopeId,
            TransactionType type,
            long amount,
            Long actualAmount,
            ShortfallSource shortfallSource,
            Boolean carryOver,
            Long cycleId,
            LocalDate occurredOn) {
        this.envelopeId = envelopeId;
        this.type = type;
        this.amount = amount;
        this.actualAmount = actualAmount;
        this.shortfallSource = shortfallSource;
        this.carryOver = carryOver;
        this.cycleId = cycleId;
        this.occurredOn = occurredOn;
    }

    /**
     * 지출 기록 생성(ENV-04). {@code amount}는 지출 시점 적립액(계획 금액), {@code actualAmount}는 실제 지출액.
     * {@code shortfallSource}는 부족 지출에서만, {@code carryOver}는 잉여 지출에서만 채워진다(나머지는 null —
     * 호출 서비스가 차액 분류로 일관성을 검증한다).
     */
    public static EnvelopeTransaction spend(
            Long envelopeId,
            long plannedAmount,
            long actualAmount,
            ShortfallSource shortfallSource,
            Boolean carryOver,
            Long cycleId,
            LocalDate occurredOn) {
        return new EnvelopeTransaction(
                envelopeId,
                TransactionType.SPEND,
                plannedAmount,
                actualAmount,
                shortfallSource,
                carryOver,
                cycleId,
                occurredOn);
    }

    public Long getId() {
        return id;
    }

    public Long getEnvelopeId() {
        return envelopeId;
    }

    public TransactionType getType() {
        return type;
    }

    public long getAmount() {
        return amount;
    }

    public Long getActualAmount() {
        return actualAmount;
    }

    public ShortfallSource getShortfallSource() {
        return shortfallSource;
    }

    public Boolean getCarryOver() {
        return carryOver;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }
}
