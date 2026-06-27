package com.jinhyoung.salary.checkin.infra;

import com.jinhyoung.salary.checkin.domain.CheckInReconciliation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 월말 체크인(ERD check_ins, RPT-01) — 사이클 종료 전일에 생활비 통장 잔액과 추가 투입액을 입력받아 계획 대비
 * 실제를 기록한다. 엔티티는 infra에 둔다(아키텍처 v1.1) — 초과액 산술은 순수
 * {@link CheckInReconciliation}이 담당한다.
 *
 * <p>{@code unique(cycle_id)}로 사이클당 1건만 허용한다(ERD 3장 멱등 제약). {@code overspend}는 입력 시점
 * 계획에 의존하는 스냅샷이라 계산 후 저장한다(계산값 비저장 원칙의 예외, ERD 3장). {@code created_at}은
 * DB default(now())에 맡겨 매핑하지 않는다(envelope_transactions·plan_lines 전례).
 */
@Entity
@Table(name = "check_ins")
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    /** 입력: 사이클 종료 시점 생활비 통장 잔액. */
    @Column(name = "living_remaining", nullable = false)
    private long livingRemaining;

    /** 입력(선택, 기본 0): 사이클 중 생활비에 추가 투입한 금액. */
    @Column(name = "topped_up", nullable = false)
    private long toppedUp;

    /** 계산 저장: toppedUp − livingRemaining (양수=초과, 음수=잉여). */
    @Column(nullable = false)
    private long overspend;

    @Column
    private String note;

    protected CheckIn() {
        // JPA
    }

    private CheckIn(Long cycleId, long livingRemaining, long toppedUp, String note) {
        this.cycleId = cycleId;
        this.livingRemaining = livingRemaining;
        this.toppedUp = toppedUp;
        this.overspend = CheckInReconciliation.overspend(livingRemaining, toppedUp);
        this.note = note;
    }

    /**
     * 체크인 기록 생성(RPT-01). 초과액은 순수 {@link CheckInReconciliation}으로 계산해 저장한다 — 입력 시점
     * 계획에 의존하는 스냅샷이므로 이후 계획이 바뀌어도 재계산하지 않는다(ERD 3장).
     */
    public static CheckIn create(Long cycleId, long livingRemaining, long toppedUp, String note) {
        return new CheckIn(cycleId, livingRemaining, toppedUp, note);
    }

    public Long getId() {
        return id;
    }

    public Long getCycleId() {
        return cycleId;
    }

    public long getLivingRemaining() {
        return livingRemaining;
    }

    public long getToppedUp() {
        return toppedUp;
    }

    public long getOverspend() {
        return overspend;
    }

    public String getNote() {
        return note;
    }
}
