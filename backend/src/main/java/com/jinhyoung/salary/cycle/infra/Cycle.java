package com.jinhyoung.salary.cycle.infra;

import com.jinhyoung.salary.cycle.domain.CycleDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 사이클 — 월 계획 스냅샷의 헤더(ERD cycles, CYCLE-02). 엔티티는 infra에 둔다(아키텍처 v1.1) —
 * 경계 산출 자체는 순수 {@link com.jinhyoung.salary.cycle.domain.CycleResolver}가 맡는다.
 *
 * <p>{@code unique(user_id, cycle_start)}로 동일 사이클 중복 생성을 막는다(스냅샷 멱등, NFR-05).
 * 생성 후 불변이며, 변경은 plan_lines 재생성으로만 한다.
 *
 * <p><b>이 엔티티의 영속화(활성 항목·봉투 적립액으로 plan_lines까지 채우는 스냅샷 생성)는 CYCLE-03</b>
 * 소관이다. 여기서는 경계·라벨·확인 실수령액을 담는 매핑과 멱등 제약만 제공한다.
 */
@Entity
@Table(name = "cycles")
public class Cycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cycle_start", nullable = false)
    private LocalDate cycleStart;

    @Column(name = "cycle_end", nullable = false)
    private LocalDate cycleEnd;

    @Column(nullable = false)
    private String label;

    /** 확인된 실수령액. 기본값은 평소 실수령액(users.base_income) — 체크리스트에서 확인·수정(CYCLE-04). */
    @Column(nullable = false)
    private long income;

    /** 체크리스트에서 실수령액을 확인했는지(CYCLE-04). 생성 시 false. */
    @Column(name = "income_confirmed", nullable = false)
    private boolean incomeConfirmed;

    protected Cycle() {
        // JPA
    }

    private Cycle(Long userId, LocalDate cycleStart, LocalDate cycleEnd, String label, long income) {
        this.userId = userId;
        this.cycleStart = cycleStart;
        this.cycleEnd = cycleEnd;
        this.label = label;
        this.income = income;
        this.incomeConfirmed = false;
    }

    /**
     * 산출된 경계({@link CycleDefinition})와 기본 실수령액(평소 금액)으로 사이클 헤더를 만든다.
     * income_confirmed는 false로 시작한다(CYCLE-04에서 확인).
     */
    public static Cycle create(Long userId, CycleDefinition definition, long income) {
        return new Cycle(userId, definition.cycleStart(), definition.cycleEnd(), definition.label(), income);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getCycleStart() {
        return cycleStart;
    }

    public LocalDate getCycleEnd() {
        return cycleEnd;
    }

    public String getLabel() {
        return label;
    }

    public long getIncome() {
        return income;
    }

    public boolean isIncomeConfirmed() {
        return incomeConfirmed;
    }
}
